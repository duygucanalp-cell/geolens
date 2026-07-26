package delivery

import (
	"encoding/json"
	"log/slog"
	"net/http"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
	"github.com/geolens/platform/platform/metrics"
)

// Handler holds dependencies for delivery HTTP handlers.
type Handler struct {
	svc     Service
	pool    dbiface.DB
	rawPool *db.Pool
	config  EmailConfig
}

// NewHandler creates a new delivery handler with the given DB interface.
func NewHandler(pool dbiface.DB) *Handler {
	return &Handler{pool: pool}
}

// NewProductionHandler creates a new delivery handler with a *db.Pool for production use.
func NewProductionHandler(pool *db.Pool, cfg EmailConfig) *Handler {
	return &Handler{
		svc:     NewService(cfg, pool),
		pool:    dbiface.NewAdapter(pool),
		rawPool: pool,
		config:  cfg,
	}
}

// GetSettings handles GET /v1/workspaces/{ws}/notifications/settings
func (h *Handler) GetSettings(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	settings, err := h.svc.GetSettings(r.Context(), workspaceID, tenantID)
	if err != nil {
		slog.Error("notification settings okuma hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "ayarlar okunamadı"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, settings)
}

// UpdateSettings handles PUT /v1/workspaces/{ws}/notifications/settings
func (h *Handler) UpdateSettings(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	var req NotificationSettings
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}
	req.WorkspaceID = workspaceID

	if err := h.svc.UpdateSettings(r.Context(), &req, tenantID); err != nil {
		slog.Error("notification settings kaydetme hatası", "error", err)
		status := http.StatusInternalServerError
		// Validation errors should be 400, not 500
		if _, ok := err.(*validationError); ok {
			status = http.StatusBadRequest
		}
		httputil.WriteJSON(w, status, map[string]string{"error": err.Error()})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, req)
}

// SendTestEmail handles POST /v1/workspaces/{ws}/notifications/test
func (h *Handler) SendTestEmail(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Email   string `json:"email"`
		Subject string `json:"subject"`
		Body    string `json:"body"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}
	if req.Email == "" {
		req.Email = httpmw.GetUserID(r.Context()) + "@example.com"
	}
	if req.Subject == "" {
		req.Subject = "GeoLens — Test Bildirimi"
	}
	if req.Body == "" {
		req.Body = "<h2>Bu bir test bildirimidir</h2><p>E-posta altyapısı çalışıyor.</p>"
	}

	err := h.svc.SendEmail(req.Email, req.Subject, req.Body)
	if err != nil {
		slog.Error("test email gönderme hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": err.Error()})
		return
	}

	tenantID := httpmw.GetTenantID(r.Context())
	if tenantID != "" {
		metrics.EmailsSent.WithLabelValues(tenantID).Inc()
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "sent", "to": req.Email})
}
