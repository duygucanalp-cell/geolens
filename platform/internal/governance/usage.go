package governance

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/geolens/platform/platform/db"
)

// UsageRecorder tracks resource consumption per tenant.
type UsageRecorder struct {
	pool *db.Pool
}

// NewUsageRecorder creates a new usage recorder.
func NewUsageRecorder(pool *db.Pool) *UsageRecorder {
	return &UsageRecorder{pool: pool}
}

// Metric names
const (
	MetricEngineCalls    = "engine_calls"
	MetricAPIRequests    = "api_requests"
	MetricStorageBytes   = "storage_bytes"
	MetricScoresComputed = "scores_computed"
)

// RecordUsage records a usage metric for a tenant.
func (u *UsageRecorder) RecordUsage(ctx context.Context, tenantID, metricName string, value int64, resourceType, resourceID string) error {
	if u.pool == nil {
		return fmt.Errorf("usage: veritabanı bağlantısı yok")
	}

	id := fmt.Sprintf("%s-%s-%d", tenantID, metricName, time.Now().UnixMicro())
	_, err := u.pool.Exec(ctx, `
		INSERT INTO governance.usage_records (id, tenant_id, metric_name, metric_value, resource_type, resource_id, recorded_at)
		VALUES ($1, $2, $3, $4, $5, $6, now())
	`, id, tenantID, metricName, value, resourceType, resourceID)
	return err
}

// IncrementUsage is a convenience wrapper that increments a metric by 1.
func (u *UsageRecorder) IncrementUsage(ctx context.Context, tenantID, metricName, resourceType, resourceID string) error {
	return u.RecordUsage(ctx, tenantID, metricName, 1, resourceType, resourceID)
}

// GetUsageSummary returns total usage for a tenant within a time range.
func (u *UsageRecorder) GetUsageSummary(ctx context.Context, tenantID string, since time.Time) (map[string]int64, error) {
	if u.pool == nil {
		return nil, fmt.Errorf("usage: veritabanı bağlantısı yok")
	}

	rows, err := u.pool.Query(ctx, `
		SELECT metric_name, SUM(metric_value)
		FROM governance.usage_records
		WHERE tenant_id = $1 AND recorded_at >= $2
		GROUP BY metric_name
	`, tenantID, since)
	if err != nil {
		return nil, fmt.Errorf("usage summary sorgu: %w", err)
	}
	defer rows.Close()

	summary := make(map[string]int64)
	for rows.Next() {
		var name string
		var total int64
		if err := rows.Scan(&name, &total); err != nil {
			slog.Warn("governance usage satır okuma hatası", "error", err)
			continue
		}
		summary[name] = total
	}
	return summary, rows.Err()
}
