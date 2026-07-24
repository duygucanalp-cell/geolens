package measure

import (
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"time"

	"github.com/geolens/platform/engine"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

// Handler holds dependencies for measure HTTP handlers.
type Handler struct {
	pool    *db.Pool
	engines *engine.Registry
}

// NewHandler creates a new measure handler.
func NewHandler(pool *db.Pool, engines *engine.Registry) *Handler {
	return &Handler{pool: pool, engines: engines}
}

// TriggerMeasurement handles POST /v1/workspaces/{ws}/measurements
// Bir markanın ölçümünü tetikler. Outbox'a job yazar, senkron çağrı yapmaz.
func (h *Handler) TriggerMeasurement(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	var req struct {
		BrandID string `json:"brand_id"`
		PanelID string `json:"panel_id"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	if req.BrandID == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "brand_id zorunludur"})
		return
	}

	// Marka bilgisini al
	var brandName, websiteURL string
	err := h.pool.QueryRow(r.Context(), `
		SELECT name, website_url FROM config.brands
		WHERE id = $1 AND workspace_id = $2 AND tenant_id = $3 AND is_active = true
	`, req.BrandID, workspaceID, tenantID).Scan(&brandName, &websiteURL)
	if err != nil {
		slog.Error("marka sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "marka bulunamadı"})
		return
	}

	// Panel belirtilmişse panel'deki prompt set'ini kullan, yoksa varsayılan prompt
	promptText := brandName + " markası hakkında ne biliyorsun? Kaynak göstererek anlat."
	var panelID string
	if req.PanelID != "" {
		panelID = req.PanelID
		var psPrompt string
		err := h.pool.QueryRow(r.Context(), `
			SELECT COALESCE(ps.prompt_text, '') FROM config.panels p
			LEFT JOIN config.prompt_sets ps ON ps.id = p.prompt_set_id
			WHERE p.id = $1 AND p.workspace_id = $2 AND p.tenant_id = $3
		`, req.PanelID, workspaceID, tenantID).Scan(&psPrompt)
		if err == nil && psPrompt != "" {
			promptText = psPrompt
		}
	}

	// Tüm kayıtlı motorları topla
	engineNames := h.engines.List()
	if len(engineNames) == 0 {
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "kayıtlı motor bulunamadı"})
		return
	}

	// n=3 örneklemeli job'ları outbox'a yaz (asenkron)
	attemptID := fmt.Sprintf("%d", time.Now().UnixNano())
	for _, engineName := range engineNames {
		for i := 0; i < 3; i++ {
			idempotencyKey := fmt.Sprintf("measure:%s:%s:%d:%s", req.BrandID, engineName, i, attemptID)
			job := JobPayload{
				BrandID:     req.BrandID,
				BrandName:   brandName,
				WebsiteURL:  websiteURL,
				PanelID:     panelID,
				WorkspaceID: workspaceID,
				TenantID:    tenantID,
				EngineName:  engineName,
				PromptText:  promptText,
				SampleIndex: i,
			}
			if err := EnqueueMeasurement(r.Context(), h.pool, job, idempotencyKey); err != nil {
				slog.Error("outbox job ekleme hatası", "error", err, "engine", engineName, "sample", i)
			}
		}
	}

	slog.Info("ölçüm job'ları kuyruğa eklendi",
		"brand", brandName,
		"panel", panelID,
		"engines", engineNames,
		"samples", len(engineNames)*3,
	)

	httputil.WriteJSON(w, http.StatusAccepted, map[string]interface{}{
		"status":  "queued",
		"brand":   brandName,
		"engines": engineNames,
	})
}

// ListScores handles GET /v1/workspaces/{ws}/scores
// Skor listesini döndürür.
func (h *Handler) ListScores(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	rows, err := h.pool.Query(r.Context(), `
		SELECT s.id, b.name, s.value, s.fidelity_label, s.freshness_at
		FROM measure.scores s
		JOIN config.brands b ON b.id = s.brand_id
		WHERE s.workspace_id = $1 AND s.tenant_id = $2
		ORDER BY s.freshness_at DESC
		LIMIT 50
	`, workspaceID, tenantID)
	if err != nil {
		// Henüz measure schema'sı yoksa boş dizi dön
		slog.Debug("skor sorgu hatası (schema henüz oluşturulmamış olabilir)", "error", err)
		httputil.WriteJSON(w, http.StatusOK, []interface{}{})
		return
	}
	defer rows.Close()

	type scoreRow struct {
		ID            string  `json:"id"`
		BrandName     string  `json:"brand_name"`
		Value         float64 `json:"value"`
		FidelityLabel string  `json:"fidelity_label"`
		FreshnessAt   string  `json:"freshness_at"`
	}

	scores := make([]scoreRow, 0)
	for rows.Next() {
		var s scoreRow
		if err := rows.Scan(&s.ID, &s.BrandName, &s.Value, &s.FidelityLabel, &s.FreshnessAt); err != nil {
			slog.Error("skor satır okuma hatası", "error", err)
			continue
		}
		scores = append(scores, s)
	}

	httputil.WriteJSON(w, http.StatusOK, scores)
}
