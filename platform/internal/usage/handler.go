// Package usage provides handlers and logic for usage functionality.
package usage

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

func (h *Handler) RecordUsage(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var input struct {
		Endpoint     string `json:"endpoint"`
		Method       string `json:"method"`
		StatusCode   int    `json:"status_code"`
		LatencyMs    int    `json:"latency_ms"`
		UserID       string `json:"user_id"`
		RequestSize  int    `json:"request_size"`
		ResponseSize int    `json:"response_size"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}
	if input.Endpoint == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "endpoint gerekli"})
		return
	}
	if input.Method == "" {
		input.Method = "GET"
	}
	if input.StatusCode == 0 {
		input.StatusCode = 200
	}

	entryID := id.New()
	now := time.Now().UTC()

	_, err := h.pool.Exec(r.Context(), `
		INSERT INTO usage.metrics (id, tenant_id, endpoint, method, status_code, latency_ms, user_id, request_size, response_size, recorded_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
	`, entryID, tenantID, input.Endpoint, input.Method, input.StatusCode,
		input.LatencyMs, input.UserID, input.RequestSize, input.ResponseSize, now)
	if err != nil {
		slog.Warn("usage metric DB'ye yazılamadı", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "kullanım kaydedilemedi"})
		return
	}

	slog.Info("usage metric kaydedildi", "entry_id", entryID, "endpoint", input.Endpoint)

	httputil.WriteJSON(w, http.StatusCreated, map[string]interface{}{
		"entry_id":    entryID,
		"endpoint":    input.Endpoint,
		"method":      input.Method,
		"latency_ms":  input.LatencyMs,
		"recorded_at": now.Format(time.RFC3339),
	})
}

func (h *Handler) ListUsage(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	if limit < 1 || limit > 100 {
		limit = 20
	}

	// LIMIT+1 pattern for has_more
	query := `SELECT id, endpoint, method, status_code, latency_ms, recorded_at
	          FROM usage.metrics WHERE tenant_id = $1 ORDER BY recorded_at DESC LIMIT $2`
	rows, err := h.pool.Query(r.Context(), query, tenantID, limit+1)
	if err != nil {
		slog.Warn("usage listesi alınamadı", "error", err)
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"data": []interface{}{}, "has_more": false})
		return
	}
	defer rows.Close()

	type entry struct {
		ID         string `json:"id"`
		Endpoint   string `json:"endpoint"`
		Method     string `json:"method"`
		StatusCode int    `json:"status_code"`
		LatencyMs  int    `json:"latency_ms"`
		RecordedAt string `json:"recorded_at"`
	}
	var entries []entry
	for rows.Next() {
		var e entry
		var ts string
		if err := rows.Scan(&e.ID, &e.Endpoint, &e.Method, &e.StatusCode, &e.LatencyMs, &ts); err != nil {
			slog.Warn("usage satır okuma hatası", "error", err)
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
		slog.Warn("usage listesi rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"data":     entries,
		"has_more": hasMore,
	})
}

func (h *Handler) GetUsageSummary(w http.ResponseWriter, r *http.Request) {
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

	var totalRequests, totalErrors, avgLatency float64
	h.pool.QueryRow(r.Context(), `
		SELECT COUNT(*), COALESCE(AVG(CASE WHEN status_code >= 400 THEN 1.0 ELSE 0 END), 0) * 100,
		       COALESCE(AVG(latency_ms), 0)
		FROM usage.metrics WHERE tenant_id = $1 AND recorded_at > NOW() - $2::INTERVAL
	`, tenantID, interval).Scan(&totalRequests, &totalErrors, &avgLatency)

	rows, err := h.pool.Query(r.Context(), `
		SELECT endpoint, COUNT(*) AS hits, COALESCE(AVG(latency_ms), 0) AS avg_latency
		FROM usage.metrics WHERE tenant_id = $1 AND recorded_at > NOW() - $2::INTERVAL
		GROUP BY endpoint ORDER BY hits DESC LIMIT 10
	`, tenantID, interval)
	var topEndpoints []map[string]interface{}
	if err == nil {
		defer rows.Close()
		for rows.Next() {
			var ep string
			var hits int
			var avgLat float64
			if err := rows.Scan(&ep, &hits, &avgLat); err != nil {
				slog.Warn("usage top endpoints satır okuma hatası", "error", err)
				continue
			}
			topEndpoints = append(topEndpoints, map[string]interface{}{
				"endpoint": ep, "hits": hits, "avg_latency_ms": avgLat,
			})
		}
	}
	if topEndpoints == nil {
		topEndpoints = []map[string]interface{}{}
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"period":         period,
		"total_requests": totalRequests,
		"error_rate_pct": totalErrors,
		"avg_latency_ms": avgLatency,
		"top_endpoints":  topEndpoints,
	})
}
