// Package contentgeo provides handlers for Content GEO (FR-E5, FR-E6).
package contentgeo

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

// Handler holds dependencies for content GEO HTTP handlers.
type Handler struct {
	pool dbiface.DB
	svc  *Engine
}

// NewHandler creates a new content GEO Handler.
func NewHandler(pool dbiface.DB) *Handler {
	return &Handler{pool: pool}
}

// NewProductionHandler creates a new content GEO Handler with a *db.Pool.
func NewProductionHandler(pool *db.Pool) *Handler {
	return &Handler{
		pool: dbiface.NewAdapter(pool),
		svc:  NewEngine(pool),
	}
}

// AnalyzeContentGap handles POST /v1/workspaces/{ws}/content-geo/gap
// Content gap analizi yapar (FR-E5).
func (h *Handler) AnalyzeContentGap(w http.ResponseWriter, r *http.Request) {
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

	result, err := h.svc.AnalyzeContentGap(r.Context(), req.BrandID, workspaceID, tenantID)
	if err != nil {
		slog.Error("content gap analiz hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "content gap analizi başarısız")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, result)
}

// ListContentGaps handles GET /v1/workspaces/{ws}/content-geo/gap
// Content gap analizi geçmişini listeler.
func (h *Handler) ListContentGaps(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")

	rows, err := h.pool.Query(r.Context(), `
		SELECT cg.id, cg.brand_id, cg.gap_type, cg.gap_score, cg.description,
		       cg.recommendation, cg.priority, cg.analyzed_at
		FROM content.gap_analyses cg
		JOIN config.brands b ON b.id = cg.brand_id
		WHERE cg.tenant_id = $1 AND b.workspace_id = $2
			AND ($3 = '' OR cg.brand_id = $3)
		ORDER BY cg.analyzed_at DESC
		LIMIT 50
	`, tenantID, workspaceID, brandID)
	if err != nil {
		slog.Debug("content gap sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, []interface{}{})
		return
	}
	defer rows.Close()

	type gapRow struct {
		ID             string    `json:"id"`
		BrandID        string    `json:"brand_id"`
		GapType        string    `json:"gap_type"`
		GapScore       float64   `json:"gap_score"`
		Description    string    `json:"description"`
		Recommendation string    `json:"recommendation"`
		Priority       string    `json:"priority"`
		AnalyzedAt     time.Time `json:"analyzed_at"`
	}

	results := make([]gapRow, 0)
	for rows.Next() {
		var g gapRow
		if err := rows.Scan(&g.ID, &g.BrandID, &g.GapType, &g.GapScore,
			&g.Description, &g.Recommendation, &g.Priority, &g.AnalyzedAt); err != nil {
			slog.Warn("content gap satır okuma hatası", "error", err)
			continue
		}
		results = append(results, g)
	}

	if rows.Err() != nil {
		slog.Warn("content gap rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, results)
}

// GetContentHubScore handles GET /v1/workspaces/{ws}/content-geo/hub-score
// Content Hub skorunu döndürür.
func (h *Handler) GetContentHubScore(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")

	if brandID == "" {
		httputil.WriteError(w, http.StatusBadRequest, "brand_id gerekli")
		return
	}

	result, err := h.svc.GetContentHubScore(r.Context(), brandID, workspaceID, tenantID)
	if err != nil {
		slog.Error("content hub skor hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "skor alınamadı")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, result)
}

// ListTopicClusters handles GET /v1/workspaces/{ws}/content-geo/topics
// Topic cluster önerilerini listeler.
func (h *Handler) ListTopicClusters(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")

	rows, err := h.pool.Query(r.Context(), `
		SELECT tc.id, tc.brand_id, tc.topic_name, tc.opportunity_score,
		       tc.relevance, tc.recommendation, tc.created_at
		FROM content.topic_clusters tc
		JOIN config.brands b ON b.id = tc.brand_id
		WHERE tc.tenant_id = $1 AND b.workspace_id = $2
			AND ($3 = '' OR tc.brand_id = $3)
		ORDER BY tc.opportunity_score DESC
		LIMIT 50
	`, tenantID, workspaceID, brandID)
	if err != nil {
		slog.Debug("topic cluster sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, []interface{}{})
		return
	}
	defer rows.Close()

	type topicRow struct {
		ID               string    `json:"id"`
		BrandID          string    `json:"brand_id"`
		TopicName        string    `json:"topic_name"`
		OpportunityScore float64   `json:"opportunity_score"`
		Relevance        string    `json:"relevance"`
		Recommendation   string    `json:"recommendation"`
		CreatedAt        time.Time `json:"created_at"`
	}

	results := make([]topicRow, 0)
	for rows.Next() {
		var t topicRow
		if err := rows.Scan(&t.ID, &t.BrandID, &t.TopicName, &t.OpportunityScore,
			&t.Relevance, &t.Recommendation, &t.CreatedAt); err != nil {
			slog.Warn("topic cluster satır okuma hatası", "error", err)
			continue
		}
		results = append(results, t)
	}

	if rows.Err() != nil {
		slog.Warn("topic rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, results)
}
