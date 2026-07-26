// Package agent provides handlers and logic for agent functionality.
package agent

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

// StartTrace yeni bir agent trace başlatır ve DB'ye kaydeder.
func (h *Handler) StartTrace(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var input struct {
		AgentName    string `json:"agent_name"`
		WorkflowName string `json:"workflow_name"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	if input.AgentName == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "agent_name gerekli"})
		return
	}

	traceID := id.New()
	now := time.Now().UTC()

	_, err := h.pool.Exec(r.Context(), `
		INSERT INTO agent.traces (id, tenant_id, agent_name, workflow_name, status, started_at)
		VALUES ($1, $2, $3, $4, 'running', $5)
	`, traceID, tenantID, input.AgentName, input.WorkflowName, now)
	if err != nil {
		slog.Warn("trace DB'ye yazılamadı", "trace_id", traceID, "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "trace başlatılamadı"})
		return
	}

	slog.Info("agent trace başlatıldı", "trace_id", traceID, "agent", input.AgentName, "tenant", tenantID)

	httputil.WriteJSON(w, http.StatusCreated, map[string]interface{}{
		"trace_id":      traceID,
		"agent_name":    input.AgentName,
		"workflow_name": input.WorkflowName,
		"status":        "running",
		"started_at":    now.Format(time.RFC3339),
	})
}

// GetTrace bir trace'in detaylarını ve adımlarını DB'den getirir.
func (h *Handler) GetTrace(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	traceID := chi.URLParam(r, "traceId")

	// Trace ana bilgilerini sorgula
	var t struct {
		AgentName       string     `json:"agent_name"`
		WorkflowName    string     `json:"workflow_name"`
		Status          string     `json:"status"`
		TotalSteps      int        `json:"total_steps"`
		CompletedSteps  int        `json:"completed_steps"`
		TotalDurationMs int        `json:"total_duration_ms"`
		StartedAt       time.Time  `json:"started_at"`
		CompletedAt     *time.Time `json:"completed_at"`
	}
	err := h.pool.QueryRow(r.Context(), `
		SELECT agent_name, workflow_name, status, total_steps, completed_steps,
		       total_duration_ms, started_at, completed_at
		FROM agent.traces WHERE id = $1 AND tenant_id = $2
	`, traceID, tenantID).Scan(&t.AgentName, &t.WorkflowName, &t.Status, &t.TotalSteps,
		&t.CompletedSteps, &t.TotalDurationMs, &t.StartedAt, &t.CompletedAt)
	if err != nil {
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "trace bulunamadı"})
		return
	}

	// Adımları sorgula
	rows, err := h.pool.Query(r.Context(), `
		SELECT id, step_name, agent_name, input, output, status, duration_ms,
		       COALESCE(error_message, ''), started_at, completed_at
		FROM agent.steps WHERE trace_id = $1 ORDER BY started_at NULLS LAST
	`, traceID)
	if err != nil {
		slog.Warn("trace adımları alınamadı", "trace_id", traceID, "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "adımlar alınamadı"})
		return
	}
	defer rows.Close()

	type stepResult struct {
		StepID      string     `json:"step_id"`
		StepName    string     `json:"step_name"`
		AgentName   string     `json:"agent"`
		Input       string     `json:"input"`
		Output      string     `json:"output"`
		Status      string     `json:"status"`
		DurationMs  int        `json:"duration_ms"`
		ErrorMsg    string     `json:"error_message,omitempty"`
		StartedAt   *time.Time `json:"started_at,omitempty"`
		CompletedAt *time.Time `json:"completed_at,omitempty"`
	}

	var steps []stepResult
	for rows.Next() {
		var s stepResult
		if err := rows.Scan(&s.StepID, &s.StepName, &s.AgentName, &s.Input, &s.Output,
			&s.Status, &s.DurationMs, &s.ErrorMsg, &s.StartedAt, &s.CompletedAt); err != nil {
			slog.Warn("step satırı okunamadı", "error", err)
			continue
		}
		steps = append(steps, s)
	}

	if rows.Err() != nil {
		slog.Warn("agent steps rows iterasyon hatası", "error", rows.Err())
	}

	if steps == nil {
		steps = []stepResult{}
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"trace_id":          traceID,
		"agent_name":        t.AgentName,
		"workflow_name":     t.WorkflowName,
		"status":            t.Status,
		"total_steps":       t.TotalSteps,
		"completed_steps":   t.CompletedSteps,
		"total_duration_ms": t.TotalDurationMs,
		"started_at":        t.StartedAt.Format(time.RFC3339),
		"steps":             steps,
	})
}

// RecordStep bir trace'e yeni bir adım ekler ve trace durumunu günceller.
func (h *Handler) RecordStep(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	traceID := chi.URLParam(r, "traceId")

	var input struct {
		StepName   string `json:"step_name"`
		AgentName  string `json:"agent_name"`
		Input      string `json:"input"`
		Output     string `json:"output"`
		Status     string `json:"status"` // running, completed, failed
		DurationMs int    `json:"duration_ms"`
		ErrorMsg   string `json:"error_message"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	// Trace'in varlığını ve tenant'ını kontrol et
	var traceStatus string
	err := h.pool.QueryRow(r.Context(), `
		SELECT status FROM agent.traces WHERE id = $1 AND tenant_id = $2
	`, traceID, tenantID).Scan(&traceStatus)
	if err != nil {
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "trace bulunamadı"})
		return
	}

	if traceStatus == "completed" || traceStatus == "cancelled" { //nolint:misspell
		httputil.WriteJSON(w, http.StatusConflict, map[string]string{"error": "tamamlanmış trace'e adım eklenemez"})
		return
	}

	stepID := id.New()
	now := time.Now().UTC()

	// Geçerli step_status
	stepStatus := input.Status
	if stepStatus != "running" && stepStatus != "completed" && stepStatus != "failed" {
		stepStatus = "running"
	}

	_, err = h.pool.Exec(r.Context(), `
		INSERT INTO agent.steps (id, trace_id, tenant_id, step_name, agent_name, input, output,
		                         status, duration_ms, error_message, started_at, completed_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11,
		        CASE WHEN $8 IN ('completed','failed') THEN $11 ELSE NULL END)
	`, stepID, traceID, tenantID, input.StepName, input.AgentName, input.Input, input.Output,
		stepStatus, input.DurationMs, input.ErrorMsg, now)
	if err != nil {
		slog.Warn("step DB'ye yazılamadı", "step_id", stepID, "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "adım kaydedilemedi"})
		return
	}

	// Trace istatistiklerini güncelle
	_, err = h.pool.Exec(r.Context(), `
		UPDATE agent.traces
		SET total_steps = total_steps + 1,
		    completed_steps = completed_steps + CASE WHEN $2 IN ('completed','failed') THEN 1 ELSE 0 END,
		    total_duration_ms = total_duration_ms + $3,
		    status = CASE WHEN $2 = 'failed' THEN 'failed' ELSE status END
		WHERE id = $1 AND tenant_id = $4
	`, traceID, stepStatus, input.DurationMs, tenantID)
	if err != nil {
		slog.Warn("trace istatistikleri güncellenemedi", "trace_id", traceID, "error", err)
	}

	slog.Info("agent step kaydedildi", "trace_id", traceID, "step_id", stepID,
		"step", input.StepName, "status", stepStatus, "duration_ms", input.DurationMs)

	httputil.WriteJSON(w, http.StatusCreated, map[string]interface{}{
		"step_id":     stepID,
		"trace_id":    traceID,
		"step_name":   input.StepName,
		"status":      stepStatus,
		"duration_ms": input.DurationMs,
	})
}

// CompleteTrace bir trace'i tamamlanmış olarak işaretler.
func (h *Handler) CompleteTrace(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	traceID := chi.URLParam(r, "traceId")

	var input struct {
		Status string `json:"status"` // completed, failed, cancelled //nolint:misspell
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	finalStatus := input.Status
	if finalStatus != "completed" && finalStatus != "failed" && finalStatus != "cancelled" { //nolint:misspell
		finalStatus = "completed"
	}

	result, err := h.pool.Exec(r.Context(), `
		UPDATE agent.traces
		SET status = $1, completed_at = NOW()
		WHERE id = $2 AND tenant_id = $3 AND status = 'running'
	`, finalStatus, traceID, tenantID)
	if err != nil {
		slog.Warn("trace güncellenemedi", "trace_id", traceID, "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "trace güncellenemedi"})
		return
	}

	rowsAffected := result.RowsAffected()
	if rowsAffected == 0 {
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "trace bulunamadı veya zaten tamamlanmış"})
		return
	}

	slog.Info("trace tamamlandı", "trace_id", traceID, "status", finalStatus)

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"trace_id": traceID,
		"status":   finalStatus,
	})
}

// ListTraces tenant bazlı trace geçmişini DB'den döndürür.
func (h *Handler) ListTraces(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	limitStr := r.URL.Query().Get("limit")
	limit, err := strconv.Atoi(limitStr)
	if err != nil || limit < 1 || limit > 100 {
		limit = 20
	}

	statusFilter := r.URL.Query().Get("status")
	offsetStr := r.URL.Query().Get("offset")
	offset, err := strconv.Atoi(offsetStr)
	if err != nil || offset < 0 {
		offset = 0
	}

	query := `SELECT id, agent_name, workflow_name, status, total_steps, completed_steps,
	                 total_duration_ms, started_at, completed_at
	          FROM agent.traces WHERE tenant_id = $1`
	args := []interface{}{tenantID}
	paramIdx := 2

	if statusFilter == "running" || statusFilter == "completed" || statusFilter == "failed" || statusFilter == "cancelled" { //nolint:misspell
		query += ` AND status = $` + strconv.Itoa(paramIdx)
		args = append(args, statusFilter)
		paramIdx++
	}

	query += ` ORDER BY created_at DESC LIMIT $` + strconv.Itoa(paramIdx) + ` OFFSET $` + strconv.Itoa(paramIdx+1)
	args = append(args, limit, offset)

	rows, err := h.pool.Query(r.Context(), query, args...)
	if err != nil {
		slog.Warn("trace listesi alınamadı", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "trace listesi alınamadı"})
		return
	}
	defer rows.Close()

	type traceResult struct {
		TraceID         string  `json:"trace_id"`
		AgentName       string  `json:"agent_name"`
		WorkflowName    string  `json:"workflow_name"`
		Status          string  `json:"status"`
		TotalSteps      int     `json:"total_steps"`
		CompletedSteps  int     `json:"completed_steps"`
		TotalDurationMs int     `json:"total_duration_ms"`
		StartedAt       string  `json:"started_at"`
		CompletedAt     *string `json:"completed_at,omitempty"`
	}

	var traces []traceResult
	for rows.Next() {
		var t traceResult
		var startedAt time.Time
		var completedAt *time.Time
		if err := rows.Scan(&t.TraceID, &t.AgentName, &t.WorkflowName, &t.Status,
			&t.TotalSteps, &t.CompletedSteps, &t.TotalDurationMs, &startedAt, &completedAt); err != nil {
			slog.Warn("trace satırı okunamadı", "error", err)
			continue
		}
		t.StartedAt = startedAt.Format(time.RFC3339)
		if completedAt != nil {
			s := completedAt.Format(time.RFC3339)
			t.CompletedAt = &s
		}
		traces = append(traces, t)
	}

	if rows.Err() != nil {
		slog.Warn("agent traces rows iterasyon hatası", "error", rows.Err())
	}

	if traces == nil {
		traces = []traceResult{}
	}

	// Toplam sayıyı al
	var total int
	countQuery := `SELECT COUNT(*) FROM agent.traces WHERE tenant_id = $1`
	countArgs := []interface{}{tenantID}
	if statusFilter == "running" || statusFilter == "completed" || statusFilter == "failed" || statusFilter == "cancelled" { //nolint:misspell
		countQuery += ` AND status = $2`
		countArgs = append(countArgs, statusFilter)
	}
	_ = h.pool.QueryRow(r.Context(), countQuery, countArgs...).Scan(&total)

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"traces": traces,
		"total":  total,
		"limit":  limit,
		"offset": offset,
	})
}
