package config

import (
	"log/slog"
	"os"
	"strconv"
	"time"
)

// Config holds all application configuration loaded from environment variables.
type Config struct {
	Port                  string
	DatabaseURL           string
	RedisURL              string
	S3Endpoint            string
	S3Region              string
	S3Bucket              string
	S3AccessKey           string
	S3SecretKey           string
	LogLevel              slog.Level
	PollInterval          time.Duration
	ConsumerGroup         string
	JWTSecret             string
	PerplexityAPIKey      string
	ChatGPTAPIKey         string
	GeminiAPIKey          string
	OTelEndpoint          string
	Environment           string
	RateLimitEnabled      bool
	AuditEnabled          bool
	MaxEnginesPerTenant   int
	MaxBrandsPerWorkspace int
	DefaultPageSize       int
	MaxRequestBodyMB      int
}

// LoadFromEnv reads configuration from environment variables with sensible defaults.
func LoadFromEnv() Config {
	return Config{
		Port:                  getEnv("PORT", "8080"),
		DatabaseURL:           getEnv("DATABASE_URL", "postgres://geolens:geolens@localhost:5432/geolens?sslmode=disable"),
		RedisURL:              getEnv("REDIS_URL", "redis://localhost:6379/0"),
		S3Endpoint:            getEnv("S3_ENDPOINT", "http://localhost:9000"),
		S3Region:              getEnv("S3_REGION", "us-east-1"),
		S3Bucket:              getEnv("S3_BUCKET", "geolens"),
		S3AccessKey:           getEnv("S3_ACCESS_KEY", "minioadmin"),
		S3SecretKey:           getEnv("S3_SECRET_KEY", "minioadmin"),
		LogLevel:              parseLogLevel(getEnv("LOG_LEVEL", "info")),
		PollInterval:          parseDuration(getEnv("POLL_INTERVAL", "30s")),
		ConsumerGroup:         getEnv("CONSUMER_GROUP", "cg:measure"),
		JWTSecret:             getEnv("JWT_SECRET", "dev-secret-change-in-production"),
		PerplexityAPIKey:      getEnv("PERPLEXITY_API_KEY", ""),
		ChatGPTAPIKey:         getEnv("CHATGPT_API_KEY", ""),
		GeminiAPIKey:          getEnv("GEMINI_API_KEY", ""),
		OTelEndpoint:          getEnv("OTEL_ENDPOINT", "http://localhost:4318"),
		Environment:           getEnv("ENVIRONMENT", "development"),
		RateLimitEnabled:      getEnv("RATE_LIMIT_ENABLED", "true") == "true",
		AuditEnabled:          getEnv("AUDIT_ENABLED", "true") == "true",
		MaxEnginesPerTenant:   GetEnvInt("MAX_ENGINES_PER_TENANT", 3),
		MaxBrandsPerWorkspace: GetEnvInt("MAX_BRANDS_PER_WORKSPACE", 20),
		DefaultPageSize:       GetEnvInt("DEFAULT_PAGE_SIZE", 50),
		MaxRequestBodyMB:      GetEnvInt("MAX_REQUEST_BODY_MB", 1),
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func parseLogLevel(level string) slog.Level {
	switch level {
	case "debug":
		return slog.LevelDebug
	case "warn":
		return slog.LevelWarn
	case "error":
		return slog.LevelError
	default:
		return slog.LevelInfo
	}
}

func parseDuration(s string) time.Duration {
	d, err := time.ParseDuration(s)
	if err != nil {
		return 30 * time.Second
	}
	return d
}

// GetEnvInt parses an integer from an environment variable or returns the default.
func GetEnvInt(key string, fallback int) int {
	if v := os.Getenv(key); v != "" {
		if i, err := strconv.Atoi(v); err == nil {
			return i
		}
	}
	return fallback
}
