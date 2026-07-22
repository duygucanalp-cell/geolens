package main

import (
	"context"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"github.com/joho/godotenv"
	"github.com/geolens/platform/internal/config"
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

	slog.Info("zamanlayıcı başlatılıyor", "poll_interval", cfg.PollInterval)

	// Dilim 1 H2'de detaylandırılacak: izleme planı tarama + idempotent iş üretimi
	// ...

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	slog.Info("zamanlayıcı kapatılıyor...")
}
