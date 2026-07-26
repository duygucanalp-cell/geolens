package incident

import (
	"context"
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

func (h *Handler) ListIncidents(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	if limit < 1 || limit > 100 {
		limit = 20
	}
	statusFilter := r.URL.Query().Get("status")
	severityFilter := r.URL.Query().Get("severity")

	// LIMIT+1 pattern for has_more
	query := `SELECT id, severity, category, title, status, source, entity_id, assigned_to, severity_score, occurred_at, resolved_at, created_at
	          FROM incident.events WHERE tenant_id = $1`
	args := []interface{}{tenantID}
	paramIdx := 2

	if statusFilter == "open" || statusFilter == "investigating" || statusFilter == "mitigated" || statusFilter == "resolved" || statusFilter == "closed" {
		query += ` AND status = $` + strconv.Itoa(paramIdx)
		args = append(args, statusFilter)
		paramIdx++
	}
	if severityFilter == "critical" || severityFilter == "high" || severityFilter == "medium" || severityFilter == "low" || severityFilter == "info" {
		query += ` AND severity = $` + strconv.Itoa(paramIdx)
		args = append(args, severityFilter)
		paramIdx++
	}
	query += ` ORDER BY created_at DESC LIMIT $` + strconv.Itoa(paramIdx)
	args = append(args, limit+1)

	rows, err := h.pool.Query(r.Context(), query, args...)
	if err != nil {
		slog.Warn("incident listesi alınamadı", "error", err)
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"incidents": []interface{}{}, "has_more": false, "count": 0})
		return
	}
	defer rows.Close()

	type inc struct {
		ID            string  `json:"id"`
		Severity      string  `json:"severity"`
		Category      string  `json:"category"`
		Title         string  `json:"title"`
		Status        string  `json:"status"`
		Source        string  `json:"source"`
		EntityID      string  `json:"entity_id"`
		AssignedTo    string  `json:"assigned_to"`
		SeverityScore float64 `json:"severity_score"`
		OccurredAt    string  `json:"occurred_at"`
		ResolvedAt    *string `json:"resolved_at,omitempty"`
		CreatedAt     string  `json:"created_at"`
	}
	var incidents []inc
	for rows.Next() {
		var i inc
		var occ, created string
		var resolved *string
		if err := rows.Scan(&i.ID, &i.Severity, &i.Category, &i.Title, &i.Status, &i.Source,
			&i.EntityID, &i.AssignedTo, &i.SeverityScore, &occ, &resolved, &created); err != nil {
			slog.Warn("incident satır okuma hatası", "error", err)
			continue
		}
		i.OccurredAt = occ
		i.ResolvedAt = resolved
		i.CreatedAt = created
		incidents = append(incidents, i)
	}

	hasMore := len(incidents) > limit
	if hasMore {
		incidents = incidents[:limit]
	}

	if incidents == nil {
		incidents = []inc{}
	}

	if rows.Err() != nil {
		slog.Warn("incident listesi rows iterasyon hatası", "error", rows.Err())
	}

	var openCount, criticalCount int
	h.pool.QueryRow(r.Context(), `SELECT COUNT(*) FROM incident.events WHERE tenant_id = $1 AND status NOT IN ('resolved','closed')`, tenantID).Scan(&openCount)
	h.pool.QueryRow(r.Context(), `SELECT COUNT(*) FROM incident.events WHERE tenant_id = $1 AND severity IN ('critical','high') AND status NOT IN ('resolved','closed')`, tenantID).Scan(&criticalCount)

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"incidents":      incidents,
		"count":          len(incidents),
		"has_more":       hasMore,
		"open_count":     openCount,
		"critical_count": criticalCount,
	})
}

func (h *Handler) CreateIncident(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var input struct {
		Severity      string  `json:"severity"`
		Category      string  `json:"category"`
		Title         string  `json:"title"`
		Description   string  `json:"description"`
		Source        string  `json:"source"`
		EntityID      string  `json:"entity_id"`
		AssignedTo    string  `json:"assigned_to"`
		SeverityScore float64 `json:"severity_score"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}
	if input.Title == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "title gerekli"})
		return
	}

	validSeverity := map[string]bool{"critical": true, "high": true, "medium": true, "low": true, "info": true}
	if !validSeverity[input.Severity] {
		input.Severity = "medium"
	}
	validCategory := map[string]bool{"outage": true, "degradation": true, "bias": true, "injection": true, "data_leak": true, "policy_violation": true, "other": true}
	if !validCategory[input.Category] {
		input.Category = "other"
	}

	incidentID := id.New()
	now := time.Now().UTC()

	_, err := h.pool.Exec(r.Context(), `
		INSERT INTO incident.events (id, tenant_id, severity, category, title, description, status, source, entity_id, assigned_to, severity_score, occurred_at)
		VALUES ($1, $2, $3, $4, $5, $6, 'open', $7, $8, $9, $10, $11)
	`, incidentID, tenantID, input.Severity, input.Category, input.Title, input.Description,
		input.Source, input.EntityID, input.AssignedTo, input.SeverityScore, now)
	if err != nil {
		slog.Warn("incident DB'ye yazılamadı", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "incident kaydedilemedi"})
		return
	}

	slog.Info("incident oluşturuldu", "incident_id", incidentID, "severity", input.Severity, "title", input.Title)

	httputil.WriteJSON(w, http.StatusCreated, map[string]interface{}{
		"incident_id":    incidentID,
		"severity":       input.Severity,
		"title":          input.Title,
		"status":         "open",
		"severity_score": input.SeverityScore,
		"created_at":     now.Format(time.RFC3339),
	})
}

func (h *Handler) UpdateIncident(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	incidentID := chi.URLParam(r, "incidentId")

	var input struct {
		Status     string `json:"status"`
		Resolution string `json:"resolution"`
		AssignedTo string `json:"assigned_to"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	if input.Status != "" {
		valid := map[string]bool{"investigating": true, "mitigated": true, "resolved": true, "closed": true}
		if !valid[input.Status] {
			httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz durum"})
			return
		}
	}

	resolvedSet := input.Status == "resolved" || input.Status == "closed"
	result, err := h.pool.Exec(r.Context(), `
		UPDATE incident.events
		SET status = CASE WHEN $2 != '' THEN $2 ELSE status END,
		    resolution = CASE WHEN $3 != '' THEN $3 ELSE resolution END,
		    assigned_to = CASE WHEN $4 != '' THEN $4 ELSE assigned_to END,
		    resolved_at = CASE WHEN $5 THEN NOW() ELSE resolved_at END,
		    updated_at = NOW()
		WHERE id = $1 AND tenant_id = $6
	`, incidentID, input.Status, input.Resolution, input.AssignedTo, resolvedSet, tenantID)
	if err != nil {
		slog.Warn("incident güncelleme hatası", "incident_id", incidentID, "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "incident güncellenemedi"})
		return
	}
	if result.RowsAffected() == 0 {
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "incident bulunamadı"})
		return
	}

	slog.Info("incident güncellendi", "incident_id", incidentID, "status", input.Status)

	httputil.WriteJSON(w, http.StatusOK, map[string]string{
		"incident_id": incidentID,
		"status":      input.Status,
	})
}
