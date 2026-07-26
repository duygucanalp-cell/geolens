package pdf

import (
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

// Handler holds dependencies for PDF HTTP handlers.
type Handler struct {
	svc  Service
	pool *db.Pool
}

// NewHandler creates a new PDF handler.
func NewHandler(pool *db.Pool) *Handler {
	return &Handler{
		svc:  NewService(pool),
		pool: pool,
	}
}

// Svc exposes the underlying service for the report processor.
func (h *Handler) Svc() Service {
	return h.svc
}

// GenerateWeeklyDigest handles POST /v1/workspaces/{ws}/reports/digest
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

// ---- Async Report Flow (FR-F5) ----

type createReportRequest struct {
	ReportType string `json:"report_type"`
	BrandID    string `json:"brand_id,omitempty"`
	BrandName  string `json:"brand_name,omitempty"`
}

// RequestReport handles POST /v1/workspaces/{ws}/reports
// Async rapor talebi oluşturur, report_id döner.
func (h *Handler) RequestReport(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	var req createReportRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteError(w, http.StatusBadRequest, "geçersiz istek")
		return
	}
	if req.ReportType == "" {
		httputil.WriteError(w, http.StatusBadRequest, "report_type zorunludur (digest, score_card, audit)")
		return
	}

	validTypes := map[string]bool{"digest": true, "score_card": true, "audit": true}
	if !validTypes[req.ReportType] {
		httputil.WriteError(w, http.StatusBadRequest, "geçersiz rapor tipi (digest, score_card, audit)")
		return
	}

	var brandID *string
	if req.BrandID != "" {
		brandID = &req.BrandID
	}

	params := map[string]string{"brand_name": req.BrandName}

	var reportID string
	err := h.pool.QueryRow(r.Context(), `
		INSERT INTO measure.reports (id, tenant_id, workspace_id, report_type, brand_id, status, params)
		VALUES (gen_random_uuid()::text, $1, $2, $3, $4, 'pending', $5)
		RETURNING id
	`, tenantID, workspaceID, req.ReportType, brandID, params).Scan(&reportID)
	if err != nil {
		slog.Error("rapor talebi oluşturma hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "rapor talebi oluşturulamadı")
		return
	}

	httputil.WriteJSON(w, http.StatusCreated, map[string]string{
		"report_id": reportID,
		"status":    "pending",
	})
}

// GetReportStatus handles GET /v1/workspaces/{ws}/reports/{reportId}/status
func (h *Handler) GetReportStatus(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	reportID := chi.URLParam(r, "reportId")

	var status, reportType string
	var fileName *string
	var fileSize *int64
	var errMsg *string
	var createdAt time.Time
	var updatedAt time.Time

	err := h.pool.QueryRow(r.Context(), `
		SELECT status, report_type, file_name, file_size, error_message, created_at, updated_at
		FROM measure.reports
		WHERE id = $1 AND workspace_id = $2 AND tenant_id = $3
	`, reportID, workspaceID, tenantID).Scan(&status, &reportType, &fileName, &fileSize, &errMsg, &createdAt, &updatedAt)
	if err != nil {
		httputil.WriteError(w, http.StatusNotFound, "rapor bulunamadı")
		return
	}

	response := map[string]interface{}{
		"report_id":   reportID,
		"report_type": reportType,
		"status":      status,
		"created_at":  createdAt.Format(time.RFC3339),
		"updated_at":  updatedAt.Format(time.RFC3339),
	}
	if fileName != nil {
		response["file_name"] = *fileName
	}
	if fileSize != nil {
		response["file_size"] = *fileSize
	}
	if errMsg != nil {
		response["error"] = *errMsg
	}

	httputil.WriteJSON(w, http.StatusOK, response)
}

// DownloadReport handles GET /v1/workspaces/{ws}/reports/{reportId}/download
func (h *Handler) DownloadReport(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	reportID := chi.URLParam(r, "reportId")

	var status, reportType string
	var fileName string
	// PDF verisi JSONB params içinde base64 olarak saklanır
	var paramsJSON *string

	err := h.pool.QueryRow(r.Context(), `
		SELECT status, report_type, file_name, params::text
		FROM measure.reports
		WHERE id = $1 AND workspace_id = $2 AND tenant_id = $3
	`, reportID, workspaceID, tenantID).Scan(&status, &reportType, &fileName, &paramsJSON)
	if err != nil {
		httputil.WriteError(w, http.StatusNotFound, "rapor bulunamadı")
		return
	}
	if status != "ready" {
		httputil.WriteError(w, http.StatusConflict, "rapor henüz hazır değil")
		return
	}

	w.Header().Set("Content-Type", "application/pdf")
	w.Header().Set("Content-Disposition", "attachment; filename=\""+fileName+"\"")
	// Jin: redirect to S3 veya direkt PDF döner
	w.Header().Set("X-Report-ID", reportID)
	w.WriteHeader(http.StatusOK)
	w.Write([]byte("PDF hazır — depolama entegrasyonu tamamlandığında direkt PDF dönecek"))
}
