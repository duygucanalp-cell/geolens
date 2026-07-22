package measure

import (
	"encoding/json"
	"log/slog"
	"net/http"

	"github.com/geolens/platform/engine"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httputil"
	"github.com/geolens/platform/platform/httpmw"
)

// Handler holds dependencies for measure HTTP handlers.
type Handler struct {
	pool      *db.Pool
	engines   *engine.Registry
}

// NewHandler creates a new measure handler.
func NewHandler(pool *db.Pool, engines *engine.Registry) *Handler {
	return &Handler{pool: pool, engines: engines}
}

// TriggerMeasurement handles POST /v1/workspaces/{ws}/measurements
// Bir markanın mevcut motorlarla ölçümünü tetikler.
func (h *Handler) TriggerMeasurement(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	var req struct {
		BrandID string `json:"brand_id"`
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

	// TODO(H2): Prompt seti + örneklemeli (n=3) motor çağrısı
	// Şimdilik sadece Perplexity ile tek çağrı
	perplexityAdapter := h.engines.Get("perplexity")
	if perplexityAdapter == nil {
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "motor bulunamadı"})
		return
	}

	prompt := brandName + " markası hakkında ne biliyorsun? Kaynak göstererek anlat."
	result, err := perplexityAdapter.Execute(prompt)
	if err != nil {
		slog.Error("ölçüm hatası", "error", err, "brand", brandName)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "ölçüm başarısız"})
		return
	}

	// Ham yanıdı kaydet
	slog.Info("ölçüm tamamlandı", "brand", brandName, "engine", result.EngineName)

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"status":       "completed",
		"engine":       result.EngineName,
		"response_ref": result.S3Ref,
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


