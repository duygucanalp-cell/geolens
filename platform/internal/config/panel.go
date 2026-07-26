package config

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

// ---- Domain Types ----

// Panel represents a reusable measurement configuration.
type Panel struct {
	ID           string    `json:"id"`
	Name         string    `json:"name"`
	Description  string    `json:"description,omitempty"`
	PromptSetID  string    `json:"prompt_set_id,omitempty"`
	ScheduleCron string    `json:"schedule_cron,omitempty"`
	IsActive     bool      `json:"is_active"`
	CreatedAt    time.Time `json:"created_at"`
}

// PanelRequest is the request body for creating/updating a panel.
type PanelRequest struct {
	Name         string   `json:"name"`
	Description  string   `json:"description,omitempty"`
	PromptSetID  string   `json:"prompt_set_id,omitempty"`
	ScheduleCron string   `json:"schedule_cron,omitempty"`
	BrandIDs     []string `json:"brand_ids,omitempty"`
}

// PanelHandler holds dependencies for panel HTTP handlers.
type PanelHandler struct {
	pool dbiface.DB
}

// NewPanelHandler creates a new panel handler.
func NewPanelHandler(pool *db.Pool) *PanelHandler {
	return &PanelHandler{pool: dbiface.NewAdapter(pool)}
}

// ListPanels handles GET /v1/workspaces/{ws}/panels
func (h *PanelHandler) ListPanels(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	rows, err := h.pool.Query(r.Context(), `
		SELECT p.id, p.name, COALESCE(p.description, ''), 
		       COALESCE(p.prompt_set_id, ''), COALESCE(p.schedule_cron, ''),
		       p.is_active, p.created_at
		FROM config.panels p
		WHERE p.workspace_id = $1 AND p.tenant_id = $2
		ORDER BY p.name
	`, workspaceID, tenantID)
	if err != nil {
		slog.Error("panel listeleme hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "sorgu hatası"})
		return
	}
	defer rows.Close()

	panels := make([]Panel, 0)
	for rows.Next() {
		var p Panel
		if err := rows.Scan(&p.ID, &p.Name, &p.Description, &p.PromptSetID, &p.ScheduleCron, &p.IsActive, &p.CreatedAt); err != nil {
			slog.Error("panel satır okuma hatası", "error", err)
			continue
		}
		panels = append(panels, p)
	}

	if rows.Err() != nil {
		slog.Error("panel listesi rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, panels)
}

// CreatePanel handles POST /v1/workspaces/{ws}/panels
func (h *PanelHandler) CreatePanel(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	var req PanelRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	if req.Name == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "panel adı zorunludur"})
		return
	}

	var panelID string
	err := h.pool.QueryRow(r.Context(), `
		INSERT INTO config.panels (id, workspace_id, tenant_id, name, description, prompt_set_id, schedule_cron)
		VALUES (gen_random_uuid()::text, $1, $2, $3, $4, $5, $6)
		RETURNING id
	`, workspaceID, tenantID, req.Name, req.Description, req.PromptSetID, req.ScheduleCron).Scan(&panelID)
	if err != nil {
		slog.Error("panel oluşturma hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "panel oluşturulamadı"})
		return
	}

	// Panel-brand ilişkilerini ekle
	for _, brandID := range req.BrandIDs {
		_, err := h.pool.Exec(r.Context(), `
			INSERT INTO config.panel_brands (panel_id, brand_id, workspace_id, tenant_id)
			VALUES ($1, $2, $3, $4)
			ON CONFLICT DO NOTHING
		`, panelID, brandID, workspaceID, tenantID)
		if err != nil {
			slog.Warn("panel-marka ilişki hatası", "panel", panelID, "brand", brandID, "error", err)
		}
	}

	httputil.WriteJSON(w, http.StatusCreated, Panel{
		ID:           panelID,
		Name:         req.Name,
		Description:  req.Description,
		PromptSetID:  req.PromptSetID,
		ScheduleCron: req.ScheduleCron,
		IsActive:     true,
		CreatedAt:    time.Now().UTC(),
	})
}

// GetPanel handles GET /v1/workspaces/{ws}/panels/{panelID}
func (h *PanelHandler) GetPanel(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	panelID := chi.URLParam(r, "panelID")

	var p Panel
	err := h.pool.QueryRow(r.Context(), `
		SELECT id, name, COALESCE(description, ''), COALESCE(prompt_set_id, ''), 
		       COALESCE(schedule_cron, ''), is_active, created_at
		FROM config.panels
		WHERE id = $1 AND workspace_id = $2 AND tenant_id = $3
	`, panelID, workspaceID, tenantID).Scan(&p.ID, &p.Name, &p.Description, &p.PromptSetID, &p.ScheduleCron, &p.IsActive, &p.CreatedAt)
	if err != nil {
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "panel bulunamadı"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, p)
}

// ---- Prompt Set Handlers ----

// PromptSet represents a reusable prompt template.
type PromptSet struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Description string `json:"description,omitempty"`
	PromptText  string `json:"prompt_text"`
	IsActive    bool   `json:"is_active"`
}

// ListPromptSets handles GET /v1/workspaces/{ws}/prompt-sets
func (h *PanelHandler) ListPromptSets(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	rows, err := h.pool.Query(r.Context(), `
		SELECT id, name, COALESCE(description, ''), prompt_text, is_active
		FROM config.prompt_sets
		WHERE workspace_id = $1 AND tenant_id = $2
		ORDER BY name
	`, workspaceID, tenantID)
	if err != nil {
		slog.Error("prompt set listeleme hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "sorgu hatası"})
		return
	}
	defer rows.Close()

	sets := make([]PromptSet, 0)
	for rows.Next() {
		var ps PromptSet
		if err := rows.Scan(&ps.ID, &ps.Name, &ps.Description, &ps.PromptText, &ps.IsActive); err != nil {
			slog.Error("prompt set satır okuma hatası", "error", err)
			continue
		}
		sets = append(sets, ps)
	}

	if rows.Err() != nil {
		slog.Error("prompt set listesi rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, sets)
}

// CreatePromptSet handles POST /v1/workspaces/{ws}/prompt-sets
func (h *PanelHandler) CreatePromptSet(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	var req struct {
		Name        string `json:"name"`
		Description string `json:"description,omitempty"`
		PromptText  string `json:"prompt_text"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	if req.Name == "" || req.PromptText == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "ad ve prompt metni zorunludur"})
		return
	}

	var setID string
	err := h.pool.QueryRow(r.Context(), `
		INSERT INTO config.prompt_sets (id, workspace_id, tenant_id, name, description, prompt_text)
		VALUES (gen_random_uuid()::text, $1, $2, $3, $4, $5)
		RETURNING id
	`, workspaceID, tenantID, req.Name, req.Description, req.PromptText).Scan(&setID)
	if err != nil {
		slog.Error("prompt set oluşturma hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "prompt set oluşturulamadı"})
		return
	}

	httputil.WriteJSON(w, http.StatusCreated, PromptSet{
		ID:          setID,
		Name:        req.Name,
		Description: req.Description,
		PromptText:  req.PromptText,
		IsActive:    true,
	})
}
