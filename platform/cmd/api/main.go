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

	// Engine registry (Dilim 1: yalnız Perplexity)
	engines := engine.NewRegistry()
	perplexityAdapter := perplexity.NewAdapter(cfg.PerplexityAPIKey)
	engines.Register(perplexityAdapter)
	slog.Info("motor kayıt defteri hazır", "engine_count", engines.Count(), "engines", engines.List())

	// Handler'lar
	authHandler := auth.NewHandler(pool, jwtService)
	configHandler := config.NewHandler(pool)
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
			r.Use(httpmw.RBAC)

			// Auth-protected utilities
			r.Post("/auth/logout", authHandler.Logout)

			// Workspace-scoped routes
			r.Route("/workspaces/{ws}", func(r chi.Router) {
				r.Use(httpmw.RequireWorkspace)

				// Config
				r.Get("/brands", configHandler.ListBrands)
				r.Post("/brands", configHandler.CreateBrand)

				// Measurement
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
