package pdf

import (
	"fmt"
	"log/slog"
	"net/http"

	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

// Handler holds dependencies for PDF HTTP handlers.
type Handler struct {
	svc Service
}

// NewHandler creates a new PDF handler.
func NewHandler() *Handler {
	return &Handler{
		svc: NewService(),
	}
}

// GenerateWeeklyDigest handles POST /v1/workspaces/{ws}/reports/digest
// Generates and returns a weekly digest PDF.
func (h *Handler) GenerateWeeklyDigest(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	result, err := h.svc.GenerateWeeklyDigest(workspaceID, tenantID)
	if err != nil {
		slog.Error("pdf digest oluşturma hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "rapor oluşturulamadı"})
		return
	}

	w.Header().Set("Content-Type", "application/pdf")
	w.Header().Set("Content-Disposition", "attachment; filename=\""+result.FileName+"\"")
	w.Header().Set("Content-Length", fmt.Sprintf("%d", len(result.Data)))
	w.WriteHeader(http.StatusOK)
	w.Write(result.Data)
}
