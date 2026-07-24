package measure

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/geolens/platform/platform/db"
)

// ---- Outbox Job Helpers ----

// JobPayload is the payload stored in the outbox for a measurement job.
type JobPayload struct {
	BrandID     string `json:"brand_id"`
	BrandName   string `json:"brand_name"`
	WebsiteURL  string `json:"website_url"`
	PanelID     string `json:"panel_id"`
	WorkspaceID string `json:"workspace_id"`
	TenantID    string `json:"tenant_id"`
	EngineName  string `json:"engine_name"`
	PromptText  string `json:"prompt_text"`
	SampleIndex int    `json:"sample_index"` // n=3 için örnek indeksi (0, 1, 2)
}

// EnqueueMeasurement writes a measurement job to the transactional outbox.
// İşlem, event_outbox tablosuna INSERT yapar — Dispatcher Redis Streams'e iletir.
func EnqueueMeasurement(ctx context.Context, pool *db.Pool, job JobPayload, idempotencyKey string) error {
	payload, err := json.Marshal(job)
	if err != nil {
		return fmt.Errorf("job payload serileştirme: %w", err)
	}

	id := generateULID()
	_, err = pool.Exec(ctx, `
		INSERT INTO public.event_outbox (id, event_type, stream, payload, tenant_id, idempotency_key, created_at)
		VALUES ($1, 'measurement.requested', 'q:measure', $2::jsonb, $3, $4, now())
	`, id, string(payload), job.TenantID, idempotencyKey)

	if err != nil {
		return fmt.Errorf("outbox ekleme: %w", err)
	}

	return nil
}

// EnqueuePanelMeasurement creates n=3 measurement jobs for each engine in a panel.
func EnqueuePanelMeasurement(ctx context.Context, pool *db.Pool, brandID, brandName, websiteURL, panelID, workspaceID, tenantID, promptText string, engineNames []string) error {
	for _, engineName := range engineNames {
		for i := 0; i < 3; i++ {
			idempotencyKey := fmt.Sprintf("measure:%s:%s:%s:%d", panelID, brandID, engineName, i)
			job := JobPayload{
				BrandID:     brandID,
				BrandName:   brandName,
				WebsiteURL:  websiteURL,
				PanelID:     panelID,
				WorkspaceID: workspaceID,
				TenantID:    tenantID,
				EngineName:  engineName,
				PromptText:  promptText,
				SampleIndex: i,
			}
			if err := EnqueueMeasurement(ctx, pool, job, idempotencyKey); err != nil {
				return fmt.Errorf("panel job ekleme [%s][%d]: %w", engineName, i, err)
			}
		}
	}
	return nil
}

// ---- Job Record Helpers ----

// CreateJobRecord creates a measurement_jobs record in the database.
func CreateJobRecord(ctx context.Context, pool *db.Pool, job JobPayload) error {
	id := generateULID()
	_, err := pool.Exec(ctx, `
		INSERT INTO measure.measurement_jobs (id, brand_id, brand_name, panel_id, engine_name, sample_index, status, tenant_id, workspace_id, created_at)
		VALUES ($1, $2, $3, $4, $5, $6, 'pending', $7, $8, now())
	`, id, job.BrandID, job.BrandName, job.PanelID, job.EngineName, job.SampleIndex, job.TenantID, job.WorkspaceID)
	return err
}

// --- Time helpers ---

// FormatDuration formats a duration for display.
func FormatDuration(d time.Duration) string {
	if d < time.Second {
		return fmt.Sprintf("%dms", d.Milliseconds())
	}
	if d < time.Minute {
		return fmt.Sprintf("%.1fs", d.Seconds())
	}
	return fmt.Sprintf("%dm%ds", int(d.Minutes()), int(d.Seconds())%60)
}
