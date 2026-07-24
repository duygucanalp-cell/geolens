package main

import (
	"context"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/joho/godotenv"
	"github.com/prometheus/client_golang/prometheus/promhttp"

	"github.com/geolens/platform/engine"
	"github.com/geolens/platform/engine/chatgpt"
	"github.com/geolens/platform/engine/gemini"
	"github.com/geolens/platform/engine/perplexity"
	"github.com/geolens/platform/internal/audit"
	"github.com/geolens/platform/internal/auth"
	"github.com/geolens/platform/internal/config"
	"github.com/geolens/platform/internal/delivery"
	"github.com/geolens/platform/internal/measure"
	"github.com/geolens/platform/internal/pdf"
	"github.com/geolens/platform/internal/recommendation"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/queue"
	"github.com/geolens/platform/platform/storage"
	"github.com/geolens/platform/platform/telemetry"
)

func main() {
	// .env yükle (varsa)
	_ = godotenv.Load()

	// Yapılandırma
	cfg := config.LoadFromEnv()

	// Logger
	slog.SetDefault(slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: cfg.LogLevel})))

	// OpenTelemetry
	shutdown, err := telemetry.InitOTel(context.Background(), cfg)
	if err != nil {
		slog.Error("opentelemetry başlatılamadı", "error", err)
		os.Exit(1)
	}
	defer shutdown()

	// PostgreSQL havuzu
	pool, err := db.NewPool(context.Background(), cfg.DatabaseURL)
	if err != nil {
		slog.Error("veritabanı bağlantısı kurulamadı", "error", err)
		os.Exit(1)
	}
	defer pool.Close()

	// JWT servisi
	jwtService := auth.NewJWTService(cfg.JWTSecret)

	// S3 Storage (MinIO)
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

	// Redis client (queue ile aynı — mevcut bağlantıyı kullan)
	redisClient, err := queue.NewRedisClient(cfg.RedisURL)
	if err != nil {
		slog.Warn("redis istemci oluşturulamadı, cache olmadan çalışılacak", "error", err)
	}
	if redisClient != nil {
		defer redisClient.Close()
	}

	// Handler'lar
	authHandler := auth.NewHandler(pool, jwtService)
	configHandler := config.NewHandler(pool)
	panelHandler := config.NewPanelHandler(pool)
	measureHandler := measure.NewHandler(pool, engines)
	auditHandler := audit.NewHandler(pool)
	deliveryHandler := delivery.NewHandler(pool, delivery.EmailConfig{
		FromName:    cfg.SendGridFromName,
		FromEmail:   cfg.SendGridFromEmail,
		SendGridKey: cfg.SendGridAPIKey,
	})
	recommendationHandler := recommendation.NewHandler(pool)
	pdfHandler := pdf.NewHandler()

	// Router
	r := chi.NewRouter()

	// Global middleware zinciri (sabit sıra — 0501 §5)
	r.Use(httpmw.PanicRecovery)
	r.Use(httpmw.RequestID)
	r.Use(httpmw.SecureHeaders)
	r.Use(httpmw.RequestTimeout(30 * time.Second))
	r.Use(httpmw.MaxBodySize(1 << 20)) // 1MB max body
	r.Use(middleware.RealIP)
	r.Use(httpmw.MetricsMiddleware) // H14: Prometheus metrikleri
	r.Use(httpmw.Logger)
	r.Use(httpmw.CORS)

	// Health check (auth'suz)
	r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"status":"ok"}`))
	})

	// Prometheus metrics endpoint (auth'suz, herkes erişebilir)
	r.Get("/metrics", promhttp.Handler().ServeHTTP)

	// API v1
	r.Route("/v1", func(r chi.Router) {
		// JSON body validation for all POST/PUT/PATCH routes
		r.Use(httpmw.ValidateContentType("application/json"))

		// Public auth routes (JWT gerekmez)
		r.Post("/auth/register", authHandler.Register)
		r.Post("/auth/login", authHandler.Login)

		// Protected routes (JWT gerekli)
		r.Group(func(r chi.Router) {
			r.Use(httpmw.Authenticate(jwtService.TokenValidator()))
			r.Use(httpmw.TenantContext(pool))

			// Auth-protected utilities
			r.Post("/auth/logout", authHandler.Logout)

			// Workspace-scoped routes (auth + workspace membership gerekli)
			r.Route("/workspaces/{ws}", func(r chi.Router) {
				r.Use(httpmw.RequireWorkspace)
				r.Use(httpmw.RequireWorkspaceAccess(pool))

				// Cache middleware (GET endpoint'leri için Redis önbellek)
				var cacheCfg httpmw.CacheConfig
				if redisClient != nil {
					cacheCfg = httpmw.CacheConfig{RDB: redisClient, TTL: 30 * time.Second}
				}
				r.Group(func(r chi.Router) {
					r.Use(httpmw.CacheMiddleware(cacheCfg))
					r.Get("/brands", configHandler.ListBrands)
					r.Get("/panels", panelHandler.ListPanels)
					r.Get("/panels/{panelID}", panelHandler.GetPanel)
					r.Get("/prompt-sets", panelHandler.ListPromptSets)
					r.Get("/scores", measureHandler.ListScores)
					r.Get("/notifications/settings", deliveryHandler.GetSettings)
					r.Get("/recommendations", recommendationHandler.ListRecommendations)
				})

				// Admin-only routes
				r.Group(func(r chi.Router) {
					r.Use(httpmw.RequireRole(httpmw.RoleAdmin))
					r.Post("/brands", configHandler.CreateBrand)
				})

				// Member+ routes
				r.Get("/brands", configHandler.ListBrands)
				r.Get("/panels", panelHandler.ListPanels)
				r.Post("/panels", panelHandler.CreatePanel)
				r.Get("/panels/{panelID}", panelHandler.GetPanel)
				r.Get("/prompt-sets", panelHandler.ListPromptSets)
				r.Post("/prompt-sets", panelHandler.CreatePromptSet)
				r.Post("/measurements", measureHandler.TriggerMeasurement)
				r.Get("/scores", measureHandler.ListScores)
				r.Post("/audit", auditHandler.RunAudit)
				r.Get("/notifications/settings", deliveryHandler.GetSettings)
				r.Put("/notifications/settings", deliveryHandler.UpdateSettings)
				r.Post("/notifications/test", deliveryHandler.SendTestEmail)
				r.Get("/recommendations", recommendationHandler.ListRecommendations)
				r.Post("/recommendations/{recId}/apply", recommendationHandler.MarkApplied)
				r.Post("/recommendations/{recId}/dismiss", recommendationHandler.MarkDismissed)
				r.Post("/reports/digest", pdfHandler.GenerateWeeklyDigest)
			})
		})
	})

	// HTTP sunucusu
	srv := &http.Server{
		Addr:         ":" + cfg.Port,
		Handler:      r,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 30 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	// Graceful shutdown
	go func() {
		slog.Info("api sunucusu başlatılıyor", "port", cfg.Port)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			slog.Error("sunucu hatası", "error", err)
			os.Exit(1)
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	slog.Info("sunucu kapatılıyor...")
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	if err := srv.Shutdown(ctx); err != nil {
		slog.Error("sunucu kapatılamadı", "error", err)
		os.Exit(1)
	}
}
