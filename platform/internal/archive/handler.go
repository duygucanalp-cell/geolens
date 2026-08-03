// Package archive provides handlers for Response Archive (FR-D13).
package archive

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

// Handler holds dependencies for archive HTTP handlers.
type Handler struct {
	pool dbiface.DB
	svc  *Engine
}

// NewHandler creates a new archive Handler.
func NewHandler(pool dbiface.DB) *Handler {
	return &Handler{pool: pool}
}

// NewProductionHandler creates a new archive Handler with a *db.Pool.
func NewProductionHandler(pool *db.Pool) *Handler {
	return &Handler{
		pool: dbiface.NewAdapter(pool),
		svc:  NewEngine(pool),
	}
}

// ListEntries handles GET /v1/workspaces/{ws}/archive
// Arşivlenmiş yanıtları listeler.
func (h *Handler) ListEntries(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")
	engineName := r.URL.Query().Get("engine")
	versionStr := r.URL.Query().Get("version")

	query := `
		SELECT ae.id, ae.brand_id, ae.engine_name, ae.prompt_text, ae.response_preview,
		       ae.s3_ref, ae.version, ae.content_hash, ae.tenant_id, ae.created_at
		FROM archive.response_entries ae
		JOIN config.brands b ON b.id = ae.brand_id
		WHERE ae.tenant_id = $1 AND b.workspace_id = $2
			AND ($3 = '' OR ae.brand_id = $3)
			AND ($4 = '' OR ae.engine_name = $4)
			AND ($5 = '' OR ae.version = $5::int)
		ORDER BY ae.created_at DESC
		LIMIT 100
	`
	rows, err := h.pool.Query(r.Context(), query, tenantID, workspaceID, brandID, engineName, versionStr)
	if err != nil {
		slog.Debug("archive sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, []interface{}{})
		return
	}
	defer rows.Close()

	type entryRow struct {
		ID              string    `json:"id"`
		BrandID         string    `json:"brand_id"`
		EngineName      string    `json:"engine_name"`
		PromptText      string    `json:"prompt_text"`
		ResponsePreview string    `json:"response_preview"`
		S3Ref           *string   `json:"s3_ref,omitempty"`
		Version         int       `json:"version"`
		ContentHash     string    `json:"content_hash"`
		CreatedAt       time.Time `json:"created_at"`
	}

	entries := make([]entryRow, 0)
	for rows.Next() {
		var e entryRow
		if err := rows.Scan(&e.ID, &e.BrandID, &e.EngineName, &e.PromptText,
			&e.ResponsePreview, &e.S3Ref, &e.Version, &e.ContentHash, &e.CreatedAt); err != nil {
			slog.Warn("archive satır okuma hatası", "error", err)
			continue
		}
		entries = append(entries, e)
	}

	if rows.Err() != nil {
		slog.Warn("archive rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, entries)
}

// GetEntry handles GET /v1/workspaces/{ws}/archive/{entryId}
// Bir arşiv girişini detaylı olarak döndürür.
func (h *Handler) GetEntry(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	entryID := chi.URLParam(r, "entryId")

	var e struct {
		ID           string    `json:"id"`
		BrandID      string    `json:"brand_id"`
		EngineName   string    `json:"engine_name"`
		PromptText   string    `json:"prompt_text"`
		ResponseFull string    `json:"response_full"`
		Version      int       `json:"version"`
		ContentHash  string    `json:"content_hash"`
		CreatedAt    time.Time `json:"created_at"`
	}
	err := h.pool.QueryRow(r.Context(), `
		SELECT ae.id, ae.brand_id, ae.engine_name, ae.prompt_text,
		       ae.response_full, ae.version, ae.content_hash, ae.created_at
		FROM archive.response_entries ae
		JOIN config.brands b ON b.id = ae.brand_id
		WHERE ae.id = $1 AND ae.tenant_id = $2 AND b.workspace_id = $3
	`, entryID, tenantID, workspaceID).Scan(&e.ID, &e.BrandID, &e.EngineName, &e.PromptText,
		&e.ResponseFull, &e.Version, &e.ContentHash, &e.CreatedAt)
	if err != nil {
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "arşiv girişi bulunamadı"})
		return
	}

	httputil.WriteJSON(w, http.StatusOK, e)
}

// ArchiveResponse handles POST /v1/workspaces/{ws}/archive
// Bir AI yanıtını arşive ekler.
func (h *Handler) ArchiveResponse(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	var req struct {
		BrandID    string `json:"brand_id"`
		EngineName string `json:"engine_name"`
		PromptText string `json:"prompt_text"`
		Response   string `json:"response"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}
	if req.BrandID == "" || req.Response == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "brand_id ve response zorunludur"})
		return
	}

	entry, err := h.svc.Archive(r.Context(), req.BrandID, req.EngineName, req.PromptText, req.Response, workspaceID, tenantID)
	if err != nil {
		slog.Error("arşivleme hatası", "error", err)
		httputil.WriteError(w, http.StatusInternalServerError, "arşivleme başarısız")
		return
	}

	httputil.WriteJSON(w, http.StatusCreated, entry)
}

// GetVersionHistory handles GET /v1/workspaces/{ws}/archive/versions
// Bir markanın versiyon geçmişini döndürür.
func (h *Handler) GetVersionHistory(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")
	engineName := r.URL.Query().Get("engine")

	if brandID == "" {
		httputil.WriteError(w, http.StatusBadRequest, "brand_id gerekli")
		return
	}

	rows, err := h.pool.Query(r.Context(), `
		SELECT ae.version, ae.id, ae.content_hash, ae.created_at
		FROM archive.response_entries ae
		JOIN config.brands b ON b.id = ae.brand_id
		WHERE ae.tenant_id = $1 AND b.workspace_id = $2 AND ae.brand_id = $3
			AND ($4 = '' OR ae.engine_name = $4)
		ORDER BY ae.version DESC
	`, tenantID, workspaceID, brandID, engineName)
	if err != nil {
		slog.Debug("version history sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, []interface{}{})
		return
	}
	defer rows.Close()

	type versionRow struct {
		Version     int       `json:"version"`
		EntryID     string    `json:"entry_id"`
		ContentHash string    `json:"content_hash"`
		CreatedAt   time.Time `json:"created_at"`
	}

	versions := make([]versionRow, 0)
	for rows.Next() {
		var v versionRow
		if err := rows.Scan(&v.Version, &v.EntryID, &v.ContentHash, &v.CreatedAt); err != nil {
			slog.Warn("version history satır okuma hatası", "error", err)
			continue
		}
		versions = append(versions, v)
	}

	if rows.Err() != nil {
		slog.Warn("version rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, versions)
}
