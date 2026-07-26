// Package version provides handlers and logic for version functionality.
package version

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/internal/id"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

type Handler struct {
	pool dbiface.DB
}

func NewHandler(pool dbiface.DB) *Handler {
	return &Handler{pool: pool}
}

func NewProductionHandler(pool *db.Pool) *Handler {
	return NewHandler(dbiface.NewAdapter(pool))
}

func (h *Handler) RecordVersion(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var input struct {
		EntityType  string `json:"entity_type"`
		EntityID    string `json:"entity_id"`
		EntityName  string `json:"entity_name"`
		OldVersion  string `json:"old_version"`
		NewVersion  string `json:"new_version"`
		ChangeNotes string `json:"change_notes"`
		ChangedBy   string `json:"changed_by"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}
	if input.EntityType == "" || input.EntityID == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "entity_type ve entity_id gerekli"})
		return
	}

	entryID := id.New()
	now := time.Now().UTC()

	_, err := h.pool.Exec(r.Context(), `
		INSERT INTO version.entries (id, tenant_id, entity_type, entity_id, entity_name, old_version, new_version, change_notes, changed_by, created_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
	`, entryID, tenantID, input.EntityType, input.EntityID, input.EntityName,
		input.OldVersion, input.NewVersion, input.ChangeNotes, input.ChangedBy, now)
	if err != nil {
		slog.Warn("version entry DB'ye yazılamadı", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "versiyon kaydedilemedi"})
		return
	}

	slog.Info("version entry kaydedildi", "entry_id", entryID, "entity", input.EntityType, "from", input.OldVersion, "to", input.NewVersion)

	httputil.WriteJSON(w, http.StatusCreated, map[string]interface{}{
		"entry_id":    entryID,
		"entity_type": input.EntityType,
		"entity_name": input.EntityName,
		"old_version": input.OldVersion,
		"new_version": input.NewVersion,
		"created_at":  now.Format(time.RFC3339),
	})
}

type versionEntry struct {
	ID          string `json:"id"`
	EntityType  string `json:"entity_type"`
	EntityID    string `json:"entity_id"`
	EntityName  string `json:"entity_name"`
	OldVersion  string `json:"old_version"`
	NewVersion  string `json:"new_version"`
	ChangeNotes string `json:"change_notes"`
	ChangedBy   string `json:"changed_by"`
	CreatedAt   string `json:"created_at"`
}

func (h *Handler) ListVersions(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	if limit < 1 || limit > 100 {
		limit = 20
	}
	entityType := r.URL.Query().Get("entity_type")
	entityID := r.URL.Query().Get("entity_id")

	// LIMIT+1 pattern for has_more
	query := `SELECT id, entity_type, entity_id, entity_name, old_version, new_version, change_notes, changed_by, created_at
	          FROM version.entries WHERE tenant_id = $1`
	args := []interface{}{tenantID}
	paramIdx := 2

	if entityType != "" {
		query += ` AND entity_type = $` + strconv.Itoa(paramIdx)
		args = append(args, entityType)
		paramIdx++
	}
	if entityID != "" {
		query += ` AND entity_id = $` + strconv.Itoa(paramIdx)
		args = append(args, entityID)
		paramIdx++
	}
	query += ` ORDER BY created_at DESC LIMIT $` + strconv.Itoa(paramIdx)
	args = append(args, limit+1)

	rows, err := h.pool.Query(r.Context(), query, args...)
	if err != nil {
		slog.Warn("version listesi alınamadı", "error", err)
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"data": []interface{}{}, "has_more": false})
		return
	}
	defer rows.Close()

	var entries []versionEntry
	for rows.Next() {
		var e versionEntry
		var ts string
		if err := rows.Scan(&e.ID, &e.EntityType, &e.EntityID, &e.EntityName, &e.OldVersion, &e.NewVersion, &e.ChangeNotes, &e.ChangedBy, &ts); err != nil {
			slog.Warn("version satır okuma hatası", "error", err)
			continue
		}
		e.CreatedAt = ts
		entries = append(entries, e)
	}

	hasMore := len(entries) > limit
	if hasMore {
		entries = entries[:limit]
	}

	if entries == nil {
		entries = []versionEntry{}
	}

	if rows.Err() != nil {
		slog.Warn("version listesi rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"data":     entries,
		"has_more": hasMore,
	})
}

func (h *Handler) GetVersionDiff(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	entryID := chi.URLParam(r, "entryId")

	var e versionEntry
	var ts string
	err := h.pool.QueryRow(r.Context(), `
		SELECT id, entity_type, entity_id, entity_name, old_version, new_version, change_notes, changed_by, created_at
		FROM version.entries WHERE id = $1 AND tenant_id = $2
	`, entryID, tenantID).Scan(&e.ID, &e.EntityType, &e.EntityID, &e.EntityName, &e.OldVersion, &e.NewVersion, &e.ChangeNotes, &e.ChangedBy, &ts)
	if err != nil {
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "versiyon kaydı bulunamadı"})
		return
	}
	e.CreatedAt = ts

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"entry":       e,
		"has_changes": e.OldVersion != e.NewVersion,
	})
}
