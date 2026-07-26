package agent

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/geolens/platform/internal/id"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

type Handler struct {
	pool *db.Pool
}

func NewHandler(pool *db.Pool) *Handler {
	return &Handler{pool: pool}
}

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

	traceID := id.New()

	slog.Info("agent trace başlatıldı", "trace_id", traceID, "agent", input.AgentName, "tenant", tenantID)

	httputil.WriteJSON(w, http.StatusCreated, map[string]interface{}{
		"trace_id":      traceID,
		"agent_name":    input.AgentName,
		"workflow_name": input.WorkflowName,
		"status":        "running",
		"started_at":    time.Now().Format(time.RFC3339),
	})
}

func (h *Handler) GetTrace(w http.ResponseWriter, r *http.Request) {
	traceID := chi.URLParam(r, "traceId")

	steps := []map[string]interface{}{
		{
			"step_id":     "st-001",
			"step_name":   "Kullanıcı girdisi al",
			"agent":       "orchestrator",
			"input":       "Marka görünürlük analizi yap",
			"output":      "Analiz başlatıldı",
			"duration_ms": 150,
			"started_at":  time.Now().Add(-10 * time.Second).Format(time.RFC3339),
			"status":      "completed",
		},
		{
			"step_id":     "st-002",
			"step_name":   "AI engine sorgula",
			"agent":       "research-agent",
			"input":       "Acme Corp görünürlük sorgula",
			"output":      "3 engine'den yanıt alındı",
			"duration_ms": 3200,
			"started_at":  time.Now().Add(-8 * time.Second).Format(time.RFC3339),
			"status":      "completed",
		},
		{
			"step_id":     "st-003",
			"step_name":   "Skor hesapla",
			"agent":       "scoring-agent",
			"input":       "Engine yanıtlarını değerlendir",
			"output":      "Skor: 78.5",
			"duration_ms": 800,
			"started_at":  time.Now().Add(-4 * time.Second).Format(time.RFC3339),
			"status":      "completed",
		},
		{
			"step_id":     "st-004",
			"step_name":   "Rapor oluştur",
			"agent":       "report-agent",
			"input":       "Skor verisi ile PDF oluştur",
			"output":      "Rapor hazır",
			"duration_ms": 1500,
			"started_at":  time.Now().Add(-2 * time.Second).Format(time.RFC3339),
			"status":      "running",
		},
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"trace_id":          traceID,
		"steps":             steps,
		"total_duration_ms": 5650,
		"status":            "running",
		"agent_count":       4,
	})
}

func (h *Handler) ListTraces(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"tenant_id": tenantID,
		"traces":    []interface{}{},
		"total":     0,
	})
}
