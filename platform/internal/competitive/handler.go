// Package competitive provides handlers for Competitive Gap Analysis (FR-D11).
package competitive

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

// Handler holds dependencies for competitive gap HTTP handlers.
type Handler struct {
	pool dbiface.DB
	svc  *Engine
}

// NewHandler creates a new competitive gap Handler.
func NewHandler(pool dbiface.DB) *Handler {
	return &Handler{pool: pool}
}

// NewProductionHandler creates a new competitive gap Handler with a *db.Pool.
func NewProductionHandler(pool *db.Pool) *Handler {
	return &Handler{
		pool: dbiface.NewAdapter(pool),
		svc:  NewEngine(pool),
	}
}

// AnalyzeGap handles POST /v1/workspaces/{ws}/competitive-gap/analyze
// Belirtilen marka-rakip çifti için gap analizi yapar.
func (h *Handler) AnalyzeGap(w http.ResponseWriter, r *http.Request) {
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

	result, err := h.svc.AnalyzeAllGaps(r.Context(), req.BrandID, workspaceID, tenantID)
	if err != nil {
		slog.Error("gap analiz hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "gap analizi başarısız")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, result)
}

// GetOverview handles GET /v1/workspaces/{ws}/competitive-gap/overview
// Tüm gap türlerini tek ekranda gösterir.
func (h *Handler) GetOverview(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")

	if brandID == "" {
		httputil.WriteError(w, http.StatusBadRequest, "brand_id gerekli")
		return
	}

	rows, err := h.pool.Query(r.Context(), `
		SELECT gs.id, gs.competitor_id, b2.name AS competitor_name,
		       gs.visibility_gap, gs.citation_gap, gs.content_gap, gs.topic_gap, gs.prompt_gap,
		       gs.competitive_score, gs.period_start, gs.period_end, gs.created_at
		FROM competitive.gap_snapshots gs
		JOIN config.brands b ON b.id = gs.brand_id
		JOIN config.brands b2 ON b2.id = gs.competitor_id
		WHERE gs.tenant_id = $1 AND b.workspace_id = $2 AND gs.brand_id = $3
		ORDER BY gs.created_at DESC
		LIMIT 20
	`, tenantID, workspaceID, brandID)
	if err != nil {
		slog.Debug("gap overview sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, []interface{}{})
		return
	}
	defer rows.Close()

	type gapRow struct {
		ID               string    `json:"id"`
		CompetitorID     string    `json:"competitor_id"`
		CompetitorName   string    `json:"competitor_name"`
		VisibilityGap    *float64  `json:"visibility_gap,omitempty"`
		CitationGap      *float64  `json:"citation_gap,omitempty"`
		ContentGap       *float64  `json:"content_gap,omitempty"`
		TopicGap         *float64  `json:"topic_gap,omitempty"`
		PromptGap        *float64  `json:"prompt_gap,omitempty"`
		CompetitiveScore float64   `json:"competitive_score"`
		PeriodStart      string    `json:"period_start"`
		PeriodEnd        string    `json:"period_end"`
		CreatedAt        time.Time `json:"created_at"`
	}

	results := make([]gapRow, 0)
	for rows.Next() {
		var g gapRow
		var ps, pe time.Time
		if err := rows.Scan(&g.ID, &g.CompetitorID, &g.CompetitorName,
			&g.VisibilityGap, &g.CitationGap, &g.ContentGap, &g.TopicGap, &g.PromptGap,
			&g.CompetitiveScore, &ps, &pe, &g.CreatedAt); err != nil {
			slog.Warn("gap satır okuma hatası", "error", err)
			continue
		}
		g.PeriodStart = ps.Format("2006-01-02")
		g.PeriodEnd = pe.Format("2006-01-02")
		results = append(results, g)
	}

	if rows.Err() != nil {
		slog.Warn("gap rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, results)
}

// GetVisibilityGap handles GET /v1/workspaces/{ws}/competitive-gap/visibility
// Visibility gap detayını döndürür.
func (h *Handler) GetVisibilityGap(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")
	competitorID := r.URL.Query().Get("competitor_id")

	if brandID == "" || competitorID == "" {
		httputil.WriteError(w, http.StatusBadRequest, "brand_id ve competitor_id gerekli")
		return
	}

	result, err := h.svc.GetGapDetail(r.Context(), brandID, competitorID, "visibility", workspaceID, tenantID)
	if err != nil {
		slog.Error("visibility gap sorgu hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "gap bilgisi alınamadı")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, result)
}

// GetRecommendations handles GET /v1/workspaces/{ws}/competitive-gap/recommendations
// Gap bazlı öneri listesini döndürür.
func (h *Handler) GetRecommendations(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")

	rows, err := h.pool.Query(r.Context(), `
		SELECT gr.id, gr.gap_type, gr.priority, gr.description, gr.impact, gr.kanit_derecesi
		FROM competitive.gap_recommendations gr
		JOIN competitive.gap_snapshots gs ON gs.id = gr.gap_id
		JOIN config.brands b ON b.id = gs.brand_id
		WHERE gr.tenant_id = $1 AND b.workspace_id = $2
			AND ($3 = '' OR gs.brand_id = $3)
		ORDER BY
			CASE gr.priority
				WHEN 'critical' THEN 1
				WHEN 'high' THEN 2
				WHEN 'medium' THEN 3
				WHEN 'low' THEN 4
			END
	`, tenantID, workspaceID, brandID)
	if err != nil {
		slog.Debug("gap recommendation sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, []interface{}{})
		return
	}
	defer rows.Close()

	type recRow struct {
		ID            string `json:"id"`
		GapType       string `json:"gap_type"`
		Priority      string `json:"priority"`
		Description   string `json:"description"`
		Impact        string `json:"impact,omitempty"`
		KanitDerecesi string `json:"kanit_derecesi,omitempty"`
	}

	recs := make([]recRow, 0)
	for rows.Next() {
		var r recRow
		if err := rows.Scan(&r.ID, &r.GapType, &r.Priority, &r.Description, &r.Impact, &r.KanitDerecesi); err != nil {
			slog.Warn("gap rec satır okuma hatası", "error", err)
			continue
		}
		recs = append(recs, r)
	}

	if rows.Err() != nil {
		slog.Warn("gap rec rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, recs)
}
