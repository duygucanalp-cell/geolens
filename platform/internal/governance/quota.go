package governance

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/geolens/platform/platform/db"
)

// QuotaChecker provides rate limit checking for tenant operations.
// Token bucket modeli: her tenant'ın belirli periyotta belirli sayıda token'ı vardır.
type QuotaChecker struct {
	pool *db.Pool
}

// NewQuotaChecker creates a new quota checker.
func NewQuotaChecker(pool *db.Pool) *QuotaChecker {
	return &QuotaChecker{pool: pool}
}

// BucketConfig holds configuration for a rate limit bucket.
type BucketConfig struct {
	BucketName string `json:"bucket_name"`
	MaxTokens  int64  `json:"max_tokens"`
}

// Default bucket configurations.
var defaultBuckets = []BucketConfig{
	{BucketName: "engine_calls_per_min", MaxTokens: 30},    // Dakikada 30 engine çağrısı
	{BucketName: "engine_calls_per_hour", MaxTokens: 500},  // Saatte 500 engine çağrısı
	{BucketName: "api_requests_per_hour", MaxTokens: 1000}, // Saatte 1000 API isteği
}

// EnsureBuckets creates default buckets for a tenant if they don't exist.
func (q *QuotaChecker) EnsureBuckets(ctx context.Context, tenantID string) error {
	if q.pool == nil {
		return nil
	}

	now := time.Now().UTC()
	windowStart := now.Truncate(time.Minute)

	for _, bucket := range defaultBuckets {
		_, err := q.pool.Exec(ctx, `
			INSERT INTO governance.rate_limit_buckets (id, tenant_id, bucket_name, max_tokens, window_start)
			VALUES ($1, $2, $3, $4, $5)
			ON CONFLICT (tenant_id, bucket_name, window_start) DO NOTHING
		`, fmt.Sprintf("%s-%s", tenantID, bucket.BucketName),
			tenantID, bucket.BucketName, bucket.MaxTokens, windowStart)
		if err != nil {
			slog.Warn("quota bucket oluşturma hatası", "tenant", tenantID, "bucket", bucket.BucketName, "error", err)
		}
	}
	return nil
}

// CheckAndConsume checks if a tenant has available tokens and consumes one if so.
func (q *QuotaChecker) CheckAndConsume(ctx context.Context, tenantID, bucketName string) (bool, error) {
	if q.pool == nil {
		return true, nil
	}

	// Mevcut zaman penceresini al
	var tokensUsed, maxTokens int64
	err := q.pool.QueryRow(ctx, `
		SELECT tokens_used, max_tokens
		FROM governance.rate_limit_buckets
		WHERE tenant_id = $1 AND bucket_name = $2
		ORDER BY window_start DESC
		LIMIT 1
	`, tenantID, bucketName).Scan(&tokensUsed, &maxTokens)

	if err != nil {
		// Bucket yoksa veya sorgu hatası varsa — varsayılan olarak izin ver
		slog.Warn("quota bucket sorgu hatası, izin veriliyor", "tenant", tenantID, "bucket", bucketName, "error", err)
		return true, nil
	}

	if tokensUsed >= maxTokens {
		return false, nil // Kota aşıldı
	}

	// Token kullan
	_, err = q.pool.Exec(ctx, `
		UPDATE governance.rate_limit_buckets
		SET tokens_used = tokens_used + 1, updated_at = now()
		WHERE tenant_id = $1 AND bucket_name = $2
		AND window_start = (
			SELECT window_start FROM governance.rate_limit_buckets
			WHERE tenant_id = $1 AND bucket_name = $2
			ORDER BY window_start DESC LIMIT 1
		)
	`, tenantID, bucketName)

	if err != nil {
		slog.Warn("quota token tüketim hatası", "tenant", tenantID, "bucket", bucketName, "error", err)
		return true, nil // Hata durumunda izin ver
	}

	return true, nil
}
