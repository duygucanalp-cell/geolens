package benchmark

import (
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"time"

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

// RunBenchmark yeni bir model benchmark kaydı oluşturur.
func (h *Handler) RunBenchmark(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var input struct {
		ModelName      string  `json:"model_name"`
		EngineName     string  `json:"engine_name"`
		Category       string  `json:"category"`
		AccuracyScore  float64 `json:"accuracy_score"`
		LatencyMs      int     `json:"latency_ms"`
		CostPerRequest float64 `json:"cost_per_request"`
		TokensPerSec   float64 `json:"tokens_per_second"`
		ResponseQual   float64 `json:"response_quality"`
		CitationRate   float64 `json:"citation_rate"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}

	if input.ModelName == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "model_name gerekli"})
		return
	}
	if input.EngineName == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "engine_name gerekli"})
		return
	}
	if input.Category == "" {
		input.Category = "llm"
	}

	benchID := id.New()
	now := time.Now().UTC()

	details := map[string]interface{}{
		"accuracy_score":   input.AccuracyScore,
		"latency_ms":       input.LatencyMs,
		"cost_per_request": input.CostPerRequest,
		"tokens_per_sec":   input.TokensPerSec,
		"response_quality": input.ResponseQual,
		"citation_rate":    input.CitationRate,
		"tested_at":        now.Format(time.RFC3339),
	}
	detailsJSON, _ := json.Marshal(details)

	_, err := h.pool.Exec(r.Context(), `
		INSERT INTO benchmark.models (id, tenant_id, model_name, engine_name, category,
		                               accuracy_score, latency_ms, cost_per_request, tokens_per_second,
		                               response_quality, citation_rate, details, tested_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13)
	`, benchID, tenantID, input.ModelName, input.EngineName, input.Category,
		input.AccuracyScore, input.LatencyMs, input.CostPerRequest, input.TokensPerSec,
		input.ResponseQual, input.CitationRate, detailsJSON, now)
	if err != nil {
		slog.Warn("benchmark DB'ye yazılamadı", "bench_id", benchID, "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "benchmark kaydedilemedi"})
		return
	}

	slog.Info("model benchmark kaydedildi", "bench_id", benchID, "model", input.ModelName, "engine", input.EngineName)

	httputil.WriteJSON(w, http.StatusCreated, map[string]interface{}{
		"bench_id":          benchID,
		"model_name":        input.ModelName,
		"engine_name":       input.EngineName,
		"category":          input.Category,
		"accuracy_score":    input.AccuracyScore,
		"latency_ms":        input.LatencyMs,
		"cost_per_request":  input.CostPerRequest,
		"tokens_per_second": input.TokensPerSec,
		"response_quality":  input.ResponseQual,
		"citation_rate":     input.CitationRate,
		"tested_at":         now.Format(time.RFC3339),
	})
}

// ListBenchmarks tenant bazında benchmark geçmişini döndürür.
func (h *Handler) ListBenchmarks(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	limitStr := r.URL.Query().Get("limit")
	limit, err := strconv.Atoi(limitStr)
	if err != nil || limit < 1 || limit > 100 {
		limit = 20
	}

	offsetStr := r.URL.Query().Get("offset")
	offset, err := strconv.Atoi(offsetStr)
	if err != nil || offset < 0 {
		offset = 0
	}

	engineFilter := r.URL.Query().Get("engine")
	categoryFilter := r.URL.Query().Get("category")

	query := `SELECT id, model_name, engine_name, category, accuracy_score, latency_ms,
	                 cost_per_request, tokens_per_second, response_quality, citation_rate, tested_at
	          FROM benchmark.models WHERE tenant_id = $1`
	args := []interface{}{tenantID}
	paramIdx := 2

	if engineFilter != "" {
		query += ` AND engine_name = $` + strconv.Itoa(paramIdx)
		args = append(args, engineFilter)
		paramIdx++
	}
	if categoryFilter != "" {
		query += ` AND category = $` + strconv.Itoa(paramIdx)
		args = append(args, categoryFilter)
		paramIdx++
	}

	query += ` ORDER BY tested_at DESC LIMIT $` + strconv.Itoa(paramIdx) + ` OFFSET $` + strconv.Itoa(paramIdx+1)
	args = append(args, limit, offset)

	rows, err := h.pool.Query(r.Context(), query, args...)
	if err != nil {
		slog.Warn("benchmark listesi alınamadı", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "benchmark geçmişi alınamadı"})
		return
	}
	defer rows.Close()

	type benchResult struct {
		ID            string  `json:"id"`
		ModelName     string  `json:"model_name"`
		EngineName    string  `json:"engine_name"`
		Category      string  `json:"category"`
		AccuracyScore float64 `json:"accuracy_score"`
		LatencyMs     int     `json:"latency_ms"`
		CostPerReq    float64 `json:"cost_per_request"`
		TokensPerSec  float64 `json:"tokens_per_second"`
		ResponseQual  float64 `json:"response_quality"`
		CitationRate  float64 `json:"citation_rate"`
		TestedAt      string  `json:"tested_at"`
	}

	var benchmarks []benchResult
	for rows.Next() {
		var b benchResult
		var testedAt string
		if err := rows.Scan(&b.ID, &b.ModelName, &b.EngineName, &b.Category,
			&b.AccuracyScore, &b.LatencyMs, &b.CostPerReq, &b.TokensPerSec,
			&b.ResponseQual, &b.CitationRate, &testedAt); err != nil {
			slog.Warn("benchmark satırı okunamadı", "error", err)
			continue
		}
		b.TestedAt = testedAt
		benchmarks = append(benchmarks, b)
	}

	if rows.Err() != nil {
		slog.Warn("benchmark rows iterasyon hatası", "error", rows.Err())
	}

	if benchmarks == nil {
		benchmarks = []benchResult{}
	}

	httputil.WriteJSON(w, http.StatusOK, benchmarks)
}

// CompareModels iki veya daha fazla motoru karşılaştırır.
func (h *Handler) CompareModels(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	engineStr := r.URL.Query().Get("engines") // virgülle ayrılmış: "perplexity,chatgpt,gemini"
	if engineStr == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "engines parametresi gerekli (virgülle ayırın)"})
		return
	}

	// Engine listesini parse et (boşlukları temizle)
	parts := strings.Split(engineStr, ",")
	engines := make([]string, 0, len(parts))
	for _, p := range parts {
		trimmed := strings.TrimSpace(p)
		if trimmed != "" {
			engines = append(engines, trimmed)
		}
	}

	if len(engines) == 0 {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçerli engine adı gerekli"})
		return
	}

	query := `SELECT DISTINCT ON (engine_name) engine_name, model_name, accuracy_score, latency_ms,
	                 cost_per_request, tokens_per_second, response_quality, citation_rate, tested_at
	          FROM benchmark.models WHERE tenant_id = $1 AND engine_name = ANY($2)
	          ORDER BY engine_name, tested_at DESC`
	rows, err := h.pool.Query(r.Context(), query, tenantID, engines)
	if err != nil {
		slog.Warn("model karşılaştırma sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "karşılaştırma alınamadı"})
		return
	}
	defer rows.Close()

	type modelEntry struct {
		EngineName   string  `json:"engine_name"`
		ModelName    string  `json:"model_name"`
		Accuracy     float64 `json:"accuracy_score"`
		LatencyMs    int     `json:"latency_ms"`
		CostPerReq   float64 `json:"cost_per_request"`
		TokensPerSec float64 `json:"tokens_per_second"`
		ResponseQual float64 `json:"response_quality"`
		CitationRate float64 `json:"citation_rate"`
		TestedAt     string  `json:"tested_at"`
	}

	var models []modelEntry
	for rows.Next() {
		var m modelEntry
		var testedAt string
		if err := rows.Scan(&m.EngineName, &m.ModelName, &m.Accuracy, &m.LatencyMs,
			&m.CostPerReq, &m.TokensPerSec, &m.ResponseQual, &m.CitationRate, &testedAt); err != nil {
			slog.Warn("karşılaştırma satırı okunamadı", "error", err)
			continue
		}
		m.TestedAt = testedAt
		models = append(models, m)
	}

	if rows.Err() != nil {
		slog.Warn("benchmark model comparison rows iterasyon hatası", "error", rows.Err())
	}

	if models == nil {
		models = []modelEntry{}
	}

	// En iyi skorları float64 olarak takip et
	type bestEntry struct {
		set   bool
		value float64
	}
	var bestAccuracy, bestTokens, bestQuality, bestCitation bestEntry
	var bestLatency, bestCost bestEntry

	for _, m := range models {
		if !bestAccuracy.set || m.Accuracy > bestAccuracy.value {
			bestAccuracy.set = true
			bestAccuracy.value = m.Accuracy
		}
		if !bestLatency.set || float64(m.LatencyMs) < bestLatency.value {
			bestLatency.set = true
			bestLatency.value = float64(m.LatencyMs)
		}
		if !bestCost.set || m.CostPerReq < bestCost.value {
			bestCost.set = true
			bestCost.value = m.CostPerReq
		}
		if !bestTokens.set || m.TokensPerSec > bestTokens.value {
			bestTokens.set = true
			bestTokens.value = m.TokensPerSec
		}
		if !bestQuality.set || m.ResponseQual > bestQuality.value {
			bestQuality.set = true
			bestQuality.value = m.ResponseQual
		}
		if !bestCitation.set || m.CitationRate > bestCitation.value {
			bestCitation.set = true
			bestCitation.value = m.CitationRate
		}
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"models": models,
		"summary": map[string]interface{}{
			"best_accuracy":       fmt.Sprintf("%.2f", bestAccuracy.value),
			"best_latency_ms":     fmt.Sprintf("%.0f", bestLatency.value),
			"best_cost_per_req":   fmt.Sprintf("%.4f", bestCost.value),
			"best_tokens_per_sec": fmt.Sprintf("%.1f", bestTokens.value),
			"best_quality":        fmt.Sprintf("%.2f", bestQuality.value),
			"best_citation_rate":  fmt.Sprintf("%.2f", bestCitation.value),
		},
		"count": len(models),
	})
}
