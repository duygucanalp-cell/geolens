package discovery

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/internal/id"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

type Handler struct {
	pool dbiface.DB
}

func NewHandler(pool dbiface.DB) *Handler {
	return &Handler{pool: pool}
}

func NewProductionHandler(pool *db.Pool) *Handler {
	return NewHandler(dbiface.NewAdapter(pool))
}

func (h *Handler) StartScan(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var input struct {
		ScanType string `json:"scan_type"`
		Provider string `json:"provider"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}
	if input.ScanType == "" {
		input.ScanType = "api"
	}
	if input.Provider == "" {
		input.Provider = "all"
	}

	scanID := id.New()

	_, err := h.pool.Exec(r.Context(), `
		INSERT INTO discovery.scans (id, tenant_id, scan_type, provider, status, started_at)
		VALUES ($1, $2, $3, $4, 'running', now())
	`, scanID, tenantID, input.ScanType, input.Provider)
	if err != nil {
		slog.Error("scan başlatma hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "tarama başlatılamadı"})
		return
	}

	// Arka planda tarama başlat (30sn timeout)
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	go h.runScan(ctx, cancel, scanID, tenantID, input.ScanType, input.Provider)

	httputil.WriteJSON(w, http.StatusCreated, map[string]interface{}{
		"scan_id": scanID,
		"status":  "running",
	})
}

func (h *Handler) runScan(ctx context.Context, cancel context.CancelFunc, scanID, tenantID, scanType, provider string) {
	defer cancel()
	slog.Info("shadow ai taraması başladı", "scan_id", scanID, "tenant", tenantID)

	findings := h.simulateScan(tenantID, provider)

	for _, f := range findings {
		select {
		case <-ctx.Done():
			slog.Warn("shadow ai taraması iptal edildi", "scan_id", scanID)
			_, _ = h.pool.Exec(ctx, `
				UPDATE discovery.scans SET status = 'failed', error_message = 'timeout', completed_at = now()
				WHERE id = $1
			`, scanID)
			return
		default:
		}

		if _, err := h.pool.Exec(ctx, `
			INSERT INTO discovery.findings (scan_id, tenant_id, resource_type, resource_name, resource_id, provider, region, risk_level, details)
			VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9::jsonb)
		`, scanID, tenantID, f.ResourceType, f.ResourceName, f.ResourceID, f.Provider, f.Region, f.RiskLevel, f.Details); err != nil {
			slog.Error("finding kayıt hatası", "error", err)
		}

		// Bulunan kaynakları registry'e de ekle
		h.registerFinding(ctx, tenantID, f)
	}

	_, err := h.pool.Exec(ctx, `
		UPDATE discovery.scans SET status = 'completed', total_found = $2, completed_at = now()
		WHERE id = $1
	`, scanID, len(findings))
	if err != nil {
		slog.Warn("scan durum güncelleme hatası", "error", err)
	}

	slog.Info("shadow ai taraması tamam", "scan_id", scanID, "findings", len(findings))
}

type finding struct {
	ResourceType string
	ResourceName string
	ResourceID   string
	Provider     string
	Region       string
	RiskLevel    string
	Details      string
}

func (h *Handler) simulateScan(tenantID, provider string) []finding {
	return []finding{
		{
			ResourceType: "lambda", ResourceName: "ai-inference-fn",
			ResourceID: "arn:aws:lambda:us-east-1:123456789012:function:ai-inference-fn",
			Provider:   "aws", Region: "us-east-1", RiskLevel: "high",
			Details: `{"runtime":"python3.12","memory":1024,"timeout":300,"has_ai_deps":true}`,
		},
		{
			ResourceType: "sagemaker", ResourceName: "prod-llm-endpoint",
			ResourceID: "arn:aws:sagemaker:us-west-2:123456789012:endpoint/prod-llm",
			Provider:   "aws", Region: "us-west-2", RiskLevel: "critical",
			Details: `{"instance_type":"ml.g5.12xlarge","model":"llama-3-70b","no_guardrails":true}`,
		},
		{
			ResourceType: "vertex_ai", ResourceName: "customer-chat-model",
			ResourceID: "projects/geolens-test/locations/us-central1/endpoints/12345",
			Provider:   "gcp", Region: "us-central1", RiskLevel: "medium",
			Details: `{"framework":"tensorflow","accelerator":"tpu","has_logging":true}`,
		},
	}
}

func (h *Handler) registerFinding(ctx context.Context, tenantID string, f finding) {
	entityName := f.ResourceName
	if entityName == "" {
		entityName = f.ResourceID
	}

	providerMap := map[string]string{
		"aws": "AWS SageMaker", "gcp": "Google Vertex AI", "azure": "Azure AI",
	}

	provider, ok := providerMap[f.Provider]
	if !ok {
		provider = f.Provider
	}

	// Proper JSON building instead of string concatenation
	metaJSON, _ := json.Marshal(map[string]interface{}{
		"discovered_by": "shadow_ai_scan",
		"resource_type": f.ResourceType,
		"region":        f.Region,
	})

	_, err := h.pool.Exec(ctx, `
		INSERT INTO registry.entities (tenant_id, entity_type, name, provider, description, lifecycle_state, risk_class, metadata)
		VALUES ($1, 'model', $2, $3, $4, 'production', $5, $6::jsonb)
		ON CONFLICT DO NOTHING
	`, tenantID, entityName, provider, "Shadow AI Discovery ile bulundu", f.RiskLevel, string(metaJSON))
	if err != nil {
		slog.Warn("registry'e kayıt hatası", "error", err)
	}
}

func (h *Handler) GetScanResults(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	scanID := chi.URLParam(r, "scanId")

	var scan struct {
		ID          string  `json:"id"`
		Status      string  `json:"status"`
		Provider    string  `json:"provider"`
		TotalFound  int     `json:"total_found"`
		StartedAt   *string `json:"started_at,omitempty"`
		CompletedAt *string `json:"completed_at,omitempty"`
		CreatedAt   string  `json:"created_at"`
	}
	err := h.pool.QueryRow(r.Context(), `
		SELECT id, status, provider, total_found, started_at, completed_at, created_at
		FROM discovery.scans WHERE id = $1 AND tenant_id = $2
	`, scanID, tenantID).Scan(&scan.ID, &scan.Status, &scan.Provider, &scan.TotalFound,
		&scan.StartedAt, &scan.CompletedAt, &scan.CreatedAt)
	if err != nil {
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "tarama bulunamadı"})
		return
	}

	rows, err := h.pool.Query(r.Context(), `
		SELECT resource_type, resource_name, resource_id, provider, region, risk_level, details, discovered_at
		FROM discovery.findings WHERE scan_id = $1 ORDER BY risk_level DESC
	`, scanID)
	if err != nil {
		httputil.WriteJSON(w, http.StatusOK, scan)
		return
	}
	defer rows.Close()

	type FindingRow struct {
		ResourceType string `json:"resource_type"`
		ResourceName string `json:"resource_name"`
		ResourceID   string `json:"resource_id"`
		Provider     string `json:"provider"`
		Region       string `json:"region"`
		RiskLevel    string `json:"risk_level"`
		Details      string `json:"details"`
		DiscoveredAt string `json:"discovered_at"`
	}

	var findings []FindingRow
	for rows.Next() {
		var f FindingRow
		if err := rows.Scan(&f.ResourceType, &f.ResourceName, &f.ResourceID, &f.Provider,
			&f.Region, &f.RiskLevel, &f.Details, &f.DiscoveredAt); err != nil {
			slog.Warn("discovery finding satır okuma hatası", "scan_id", scanID, "error", err)
			continue
		}
		findings = append(findings, f)
	}

	if rows.Err() != nil {
		slog.Warn("discovery finding rows iterasyon hatası", "scan_id", scanID, "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"scan":     scan,
		"findings": findings,
	})
}
