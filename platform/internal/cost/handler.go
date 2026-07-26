// Package cost provides handlers and logic for cost functionality.
package cost

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"strconv"
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

func (h *Handler) RecordCost(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var input struct {
		EngineName   string  `json:"engine_name"`
		ModelName    string  `json:"model_name"`
		Operation    string  `json:"operation"`
		TokenCount   int     `json:"token_count"`
		InputTokens  int     `json:"input_tokens"`
		OutputTokens int     `json:"output_tokens"`
		CostUSD      float64 `json:"cost_usd"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}
	if input.EngineName == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "engine_name gerekli"})
		return
	}

	entryID := id.New()
	now := time.Now().UTC()

	if input.Operation == "" {
		input.Operation = "other"
	}

	_, err := h.pool.Exec(r.Context(), `
		INSERT INTO cost.entries (id, tenant_id, engine_name, model_name, operation, token_count, input_tokens, output_tokens, cost_usd, recorded_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
	`, entryID, tenantID, input.EngineName, input.ModelName, input.Operation,
		input.TokenCount, input.InputTokens, input.OutputTokens, input.CostUSD, now)
	if err != nil {
		slog.Warn("cost entry DB'ye yazılamadı", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "maliyet kaydedilemedi"})
		return
	}

	slog.Info("cost entry kaydedildi", "entry_id", entryID, "engine", input.EngineName, "cost", input.CostUSD)

	httputil.WriteJSON(w, http.StatusCreated, map[string]interface{}{
		"entry_id":    entryID,
		"engine_name": input.EngineName,
		"model_name":  input.ModelName,
		"cost_usd":    input.CostUSD,
		"token_count": input.TokenCount,
		"recorded_at": now.Format(time.RFC3339),
	})
}

func (h *Handler) ListCosts(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	if limit < 1 || limit > 100 {
		limit = 20
	}
	engineFilter := r.URL.Query().Get("engine")

	// LIMIT+1 pattern for has_more
	query := `SELECT id, engine_name, model_name, operation, token_count, cost_usd, recorded_at
	          FROM cost.entries WHERE tenant_id = $1`
	args := []interface{}{tenantID}
	paramIdx := 2

	if engineFilter != "" {
		query += ` AND engine_name = $` + strconv.Itoa(paramIdx)
		args = append(args, engineFilter)
		paramIdx++
	}
	query += ` ORDER BY recorded_at DESC LIMIT $` + strconv.Itoa(paramIdx)
	args = append(args, limit+1)

	rows, err := h.pool.Query(r.Context(), query, args...)
	if err != nil {
		slog.Warn("cost listesi alınamadı", "error", err)
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"data": []interface{}{}, "has_more": false})
		return
	}
	defer rows.Close()

	type entry struct {
		ID         string  `json:"id"`
		EngineName string  `json:"engine_name"`
		ModelName  string  `json:"model_name"`
		Operation  string  `json:"operation"`
		TokenCount int     `json:"token_count"`
		CostUSD    float64 `json:"cost_usd"`
		RecordedAt string  `json:"recorded_at"`
	}
	var entries []entry
	for rows.Next() {
		var e entry
		var ts string
		if err := rows.Scan(&e.ID, &e.EngineName, &e.ModelName, &e.Operation, &e.TokenCount, &e.CostUSD, &ts); err != nil {
			slog.Warn("cost satır okuma hatası", "error", err)
			continue
		}
		e.RecordedAt = ts
		entries = append(entries, e)
	}

	hasMore := len(entries) > limit
	if hasMore {
		entries = entries[:limit]
	}

	if entries == nil {
		entries = []entry{}
	}

	if rows.Err() != nil {
		slog.Warn("cost listesi rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"data":     entries,
		"has_more": hasMore,
	})
}

func (h *Handler) GetCostSummary(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	period := r.URL.Query().Get("period")
	if period == "" {
		period = "7d"
	}

	var interval string
	switch period {
	case "30d":
		interval = "30 days"
	case "90d":
		interval = "90 days"
	case "1d":
		interval = "1 day"
	default:
		interval = "7 days"
	}

	var totalCost float64
	var totalTokens int

	err := h.pool.QueryRow(r.Context(), `
		SELECT COALESCE(SUM(cost_usd), 0), COALESCE(SUM(token_count), 0)
		FROM cost.entries WHERE tenant_id = $1 AND recorded_at > NOW() - $2::INTERVAL
	`, tenantID, interval).Scan(&totalCost, &totalTokens)
	if err != nil {
		slog.Warn("cost summary sorgu hatası", "error", err)
	}

	type engineCost struct {
		Engine string  `json:"engine"`
		Cost   float64 `json:"cost"`
		Tokens int     `json:"tokens"`
	}

	var breakdown []engineCost
	rows, err := h.pool.Query(r.Context(), `
		SELECT engine_name, COALESCE(SUM(cost_usd), 0) AS total, COALESCE(SUM(token_count), 0) AS tokens
		FROM cost.entries WHERE tenant_id = $1 AND recorded_at > NOW() - $2::INTERVAL
		GROUP BY engine_name ORDER BY total DESC
	`, tenantID, interval)
	if err != nil {
		slog.Warn("cost breakdown sorgu hatası", "error", err)
	} else {
		defer rows.Close()
		for rows.Next() {
			var ec engineCost
			if err := rows.Scan(&ec.Engine, &ec.Cost, &ec.Tokens); err != nil {
				slog.Warn("cost breakdown satır okuma hatası", "error", err)
				continue
			}
			breakdown = append(breakdown, ec)
		}
		if rows.Err() != nil {
			slog.Warn("cost breakdown rows iterasyon hatası", "error", rows.Err())
		}
	}
	if breakdown == nil {
		breakdown = []engineCost{}
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"period":           period,
		"total_cost_usd":   totalCost,
		"total_tokens":     totalTokens,
		"engine_breakdown": breakdown,
	})
}
