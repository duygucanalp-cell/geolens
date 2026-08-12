// Package config provides config functionality.
package config

import (
	"log/slog"
	"math"
	"os"
	"strconv"
	"strings"
	"time"
)

// ScoreWeightsV2 holds normalized 7-component weights for the Visibility Index
// v2 profile (0409 v1.3): presence, position, source, competitor, appearance,
// sentiment, compvis. Sum should equal 1.0.
type ScoreWeightsV2 struct {
	Presence   float64
	Position   float64
	Source     float64
	Competitor float64
	Appearance float64
	Sentiment  float64
	CompVis    float64
}

// Config holds all application configuration loaded from environment variables.
type Config struct {
	Port string
	// WorkerMetricsPort — worker prosesinin Prometheus /metrics endpoint'i.
	// API'nin :8080/metrics'inden ayrıdır: worker kendi prosesindeki metrikleri
	// (breaker metrikleri dahil) bu portta serve eder (0421 M-4 gözlemlenebilirlik).
	WorkerMetricsPort string
	// SchedulerMetricsPort — scheduler prosesinin Prometheus /metrics endpoint'i.
	// Worker'ın :8081'inden ayrıdır (aynı host üzerinde çakışmamak için 8082).
	SchedulerMetricsPort    string
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
	// ScoreAlgorithmVersion — SCORE_ALGORITHM_VERSION: "1.0.0"=eski 4 bileşenli,
	// "2.0.0"=7 bileşenli VI (0409 v1.3, A3-5). Varsayılan 2.0.0; 1.0.0 geri dönüş için feature flag.
	ScoreAlgorithmVersion string

	// IntentWeightScaleRaw — INTENT_WEIGHT_SCALE: prompt intent'ine göre VI bileşen
	// çarpanları (0421 A3-3). Biçim: "intent=v1,v2,...,v7;intent=..." — örn.
	// "presence=1.25,1.00,0.90,0.90,1.10,0.90,0.90;comparison=0.90,1.00,0.90,1.40,0.90,0.90,1.30".
	// Boş bırakılırsa varsayılan intentComponentScale kullanılır (pilot verisiyle kalibrasyon
	// için env üzerinden override — 0404 §4).
	IntentWeightScaleRaw string

	// ML serving (0421 A0-3) — ML_SERVING_URL doluysa ML client aktifleşir,
	// boşsa kural tabanlı bileşenler fallback olarak çalışmaya devam eder.
	MLServingURL string // ML_SERVING_URL (varsayılan boş → fallback)
	MLTimeOut    time.Duration

	// BenchmarkMinTenants — BENCHMARK_MIN_TENANTS: sektör benchmark istatistiklerinin
	// yayınlanması için gereken minimum kiracı sayısı (NFR-13 diferansiyel gizlilik eşiği).
	// Pilot döneminde düşürülebilir (ör. 2), üretimde ≥5 kalmalıdır.
	BenchmarkMinTenants int
}

// LoadFromEnv reads configuration from environment variables with sensible defaults.
func LoadFromEnv() Config {
	return Config{
		Port:                    getEnv("PORT", "8080"),
		WorkerMetricsPort:       getEnv("WORKER_METRICS_PORT", "8081"),
		SchedulerMetricsPort:    getEnv("SCHEDULER_METRICS_PORT", "8082"),
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
		ScoreAlgorithmVersion:   getEnv("SCORE_ALGORITHM_VERSION", "2.0.0"),
		IntentWeightScaleRaw:    getEnv("INTENT_WEIGHT_SCALE", ""),
		MLServingURL:            getEnv("ML_SERVING_URL", ""),
		MLTimeOut:               parseDuration(getEnv("ML_TIMEOUT", "5s")),
		BenchmarkMinTenants:     GetEnvInt("BENCHMARK_MIN_TENANTS", 5),
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

// ParseScoreWeightsV2 parses 7-component SCORE_WEIGHTS
// ("presence,position,source,competitor,appearance,sentiment,compvis").
// A3-5: 7 bileşenli VI profili (0409 v1.3). Geçersiz girdi → ok=false.
func (c Config) ParseScoreWeightsV2() (weights ScoreWeightsV2, ok bool) {
	if c.ScoreWeightsRaw == "" {
		return ScoreWeightsV2{}, false
	}
	parts := strings.Split(c.ScoreWeightsRaw, ",")
	if len(parts) != 7 {
		return ScoreWeightsV2{}, false
	}
	vals := make([]float64, 7)
	for i, p := range parts {
		v, err := strconv.ParseFloat(strings.TrimSpace(p), 64)
		if err != nil {
			return ScoreWeightsV2{}, false
		}
		vals[i] = v
	}
	sum := 0.0
	for _, v := range vals {
		sum += v
	}
	if sum <= 0 {
		return ScoreWeightsV2{}, false
	}
	return ScoreWeightsV2{
		Presence: vals[0], Position: vals[1], Source: vals[2], Competitor: vals[3],
		Appearance: vals[4], Sentiment: vals[5], CompVis: vals[6],
	}, true
}

// ParseIntentWeightScale parses the INTENT_WEIGHT_SCALE env into a per-intent
// 7-component scale map (0421 A3-3). Biçim:
//
//	"presence=1.25,1.00,0.90,0.90,1.10,0.90,0.90;comparison=0.90,..."
//
// Boş/geçersiz girdide ok=false döner — varsayılan intentComponentScale kullanılır.
func (c Config) ParseIntentWeightScale() (map[string][7]float64, bool) {
	return ParseIntentWeightScaleRaw(c.IntentWeightScaleRaw)
}

// ParseIntentWeightScaleRaw parses a raw INTENT_WEIGHT_SCALE string. Ayrık olarak
// test edilebilmesi için raw string üzerinde çalışır (service nil cfg ile de kullanabilir).
func ParseIntentWeightScaleRaw(raw string) (map[string][7]float64, bool) {
	if strings.TrimSpace(raw) == "" {
		return nil, false
	}
	out := make(map[string][7]float64)
	for _, entry := range strings.Split(raw, ";") {
		entry = strings.TrimSpace(entry)
		if entry == "" {
			continue
		}
		kv := strings.SplitN(entry, "=", 2)
		if len(kv) != 2 {
			return nil, false
		}
		intent := strings.TrimSpace(kv[0])
		if intent == "" {
			return nil, false
		}
		parts := strings.Split(kv[1], ",")
		if len(parts) != 7 {
			return nil, false
		}
		var vals [7]float64
		for i, p := range parts {
			v, err := strconv.ParseFloat(strings.TrimSpace(p), 64)
			if err != nil || v < 0 || math.IsNaN(v) || math.IsInf(v, 0) {
				return nil, false
			}
			vals[i] = v
		}
		out[intent] = vals
	}
	if len(out) == 0 {
		return nil, false
	}
	return out, true
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
