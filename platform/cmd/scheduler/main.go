// Package main is the entry point for the GeoLens measurement scheduler.
package main

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"sync"
	"syscall"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/joho/godotenv"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"github.com/robfig/cron/v3"

	"github.com/geolens/platform/engine"
	"github.com/geolens/platform/engine/chatgpt"
	"github.com/geolens/platform/engine/gemini"
	"github.com/geolens/platform/engine/perplexity"
	"github.com/geolens/platform/internal/config"
	"github.com/geolens/platform/internal/delivery"
	"github.com/geolens/platform/internal/measure"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/queue"
	"github.com/geolens/platform/platform/telemetry"
)

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
	// NOT: pool kapatma yalnızca shutdown'da (defer) yapılır — önceden burada
	// çağrılan pool.Close(), dispatcher'ın tüm DB işlemlerini "closed pool"
	// hatasıyla kırıyordu (outbox iletimi, cron job'ları çalışmıyordu).
	defer pool.Close()

	rdb, err := queue.NewRedisClient(cfg.RedisURL)
	if err != nil {
		slog.Error("redis bağlantısı kurulamadı", "error", err)
		return
	}
	defer func() { _ = rdb.Close() }()

	slog.Info("zamanlayıcı başlatılıyor", "poll_interval", cfg.PollInterval)

	// Engine registry
	engines := engine.NewRegistry()

	// Perplexity (Kademe 1) — storage'sız, sadece motor adı için
	perplexityAdapter := perplexity.NewAdapter(cfg.PerplexityAPIKey, nil)
	engines.Register(perplexityAdapter)

	// ChatGPT / OpenAI (Kademe 1) — storage'sız, sadece motor adı için
	chatgptAdapter := chatgpt.NewAdapter(cfg.ChatGPTAPIKey, nil)
	engines.Register(chatgptAdapter)

	// Gemini / Google AI (Kademe 1) — storage'sız, sadece motor adı için
	geminiAdapter := gemini.NewAdapter(cfg.GeminiAPIKey, nil)
	engines.Register(geminiAdapter)

	// Google AI Overview + Google AI Mode (Kademe 3 — directional) — FR-B6 HT2 genişletmesi
	engines.Register(geminiAdapter.WithAIOverview("", ""))
	engines.Register(geminiAdapter.WithAIMode("", ""))

	slog.Info("motor kayıt defteri hazır", "engine_count", engines.Count(), "engines", engines.List())

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Outbox dağıtıcıyı başlat
	dispatcher := queue.NewDispatcher(pool.Pool, rdb, cfg.PollInterval, cfg.ConsumerGroup)

	// Haftalık digest e-posta gönderimi için delivery servisi
	emailCfg := delivery.EmailConfig{
		FromName:    cfg.SendGridFromName,
		FromEmail:   cfg.SendGridFromEmail,
		SendGridKey: cfg.SendGridAPIKey,
	}
	deliverySvc := delivery.NewService(emailCfg, pool)

	var wg sync.WaitGroup
	wg.Add(3)
	go func() {
		defer wg.Done()
		dispatcher.Start(ctx)
	}()
	go func() {
		defer wg.Done()
		runScheduler(ctx, pool.Pool, engines, cfg.PollInterval)
	}()
	go func() {
		defer wg.Done()
		runDigestScheduler(ctx, pool.Pool, deliverySvc)
	}()

	// Scheduler metrics sunucusu — kendi prosesindeki Prometheus metriklerini
	// /metrics üzerinden serve eder. Worker (:8081) ve API'den (:8080) ayrıdır;
	// scrape için SCHEDULER_METRICS_PORT (varsayılan 8082) kullanılır.
	metricsSrv := &http.Server{
		Addr:         ":" + cfg.SchedulerMetricsPort,
		Handler:      promhttp.Handler(),
		ReadTimeout:  5 * time.Second,
		WriteTimeout: 10 * time.Second,
		IdleTimeout:  30 * time.Second,
	}
	wg.Add(1)
	go func() {
		defer wg.Done()
		slog.Info("scheduler metrics sunucusu başlatılıyor", "port", cfg.SchedulerMetricsPort)
		if err := metricsSrv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			slog.Error("scheduler metrics sunucusu hatası", "error", err)
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	slog.Info("zamanlayıcı kapatılıyor...")
	cancel()
	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer shutdownCancel()
	if err := metricsSrv.Shutdown(shutdownCtx); err != nil {
		slog.Warn("scheduler metrics sunucusu kapatılamadı", "error", err)
	}
	wg.Wait()
	slog.Info("zamanlayıcı durduruldu")
}

// runScheduler periodically scans panels and enqueues measurement jobs.
func runScheduler(ctx context.Context, pool *pgxpool.Pool, engines *engine.Registry, pollInterval time.Duration) {
	ticker := time.NewTicker(pollInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			if err := scanAndEnqueue(ctx, pool, engines); err != nil {
				slog.Error("zamanlayıcı tarama hatası", "error", err)
			}
		}
	}
}

// scanAndEnqueue scans all active panels and creates measurement jobs for due items.
func scanAndEnqueue(ctx context.Context, pool *pgxpool.Pool, engines *engine.Registry) error {
	// Aktif panelleri ve markalarını getir
	rows, err := pool.Query(ctx, `
		SELECT p.id, p.workspace_id, p.tenant_id, 
		       COALESCE(ps.prompt_text, ''), 
		       p.schedule_cron,
		       COALESCE(p.last_measured_at, '1970-01-01'::timestamptz)
		FROM config.panels p
		LEFT JOIN config.prompt_sets ps ON ps.id = p.prompt_set_id
		WHERE p.is_active = true
	`)
	if err != nil {
		return fmt.Errorf("panel sorgu: %w", err)
	}
	defer rows.Close()

	var enqueued int
	for rows.Next() {
		var panelID, workspaceID, tenantID, promptText, scheduleCron string
		var lastMeasuredAt time.Time

		if err := rows.Scan(&panelID, &workspaceID, &tenantID, &promptText, &scheduleCron, &lastMeasuredAt); err != nil {
			slog.Error("panel satır okuma hatası", "error", err)
			continue
		}

		// Zamanlama kontrolü: panel her zaman ölçülecek mi?
		if scheduleCron != "" {
			if !isDue(scheduleCron, lastMeasuredAt) {
				continue
			}
		}

		// Panel'in markalarını getir
		brands, err := getPanelBrands(ctx, pool, panelID, workspaceID, tenantID)
		if err != nil {
			slog.Error("panel marka sorgu hatası", "panel", panelID, "error", err)
			continue
		}

		if len(brands) == 0 {
			continue
		}

		// Varsayılan prompt
		if promptText == "" {
			promptText = "{brand_name} markası hakkında ne biliyorsun? Kaynak göstererek anlat."
		}

		engineNames := engines.List()
		if len(engineNames) == 0 {
			continue
		}

		// Her marka için n=3 job oluştur
		for _, brand := range brands {
			actualPrompt := strings.ReplaceAll(promptText, "{brand_name}", brand.Name)
			for _, engineName := range engineNames {
				for i := 0; i < 3; i++ {
					idempotencyKey := fmt.Sprintf("schedule:%s:%s:%s:%d", panelID, brand.ID, engineName, i)
					job := measure.JobPayload{
						BrandID:     brand.ID,
						BrandName:   brand.Name,
						WebsiteURL:  brand.WebsiteURL,
						PanelID:     panelID,
						WorkspaceID: workspaceID,
						TenantID:    tenantID,
						EngineName:  engineName,
						PromptText:  actualPrompt,
						SampleIndex: i,
					}
					if err := measure.EnqueueMeasurement(ctx, &db.Pool{Pool: pool}, job, idempotencyKey); err != nil {
						slog.Warn("zamanlayıcı job ekleme hatası",
							"brand", brand.Name, "engine", engineName, "error", err)
						continue
					}
					enqueued++
				}
			}
		}

		// last_measured_at güncelle
		_, err = pool.Exec(ctx, `UPDATE config.panels SET last_measured_at = now() WHERE id = $1`, panelID)
		if err != nil {
			slog.Warn("panel last_measured_at güncelleme hatası", "panel", panelID, "error", err)
		}
	}

	if enqueued > 0 {
		slog.Info("zamanlayıcı job'ları kuyruğa ekledi", "count", enqueued)
	}

	return rows.Err()
}

type panelBrand struct {
	ID         string
	Name       string
	WebsiteURL string
}

// getPanelBrands returns all brands linked to a panel.
func getPanelBrands(ctx context.Context, pool *pgxpool.Pool, panelID, workspaceID, tenantID string) ([]panelBrand, error) {
	rows, err := pool.Query(ctx, `
		SELECT b.id, b.name, b.website_url
		FROM config.brands b
		JOIN config.panel_brands pb ON pb.brand_id = b.id
		WHERE pb.panel_id = $1 AND pb.workspace_id = $2 AND pb.tenant_id = $3 AND b.is_active = true
	`, panelID, workspaceID, tenantID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var brands []panelBrand
	for rows.Next() {
		var b panelBrand
		if err := rows.Scan(&b.ID, &b.Name, &b.WebsiteURL); err != nil {
			return nil, err
		}
		brands = append(brands, b)
	}
	return brands, rows.Err()
}

// runDigestScheduler runs weekly digest jobs on a cron schedule.
func runDigestScheduler(ctx context.Context, pool *pgxpool.Pool, svc delivery.Service) {
	c := cron.New(cron.WithLocation(time.UTC))
	// Her Pazartesi 09:00 UTC
	_, err := c.AddFunc("0 9 * * 1", func() {
		slog.Info("haftalık digest gönderimi başlıyor")
		if err := processDigests(ctx, pool, svc); err != nil {
			slog.Error("digest işleme hatası", "error", err)
		}
	})
	if err != nil {
		slog.Error("digest cron kurulum hatası", "error", err)
		return
	}
	c.Start()
	<-ctx.Done()
	c.Stop()
}

// processDigests queries workspaces with digest enabled and sends weekly digests.
func processDigests(ctx context.Context, pool *pgxpool.Pool, svc delivery.Service) error {
	rows, err := pool.Query(ctx, `
		SELECT workspace_id, tenant_id
		FROM delivery.notification_settings
		WHERE digest_enabled = true
	`)
	if err != nil {
		return fmt.Errorf("digest sorgu: %w", err)
	}
	defer rows.Close()

	var sent int
	for rows.Next() {
		var workspaceID, tenantID string
		if err := rows.Scan(&workspaceID, &tenantID); err != nil {
			slog.Error("digest satır okuma hatası", "error", err)
			continue
		}
		if err := svc.SendWeeklyDigest(workspaceID, tenantID); err != nil {
			slog.Error("digest gönderme hatası",
				"workspace", workspaceID, "error", err)
			continue
		}
		sent++
	}

	if sent > 0 {
		slog.Info("haftalık digest'ler gönderildi", "count", sent)
	}
	return rows.Err()
}

// isDue checks if a panel is due for measurement based on its cron schedule.
func isDue(cronExpr string, lastMeasuredAt time.Time) bool {
	if cronExpr == "" {
		return true
	}

	schedule, err := cron.ParseStandard(cronExpr)
	if err != nil {
		slog.Warn("geçersiz cron ifadesi, varsayılan 1 saat kullanılıyor", "cron", cronExpr, "error", err)
		return time.Since(lastMeasuredAt) > time.Hour
	}

	next := schedule.Next(lastMeasuredAt)
	return time.Now().After(next)
}
