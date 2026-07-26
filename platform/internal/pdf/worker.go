package pdf

import (
	"context"
	"encoding/json"
	"log/slog"
	"time"

	"github.com/geolens/platform/platform/db"
)

// StartReportProcessor launches a background goroutine that polls for pending reports
// and generates them asynchronously.
func StartReportProcessor(pool *db.Pool, svc Service, interval time.Duration) {
	go func() {
		slog.Info("rapor işleyici başlatıldı", "interval", interval.String())
		ticker := time.NewTicker(interval)
		defer ticker.Stop()

		for range ticker.C {
			processPendingReports(pool, svc)
		}
	}()
}

func processPendingReports(pool *db.Pool, svc Service) {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	rows, err := pool.Query(ctx, `
		SELECT id, tenant_id, workspace_id, report_type, COALESCE(brand_id, ''), COALESCE(params::text, '{}')
		FROM measure.reports
		WHERE status = 'pending'
		ORDER BY created_at ASC
		LIMIT 5
	`)
	if err != nil {
		slog.Warn("bekleyen rapor sorgu hatası", "error", err)
		return
	}
	defer rows.Close()

	for rows.Next() {
		var id, tenantID, workspaceID, reportType, brandID, paramsJSON string
		if err := rows.Scan(&id, &tenantID, &workspaceID, &reportType, &brandID, &paramsJSON); err != nil {
			slog.Warn("rapor satır okuma hatası", "error", err)
			continue
		}

		// generating olarak işaretle
		_, _ = pool.Exec(ctx, `
			UPDATE measure.reports SET status = 'generating', updated_at = now()
			WHERE id = $1 AND status = 'pending'
		`, id)

		var params map[string]string
		_ = json.Unmarshal([]byte(paramsJSON), &params)

		var reportTypeEnum ReportType
		switch reportType {
		case "digest":
			reportTypeEnum = ReportWeeklyDigest
		case "score_card":
			reportTypeEnum = ReportScoreCard
		case "audit":
			reportTypeEnum = ReportAudit
		default:
			markFailed(pool, id, "bilinmeyen rapor tipi: "+reportType)
			continue
		}

		result, err := svc.Generate(ReportRequest{
			Type:        reportTypeEnum,
			WorkspaceID: workspaceID,
			TenantID:    tenantID,
			BrandID:     brandID,
			BrandName:   params["brand_name"],
		})
		if err != nil {
			slog.Error("rapor oluşturma hatası", "id", id, "error", err)
			markFailed(pool, id, err.Error())
			continue
		}

		// PDF verisini JSONB params içinde base64 veya direkt binary olarak sakla
		fileParams := map[string]interface{}{
			"brand_name":  params["brand_name"],
			"pdf_b64":     result.Data,
			"page_count":  result.PageCount,
			"generatedAt": result.GeneratedAt,
		}
		fileParamsBytes, _ := json.Marshal(fileParams)

		_, err = pool.Exec(ctx, `
			UPDATE measure.reports
			SET status = 'ready', file_name = $2, file_size = $3, params = $4, updated_at = now()
			WHERE id = $1
		`, id, result.FileName, int64(len(result.Data)), fileParamsBytes)
		if err != nil {
			slog.Error("rapor durum güncelleme hatası", "id", id, "error", err)
			continue
		}

		slog.Info("rapor hazır", "id", id, "type", reportType, "size", len(result.Data))
	}
}

func markFailed(pool *db.Pool, id, errMsg string) {
	_, err := pool.Exec(context.Background(), `
		UPDATE measure.reports
		SET status = 'failed', error_message = $2, updated_at = now()
		WHERE id = $1
	`, id, errMsg)
	if err != nil {
		slog.Warn("rapor hata durumu güncelleme hatası", "id", id, "error", err)
	}
}
