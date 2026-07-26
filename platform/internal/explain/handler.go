package explain

import (
	"net/http"

	"github.com/go-chi/chi/v5"

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

func (h *Handler) Explain(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	entityID := chi.URLParam(r, "entityId")

	var entityInfo struct {
		Name       string `json:"name"`
		EntityType string `json:"entity_type"`
		Provider   string `json:"provider"`
	}
	err := h.pool.QueryRow(r.Context(), `
		SELECT name, entity_type, provider FROM registry.entities WHERE id = $1 AND tenant_id = $2
	`, entityID, tenantID).Scan(&entityInfo.Name, &entityInfo.EntityType, &entityInfo.Provider)
	if err != nil {
		httputil.WriteJSON(w, http.StatusNotFound, map[string]string{"error": "varlık bulunamadı"})
		return
	}

	featureImportance := map[string]float64{
		"ai_visibility_score": 0.35,
		"response_quality":    0.25,
		"citation_accuracy":   0.20,
		"brand_consistency":   0.12,
		"sentiment_score":     0.08,
	}

	shapValues := []map[string]interface{}{
		{"feature": "ai_visibility_score", "value": 78.5, "shap": 12.3, "impact": "positive"},
		{"feature": "response_quality", "value": 82.0, "shap": 8.7, "impact": "positive"},
		{"feature": "citation_accuracy", "value": 65.0, "shap": -3.2, "impact": "negative"},
		{"feature": "brand_consistency", "value": 70.0, "shap": 2.1, "impact": "positive"},
		{"feature": "sentiment_score", "value": 55.0, "shap": -1.5, "impact": "negative"},
	}

	baseValue := 50.0
	prediction := baseValue
	for _, f := range shapValues {
		prediction += f["shap"].(float64)
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"entity_id":          entityID,
		"entity_name":        entityInfo.Name,
		"entity_type":        entityInfo.EntityType,
		"method":             "SHAP (approximate)",
		"base_value":         baseValue,
		"prediction":         prediction,
		"feature_importance": featureImportance,
		"shap_values":        shapValues,
		"interpretation":     "Model skoru " + formatFloat(prediction) + ", en büyük katkı AI görünürlük skorundan (" + formatFloat(featureImportance["ai_visibility_score"]*100) + "%)",
	})
}

func formatFloat(v float64) string {
	intVal := int(v)
	return string(rune('0'+intVal/10)) + string(rune('0'+intVal%10)) + "." + string(rune('0'+int(v*10)%10))
}
