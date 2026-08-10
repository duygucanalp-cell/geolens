// Package main is the entry point for the GeoLens background worker.
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
	"github.com/geolens/platform/engine/claude"
	"github.com/geolens/platform/engine/copilot"
	"github.com/geolens/platform/engine/gemini"
	"github.com/geolens/platform/engine/grok"
	"github.com/geolens/platform/engine/perplexity"
	"github.com/geolens/platform/internal/benchmark"
	"github.com/geolens/platform/internal/competitive"
	"github.com/geolens/platform/internal/config"
	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/internal/delivery"
	"github.com/geolens/platform/internal/id"
	"github.com/geolens/platform/internal/measure"
	"github.com/geolens/platform/internal/recommendation"
	"github.com/geolens/platform/internal/sentiment"
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
		return
	}
	defer shutdown()

	pool, err := db.NewPool(context.Background(), cfg.DatabaseURL)
	if err != nil {
		slog.Error("veritabanı bağlantısı kurulamadı", "error", err)
		return
	}
	pool.Close()

	rdb, err := queue.NewRedisClient(cfg.RedisURL)
	if err != nil {
		slog.Error("redis bağlantısı kurulamadı", "error", err)
		return
	}
	defer func() { _ = rdb.Close() }()

	// S3 Storage
	s3Client, err := storage.NewClient(cfg.S3Endpoint, cfg.S3AccessKey, cfg.S3SecretKey, cfg.S3Bucket, cfg.S3Region, false)
	if err != nil {
		slog.Warn("S3 istemci oluşturulamadı, storage olmadan çalışılacak", "error", err)
	}

	// Ortak RawSaver: nil-hatasız storage backend
	// Crypto-shredding: STORAGE_MASTER_KEY varsa EncryptedClient, yoksa plain Client kullan
	var saver engine.RawSaver
	if err == nil {
		if cfg.StorageMasterKey != "" {
			encClient, encryptErr := storage.NewEncryptedClient(s3Client, cfg.StorageMasterKey)
			if encryptErr != nil {
				slog.Warn("EncryptedClient oluşturulamadı, şifresiz storage kullanılacak", "error", encryptErr)
				saver = s3Client
			} else {
				saver = encClient
				slog.Info("kripto-silme etkin: S3 verileri AES-256-GCM şifreli")
			}
		} else {
			saver = s3Client
		}
	}

	// Engine registry
	engines := engine.NewRegistry()

	// Perplexity (Kademe 1)
	perplexityAdapter := perplexity.NewAdapter(cfg.PerplexityAPIKey, saver)
	engines.Register(perplexityAdapter)

	// ChatGPT / OpenAI (Kademe 1)
	chatgptAdapter := chatgpt.NewAdapter(cfg.ChatGPTAPIKey, saver)
	engines.Register(chatgptAdapter)

	// Gemini / Google AI (Kademe 1)
	geminiAdapter := gemini.NewAdapter(cfg.GeminiAPIKey, saver)
	engines.Register(geminiAdapter)

	// Google AI Overview + Google AI Mode (Kademe 3 — directional) — FR-B6 HT2 genişletmesi
	engines.Register(geminiAdapter.WithAIOverview("", ""))
	engines.Register(geminiAdapter.WithAIMode("", ""))

	// Claude / Anthropic (Kademe 2)
	claudeAdapter := claude.NewAdapter(cfg.ClaudeAPIKey, saver)
	engines.Register(claudeAdapter)

	// Grok / xAI (Kademe 2)
	grokAdapter := grok.NewAdapter(cfg.GrokAPIKey, saver)
	engines.Register(grokAdapter)

	// Copilot / Microsoft (Kademe 3)
	copilotAdapter := copilot.NewAdapter(cfg.CopilotAPIKey, saver)
	engines.Register(copilotAdapter)

	slog.Info("motor kayıt defteri hazır", "engine_count", engines.Count(), "engines", engines.List())

	// Delivery servisi (bildirim gönderimi)
	emailCfg := delivery.EmailConfig{
		FromName:    cfg.SendGridFromName,
		FromEmail:   cfg.SendGridFromEmail,
		SendGridKey: cfg.SendGridAPIKey,
	}
	deliverySvc := delivery.NewService(emailCfg, pool)

	// Ölçüm servisi (skor hesaplama)
	measureSvc := measure.NewService(pool, engines, &cfg)

	// Tavsiye servisi (kural değerlendirme)
	recommendationSvc := recommendation.NewService(pool)

	// AI Analiz motorları (sentiment, competitive gap)
	sentimentEngine := sentiment.NewEngine(pool)
	competitiveEngine := competitive.NewEngine(pool)

	// Benchmark sektör istatistikleri toplayıcı (FR-D5/C)
	benchmarkAggregator := benchmark.NewAggregator(dbiface.NewAdapter(pool), nil)
	// Benchmark collector ticker-based'dir, Redis Stream gerektirmez
	benchmarkCollector := benchmark.NewCollector(benchmarkAggregator, 1*time.Hour)

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Redis Stream consumer group'u oluştur (yoksa)
	analysisStreams := []string{queue.StreamSentiment, queue.StreamReplay, queue.StreamArchive, queue.StreamGap, queue.StreamTechnicalGeo, queue.StreamContentGeo, queue.StreamGovernance}
	for _, s := range append([]string{queue.StreamMeasure}, analysisStreams...) {
		if err := rdb.XGroupCreateMkStream(ctx, s, cfg.ConsumerGroup, "0").Err(); err != nil {
			if !isGroupAlreadyExists(err) {
				slog.Warn("redis stream grubu oluşturma", "stream", s, "error", err)
			}
		} else {
			slog.Info("redis stream grubu oluşturuldu", "stream", s, "group", cfg.ConsumerGroup)
		}
	}

	var wg sync.WaitGroup

	// Ana worker goroutine (mesaj işleme)
	wg.Add(1)
	go func() {
		defer wg.Done()
		runWorker(ctx, pool.Pool, rdb, engines, saver, cfg.ConsumerGroup, cfg.ConsumerGroup, measureSvc, recommendationSvc, deliverySvc, sentimentEngine, competitiveEngine)
	}()

	// Faz 4 yönetişim olay tüketicisi (q:governance — guardrail, gate, incident, drift, redteam)
	wg.Add(1)
	go func() {
		defer wg.Done()
		runGovernanceWorker(ctx, rdb, cfg.ConsumerGroup)
	}()

	// Queue depth collector (periyodik XLEN ile kuyruk derinliği metrikleri)
	wg.Add(1)
	go func() {
		defer wg.Done()
		runQueueDepthCollector(ctx, rdb)
	}()

	// Account metrics collector (periyodik DB sorguları ile iş metrikleri)
	wg.Add(1)
	go func() {
		defer wg.Done()
		runAccountMetricsCollector(ctx, pool.Pool)
	}()

	// Benchmark aggregator (periyodik sektör istatistikleri toplulaştırması)
	wg.Add(1)
	go func() {
		defer wg.Done()
		if err := benchmarkCollector.Run(ctx); err != nil && err != context.Canceled && err != context.DeadlineExceeded {
			slog.Warn("benchmark toplayıcı beklenmeyen hata ile durdu", "error", err)
		}
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

// runWorker continuously reads from Redis Stream and processes measurement jobs.
func runWorker(ctx context.Context, pool *pgxpool.Pool, rdb *redis.Client, engines *engine.Registry, s3Client engine.RawSaver, consumerGroup, ackGroup string, measureSvc measure.Service, recSvc recommendation.Service, deliverySvc delivery.Service, sentimentEng *sentiment.Engine, competitiveEng *competitive.Engine) {
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
					for _, s := range append([]string{queue.StreamMeasure}, []string{queue.StreamSentiment, queue.StreamReplay, queue.StreamArchive, queue.StreamGap, queue.StreamTechnicalGeo, queue.StreamContentGeo}...) {
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
					processMessage(ctx, pool, rdb, engines, s3Client, stream.Stream, msg.ID, msg.Values, ackGroup, measureSvc, recSvc, deliverySvc, sentimentEng, competitiveEng)
				}
			}
		}
	}
}

// runGovernanceWorker consumes Faz 4 governance events from q:governance (O-6).
// Olaylar kaynak tablolarında (guardrail.evaluations, drift.alerts, vb.) zaten kalıcıdır;
// bu tüketici telemetri toplar (GovernanceEventsTotal) ve mesajları ACK'ler.
// Gelecekte webhook/bildirim tüketicisi buraya eklenebilir.
func runGovernanceWorker(ctx context.Context, rdb *redis.Client, consumerGroup string) {
	for {
		select {
		case <-ctx.Done():
			return
		default:
			results, err := rdb.XReadGroup(ctx, &redis.XReadGroupArgs{
				Group:    consumerGroup,
				Consumer: consumerName + "-governance",
				Streams:  []string{queue.StreamGovernance, ">"},
				Count:    10,
				Block:    5 * time.Second,
			}).Result()

			if err != nil && err != redis.Nil {
				if isNoGroupError(err) {
					slog.Warn("governance stream grubu bulunamadı, yeniden oluşturuluyor", "error", err)
					_ = rdb.XGroupCreateMkStream(ctx, queue.StreamGovernance, consumerGroup, "0").Err()
				} else {
					slog.Error("governance stream okuma hatası", "error", err)
				}
				time.Sleep(1 * time.Second)
				continue
			}

			if err == redis.Nil || len(results) == 0 {
				continue
			}

			for _, stream := range results {
				for _, msg := range stream.Messages {
					processGovernanceMessage(rdb, stream.Stream, msg.ID, msg.Values, consumerGroup)
				}
			}
		}
	}
}

// processGovernanceMessage processes a single q:governance message: metrik toplar ve ACK'ler.
func processGovernanceMessage(rdb *redis.Client, stream, msgID string, values map[string]interface{}, consumerGroup string) {
	logger := slog.With("msg_id", msgID, "stream", stream)

	eventType, _ := values["event"].(string)
	tenantID, _ := values["tenant_id"].(string)

	if eventType == "" {
		logger.Warn("governance: event tipi eksik")
		ackMessage(rdb, stream, msgID, consumerGroup)
		return
	}

	metrics.GovernanceEventsTotal.WithLabelValues(eventType, tenantID).Inc()
	logger.Debug("governance olayı işlendi", "event_type", eventType, "tenant_id", tenantID)

	ackMessage(rdb, stream, msgID, consumerGroup)
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
	measureSvc measure.Service,
	recSvc recommendation.Service,
	deliverySvc delivery.Service,
	sentimentEng *sentiment.Engine,
	competitiveEng *competitive.Engine,
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
	result, err := adapter.Execute(ctx, job.PromptText)
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
		`, id.New(), jobID, job.EngineName, result.Content, result.Content, s3Ref, job.TenantID)
		if err != nil {
			logger.Error("worker: raw_response kaydetme hatası", "error", err)
		}
	}

	// Kuyruk metriklerini güncelle
	metrics.QueueMessagesConsumed.WithLabelValues(stream).Inc()
	metrics.QueueMessageProcessingDuration.WithLabelValues(stream).Observe(duration.Seconds())

	// Redis Stream'den ACK'le
	ackMessage(rdb, stream, msgID, consumerGroup)

	// Skor hesaplama + AI analizleri (arka planda, hata worker'ı durdurmaz)
	computeAndEvaluate(ctx, pool, job.TenantID, job.WorkspaceID, job.PanelID, job.BrandID, measureSvc, recSvc, deliverySvc, sentimentEng, competitiveEng)

	logger.Info("worker: iş tamamlandı")
}

// computeAndEvaluate loads raw responses, computes a score, evaluates rules, runs AI analysis, and sends notifications.
func computeAndEvaluate(
	ctx context.Context,
	pool *pgxpool.Pool,
	tenantID, workspaceID, panelID, brandID string,
	measureSvc measure.Service,
	recSvc recommendation.Service,
	deliverySvc delivery.Service,
	sentimentEng *sentiment.Engine,
	competitiveEng *competitive.Engine,
) {
	logger := slog.With("brand", brandID, "tenant", tenantID, "workspace", workspaceID)

	// 1. Ham yanıtları yükle
	rows, err := pool.Query(ctx, `
		SELECT engine_name, content_text, COALESCE(raw_body, content_text)
		FROM measure.raw_responses
		WHERE tenant_id = $1 AND workspace_id = $2 AND brand_id = $3
		AND created_at > now() - interval '1 hour'
		ORDER BY engine_name, created_at
	`, tenantID, workspaceID, brandID)
	if err != nil {
		logger.Warn("compute: raw_response sorgu hatası", "error", err)
		return
	}
	defer rows.Close()

	type engineResponse struct {
		engine string
		raw    engine.RawResponse
	}
	var engineResponses []engineResponse
	for rows.Next() {
		var engineName, contentText, rawBody string
		if err := rows.Scan(&engineName, &contentText, &rawBody); err != nil {
			logger.Warn("compute: satır okuma hatası", "error", err)
			continue
		}
		engineResponses = append(engineResponses, engineResponse{
			engine: engineName,
			raw: engine.RawResponse{
				Content: contentText,
			},
		})
	}
	if err := rows.Err(); err != nil {
		logger.Warn("compute: satır okuma hatası", "error", err)
		return
	}

	if len(engineResponses) == 0 {
		return
	}

	// 2. Motor bazında grupla -> MeasurementResult
	engineMap := make(map[string][]engine.RawResponse)
	for _, er := range engineResponses {
		engineMap[er.engine] = append(engineMap[er.engine], er.raw)
	}

	var results []measure.MeasurementResult
	for eng, raws := range engineMap {
		results = append(results, measure.MeasurementResult{
			RawResponses: raws,
			BrandID:      brandID,
			PanelID:      panelID,
			WorkspaceID:  workspaceID,
			TenantID:     tenantID,
			EngineMeta:   engine.EngineMeta{EngineName: eng},
		})
	}

	// 3. Skor hesapla
	score, err := measureSvc.CalculateScore(ctx, panelID, results, measure.ComponentWeights{})
	if err != nil {
		logger.Warn("compute: skor hesaplama hatası", "error", err)
		return
	}
	metrics.MeasurementsCompleted.WithLabelValues(tenantID).Inc()
	logger.Info("compute: skor hesaplandı", "value", score.Value)

	// 4. Tavsiyeleri değerlendir
	recs, err := recSvc.Evaluate(ctx, brandID, workspaceID, tenantID)
	if err != nil {
		logger.Warn("compute: tavsiye değerlendirme hatası", "error", err)
		return
	}
	logger.Info("compute: tavsiyeler değerlendirildi", "count", len(recs))

	// 5. AI Analizleri (sentiment, hallucination, competitive gap)
	// Bunlar worker'ı bloke etmez; hata durumunda sadece log yazılır

	// 5a. Duygu analizi (FR-D7)
	if sentimentEng != nil {
		sentimentResults, sentErr := sentimentEng.AnalyzeSentiment(ctx, brandID, workspaceID, tenantID, "")
		if sentErr != nil {
			logger.Warn("compute: sentiment analiz hatası", "error", sentErr)
		} else {
			logger.Info("compute: sentiment analizi tamamlandı", "count", len(sentimentResults))
		}

		// 5b. Hallüsinasyon tespiti (FR-D8)
		hallResults, hallErr := sentimentEng.DetectHallucinations(ctx, brandID, workspaceID, tenantID)
		if hallErr != nil {
			logger.Warn("compute: hallüsinasyon tespit hatası", "error", hallErr)
		} else {
			logger.Info("compute: hallüsinasyon tespiti tamamlandı", "count", len(hallResults))
		}
	}

	// 5c. Competitive gap analizi (FR-D11)
	if competitiveEng != nil {
		gapResults, gapErr := competitiveEng.AnalyzeAllGaps(ctx, brandID, workspaceID, tenantID)
		if gapErr != nil {
			logger.Warn("compute: competitive gap analiz hatası", "error", gapErr)
		} else {
			logger.Info("compute: competitive gap analizi tamamlandı", "competitors", len(gapResults))
		}
	}

	// 6. Kritik bildirimleri kontrol et
	var criticalRecs []recommendation.Recommendation
	for _, r := range recs {
		if r.Severity == "critical" || r.Severity == "high" {
			criticalRecs = append(criticalRecs, r)
		}
	}
	if len(criticalRecs) == 0 {
		return
	}

	// Bildirim ayarlarını kontrol et
	settings, err := deliverySvc.GetSettings(ctx, workspaceID, tenantID)
	if err != nil || settings == nil || !settings.NotifyOnDrop {
		return
	}

	for _, r := range criticalRecs {
		notif := delivery.Notification{
			TenantID:    tenantID,
			WorkspaceID: workspaceID,
			Type:        delivery.NotificationScoreDrop,
			Channel:     delivery.ChannelEmail,
			Title:       r.Title,
			Body:        r.Detail,
			Data: map[string]interface{}{
				"brand_id":  brandID,
				"severity":  r.Severity,
				"score":     score.Value,
				"threshold": settings.DropThreshold,
			},
			Status: delivery.DeliveryPending,
		}
		if err := deliverySvc.SendNotification(notif); err != nil {
			logger.Warn("compute: bildirim gönderme hatası", "error", err)
		}
	}
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

// runAccountMetricsCollector periodically queries the database for account-level business metrics
// and updates Prometheus gauges (ActiveUsers, TotalBrands, MeasurementsCompleted, AuditsCompleted).
// Her 5 dakikada bir çalışır — bu metrikler yavaş değişir.
func runAccountMetricsCollector(ctx context.Context, pool *pgxpool.Pool) {
	ticker := time.NewTicker(5 * time.Minute)
	defer ticker.Stop()

	slog.Info("hesap metrikleri toplayıcı başlatıldı", "interval", "5m")

	for {
		select {
		case <-ctx.Done():
			slog.Info("hesap metrikleri toplayıcı durduruldu")
			return
		case <-ticker.C:
			collectAccountMetrics(ctx, pool)
		}
	}
}

// collectAccountMetrics runs all account-level DB queries and updates Prometheus gauges.
func collectAccountMetrics(ctx context.Context, pool *pgxpool.Pool) {
	// ActiveUsers: her tenant için kullanıcı sayısı
	rows, err := pool.Query(ctx, `
		SELECT tenant_id, COUNT(*) AS count
		FROM identity.users
		GROUP BY tenant_id
	`)
	if err != nil {
		slog.Warn("account metric: active_users sorgu hatası", "error", err)
	} else {
		for rows.Next() {
			var tenantID string
			var count int
			if err := rows.Scan(&tenantID, &count); err != nil {
				slog.Warn("account metric: active_users satır okuma hatası", "error", err)
				continue
			}
			metrics.ActiveUsers.WithLabelValues(tenantID).Set(float64(count))
		}
		rows.Close()
	}

	// TotalBrands: her tenant için marka sayısı
	rows, err = pool.Query(ctx, `
		SELECT tenant_id, COUNT(*) AS count
		FROM config.brands
		GROUP BY tenant_id
	`)
	if err != nil {
		slog.Warn("account metric: total_brands sorgu hatası", "error", err)
	} else {
		for rows.Next() {
			var tenantID string
			var count int
			if err := rows.Scan(&tenantID, &count); err != nil {
				slog.Warn("account metric: total_brands satır okuma hatası", "error", err)
				continue
			}
			metrics.TotalBrands.WithLabelValues(tenantID).Set(float64(count))
		}
		rows.Close()
	}

	// MeasurementsCompleted: her tenant için tamamlanmış ölçüm sayısı
	rows, err = pool.Query(ctx, `
		SELECT tenant_id, COUNT(*) AS count
		FROM measure.measurement_jobs
		WHERE status = 'completed'
		GROUP BY tenant_id
	`)
	if err != nil {
		slog.Warn("account metric: measurements_completed sorgu hatası", "error", err)
	} else {
		for rows.Next() {
			var tenantID string
			var count int
			if err := rows.Scan(&tenantID, &count); err != nil {
				slog.Warn("account metric: measurements_completed satır okuma hatası", "error", err)
				continue
			}
			metrics.MeasurementsCompleted.WithLabelValues(tenantID).Set(float64(count))
		}
		rows.Close()
	}

	// AuditsCompleted: her tenant için audit sayısı (governance.audit_results)
	rows, err = pool.Query(ctx, `
		SELECT tenant_id, COUNT(*) AS count
		FROM governance.audit_results
		GROUP BY tenant_id
	`)
	if err != nil {
		slog.Warn("account metric: audits_completed sorgu hatası", "error", err)
	} else {
		for rows.Next() {
			var tenantID string
			var count int
			if err := rows.Scan(&tenantID, &count); err != nil {
				slog.Warn("account metric: audits_completed satır okuma hatası", "error", err)
				continue
			}
			metrics.AuditsCompleted.WithLabelValues(tenantID).Set(float64(count))
		}
		rows.Close()
	}

	// RecommendationsGenerated: her tenant için toplam öneri sayısı
	rows, err = pool.Query(ctx, `
		SELECT tenant_id, COUNT(*) AS count
		FROM recommendation.results
		GROUP BY tenant_id
	`)
	if err != nil {
		slog.Warn("account metric: recommendations_generated sorgu hatası", "error", err)
	} else {
		for rows.Next() {
			var tenantID string
			var count int
			if err := rows.Scan(&tenantID, &count); err != nil {
				slog.Warn("account metric: recommendations_generated satır okuma hatası", "error", err)
				continue
			}
			metrics.RecommendationsGenerated.WithLabelValues(tenantID).Set(float64(count))
		}
		rows.Close()
	}

	slog.Debug("hesap metrikleri güncellendi")
}

// runQueueDepthCollector periodically reads Redis Stream lengths (XLEN) and updates Prometheus gauges.
// Her 15 saniyede bir tüm stream'lerin derinliğini ölçer ve QueueDepth/QueueDeadLetterSize metriklerini günceller.
func runQueueDepthCollector(ctx context.Context, rdb *redis.Client) {
	ticker := time.NewTicker(15 * time.Second)
	defer ticker.Stop()

	streams := []string{queue.StreamMeasure, queue.StreamAudit, queue.StreamReport, queue.StreamNotify,
		queue.StreamSentiment, queue.StreamReplay, queue.StreamArchive, queue.StreamGap, queue.StreamTechnicalGeo, queue.StreamContentGeo, queue.StreamGovernance}
	deadStreams := []string{queue.StreamDead}

	slog.Info("kuyruk derinliği toplayıcı başlatıldı", "interval", "15s")

	for {
		select {
		case <-ctx.Done():
			slog.Info("kuyruk derinliği toplayıcı durduruldu")
			return
		case <-ticker.C:
			// Normal stream derinlikleri
			for _, stream := range streams {
				length, err := rdb.XLen(ctx, stream).Result()
				if err != nil {
					slog.Warn("XLEN hatası", "stream", stream, "error", err)
					continue
				}
				metrics.QueueDepth.WithLabelValues(stream).Set(float64(length))
			}

			// Dead letter queue derinliği
			for _, stream := range deadStreams {
				length, err := rdb.XLen(ctx, stream).Result()
				if err != nil {
					slog.Warn("XLEN hatası (dead)", "stream", stream, "error", err)
					continue
				}
				metrics.QueueDeadLetterSize.WithLabelValues(stream).Set(float64(length))
			}
		}
	}
}
