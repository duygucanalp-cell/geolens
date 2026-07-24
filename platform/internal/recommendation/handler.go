package recommendation

import (
	"log/slog"
	"net/http"

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

	// TODO(H10): brandID'yi query param'dan veya tüm markalar için al
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
