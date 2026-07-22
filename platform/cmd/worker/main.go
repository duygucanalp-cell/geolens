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
	"github.com/geolens/platform/internal/config"
	"github.com/geolens/platform/internal/measure"
	"github.com/geolens/platform/platform/db"
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
	s3Client, err := storage.NewClient(cfg.S3Endpoint, cfg.S3AccessKey, cfg.S3SecretKey, cfg.S3Bucket, cfg.S3Region, false)
	if err != nil {
		slog.Warn("S3 istemci oluşturulamadı, storage olmadan çalışılacak", "error", err)
	}

	// Engine registry
	engines := engine.NewRegistry()
	slog.Info("motor kayıt defteri hazır", "engine_count", engines.Count(), "engines", engines.List())

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	var wg sync.WaitGroup
	wg.Add(1)
	go func() {
		defer wg.Done()
		runWorker(ctx, pool.Pool, rdb, engines, s3Client)
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
func runWorker(ctx context.Context, pool *pgxpool.Pool, rdb *redis.Client, engines *engine.Registry, s3Client interface{ SaveRawResponse(ctx context.Context, tenantID, workspaceID, engineName string, data []byte) (string, error) }) {
	for {
		select {
		case <-ctx.Done():
			return
		default:
			// Redis Stream'den mesaj oku (BLOCK ile bekle)
			results, err := rdb.XReadGroup(ctx, &redis.XReadGroupArgs{
				Group:    "workers",
				Consumer: consumerName,
				Streams:  []string{queue.StreamMeasure, ">"},
				Count:    10,
				Block:    5 * time.Second,
			}).Result()

			if err != nil && err != redis.Nil {
				slog.Error("redis stream okuma hatası", "error", err)
				time.Sleep(1 * time.Second)
				continue
			}

			if err == redis.Nil || len(results) == 0 {
				continue
			}

			for _, stream := range results {
				for _, msg := range stream.Messages {
					processMessage(ctx, pool, rdb, engines, s3Client, stream.Stream, msg.ID, msg.Values)
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
	s3Client interface{ SaveRawResponse(context.Context, string, string, string, []byte) (string, error) },
	stream, msgID string,
	values map[string]interface{},
) {
	logger := slog.With("msg_id", msgID, "stream", stream)

	// Mesajı ayrıştır
	var dataStr string
	if v, ok := values["data"]; ok {
		dataStr = fmt.Sprintf("%v", v)
	} else {
		logger.Warn("worker: data alanı eksik")
		ackMessage(rdb, stream, msgID)
		return
	}

	var msgData map[string]interface{}
	if err := json.Unmarshal([]byte(dataStr), &msgData); err != nil {
		logger.Warn("worker: data ayrıştırma hatası", "error", err)
		ackMessage(rdb, stream, msgID)
		return
	}

	// Event tipini kontrol et
	eventType, _ := values["event"].(string)
	if eventType != "measurement.requested" {
		// Tanınmayan event tipi — yine de ACK'le
		ackMessage(rdb, stream, msgID)
		return
	}

	// Payload'ı ayrıştır
	payloadRaw, ok := msgData["payload"]
	if !ok {
		logger.Warn("worker: payload alanı eksik")
		ackMessage(rdb, stream, msgID)
		return
	}

	payloadJSON, err := json.Marshal(payloadRaw)
	if err != nil {
		logger.Warn("worker: payload marshal hatası", "error", err)
		ackMessage(rdb, stream, msgID)
		return
	}

	var job measure.JobPayload
	if err := json.Unmarshal(payloadJSON, &job); err != nil {
		logger.Warn("worker: job payload ayrıştırma hatası", "error", err)
		ackMessage(rdb, stream, msgID)
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
		ackMessage(rdb, stream, msgID)
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

	if err != nil {
		logger.Error("worker: engine çağrı hatası", "error", err, "duration", duration)
		sendToDeadLetter(rdb, msgID, job, err.Error())
		ackMessage(rdb, stream, msgID)
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

	// measurement_jobs tablosuna kaydet
	jobID := generateID()
	_, err = pool.Exec(ctx, `
		INSERT INTO measure.measurement_jobs (id, brand_id, brand_name, panel_id, engine_name, sample_index, status, raw_response_ref, tenant_id, workspace_id, duration_ms, created_at)
		VALUES ($1, $2, $3, $4, $5, $6, 'completed', $7, $8, $9, $10, now())
	`, jobID, job.BrandID, job.BrandName, job.PanelID, job.EngineName, job.SampleIndex, s3Ref, job.TenantID, job.WorkspaceID, duration.Milliseconds())
	if err != nil {
		logger.Error("worker: measurement_job kaydetme hatası", "error", err)
	}

	// Ham yanıtı raw_responses tablosuna kaydet
	_, err = pool.Exec(ctx, `
		INSERT INTO measure.raw_responses (id, job_id, engine_name, model_version, content, s3_ref, fidelity_label, duration_ms, tenant_id, created_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, now())
	`, generateID(), jobID, job.EngineName, result.FidelityLabel, result.Content, s3Ref, result.FidelityLabel, duration.Milliseconds(), job.TenantID)
	if err != nil {
		logger.Error("worker: raw_response kaydetme hatası", "error", err)
	}

	// Redis Stream'den ACK'le
	ackMessage(rdb, stream, msgID)
	logger.Info("worker: iş tamamlandı")
}

// ackMessage acknowledges a message from Redis Stream.
func ackMessage(rdb *redis.Client, stream, msgID string) {
	if err := rdb.XAck(context.Background(), stream, "workers", msgID).Err(); err != nil {
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
