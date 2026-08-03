// Package technicalgeo provides handlers for Technical GEO (FR-B6, FR-B7, FR-E7).
package technicalgeo

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

// Handler holds dependencies for technical GEO HTTP handlers.
type Handler struct {
	pool dbiface.DB
	svc  *Engine
}

// NewHandler creates a new technical GEO Handler.
func NewHandler(pool dbiface.DB) *Handler {
	return &Handler{pool: pool}
}

// NewProductionHandler creates a new technical GEO Handler with a *db.Pool.
func NewProductionHandler(pool *db.Pool) *Handler {
	return &Handler{
		pool: dbiface.NewAdapter(pool),
		svc:  NewEngine(pool),
	}
}

// AnalyzeBots handles POST /v1/workspaces/{ws}/technical-geo/bots
// LLM bot erişim durumunu analiz eder (FR-B6).
func (h *Handler) AnalyzeBots(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	var req struct {
		BrandID string `json:"brand_id"`
		URL     string `json:"url,omitempty"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}
	if req.BrandID == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "brand_id zorunludur"})
		return
	}

	result, err := h.svc.AnalyzeBotAccess(r.Context(), req.BrandID, req.URL, workspaceID, tenantID)
	if err != nil {
		slog.Error("bot analiz hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "bot analizi başarısız")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, result)
}

// ListBotAnalyses handles GET /v1/workspaces/{ws}/technical-geo/bots
// Bot analizi geçmişini listeler.
func (h *Handler) ListBotAnalyses(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")

	rows, err := h.pool.Query(r.Context(), `
		SELECT ba.id, ba.brand_id, ba.bot_name, ba.url, ba.is_blocked,
		       ba.robots_txt_rule, ba.ges_score, ba.analyzed_at
		FROM technical.bot_analyses ba
		JOIN config.brands b ON b.id = ba.brand_id
		WHERE ba.tenant_id = $1 AND b.workspace_id = $2
			AND ($3 = '' OR ba.brand_id = $3)
		ORDER BY ba.analyzed_at DESC
		LIMIT 50
	`, tenantID, workspaceID, brandID)
	if err != nil {
		slog.Debug("bot analiz sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, []interface{}{})
		return
	}
	defer rows.Close()

	type botRow struct {
		ID            string    `json:"id"`
		BrandID       string    `json:"brand_id"`
		BotName       string    `json:"bot_name"`
		URL           string    `json:"url"`
		IsBlocked     bool      `json:"is_blocked"`
		RobotsTxtRule string    `json:"robots_txt_rule"`
		GESScore      float64   `json:"ges_score"`
		AnalyzedAt    time.Time `json:"analyzed_at"`
	}

	results := make([]botRow, 0)
	for rows.Next() {
		var b botRow
		if err := rows.Scan(&b.ID, &b.BrandID, &b.BotName, &b.URL, &b.IsBlocked,
			&b.RobotsTxtRule, &b.GESScore, &b.AnalyzedAt); err != nil {
			slog.Warn("bot satır okuma hatası", "error", err)
			continue
		}
		results = append(results, b)
	}

	if rows.Err() != nil {
		slog.Warn("bot rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, results)
}

// AnalyzeSchema handles POST /v1/workspaces/{ws}/technical-geo/schema
// Schema.org kullanımını analiz eder (FR-B7).
func (h *Handler) AnalyzeSchema(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	var req struct {
		BrandID string `json:"brand_id"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}
	if req.BrandID == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "brand_id zorunludur"})
		return
	}

	result, err := h.svc.AnalyzeSchema(r.Context(), req.BrandID, workspaceID, tenantID)
	if err != nil {
		slog.Error("schema analiz hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "schema analizi başarısız")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, result)
}

// ListSchemaAnalyses handles GET /v1/workspaces/{ws}/technical-geo/schema
// Schema analizi geçmişini listeler.
func (h *Handler) ListSchemaAnalyses(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")

	rows, err := h.pool.Query(r.Context(), `
		SELECT sa.id, sa.brand_id, sa.schema_type, sa.is_present, sa.schema_score,
		       sa.recommendation, sa.analyzed_at
		FROM technical.schema_analyses sa
		JOIN config.brands b ON b.id = sa.brand_id
		WHERE sa.tenant_id = $1 AND b.workspace_id = $2
			AND ($3 = '' OR sa.brand_id = $3)
		ORDER BY sa.analyzed_at DESC
		LIMIT 50
	`, tenantID, workspaceID, brandID)
	if err != nil {
		slog.Debug("schema analiz sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, []interface{}{})
		return
	}
	defer rows.Close()

	type schemaRow struct {
		ID             string    `json:"id"`
		BrandID        string    `json:"brand_id"`
		SchemaType     string    `json:"schema_type"`
		IsPresent      bool      `json:"is_present"`
		SchemaScore    float64   `json:"schema_score"`
		Recommendation string    `json:"recommendation"`
		AnalyzedAt     time.Time `json:"analyzed_at"`
	}

	results := make([]schemaRow, 0)
	for rows.Next() {
		var s schemaRow
		if err := rows.Scan(&s.ID, &s.BrandID, &s.SchemaType, &s.IsPresent,
			&s.SchemaScore, &s.Recommendation, &s.AnalyzedAt); err != nil {
			slog.Warn("schema satır okuma hatası", "error", err)
			continue
		}
		results = append(results, s)
	}

	if rows.Err() != nil {
		slog.Warn("schema rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, results)
}

// GetTechnicalGEOScore handles GET /v1/workspaces/{ws}/technical-geo/score
// Genel Teknik GEO skorunu döndürür.
func (h *Handler) GetTechnicalGEOScore(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")

	if brandID == "" {
		httputil.WriteError(w, http.StatusBadRequest, "brand_id gerekli")
		return
	}

	result, err := h.svc.GetScore(r.Context(), brandID, workspaceID, tenantID)
	if err != nil {
		slog.Error("teknik GEO skor hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "skor alınamadı")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, result)
}
