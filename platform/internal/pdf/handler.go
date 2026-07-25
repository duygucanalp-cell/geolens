package pdf

import (
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"

	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

// Handler holds dependencies for PDF HTTP handlers.
type Handler struct {
	svc Service
}

// NewHandler creates a new PDF handler.
func NewHandler(pool *db.Pool) *Handler {
	return &Handler{
		svc: NewService(pool),
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

// GenerateScoreCard handles POST /v1/workspaces/{ws}/reports/score-card
// Generates and returns a brand score card PDF.
func (h *Handler) GenerateScoreCard(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	var req struct {
		BrandID   string `json:"brand_id"`
		BrandName string `json:"brand_name,omitempty"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}
	if req.BrandID == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "brand_id zorunludur"})
		return
	}

	result, err := h.svc.Generate(ReportRequest{
		Type:        ReportScoreCard,
		WorkspaceID: workspaceID,
		TenantID:    tenantID,
		BrandID:     req.BrandID,
		BrandName:   req.BrandName,
	})
	if err != nil {
		slog.Error("pdf score card oluşturma hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "skor kartı oluşturulamadı"})
		return
	}

	w.Header().Set("Content-Type", "application/pdf")
	w.Header().Set("Content-Disposition", "attachment; filename=\""+result.FileName+"\"")
	w.Header().Set("Content-Length", fmt.Sprintf("%d", len(result.Data)))
	w.WriteHeader(http.StatusOK)
	w.Write(result.Data)
}

// GenerateAuditReport handles POST /v1/workspaces/{ws}/reports/audit
// Generates and returns a brand audit report PDF.
func (h *Handler) GenerateAuditReport(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	var req struct {
		BrandID   string `json:"brand_id"`
		BrandName string `json:"brand_name,omitempty"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}
	if req.BrandID == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "brand_id zorunludur"})
		return
	}

	result, err := h.svc.Generate(ReportRequest{
		Type:        ReportAudit,
		WorkspaceID: workspaceID,
		TenantID:    tenantID,
		BrandID:     req.BrandID,
		BrandName:   req.BrandName,
	})
	if err != nil {
		slog.Error("pdf audit rapor oluşturma hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "denetim raporu oluşturulamadı"})
		return
	}

	w.Header().Set("Content-Type", "application/pdf")
	w.Header().Set("Content-Disposition", "attachment; filename=\""+result.FileName+"\"")
	w.Header().Set("Content-Length", fmt.Sprintf("%d", len(result.Data)))
	w.WriteHeader(http.StatusOK)
	w.Write(result.Data)
}
