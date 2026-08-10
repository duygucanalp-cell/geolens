// Package config provides config functionality.
package config

import (
	"log/slog"
	"os"
	"strconv"
	"strings"
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
	// Hardcoded değerlerin env'e taşınması (PO review §4)
	JWTTokenTTL       time.Duration // JWT_TOKEN_TTL (varsayılan 2h)
	StripePriceIDsRaw string        // STRIPE_PRICE_IDS — "tier=priceId,..." (varsayılan boş → Stripe default map)
	ScoreWeightsRaw   string        // SCORE_WEIGHTS — "presence,position,source,competitor" (varsayılan 35/25/20/20)
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
		JWTTokenTTL:             parseDuration(getEnv("JWT_TOKEN_TTL", "2h")),
		StripePriceIDsRaw:       getEnv("STRIPE_PRICE_IDS", ""),
		ScoreWeightsRaw:         getEnv("SCORE_WEIGHTS", ""),
	}
}

// ParseStripePriceIDs parses the STRIPE_PRICE_IDS env ("tier=priceId,tier=priceId,...")
// into the Stripe price map. Boş veya geçersiz girdide nil döner (Stripe default map kullanılır).
// Örn: "pro=price_1xxx,business=price_2xxx,enterprise=price_3xxx"
func (c Config) ParseStripePriceIDs() map[string]string {
	if c.StripePriceIDsRaw == "" {
		return nil
	}
	out := make(map[string]string)
	for _, pair := range strings.Split(c.StripePriceIDsRaw, ",") {
		kv := strings.SplitN(pair, "=", 2)
		if len(kv) != 2 {
			continue
		}
		key := strings.TrimSpace(kv[0])
		if key != "" {
			out[key] = strings.TrimSpace(kv[1])
		}
	}
	if len(out) == 0 {
		return nil
	}
	return out
}

// ParseScoreWeights parses the SCORE_WEIGHTS env ("presence,position,source,competitor").
// Geçersiz girdide ok=false döner — varsayılan GAVF ağırlıkları (35/25/20/20) kullanılır.
func (c Config) ParseScoreWeights() (presence, position, source, competitor float64, ok bool) {
	if c.ScoreWeightsRaw == "" {
		return 0, 0, 0, 0, false
	}
	parts := strings.Split(c.ScoreWeightsRaw, ",")
	if len(parts) != 4 {
		return 0, 0, 0, 0, false
	}
	vals := make([]float64, 4)
	for i, p := range parts {
		v, err := strconv.ParseFloat(strings.TrimSpace(p), 64)
		if err != nil {
			return 0, 0, 0, 0, false
		}
		vals[i] = v
	}
	return vals[0], vals[1], vals[2], vals[3], true
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
