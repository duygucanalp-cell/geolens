package config

import (
	"encoding/json"
	"log/slog"
	"net/http"

	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httputil"
	"github.com/geolens/platform/platform/httpmw"
)

type brandRequest struct {
	Name        string `json:"name"`
	WebsiteURL  string `json:"website_url"`
	Competitors []string `json:"competitors,omitempty"`
}

type brandResponse struct {
	ID         string `json:"id"`
	Name       string `json:"name"`
	WebsiteURL string `json:"website_url"`
}

// Handler holds dependencies for config HTTP handlers.
type Handler struct {
	pool *db.Pool
}

// NewHandler creates a new config handler.
func NewHandler(pool *db.Pool) *Handler {
	return &Handler{pool: pool}
}

// ListBrands handles GET /v1/workspaces/{ws}/brands
func (h *Handler) ListBrands(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	rows, err := h.pool.Query(r.Context(), `
		SELECT id, name, website_url
		FROM config.brands
		WHERE workspace_id = $1 AND tenant_id = $2 AND is_active = true
		ORDER BY name
	`, workspaceID, tenantID)
	if err != nil {
		slog.Error("marka listeleme hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "sorgu hatası"})
		return
	}
	defer rows.Close()

	brands := make([]brandResponse, 0)
	for rows.Next() {
		var b brandResponse
		if err := rows.Scan(&b.ID, &b.Name, &b.WebsiteURL); err != nil {
			slog.Error("marka satır okuma hatası", "error", err)
			continue
		}
		brands = append(brands, b)
	}

	httputil.WriteJSON(w, http.StatusOK, brands)
}

// CreateBrand handles POST /v1/workspaces/{ws}/brands
func (h *Handler) CreateBrand(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	var req brandRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	if req.Name == "" || req.WebsiteURL == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "marka adı ve web sitesi zorunludur"})
		return
	}

	var brandID string
	err := h.pool.QueryRow(r.Context(), `
		INSERT INTO config.brands (id, workspace_id, tenant_id, name, website_url)
		VALUES (gen_random_uuid()::text, $1, $2, $3, $4)
		RETURNING id
	`, workspaceID, tenantID, req.Name, req.WebsiteURL).Scan(&brandID)

	if err != nil {
		slog.Error("marka oluşturma hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "marka oluşturulamadı"})
		return
	}

	httputil.WriteJSON(w, http.StatusCreated, brandResponse{
		ID:         brandID,
		Name:       req.Name,
		WebsiteURL: req.WebsiteURL,
	})
}


