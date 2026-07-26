package retention

import (
	"encoding/json"
	"log/slog"
	"net/http"

	"github.com/go-chi/chi/v5"

	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

type Handler struct {
	pool *db.Pool
}

func NewHandler(pool *db.Pool) *Handler {
	return &Handler{pool: pool}
}

type Policy struct {
	ID               string `json:"id"`
	TenantID         string `json:"tenant_id"`
	EntityType       string `json:"entity_type"`
	RetentionDays    int    `json:"retention_days"`
	ArchivalStrategy string `json:"archival_strategy"`
	Enabled          bool   `json:"enabled"`
	CreatedAt        string `json:"created_at"`
	UpdatedAt        string `json:"updated_at"`
}

// ListPolicies returns all retention policies for the current tenant.
func (h *Handler) ListPolicies(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	rows, err := h.pool.Query(r.Context(), `
		SELECT id, tenant_id, entity_type, retention_days, archival_strategy, enabled, created_at, updated_at
		FROM retention.policies WHERE tenant_id = $1 ORDER BY entity_type
	`, tenantID)
	if err != nil {
		slog.Error("saklama politikası sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"policies": []interface{}{}})
		return
	}
	defer rows.Close()

	var policies []Policy
	for rows.Next() {
		var p Policy
		if err := rows.Scan(&p.ID, &p.TenantID, &p.EntityType, &p.RetentionDays, &p.ArchivalStrategy, &p.Enabled, &p.CreatedAt, &p.UpdatedAt); err != nil {
			slog.Error("saklama politikası okuma hatası", "error", err)
			continue
		}
		policies = append(policies, p)
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"policies": policies})
}

// UpsertPolicy creates or updates a retention policy.
func (h *Handler) UpsertPolicy(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var input struct {
		EntityType       string `json:"entity_type"`
		RetentionDays    int    `json:"retention_days"`
		ArchivalStrategy string `json:"archival_strategy"`
		Enabled          bool   `json:"enabled"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	if input.RetentionDays < 30 {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "saklama süresi en az 30 gün olmalıdır"})
		return
	}

	validStrategies := map[string]bool{"delete": true, "anonymize": true, "archive_s3": true}
	if !validStrategies[input.ArchivalStrategy] {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz arşiv stratejisi: delete, anonymize, archive_s3"})
		return
	}

	var p Policy
	err := h.pool.QueryRow(r.Context(), `
		INSERT INTO retention.policies (tenant_id, entity_type, retention_days, archival_strategy, enabled)
		VALUES ($1, $2, $3, $4, $5)
		ON CONFLICT (tenant_id, entity_type) DO UPDATE SET
			retention_days = EXCLUDED.retention_days,
			archival_strategy = EXCLUDED.archival_strategy,
			enabled = EXCLUDED.enabled,
			updated_at = now()
		RETURNING id, tenant_id, entity_type, retention_days, archival_strategy, enabled, created_at, updated_at
	`, tenantID, input.EntityType, input.RetentionDays, input.ArchivalStrategy, input.Enabled,
	).Scan(&p.ID, &p.TenantID, &p.EntityType, &p.RetentionDays, &p.ArchivalStrategy, &p.Enabled, &p.CreatedAt, &p.UpdatedAt)
	if err != nil {
		slog.Error("saklama politikası kayıt hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "politika kaydedilemedi"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, p)
}

// DeletePolicy deletes a retention policy.
func (h *Handler) DeletePolicy(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	policyID := chi.URLParam(r, "policyId")

	result, err := h.pool.Exec(r.Context(), `
		DELETE FROM retention.policies WHERE id = $1 AND tenant_id = $2
	`, policyID, tenantID)
	if err != nil {
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "politika silinemedi"})
		return
	}
	if result.RowsAffected() == 0 {
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "politika bulunamadı"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "silindi"})
}

// GetArchiveSummary returns data archival summary.
func (h *Handler) GetArchiveSummary(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var totalArchived int
	_ = h.pool.QueryRow(r.Context(), `
		SELECT COUNT(*) FROM retention.archives WHERE tenant_id = $1
	`, tenantID).Scan(&totalArchived)

	var totalSize string
	_ = h.pool.QueryRow(r.Context(), `
		SELECT COALESCE(COUNT(*)::TEXT || ' kayıt', '0') FROM retention.archives WHERE tenant_id = $1
	`, tenantID).Scan(&totalSize)

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"total_archived": totalArchived,
		"total_size":     totalSize,
		"entities": []string{
			"measurement — ölçüm sonuçları",
			"audit_log — denetim günlükleri",
			"report — PDF raporlar",
			"alert — uyarı kayıtları",
		},
	})
}
