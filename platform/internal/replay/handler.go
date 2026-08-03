// Package replay provides handlers for Conversation Replay (FR-D12).
package replay

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

// Handler holds dependencies for replay HTTP handlers.
type Handler struct {
	pool dbiface.DB
	svc  *Engine
}

// NewHandler creates a new replay Handler.
func NewHandler(pool dbiface.DB) *Handler {
	return &Handler{pool: pool}
}

// NewProductionHandler creates a new replay Handler with a *db.Pool.
func NewProductionHandler(pool *db.Pool) *Handler {
	return &Handler{
		pool: dbiface.NewAdapter(pool),
		svc:  NewEngine(pool),
	}
}

// CaptureSnapshot handles POST /v1/workspaces/{ws}/replay/capture
// Mevcut AI yanıtlarının anlık görüntüsünü alır.
func (h *Handler) CaptureSnapshot(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	var req struct {
		BrandID string `json:"brand_id"`
		Prompt  string `json:"prompt"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}
	if req.BrandID == "" || req.Prompt == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "brand_id ve prompt zorunludur"})
		return
	}

	snapshot, err := h.svc.CaptureSnapshot(r.Context(), req.BrandID, req.Prompt, workspaceID, tenantID)
	if err != nil {
		slog.Error("replay snapshot hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "snapshot alınamadı"})
		return
	}

	httputil.WriteJSON(w, http.StatusCreated, snapshot)
}

// ListSnapshots handles GET /v1/workspaces/{ws}/replay
// Anlık görüntüleri listeler.
func (h *Handler) ListSnapshots(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")

	rows, err := h.pool.Query(r.Context(), `
		SELECT cs.id, cs.brand_id, cs.prompt_text, cs.engine_name, cs.response_preview,
		       cs.content_hash, cs.s3_ref, cs.created_at
		FROM replay.conversation_snapshots cs
		JOIN config.brands b ON b.id = cs.brand_id
		WHERE cs.tenant_id = $1 AND b.workspace_id = $2
			AND ($3 = '' OR cs.brand_id = $3)
		ORDER BY cs.created_at DESC
		LIMIT 100
	`, tenantID, workspaceID, brandID)
	if err != nil {
		slog.Debug("replay sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, []interface{}{})
		return
	}
	defer rows.Close()

	type snapshotRow struct {
		ID              string    `json:"id"`
		BrandID         string    `json:"brand_id"`
		PromptText      string    `json:"prompt_text"`
		EngineName      string    `json:"engine_name"`
		ResponsePreview string    `json:"response_preview"`
		ContentHash     string    `json:"content_hash"`
		S3Ref           *string   `json:"s3_ref,omitempty"`
		CreatedAt       time.Time `json:"created_at"`
	}

	snapshots := make([]snapshotRow, 0)
	for rows.Next() {
		var s snapshotRow
		if err := rows.Scan(&s.ID, &s.BrandID, &s.PromptText, &s.EngineName,
			&s.ResponsePreview, &s.ContentHash, &s.S3Ref, &s.CreatedAt); err != nil {
			slog.Warn("replay satır okuma hatası", "error", err)
			continue
		}
		snapshots = append(snapshots, s)
	}

	if rows.Err() != nil {
		slog.Warn("replay rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, snapshots)
}

// GetSnapshot handles GET /v1/workspaces/{ws}/replay/{snapshotId}
// Bir anlık görüntüyü detaylı olarak döndürür.
func (h *Handler) GetSnapshot(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	snapshotID := chi.URLParam(r, "snapshotId")

	var s struct {
		ID           string    `json:"id"`
		BrandID      string    `json:"brand_id"`
		PromptText   string    `json:"prompt_text"`
		EngineName   string    `json:"engine_name"`
		ResponseFull string    `json:"response_full"`
		ContentHash  string    `json:"content_hash"`
		S3Ref        *string   `json:"s3_ref,omitempty"`
		CreatedAt    time.Time `json:"created_at"`
	}
	err := h.pool.QueryRow(r.Context(), `
		SELECT cs.id, cs.brand_id, cs.prompt_text, cs.engine_name,
		       COALESCE(cs.response_full, cs.response_preview),
		       cs.content_hash, cs.s3_ref, cs.created_at
		FROM replay.conversation_snapshots cs
		JOIN config.brands b ON b.id = cs.brand_id
		WHERE cs.id = $1 AND cs.tenant_id = $2 AND b.workspace_id = $3
	`, snapshotID, tenantID, workspaceID).Scan(&s.ID, &s.BrandID, &s.PromptText, &s.EngineName,
		&s.ResponseFull, &s.ContentHash, &s.S3Ref, &s.CreatedAt)
	if err != nil {
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "snapshot bulunamadı"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, s)
}

// DeleteSnapshot handles DELETE /v1/workspaces/{ws}/replay/{snapshotId}
// Bir anlık görüntüyü siler (KVKK uyumlu).
func (h *Handler) DeleteSnapshot(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	snapshotID := chi.URLParam(r, "snapshotId")

	result, err := h.pool.Exec(r.Context(), `
		DELETE FROM replay.conversation_snapshots WHERE id = $1 AND tenant_id = $2
	`, snapshotID, tenantID)
	if err != nil {
		slog.Error("replay silme hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "silme başarısız")
		return
	}
	if result.RowsAffected() == 0 {
		httputil.WriteError(w, http.StatusNotFound, "snapshot bulunamadı")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]string{"status": "deleted"})
}

// CompareSnapshots handles GET /v1/workspaces/{ws}/replay/compare
// İki snapshot arasındaki farkı karşılaştırır.
func (h *Handler) CompareSnapshots(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	snapshotA := r.URL.Query().Get("snapshot_a")
	snapshotB := r.URL.Query().Get("snapshot_b")

	if snapshotA == "" || snapshotB == "" {
		httputil.WriteError(w, http.StatusBadRequest, "snapshot_a ve snapshot_b gerekli")
		return
	}

	diff, err := h.svc.Compare(r.Context(), snapshotA, snapshotB, workspaceID, tenantID)
	if err != nil {
		slog.Error("replay karşılaştırma hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "karşılaştırma başarısız")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, diff)
}
