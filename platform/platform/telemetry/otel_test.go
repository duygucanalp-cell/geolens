package telemetry

import (
	"context"
	"log/slog"
	"testing"

	"github.com/geolens/platform/internal/config"
)

func TestInitOTel_DevelopmentNoEndpoint(t *testing.T) {
	cfg := config.Config{
		Environment:  "development",
		OTelEndpoint: "",
		LogLevel:     slog.LevelInfo,
	}

	shutdown, err := InitOTel(context.Background(), cfg)
	if err != nil {
		t.Fatalf("InitOTel failed: %v", err)
	}
	if shutdown != nil {
		shutdown()
	}
}

func TestInitOTel_DevelopmentWithEndpoint(t *testing.T) {
	cfg := config.Config{
		Environment:  "development",
		OTelEndpoint: "http://localhost:4318",
		LogLevel:     slog.LevelDebug,
	}

	shutdown, err := InitOTel(context.Background(), cfg)
	if err != nil {
		t.Logf("expected possible connection error (no OTel collector), got: %v", err)
	}
	if shutdown != nil {
		shutdown()
	}
}

func TestInitOTel_ProductionNoEndpoint(t *testing.T) {
	cfg := config.Config{
		Environment:  "production",
		OTelEndpoint: "",
		LogLevel:     slog.LevelInfo,
	}

	shutdown, err := InitOTel(context.Background(), cfg)
	if err == nil && shutdown != nil {
		shutdown()
	}
}

func TestInitOTel_EmptyEnvironment(t *testing.T) {
	cfg := config.Config{
		Environment:  "",
		OTelEndpoint: "",
		LogLevel:     slog.LevelInfo,
	}

	shutdown, err := InitOTel(context.Background(), cfg)
	if err != nil {
		t.Logf("InitOTel error (expected possible): %v", err)
	}
	if shutdown != nil {
		shutdown()
	}
}

func TestConfigDefaults(t *testing.T) {
	// Test that LoadFromEnv doesn't panic
	_ = config.LoadFromEnv()
}

func TestGetEnvInt(t *testing.T) {
	v := config.GetEnvInt("NONEXISTENT_VAR_FOR_TEST", 42)
	if v != 42 {
		t.Errorf("expected 42, got %d", v)
	}
}
