package public

import (
	"log/slog"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

// Handler serves public API endpoints under /public/v1
type Handler struct {
	pool *db.Pool
}

// NewHandler creates a new public API Handler.
func NewHandler(pool *db.Pool) *Handler {
	return &Handler{pool: pool}
}

// GetScore handles GET /public/v1/scores/{brandID}
// Returns the latest score for a brand (public, API key auth).
func (h *Handler) GetScore(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := chi.URLParam(r, "brandID")

	if brandID == "" {
		httputil.WriteError(w, http.StatusBadRequest, "brand_id gerekli")
		return
	}

	var brandName string
	var score float64
	var fidelityLabel string
	var freshnessAt time.Time

	err := h.pool.QueryRow(r.Context(), `
		SELECT b.name, COALESCE(s.value, 0), COALESCE(s.fidelity_label, 'yok'), s.freshness_at
		FROM config.brands b
		LEFT JOIN measure.scores s ON s.brand_id = b.id AND s.tenant_id = b.tenant_id
		WHERE b.id = $1 AND b.tenant_id = $2 AND b.is_active = true
		ORDER BY s.freshness_at DESC LIMIT 1
	`, brandID, tenantID).Scan(&brandName, &score, &fidelityLabel, &freshnessAt)
	if err != nil {
		slog.Debug("public: marka bulunamadı", "brand_id", brandID, "error", err)
		httputil.WriteError(w, http.StatusNotFound, "marka bulunamadı")
		return
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"brand_id":    brandID,
		"brand_name":  brandName,
		"score":       score,
		"fidelity":    fidelityLabel,
		"measured_at": freshnessAt.Format(time.RFC3339),
	})
}

// ListTrends handles GET /public/v1/trends?brand_id=xxx
// Returns score trends for a brand.
func (h *Handler) ListTrends(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")

	if brandID == "" {
		httputil.WriteError(w, http.StatusBadRequest, "brand_id parametresi gerekli")
		return
	}

	rows, err := h.pool.Query(r.Context(), `
		SELECT s.value, s.fidelity_label, s.freshness_at
		FROM measure.scores s
		WHERE s.brand_id = $1 AND s.tenant_id = $2
		ORDER BY s.freshness_at ASC
		LIMIT 50
	`, brandID, tenantID)
	if err != nil {
		slog.Error("public: trend sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"trends": []interface{}{}})
		return
	}
	defer rows.Close()

	type trendPoint struct {
		Value         float64 `json:"value"`
		FidelityLabel string  `json:"fidelity_label"`
		MeasuredAt    string  `json:"measured_at"`
	}

	trends := make([]trendPoint, 0)
	for rows.Next() {
		var t trendPoint
		var measuredAt time.Time
		if err := rows.Scan(&t.Value, &t.FidelityLabel, &measuredAt); err != nil {
			slog.Warn("public: trend satır okuma hatası", "error", err)
			continue
		}
		t.MeasuredAt = measuredAt.Format(time.RFC3339)
		trends = append(trends, t)
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"trends": trends})
}
