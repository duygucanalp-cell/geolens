package bias

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"strconv"

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

func (h *Handler) Evaluate(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

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

	// Persist sonucu DB'ye yaz
	detailsJSON, _ := json.Marshal(results)
	recsJSON, _ := json.Marshal(results["recommendations"])

	fairnessScore, _ := results["fairness_score"].(float64)
	hasBias, _ := results["has_bias"].(bool)

	_, err := h.pool.Exec(r.Context(), `
		INSERT INTO bias.tests (id, tenant_id, model_id, metric_type, fairness_score, has_bias, max_gap, details, recommendations)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
	`, testID, tenantID, input.ModelID, input.MetricType, fairnessScore, hasBias, results["max_gap"], detailsJSON, recsJSON)
	if err != nil {
		slog.Warn("bias testi DB'ye yazılamadı", "test_id", testID, "error", err)
		// Non-fatal: sonucu yine de döndür
	}

	slog.Info("bias değerlendirmesi tamam", "test_id", testID, "metric", input.MetricType, "has_bias", hasBias, "fairness_score", fairnessScore)

	httputil.WriteJSON(w, http.StatusCreated, map[string]interface{}{
		"test_id":         testID,
		"model_id":        input.ModelID,
		"metric_type":     input.MetricType,
		"results":         results,
		"fairness_score":  fairnessScore,
		"recommendations": results["recommendations"],
	})
}

func (h *Handler) ListTests(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	modelID := r.URL.Query().Get("model_id")
	limit := r.URL.Query().Get("limit")
	if limit == "" {
		limit = "20"
	}

	limitInt, err := strconv.Atoi(limit)
	if err != nil || limitInt < 1 || limitInt > 100 {
		limitInt = 20
	}

	// LIMIT+1 pattern for has_more
	query := `SELECT id, model_id, metric_type, fairness_score, has_bias, max_gap, details, recommendations, created_at
		FROM bias.tests WHERE tenant_id = $1`
	args := []interface{}{tenantID}

	if modelID != "" {
		query += ` AND model_id = $2`
		args = append(args, modelID)
		query += ` ORDER BY created_at DESC LIMIT $3`
		args = append(args, limitInt+1)
	} else {
		query += ` ORDER BY created_at DESC LIMIT $2`
		args = append(args, limitInt+1)
	}

	rows, err := h.pool.Query(r.Context(), query, args...)
	if err != nil {
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "test geçmişi alınamadı"})
		return
	}
	defer rows.Close()

	type testResult struct {
		ID            string      `json:"id"`
		ModelID       string      `json:"model_id"`
		MetricType    string      `json:"metric_type"`
		FairnessScore float64     `json:"fairness_score"`
		HasBias       bool        `json:"has_bias"`
		MaxGap        float64     `json:"max_gap"`
		Details       interface{} `json:"details"`
		Recs          interface{} `json:"recommendations"`
		CreatedAt     string      `json:"created_at"`
	}

	var tests []testResult
	for rows.Next() {
		var t testResult
		var createdAt string
		if err := rows.Scan(&t.ID, &t.ModelID, &t.MetricType, &t.FairnessScore, &t.HasBias, &t.MaxGap, &t.Details, &t.Recs, &createdAt); err != nil {
			slog.Warn("bias test satırı okunamadı", "error", err)
			continue
		}
		t.CreatedAt = createdAt
		tests = append(tests, t)
	}

	hasMore := len(tests) > limitInt
	if hasMore {
		tests = tests[:limitInt]
	}

	if tests == nil {
		tests = []testResult{}
	}

	if rows.Err() != nil {
		slog.Warn("bias test rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"data":     tests,
		"has_more": hasMore,
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
			"max_gap":         0.0,
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
		"max_gap":         gap,
		"groups":          groups,
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
			"max_gap":         0.0,
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
		"max_gap":        gap,
		"tpr_groups":     tpr,
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
			"max_gap":         0.0,
			"recommendations": []string{"Korumalı ve korumasız grup oranları gerekli"},
		}
	}

	ratio := protected / nonProtected
	score := ratio
	if score > 1 {
		score = 1.0 / ratio
	}

	hasBias := ratio < 0.8 || ratio > 1.25
	recs := []string{}
	if hasBias {
		recs = append(recs, "Farklı etki oranı: "+formatPct(ratio)+" — 0.8-1.25 aralığı dışında (EEOC standardı)")
	}

	return map[string]interface{}{
		"fairness_score":   score,
		"has_bias":         hasBias,
		"max_gap":          ratio,
		"disparate_impact": ratio,
		"four_fifths_rule": ratio >= 0.8,
		"recommendations":  recs,
	}
}

func formatPct(v float64) string {
	return fmt.Sprintf("%.1f%%", v*100)
}
