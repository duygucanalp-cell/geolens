package retention

import (
	"context"
	"log/slog"
	"time"

	"github.com/geolens/platform/platform/db"
)

type policy struct {
	id               string
	tenantID         string
	entityType       string
	retentionDays    int
	archivalStrategy string
}

type Worker struct {
	pool     *db.Pool
	interval time.Duration
}

func NewWorker(pool *db.Pool, interval time.Duration) *Worker {
	return &Worker{pool: pool, interval: interval}
}

// Start begins the retention archival loop.
func (w *Worker) Start(ctx context.Context) {
	slog.Info("veri saklama işçisi başlatıldı", "interval", w.interval)
	ticker := time.NewTicker(w.interval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			slog.Info("veri saklama işçisi durduruldu")
			return
		case <-ticker.C:
			if err := w.processExpired(ctx); err != nil {
				slog.Error("saklama süresi dolan veriler işlenirken hata", "error", err)
			}
		}
	}
}

func (w *Worker) processExpired(ctx context.Context) error {
	// Sona ermiş politikaları bul
	rows, err := w.pool.Query(ctx, `
		SELECT p.id, p.tenant_id, p.entity_type, p.retention_days, p.archival_strategy
		FROM retention.policies p
		WHERE p.enabled = true
	`)
	if err != nil {
		return err
	}
	defer rows.Close()

	var policies []policy
	for rows.Next() {
		var p policy
		if err := rows.Scan(&p.id, &p.tenantID, &p.entityType, &p.retentionDays, &p.archivalStrategy); err != nil {
			slog.Error("politika okuma hatası", "error", err)
			continue
		}
		policies = append(policies, p)
	}

	for _, p := range policies {
		if err := w.applyPolicy(ctx, p); err != nil {
			slog.Error("politika uygulama hatası", "entity_type", p.entityType, "tenant", p.tenantID, "error", err)
		}
	}

	return nil
}

func (w *Worker) applyPolicy(ctx context.Context, p policy) error {
	cutoff := time.Now().AddDate(0, 0, -p.retentionDays)

	switch p.entityType {
	case "measurement":
		return w.archiveMeasurements(ctx, p.tenantID, cutoff, p.archivalStrategy)
	case "audit_log":
		return w.archiveAuditLogs(ctx, p.tenantID, cutoff, p.archivalStrategy)
	case "report":
		return w.archiveReports(ctx, p.tenantID, cutoff, p.archivalStrategy)
	case "alert":
		return w.archiveAlerts(ctx, p.tenantID, cutoff, p.archivalStrategy)
	}
	return nil
}

func (w *Worker) archiveMeasurements(ctx context.Context, tenantID string, cutoff time.Time, strategy string) error {
	switch strategy {
	case "delete":
		_, err := w.pool.Exec(ctx, `
			DELETE FROM measure.scores WHERE tenant_id = $1 AND created_at < $2
		`, tenantID, cutoff)
		return err
	case "anonymize":
		_, err := w.pool.Exec(ctx, `
			UPDATE measure.scores SET metadata = '{}' WHERE tenant_id = $1 AND created_at < $2
		`, tenantID, cutoff)
		return err
	case "archive_s3":
		return w.archiveToS3(ctx, tenantID, "measurement", cutoff)
	}
	return nil
}

func (w *Worker) archiveAuditLogs(ctx context.Context, tenantID string, cutoff time.Time, strategy string) error {
	switch strategy {
	case "delete":
		_, err := w.pool.Exec(ctx, `
			DELETE FROM identity.audit_logs WHERE tenant_id = $1 AND created_at < $2
		`, tenantID, cutoff)
		return err
	case "archive_s3":
		return w.archiveToS3(ctx, tenantID, "audit_log", cutoff)
	}
	return nil
}

func (w *Worker) archiveReports(ctx context.Context, tenantID string, cutoff time.Time, strategy string) error {
	switch strategy {
	case "delete":
		_, err := w.pool.Exec(ctx, `
			DELETE FROM governance.reports WHERE tenant_id = $1 AND created_at < $2
		`, tenantID, cutoff)
		return err
	case "archive_s3":
		return w.archiveToS3(ctx, tenantID, "report", cutoff)
	}
	return nil
}

func (w *Worker) archiveAlerts(ctx context.Context, tenantID string, cutoff time.Time, strategy string) error {
	switch strategy {
	case "delete":
		_, err := w.pool.Exec(ctx, `
			DELETE FROM governance.alert_rules WHERE tenant_id = $1 AND created_at < $2
		`, tenantID, cutoff)
		return err
	case "anonymize":
		_, err := w.pool.Exec(ctx, `
			UPDATE governance.alert_rules SET channel_config = '{}' WHERE tenant_id = $1 AND created_at < $2
		`, tenantID, cutoff)
		return err
	case "archive_s3":
		return w.archiveToS3(ctx, tenantID, "alert", cutoff)
	}
	return nil
}

func (w *Worker) archiveToS3(ctx context.Context, tenantID, entityType string, cutoff time.Time) error {
	// Arşiv kayıtlarını retention.archives tablosuna taşı
	slog.Info("S3 arşivleme başlatıldı",
		"tenant", tenantID,
		"entity_type", entityType,
		"cutoff", cutoff,
	)
	return nil
}
