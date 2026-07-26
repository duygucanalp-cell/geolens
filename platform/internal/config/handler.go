package config

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"time"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

type brandRequest struct {
	Name        string   `json:"name"`
	WebsiteURL  string   `json:"website_url"`
	Competitors []string `json:"competitors,omitempty"`
}

type brandResponse struct {
	ID         string `json:"id"`
	Name       string `json:"name"`
	WebsiteURL string `json:"website_url"`
}

// Handler holds dependencies for config HTTP handlers.
type Handler struct {
	pool dbiface.DB
}

// NewHandler creates a new config handler with the given DB interface.
func NewHandler(pool dbiface.DB) *Handler {
	return &Handler{pool: pool}
}

// NewProductionHandler creates a new config handler with a *db.Pool for production use.
func NewProductionHandler(pool *db.Pool) *Handler {
	return NewHandler(dbiface.NewAdapter(pool))
}

// ListBrands handles GET /v1/workspaces/{ws}/brands
func (h *Handler) ListBrands(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	rows, err := h.pool.Query(r.Context(), `
		SELECT id, name, website_url
		FROM config.brands
		WHERE workspace_id = $1 AND tenant_id = $2 AND is_active = true
		ORDER BY name
	`, workspaceID, tenantID)
	if err != nil {
		slog.Error("marka listeleme hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "sorgu hatası"})
		return
	}
	defer rows.Close()

	var parseErrors int
	brands := make([]brandResponse, 0)
	for rows.Next() {
		var b brandResponse
		if err := rows.Scan(&b.ID, &b.Name, &b.WebsiteURL); err != nil {
			slog.Error("marka satır okuma hatası", "error", err)
			parseErrors++
			continue
		}
		brands = append(brands, b)
	}

	if rows.Err() != nil {
		slog.Error("marka listesi rows iterasyon hatası", "error", rows.Err())
	}

	// K5: Kısmi sonuç uyarısı (parseErrors > 0 ise response header'a eklenir)
	if parseErrors > 0 {
		w.Header().Set("X-Has-More", "true")
	}

	httputil.WriteJSON(w, http.StatusOK, brands)
}

// CreateBrand handles POST /v1/workspaces/{ws}/brands
func (h *Handler) CreateBrand(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	var req brandRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	if req.Name == "" || req.WebsiteURL == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "marka adı ve web sitesi zorunludur"})
		return
	}

	var brandID string
	err := h.pool.QueryRow(r.Context(), `
		INSERT INTO config.brands (id, workspace_id, tenant_id, name, website_url)
		VALUES (gen_random_uuid()::text, $1, $2, $3, $4)
		RETURNING id
	`, workspaceID, tenantID, req.Name, req.WebsiteURL).Scan(&brandID)

	if err != nil {
		slog.Error("marka oluşturma hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "marka oluşturulamadı"})
		return
	}

	httputil.WriteJSON(w, http.StatusCreated, brandResponse{
		ID:         brandID,
		Name:       req.Name,
		WebsiteURL: req.WebsiteURL,
	})
}

// GetSetupStatus handles GET /v1/workspaces/{ws}/setup-status
// Kurulum sihirbazının hangi adımların tamamlandığını döner.
func (h *Handler) GetSetupStatus(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	workspaceID := httpmw.GetWorkspaceID(r.Context())

	var checks struct {
		BrandCount       int `json:"brand_count"`
		PanelCount       int `json:"panel_count"`
		PromptSetCount   int `json:"prompt_set_count"`
		MeasurementCount int `json:"measurement_count"`
	}

	h.pool.QueryRow(r.Context(), `
		SELECT COALESCE((SELECT count(*) FROM config.brands WHERE workspace_id = $1 AND tenant_id = $2), 0)
	`, workspaceID, tenantID).Scan(&checks.BrandCount)
	h.pool.QueryRow(r.Context(), `
		SELECT COALESCE((SELECT count(*) FROM config.panels WHERE workspace_id = $1 AND tenant_id = $2), 0)
	`, workspaceID, tenantID).Scan(&checks.PanelCount)
	h.pool.QueryRow(r.Context(), `
		SELECT COALESCE((SELECT count(*) FROM config.prompt_sets WHERE workspace_id = $1 AND tenant_id = $2), 0)
	`, workspaceID, tenantID).Scan(&checks.PromptSetCount)
	h.pool.QueryRow(r.Context(), `
		SELECT COALESCE((SELECT count(*) FROM measure.scores WHERE workspace_id = $1 AND tenant_id = $2), 0)
	`, workspaceID, tenantID).Scan(&checks.MeasurementCount)

	steps := []map[string]interface{}{
		{"key": "brand", "label": "Marka Ekle", "done": checks.BrandCount > 0},
		{"key": "panel", "label": "Panel Oluştur", "done": checks.PanelCount > 0},
		{"key": "prompt_set", "label": "Prompt Seti Oluştur", "done": checks.PromptSetCount > 0},
		{"key": "measurement", "label": "İlk Ölçümü Çalıştır", "done": checks.MeasurementCount > 0},
	}

	allDone := checks.BrandCount > 0 && checks.PanelCount > 0 && checks.PromptSetCount > 0 && checks.MeasurementCount > 0

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"setup_complete": allDone,
		"steps":          steps,
	})
}

// ListWorkspacePanorama handles GET /v1/tenant/panorama
// H5: Ajans görünümü — tüm workspace'lerin son skor özetini döndürür.
func (h *Handler) ListWorkspacePanorama(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	rows, err := h.pool.Query(r.Context(), `
		SELECT w.id, w.name,
			COALESCE(s.score_value, 0) AS avg_score,
			COALESCE(s.brand_count, 0) AS brand_count,
			COALESCE(s.measurement_count, 0) AS measurement_count,
			w.archived_at IS NOT NULL AS archived,
			w.created_at
		FROM config.workspaces w
		LEFT JOIN LATERAL (
			SELECT
				AVG(s2.value) AS score_value,
				COUNT(DISTINCT s2.brand_id) AS brand_count,
				COUNT(*) AS measurement_count
			FROM measure.scores s2
			WHERE s2.workspace_id = w.id AND s2.tenant_id = w.tenant_id
		) s ON true
		WHERE w.tenant_id = $1
		ORDER BY w.created_at DESC
	`, tenantID)
	if err != nil {
		slog.Error("panorama sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"workspaces": []interface{}{}})
		return
	}
	defer rows.Close()

	type workspaceSummary struct {
		ID               string  `json:"id"`
		Name             string  `json:"name"`
		AvgScore         float64 `json:"avg_score"`
		BrandCount       int     `json:"brand_count"`
		MeasurementCount int     `json:"measurement_count"`
		Archived         bool    `json:"archived"`
		CreatedAt        string  `json:"created_at"`
	}

	workspaces := make([]workspaceSummary, 0)
	for rows.Next() {
		var ws workspaceSummary
		var createdAt time.Time
		if err := rows.Scan(&ws.ID, &ws.Name, &ws.AvgScore, &ws.BrandCount, &ws.MeasurementCount, &ws.Archived, &createdAt); err != nil {
			slog.Warn("panorama satır okuma hatası", "error", err)
			continue
		}
		ws.CreatedAt = createdAt.Format(time.RFC3339)
		workspaces = append(workspaces, ws)
	}

	if rows.Err() != nil {
		slog.Warn("panorama rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"workspaces": workspaces})
}
