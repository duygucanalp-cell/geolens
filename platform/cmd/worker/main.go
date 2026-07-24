package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"sync"
	"syscall"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/joho/godotenv"
	"github.com/redis/go-redis/v9"

	"github.com/geolens/platform/engine"
	"github.com/geolens/platform/engine/chatgpt"
	"github.com/geolens/platform/engine/gemini"
	"github.com/geolens/platform/engine/perplexity"
	"github.com/geolens/platform/internal/config"
	"github.com/geolens/platform/internal/measure"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/metrics"
	"github.com/geolens/platform/platform/queue"
	"github.com/geolens/platform/platform/storage"
	"github.com/geolens/platform/platform/telemetry"
)

const consumerName = "worker-1"

func main() {
	_ = godotenv.Load()
	cfg := config.LoadFromEnv()
	slog.SetDefault(slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: cfg.LogLevel})))

	shutdown, err := telemetry.InitOTel(context.Background(), cfg)
	if err != nil {
		slog.Error("opentelemetry başlatılamadı", "error", err)
		os.Exit(1)
	}
	defer shutdown()

	pool, err := db.NewPool(context.Background(), cfg.DatabaseURL)
	if err != nil {
		slog.Error("veritabanı bağlantısı kurulamadı", "error", err)
		os.Exit(1)
	}
	defer pool.Close()

	rdb, err := queue.NewRedisClient(cfg.RedisURL)
	if err != nil {
		slog.Error("redis bağlantısı kurulamadı", "error", err)
		os.Exit(1)
	}
	defer rdb.Close()

	// S3 Storage
	s3Storage, err := storage.NewClient(cfg.S3Endpoint, cfg.S3AccessKey, cfg.S3SecretKey, cfg.S3Bucket, cfg.S3Region, false)
	if err != nil {
		slog.Warn("S3 istemci oluşturulamadı, storage olmadan çalışılacak", "error", err)
	}

	// Engine registry
	engines := engine.NewRegistry()

	// Ortak RawSaver: nil-hatasız storage backend
	var saver engine.RawSaver
	if err == nil {
		saver = s3Storage
	}

	// Perplexity (Kademe 1)
	perplexityAdapter := perplexity.NewAdapter(cfg.PerplexityAPIKey, saver)
	engines.Register(perplexityAdapter)

	// ChatGPT / OpenAI (Kademe 1)
	chatgptAdapter := chatgpt.NewAdapter(cfg.ChatGPTAPIKey, saver)
	engines.Register(chatgptAdapter)

	// Gemini / Google AI (Kademe 1)
	geminiAdapter := gemini.NewAdapter(cfg.GeminiAPIKey, saver)
	engines.Register(geminiAdapter)

	slog.Info("motor kayıt defteri hazır", "engine_count", engines.Count(), "engines", engines.List())

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Redis Stream consumer group'u oluştur (yoksa)
	for _, s := range []string{queue.StreamMeasure} {
		if err := rdb.XGroupCreateMkStream(ctx, s, cfg.ConsumerGroup, "0").Err(); err != nil {
			slog.Warn("redis stream grubu oluşturma", "stream", s, "error", err)
		} else {
			slog.Info("redis stream grubu oluşturuldu", "stream", s, "group", cfg.ConsumerGroup)
		}
	}

	var wg sync.WaitGroup
	wg.Add(1)
	go func() {
		defer wg.Done()
		runWorker(ctx, pool.Pool, rdb, engines, saver, cfg.ConsumerGroup, cfg.ConsumerGroup)
	}()

	slog.Info("worker başlatılıyor", "consumer_group", cfg.ConsumerGroup)

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	slog.Info("worker kapatılıyor...")
	cancel()
	wg.Wait()
	slog.Info("worker durduruldu")
}

// streamMessage represents a message from Redis Stream.
type streamMessage struct {
	ID       string
	Event    string
	TenantID string
	Data     map[string]interface{}
}

// runWorker continuously reads from Redis Stream and processes measurement jobs.
func runWorker(ctx context.Context, pool *pgxpool.Pool, rdb *redis.Client, engines *engine.Registry, s3Client engine.RawSaver, consumerGroup, ackGroup string) {
	for {
		select {
		case <-ctx.Done():
			return
		default:
			// Redis Stream'den mesaj oku (BLOCK ile bekle)
			results, err := rdb.XReadGroup(ctx, &redis.XReadGroupArgs{
				Group:    consumerGroup,
				Consumer: consumerName,
				Streams:  []string{queue.StreamMeasure, ">"},
				Count:    10,
				Block:    5 * time.Second,
			}).Result()

			if err != nil && err != redis.Nil {
				if isNoGroupError(err) {
					// Stream veya consumer group silinmiş olabilir, yeniden oluştur
					slog.Warn("redis stream grubu bulunamadı, yeniden oluşturuluyor", "error", err)
					for _, s := range []string{queue.StreamMeasure} {
						_ = rdb.XGroupCreateMkStream(ctx, s, consumerGroup, "0").Err()
					}
				} else {
					slog.Error("redis stream okuma hatası", "error", err)
				}
				time.Sleep(1 * time.Second)
				continue
			}

			if err == redis.Nil || len(results) == 0 {
				continue
			}

			for _, stream := range results {
				for _, msg := range stream.Messages {
					processMessage(ctx, pool, rdb, engines, s3Client, stream.Stream, msg.ID, msg.Values, ackGroup)
				}
			}
		}
	}
}

// processMessage processes a single Redis Stream message.
func processMessage(
	ctx context.Context,
	pool *pgxpool.Pool,
	rdb *redis.Client,
	engines *engine.Registry,
	s3Client engine.RawSaver,
	stream, msgID string,
	values map[string]interface{},
	consumerGroup string,
) {
	logger := slog.With("msg_id", msgID, "stream", stream)

	// Mesajı ayrıştır
	var dataStr string
	if v, ok := values["data"]; ok {
		dataStr = fmt.Sprintf("%v", v)
	} else {
		logger.Warn("worker: data alanı eksik")
		ackMessage(rdb, stream, msgID, consumerGroup)
		return
	}

	var msgData map[string]interface{}
	if err := json.Unmarshal([]byte(dataStr), &msgData); err != nil {
		logger.Warn("worker: data ayrıştırma hatası", "error", err)
		ackMessage(rdb, stream, msgID, consumerGroup)
		return
	}

	// Event tipini kontrol et
	eventType, _ := values["event"].(string)
	if eventType != "measurement.requested" {
		// Tanınmayan event tipi — yine de ACK'le
		ackMessage(rdb, stream, msgID, consumerGroup)
		return
	}

	// Payload'ı ayrıştır
	payloadRaw, ok := msgData["payload"]
	if !ok {
		logger.Warn("worker: payload alanı eksik")
		ackMessage(rdb, stream, msgID, consumerGroup)
		return
	}

	payloadJSON, err := json.Marshal(payloadRaw)
	if err != nil {
		logger.Warn("worker: payload marshal hatası", "error", err)
		ackMessage(rdb, stream, msgID, consumerGroup)
		return
	}

	var job measure.JobPayload
	if err := json.Unmarshal(payloadJSON, &job); err != nil {
		logger.Warn("worker: job payload ayrıştırma hatası", "error", err)
		ackMessage(rdb, stream, msgID, consumerGroup)
		return
	}

	logger = logger.With("brand", job.BrandName, "engine", job.EngineName, "sample", job.SampleIndex)
	logger.Info("worker: işleniyor")

	// Engine adapter'ını al
	adapter := engines.Get(job.EngineName)
	if adapter == nil {
		logger.Warn("worker: motor bulunamadı")
		// Dead letter queue'ya yönlendir
		sendToDeadLetter(rdb, msgID, job, fmt.Sprintf("engine %s not found", job.EngineName))
		ackMessage(rdb, stream, msgID, consumerGroup)
		return
	}

	// Tenant/workspace context
	type contextualEngine interface {
		WithContext(tenantID, workspaceID string) engine.Adapter
	}
	if ce, ok := adapter.(contextualEngine); ok {
		adapter = ce.WithContext(job.TenantID, job.WorkspaceID)
	}

	// Engine çağrısı yap
	start := time.Now()
	result, err := adapter.Execute(job.PromptText)
	duration := time.Since(start)

	// Engine metriklerini kaydet
	metrics.EngineCallsTotal.WithLabelValues(job.EngineName, job.TenantID).Inc()
	metrics.EngineCallDuration.WithLabelValues(job.EngineName).Observe(duration.Seconds())
	if result != nil {
		metrics.EngineResponseSize.WithLabelValues(job.EngineName).Observe(float64(len(result.Content)))
	}

	if err != nil {
		logger.Error("worker: engine çağrı hatası", "error", err, "duration", duration)
		metrics.EngineCallsFailed.WithLabelValues(job.EngineName, job.TenantID).Inc()
		metrics.QueueMessagesFailed.WithLabelValues(stream).Inc()
		sendToDeadLetter(rdb, msgID, job, err.Error())
		ackMessage(rdb, stream, msgID, consumerGroup)
		return
	}

	logger.Info("worker: engine yanıtı alındı",
		"duration", duration,
		"citations", len(result.Citations),
	)

	// Ham yanıtı S3'e kaydet (storage varsa)
	var s3Ref string
	if s3Client != nil {
		rawJSON, _ := json.Marshal(result)
		key, saveErr := s3Client.SaveRawResponse(ctx, job.TenantID, job.WorkspaceID, job.EngineName, rawJSON)
		if saveErr != nil {
			logger.Warn("worker: S3 kaydetme hatası", "error", saveErr)
		} else {
			s3Ref = key
		}
	}

	// measurement_jobs tablosuna kaydet (idempotent: conflict'te güncelle, her durumda id döner)
	// msgID Redis mesaj ID'sidir, her mesaj için unique — idempotency key'i unique yapar
	idempotencyKey := fmt.Sprintf("worker:%s:%s:%d:%s", job.BrandID, job.EngineName, job.SampleIndex, msgID)
	var jobID string
	err = pool.QueryRow(ctx, `
		INSERT INTO measure.measurement_jobs (id, brand_id, panel_id, engine_name, status, tenant_id, workspace_id, prompt_text, sample_count, idempotency_key, created_at)
		VALUES (gen_random_uuid()::text, $1, $2, $3, 'completed', $4, $5, '', 3, $6, now())
		ON CONFLICT (idempotency_key) DO UPDATE SET status = 'completed', updated_at = now()
		RETURNING id
	`, job.BrandID, job.PanelID, job.EngineName, job.TenantID, job.WorkspaceID, idempotencyKey).Scan(&jobID)
	if err != nil {
		logger.Error("worker: measurement_job kaydetme hatası", "error", err)
	}

	// Ham yanıtı raw_responses tablosuna kaydet (sadece job kaydı başarılıysa)
	if jobID != "" {
		_, err = pool.Exec(ctx, `
			INSERT INTO measure.raw_responses (id, job_id, engine_name, raw_body, content_text, s3_ref, tenant_id, created_at)
			VALUES ($1, $2, $3, $4, $5, $6, $7, now())
		`, generateID(), jobID, job.EngineName, result.Content, result.Content, s3Ref, job.TenantID)
		if err != nil {
			logger.Error("worker: raw_response kaydetme hatası", "error", err)
		}
	}

	// Kuyruk metriklerini güncelle
	metrics.QueueMessagesConsumed.WithLabelValues(stream).Inc()
	metrics.QueueMessageProcessingDuration.WithLabelValues(stream).Observe(duration.Seconds())

	// Redis Stream'den ACK'le
	ackMessage(rdb, stream, msgID, consumerGroup)
	logger.Info("worker: iş tamamlandı")
}

// isGroupAlreadyExists checks if a Redis error is BUSYGROUP (group already exists).
// isNoGroupError checks if the error is NOGROUP (stream or group doesn't exist).
func isNoGroupError(err error) bool {
	if err == nil {
		return false
	}
	errStr := err.Error()
	return len(errStr) >= 7 && errStr[:7] == "NOGROUP"
}

func isGroupAlreadyExists(err error) bool {
	if err == nil {
		return false
	}
	errStr := err.Error()
	return errStr == "BUSYGROUP Consumer Group name already exists" ||
		errStr == "BUSYGROUP consumer group already exists" ||
		len(errStr) >= 9 && errStr[:9] == "BUSYGROUP"
}

// ackMessage acknowledges a message from Redis Stream.
func ackMessage(rdb *redis.Client, stream, msgID, consumerGroup string) {
	if err := rdb.XAck(context.Background(), stream, consumerGroup, msgID).Err(); err != nil {
		slog.Warn("worker: XAck hatası", "stream", stream, "msg_id", msgID, "error", err)
	}
}

// sendToDeadLetter sends a failed message to the dead letter queue.
func sendToDeadLetter(rdb *redis.Client, msgID string, job measure.JobPayload, reason string) {
	data, _ := json.Marshal(map[string]interface{}{
		"original_msg_id": msgID,
		"job":             job,
		"reason":          reason,
		"timestamp":       time.Now().UTC().Format(time.RFC3339),
	})

	if err := rdb.XAdd(context.Background(), &redis.XAddArgs{
		Stream: queue.StreamDead,
		Values: map[string]interface{}{
			"event": "measurement.failed",
			"data":  string(data),
		},
	}).Err(); err != nil {
		slog.Error("worker: dead letter gönderme hatası", "error", err)
	}
}

// generateID creates a simple unique ID for DB records.
func generateID() string {
	now := time.Now().UnixMicro()
	return fmt.Sprintf("%d-%d", now, time.Now().Nanosecond()%10000)
}
