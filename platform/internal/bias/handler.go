package bias

import (
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"

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

func (h *Handler) Evaluate(w http.ResponseWriter, r *http.Request) {
	_ = httpmw.GetTenantID(r.Context())

	var input struct {
		ModelID    string                 `json:"model_id"`
		MetricType string                 `json:"metric_type"`
		Data       map[string]interface{} `json:"data"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	testID := id.New()

	results := h.computeBias(input.MetricType, input.Data)

	slog.Info("bias değerlendirmesi tamam", "test_id", testID, "metric", input.MetricType)

	httputil.WriteJSON(w, http.StatusCreated, map[string]interface{}{
		"test_id":         testID,
		"model_id":        input.ModelID,
		"metric_type":     input.MetricType,
		"results":         results,
		"fairness_score":  results["fairness_score"],
		"recommendations": results["recommendations"],
	})
}

func (h *Handler) computeBias(metricType string, data map[string]interface{}) map[string]interface{} {
	results := map[string]interface{}{
		"metric_type":     metricType,
		"fairness_score":  0.0,
		"has_bias":        false,
		"recommendations": []string{},
	}

	switch metricType {
	case "demographic_parity":
		results = h.demographicParity(data)
	case "equal_opportunity":
		results = h.equalOpportunity(data)
	case "disparate_impact":
		results = h.disparateImpact(data)
	default:
		results["error"] = "bilinmeyen metrik: " + metricType
	}

	return results
}

func (h *Handler) demographicParity(data map[string]interface{}) map[string]interface{} {
	groups := make(map[string]float64)
	for k, v := range data {
		if val, ok := v.(float64); ok {
			groups[k] = val
		}
	}

	if len(groups) == 0 {
		return map[string]interface{}{
			"fairness_score":  1.0,
			"has_bias":        false,
			"recommendations": []string{"Değerlendirme için grup bazlı pozitif oran verisi gerekli"},
		}
	}

	var min, max float64
	first := true
	for _, v := range groups {
		if first {
			min, max = v, v
			first = false
			continue
		}
		if v < min {
			min = v
		}
		if v > max {
			max = v
		}
	}

	gap := max - min
	score := 1.0 - gap
	if score < 0 {
		score = 0
	}

	recs := []string{}
	if gap > 0.1 {
		recs = append(recs, "Gruplar arası pozitif oran farkı %"+formatPct(gap)+" — demografik parite ihlali")
	}
	if gap > 0.2 {
		recs = append(recs, "Kritik eşik aşıldı (%20), model yeniden eğitilmeli veya ağırlıklandırma yapılmalı")
	}

	return map[string]interface{}{
		"fairness_score":  score,
		"has_bias":        gap > 0.1,
		"groups":          groups,
		"max_gap":         gap,
		"recommendations": recs,
	}
}

func (h *Handler) equalOpportunity(data map[string]interface{}) map[string]interface{} {
	tpr := make(map[string]float64)
	for k, v := range data {
		if val, ok := v.(float64); ok {
			tpr[k] = val
		}
	}

	if len(tpr) == 0 {
		return map[string]interface{}{
			"fairness_score":  1.0,
			"has_bias":        false,
			"recommendations": []string{"True Positive Rate verisi gerekli"},
		}
	}

	var min, max float64
	first := true
	for _, v := range tpr {
		if first {
			min, max = v, v
			first = false
			continue
		}
		if v < min {
			min = v
		}
		if v > max {
			max = v
		}
	}

	gap := max - min
	score := 1.0 - gap
	if score < 0 {
		score = 0
	}

	return map[string]interface{}{
		"fairness_score": score,
		"has_bias":       gap > 0.1,
		"tpr_groups":     tpr,
		"max_gap":        gap,
	}
}

func (h *Handler) disparateImpact(data map[string]interface{}) map[string]interface{} {
	var protected, nonProtected float64
	var found bool

	if v, ok := data["protected_group_rate"]; ok {
		protected, found = v.(float64)
	}
	if v, ok := data["non_protected_group_rate"]; ok {
		nonProtected, _ = v.(float64)
	}

	if !found || nonProtected == 0 {
		return map[string]interface{}{
			"fairness_score":  1.0,
			"has_bias":        false,
			"recommendations": []string{"Korumalı ve korumasız grup oranları gerekli"},
		}
	}

	ratio := protected / nonProtected
	score := ratio
	if score > 1 {
		score = 1.0 / ratio
	}
	if score > 1 {
		score = 1
	}

	hasBias := ratio < 0.8 || ratio > 1.25
	recs := []string{}
	if hasBias {
		recs = append(recs, "Farklı etki oranı: "+formatPct(ratio)+" — 0.8-1.25 aralığı dışında (EEOC standardı)")
	}

	return map[string]interface{}{
		"fairness_score":   score,
		"has_bias":         hasBias,
		"disparate_impact": ratio,
		"four_fifths_rule": ratio >= 0.8,
		"recommendations":  recs,
	}
}

func formatPct(v float64) string {
	return fmt.Sprintf("%.1f%%", v*100)
}
