package audit

import (
	"encoding/json"
	"log/slog"
	"net/http"

	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

// Handler holds dependencies for audit HTTP handlers.
type Handler struct {
	pool *db.Pool
	svc  Service
}

// NewHandler creates a new audit handler.
func NewHandler(pool *db.Pool) *Handler {
	return &Handler{
		pool: pool,
		svc:  NewService(pool),
	}
}

// AuditRequest is the request body for triggering an audit.
type AuditRequest struct {
	BrandID    string `json:"brand_id"`
	BrandName  string `json:"brand_name"`
	WebsiteURL string `json:"website_url"`
}

// RunAudit handles POST /v1/workspaces/{ws}/audit
// Triggers a site audit for the given brand.
func (h *Handler) RunAudit(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	var req AuditRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	if req.BrandID == "" || req.WebsiteURL == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "brand_id ve website_url zorunludur"})
		return
	}

	// Marka doğrulama
	var brandName string
	if req.BrandName != "" {
		brandName = req.BrandName
	} else {
		err := h.pool.QueryRow(r.Context(), `
			SELECT name FROM config.brands
			WHERE id = $1 AND workspace_id = $2 AND tenant_id = $3 AND is_active = true
		`, req.BrandID, workspaceID, tenantID).Scan(&brandName)
		if err != nil {
			slog.Error("audit marka sorgu hatası", "error", err)
			httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "marka bulunamadı"})
			return
		}
	}

	result, err := h.svc.Audit(req.BrandID, brandName, req.WebsiteURL)
	if err != nil {
		slog.Error("audit hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "denetim başarısız"})
		return
	}

	// Sonucu workspace/tenant context ile zenginleştir
	result.WorkspaceID = workspaceID
	result.TenantID = tenantID

	slog.Info("site denetimi tamamlandı",
		"brand", brandName,
		"score", result.OverallScore,
		"issues", len(result.Issues),
	)

	httputil.WriteJSON(w, http.StatusOK, result)
}
