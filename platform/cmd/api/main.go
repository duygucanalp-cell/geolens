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

	"github.com/geolens/platform/engine"
	"github.com/geolens/platform/engine/perplexity"
	"github.com/geolens/platform/internal/auth"
	"github.com/geolens/platform/internal/config"
	"github.com/geolens/platform/internal/measure"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
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
	var storageSaver perplexity.RawSaver
	if err == nil {
		storageSaver = s3Storage
	}

	// Engine registry (Dilim 1: yalnız Perplexity)
	engines := engine.NewRegistry()
	perplexityAdapter := perplexity.NewAdapter(cfg.PerplexityAPIKey, storageSaver)
	engines.Register(perplexityAdapter)
	slog.Info("motor kayıt defteri hazır", "engine_count", engines.Count(), "engines", engines.List())

	// Handler'lar
	authHandler := auth.NewHandler(pool, jwtService)
	configHandler := config.NewHandler(pool)
	panelHandler := config.NewPanelHandler(pool)
	measureHandler := measure.NewHandler(pool, engines)

	// Router
	r := chi.NewRouter()

	// Global middleware zinciri (sabit sıra — 0501 §5)
	r.Use(httpmw.PanicRecovery)
	r.Use(httpmw.RequestID)
	r.Use(middleware.RealIP)
	r.Use(httpmw.Logger)
	r.Use(httpmw.CORS)

	// Health check (auth'suz)
	r.Get("/health", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"status":"ok"}`))
	})

	// API v1
	r.Route("/v1", func(r chi.Router) {
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
