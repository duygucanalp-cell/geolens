package config

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"time"

	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

// ArchiveWorkspace handles POST /v1/workspaces/{ws}/archive
// H4: Çalışma alanını arşivler (brand'ler pasif, ölçüm durdurulur).
func (h *Handler) ArchiveWorkspace(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	now := time.Now().UTC()
	_, err := h.pool.Exec(r.Context(), `
		UPDATE config.workspaces SET archived_at = $1, updated_at = $1
		WHERE id = $2 AND tenant_id = $3
	`, now, workspaceID, tenantID)
	if err != nil {
		slog.Error("workspace arşivleme hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "arşivleme başarısız")
		return
	}

	// Tüm brand'leri de arşivle
	_, _ = h.pool.Exec(r.Context(), `
		UPDATE config.brands SET archived_at = $1, is_active = false, updated_at = $1
		WHERE workspace_id = $2 AND tenant_id = $3
	`, now, workspaceID, tenantID)

	httputil.WriteJSON(w, http.StatusOK, map[string]string{
		"status":      "archived",
		"archived_at": now.Format(time.RFC3339),
	})
}

// UnarchiveWorkspace handles POST /v1/workspaces/{ws}/unarchive
func (h *Handler) UnarchiveWorkspace(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	_, err := h.pool.Exec(r.Context(), `
		UPDATE config.workspaces SET archived_at = NULL, updated_at = now()
		WHERE id = $1 AND tenant_id = $2
	`, workspaceID, tenantID)
	if err != nil {
		slog.Error("workspace geri alma hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "geri alma başarısız")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "unarchived"})
}

// TransferWorkspace handles POST /v1/workspaces/{ws}/transfer
// H4: Çalışma alanını başka bir kiracıya devreder (admin yetkisi gerekir).
func (h *Handler) TransferWorkspace(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	var req struct {
		TargetTenantID string `json:"target_tenant_id"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteError(w, http.StatusBadRequest, "geçersiz istek")
		return
	}
	if req.TargetTenantID == "" {
		httputil.WriteError(w, http.StatusBadRequest, "hedef kiracı ID gerekli")
		return
	}

	// Hedef kiracının varlığını kontrol et
	var exists bool
	err := h.pool.QueryRow(r.Context(), `
		SELECT EXISTS(SELECT 1 FROM identity.tenants WHERE id = $1)
	`, req.TargetTenantID).Scan(&exists)
	if err != nil || !exists {
		httputil.WriteError(w, http.StatusNotFound, "hedef kiracı bulunamadı")
		return
	}

	// Transaction ile workspace ve brand'leri devret
	tx, err := h.pool.Begin(r.Context())
	if err != nil {
		slog.Error("transaction başlatma hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "devir başarısız")
		return
	}
	defer tx.Rollback(r.Context())

	_, err = tx.Exec(r.Context(), `
		UPDATE config.workspaces SET tenant_id = $1, updated_at = now()
		WHERE id = $2 AND tenant_id = $3
	`, req.TargetTenantID, workspaceID, tenantID)
	if err != nil {
		slog.Error("workspace devir hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "devir başarısız")
		return
	}

	_, err = tx.Exec(r.Context(), `
		UPDATE config.brands SET tenant_id = $1, updated_at = now()
		WHERE workspace_id = $2 AND tenant_id = $3
	`, req.TargetTenantID, workspaceID, tenantID)
	if err != nil {
		slog.Error("brand devir hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "devir başarısız")
		return
	}

	if err := tx.Commit(r.Context()); err != nil {
		slog.Error("transaction commit hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "devir başarısız")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{
		"status":           "transferred",
		"target_tenant_id": req.TargetTenantID,
	})
}
