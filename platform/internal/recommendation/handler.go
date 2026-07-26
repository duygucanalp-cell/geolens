package recommendation

import (
	"log/slog"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

// Handler holds dependencies for recommendation HTTP handlers.
type Handler struct {
	svc Service
}

// NewHandler creates a new recommendation handler.
func NewHandler(pool *db.Pool) *Handler {
	return &Handler{
		svc: NewService(pool),
	}
}

// ListRecommendations handles GET /v1/workspaces/{ws}/recommendations
func (h *Handler) ListRecommendations(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	brandID := r.URL.Query().Get("brand_id")

	recs, err := h.svc.Evaluate(brandID, workspaceID, tenantID)
	if err != nil {
		slog.Error("öneri değerlendirme hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "öneriler alınamadı"})
		return
	}

	if recs == nil {
		recs = []Recommendation{}
	}

	httputil.WriteJSON(w, http.StatusOK, recs)
}

// MarkApplied handles POST /v1/workspaces/{ws}/recommendations/{recId}/apply
func (h *Handler) MarkApplied(w http.ResponseWriter, r *http.Request) {
	recID := chi.URLParam(r, "recId")
	tenantID := httpmw.GetTenantID(r.Context())
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	if recID == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "recommendation_id gerekli"})
		return
	}

	if err := h.svc.MarkApplied(recID, tenantID, workspaceID); err != nil {
		slog.Error("öneri uygulama hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "öneri uygulanamadı"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "applied"})
}

// MarkDismissed handles POST /v1/workspaces/{ws}/recommendations/{recId}/dismiss
func (h *Handler) MarkDismissed(w http.ResponseWriter, r *http.Request) {
	recID := chi.URLParam(r, "recId")
	tenantID := httpmw.GetTenantID(r.Context())
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	if recID == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "recommendation_id gerekli"})
		return
	}

	if err := h.svc.MarkDismissed(recID, tenantID, workspaceID); err != nil {
		slog.Error("öneri gizleme hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "öneri gizlenemedi"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "dismissed"})
}

// GetImpact handles GET /v1/workspaces/{ws}/recommendations/{recId}/impact
// H3: Uygulanan önerinin skor değişimini gösterir (öncesi vs sonrası).
func (h *Handler) GetImpact(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	recID := chi.URLParam(r, "recId")

	if recID == "" {
		httputil.WriteError(w, http.StatusBadRequest, "recommendation_id gerekli")
		return
	}

	var brandID string
	var appliedAt *time.Time
	err := h.svc.GetPool().QueryRow(r.Context(), `
		SELECT brand_id, applied_at FROM recommendation.results
		WHERE id = $1 AND workspace_id = $2 AND tenant_id = $3 AND applied = true
	`, recID, workspaceID, tenantID).Scan(&brandID, &appliedAt)
	if err != nil {
		httputil.WriteError(w, http.StatusNotFound, "öneri bulunamadı veya henüz uygulanmamış")
		return
	}

	if appliedAt == nil {
		httputil.WriteError(w, http.StatusConflict, "öneri uygulanma tarihi bulunamadı")
		return
	}

	type scoreAtTime struct {
		Value      float64 `json:"value"`
		Fidelity   string  `json:"fidelity"`
		MeasuredAt string  `json:"measured_at"`
	}

	// Önceki skor (recommendation'dan önceki en son score)
	var beforeScore scoreAtTime
	var beforeVal float64
	var beforeFidelity string
	var beforeAt time.Time
	err = h.svc.GetPool().QueryRow(r.Context(), `
		SELECT value, COALESCE(fidelity_label, 'yok'), freshness_at
		FROM measure.scores
		WHERE brand_id = $1 AND workspace_id = $2 AND tenant_id = $3
			AND freshness_at <= $4
		ORDER BY freshness_at DESC LIMIT 1
	`, brandID, workspaceID, tenantID, *appliedAt).Scan(&beforeVal, &beforeFidelity, &beforeAt)
	if err == nil {
		beforeScore = scoreAtTime{Value: beforeVal, Fidelity: beforeFidelity, MeasuredAt: beforeAt.Format(time.RFC3339)}
	}

	// Sonraki skor (recommendation'dan sonraki en son score)
	var afterScore scoreAtTime
	var afterVal float64
	var afterFidelity string
	var afterAt time.Time
	err = h.svc.GetPool().QueryRow(r.Context(), `
		SELECT value, COALESCE(fidelity_label, 'yok'), freshness_at
		FROM measure.scores
		WHERE brand_id = $1 AND workspace_id = $2 AND tenant_id = $3
			AND freshness_at > $4
		ORDER BY freshness_at ASC LIMIT 1
	`, brandID, workspaceID, tenantID, *appliedAt).Scan(&afterVal, &afterFidelity, &afterAt)
	if err == nil {
		afterScore = scoreAtTime{Value: afterVal, Fidelity: afterFidelity, MeasuredAt: afterAt.Format(time.RFC3339)}
	}

	change := 0.0
	if afterScore.Value != 0 || beforeScore.Value != 0 {
		change = afterScore.Value - beforeScore.Value
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"recommendation_id": recID,
		"brand_id":          brandID,
		"applied_at":        appliedAt.Format(time.RFC3339),
		"before":            beforeScore,
		"after":             afterScore,
		"change":            change,
	})
}

// ListRules handles GET /v1/workspaces/{ws}/recommendations/rules
// H8: Tüm kural kütüphanesini döndürür.
func (h *Handler) ListRules(w http.ResponseWriter, r *http.Request) {
	rules := h.svc.GetRules()
	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"rules": rules,
		"count": len(rules),
	})
}

// ListRulesBySector handles GET /v1/workspaces/{ws}/recommendations/rules/{sector}
// H8: Sektöre göre kural paketi döndürür.
func (h *Handler) ListRulesBySector(w http.ResponseWriter, r *http.Request) {
	sector := chi.URLParam(r, "sector")
	if sector == "" {
		httputil.WriteError(w, http.StatusBadRequest, "sektör gerekli")
		return
	}
	rules := h.svc.GetRulesBySector(sector)
	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"sector": sector,
		"rules":  rules,
		"count":  len(rules),
	})
}
