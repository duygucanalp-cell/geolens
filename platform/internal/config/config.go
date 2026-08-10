// Package config provides config functionality.
package config

import (
	"log/slog"
	"os"
	"strconv"
	"time"
)

// Config holds all application configuration loaded from environment variables.
type Config struct {
	Port                    string
	DatabaseURL             string
	RedisURL                string
	S3Endpoint              string
	S3Region                string
	S3Bucket                string
	S3AccessKey             string
	S3SecretKey             string
	StorageMasterKey        string
	LogLevel                slog.Level
	PollInterval            time.Duration
	ConsumerGroup           string
	JWTSecret               string
	PerplexityAPIKey        string
	ChatGPTAPIKey           string
	GeminiAPIKey            string
	ClaudeAPIKey            string
	GrokAPIKey              string
	CopilotAPIKey           string
	MistralAPIKey           string
	StripeAPIKey            string
	StripeWebhookSecret     string
	EFaturaMode             string
	ElasticsearchURL        string
	ElasticsearchAPIKey     string
	ClickHouseURL           string
	ClickHouseUser          string
	ClickHousePassword      string
	ClickHouseDatabase      string
	SendGridAPIKey          string
	SendGridFromEmail       string
	SendGridFromName        string
	OTelEndpoint            string
	Environment             string
	RateLimitEnabled        bool
	AuditEnabled            bool
	SampleCount             int
	MaxEnginesPerTenant     int
	MaxBrandsPerWorkspace   int
	DefaultPageSize         int
	MaxRequestBodyMB        int
	SAMLCertPath            string
	SAMLKeyPath             string
	RetentionInterval       time.Duration
	GoogleOAuthClientID     string
	GoogleOAuthClientSecret string
	BaseURL                 string
}

// LoadFromEnv reads configuration from environment variables with sensible defaults.
func LoadFromEnv() Config {
	return Config{
		Port:                    getEnv("PORT", "8080"),
		DatabaseURL:             getEnv("DATABASE_URL", "postgres://geolens:geolens@localhost:5432/geolens?sslmode=disable"),
		RedisURL:                getEnv("REDIS_URL", "redis://localhost:6379/0"),
		S3Endpoint:              getEnv("S3_ENDPOINT", "http://localhost:9000"),
		S3Region:                getEnv("S3_REGION", "us-east-1"),
		S3Bucket:                getEnv("S3_BUCKET", "geolens"),
		S3AccessKey:             getEnv("S3_ACCESS_KEY", "minioadmin"),
		S3SecretKey:             getEnv("S3_SECRET_KEY", "minioadmin"),
		StorageMasterKey:        getEnv("STORAGE_MASTER_KEY", ""),
		LogLevel:                parseLogLevel(getEnv("LOG_LEVEL", "info")),
		PollInterval:            parseDuration(getEnv("POLL_INTERVAL", "30s")),
		ConsumerGroup:           getEnv("CONSUMER_GROUP", "cg:measure"),
		JWTSecret:               getEnv("JWT_SECRET", "dev-secret-change-in-production"),
		PerplexityAPIKey:        getEnv("PERPLEXITY_API_KEY", ""),
		ChatGPTAPIKey:           getEnv("CHATGPT_API_KEY", ""),
		GeminiAPIKey:            getEnv("GEMINI_API_KEY", ""),
		ClaudeAPIKey:            getEnv("CLAUDE_API_KEY", ""),
		GrokAPIKey:              getEnv("GROK_API_KEY", ""),
		CopilotAPIKey:           getEnv("COPILOT_API_KEY", ""),
		MistralAPIKey:           getEnv("MISTRAL_API_KEY", ""),
		StripeAPIKey:            getEnv("STRIPE_API_KEY", ""),
		StripeWebhookSecret:     getEnv("STRIPE_WEBHOOK_SECRET", ""),
		EFaturaMode:             getEnv("EFATURA_MODE", "mock"),
		ElasticsearchURL:        getEnv("ELASTICSEARCH_URL", ""),
		ElasticsearchAPIKey:     getEnv("ELASTICSEARCH_API_KEY", ""),
		ClickHouseURL:           getEnv("CLICKHOUSE_URL", ""),
		ClickHouseUser:          getEnv("CLICKHOUSE_USER", "default"),
		ClickHousePassword:      getEnv("CLICKHOUSE_PASSWORD", ""),
		ClickHouseDatabase:      getEnv("CLICKHOUSE_DATABASE", "geolens"),
		SendGridAPIKey:          getEnv("SENDGRID_API_KEY", ""),
		SendGridFromEmail:       getEnv("SENDGRID_FROM_EMAIL", "geolens@example.com"),
		SendGridFromName:        getEnv("SENDGRID_FROM_NAME", "GeoLens"),
		OTelEndpoint:            getEnv("OTEL_ENDPOINT", "http://localhost:4318"),
		Environment:             getEnv("ENVIRONMENT", "development"),
		SampleCount:             GetEnvInt("SAMPLE_COUNT", 3),
		RateLimitEnabled:        getEnv("RATE_LIMIT_ENABLED", "true") == "true",
		AuditEnabled:            getEnv("AUDIT_ENABLED", "true") == "true",
		MaxEnginesPerTenant:     GetEnvInt("MAX_ENGINES_PER_TENANT", 3),
		MaxBrandsPerWorkspace:   GetEnvInt("MAX_BRANDS_PER_WORKSPACE", 20),
		DefaultPageSize:         GetEnvInt("DEFAULT_PAGE_SIZE", 50),
		MaxRequestBodyMB:        GetEnvInt("MAX_REQUEST_BODY_MB", 1),
		SAMLCertPath:            getEnv("SAML_CERT_PATH", ""),
		SAMLKeyPath:             getEnv("SAML_KEY_PATH", ""),
		RetentionInterval:       parseDuration(getEnv("RETENTION_INTERVAL", "24h")),
		GoogleOAuthClientID:     getEnv("GOOGLE_OAUTH_CLIENT_ID", ""),
		GoogleOAuthClientSecret: getEnv("GOOGLE_OAUTH_CLIENT_SECRET", ""),
		BaseURL:                 getEnv("BASE_URL", "http://localhost:8080"),
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
