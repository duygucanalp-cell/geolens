package recommendation

import (
	"log/slog"
	"net/http"

	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
	"github.com/go-chi/chi/v5"
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
	if recID == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "recommendation_id gerekli"})
		return
	}

	if err := h.svc.MarkApplied(recID); err != nil {
		slog.Error("öneri uygulama hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "öneri uygulanamadı"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "applied"})
}

// MarkDismissed handles POST /v1/workspaces/{ws}/recommendations/{recId}/dismiss
func (h *Handler) MarkDismissed(w http.ResponseWriter, r *http.Request) {
	recID := chi.URLParam(r, "recId")
	if recID == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "recommendation_id gerekli"})
		return
	}

	if err := h.svc.MarkDismissed(recID); err != nil {
		slog.Error("öneri gizleme hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "öneri gizlenemedi"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "dismissed"})
}
