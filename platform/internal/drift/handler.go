// Package drift provides handlers and logic for model/metric drift detection.
package drift

import (
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"log/slog"
	"math"
	"net/http"
	"strconv"

	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
	"github.com/geolens/platform/platform/queue"
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

type Observation struct {
	ID          string  `json:"id"`
	TenantID    string  `json:"tenant_id"`
	EntityID    string  `json:"entity_id"`
	EntityName  string  `json:"entity_name"`
	Metric      string  `json:"metric"`
	Value       float64 `json:"value"`
	WindowStart string  `json:"window_start"`
	CreatedAt   string  `json:"created_at"`
}

type Alert struct {
	ID            string  `json:"id"`
	TenantID      string  `json:"tenant_id"`
	EntityID      string  `json:"entity_id"`
	EntityName    string  `json:"entity_name"`
	Metric        string  `json:"metric"`
	DriftScore    float64 `json:"drift_score"`
	Severity      string  `json:"severity"`
	ReferenceMean float64 `json:"reference_mean"`
	CurrentMean   float64 `json:"current_mean"`
	Delta         float64 `json:"delta"`
	Detail        string  `json:"detail"`
	CreatedAt     string  `json:"created_at"`
}

// Record bir gözlem (observation) kaydeder.
func (h *Handler) Record(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	var input struct {
		EntityID    string  `json:"entity_id"`
		EntityName  string  `json:"entity_name"`
		Metric      string  `json:"metric"`
		Value       float64 `json:"value"`
		WindowStart string  `json:"window_start"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "geçersiz istek"})
		return
	}
	if input.EntityID == "" || input.Metric == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "entity_id ve metric zorunludur"})
		return
	}

	var obs Observation
	query := `
		INSERT INTO drift.observations (tenant_id, entity_id, entity_name, metric, value, window_start)
		VALUES ($1, $2, $3, $4, $5, COALESCE(NULLIF($6, '')::timestamptz, now()))
		RETURNING id, tenant_id, entity_id, entity_name, metric, value, window_start, created_at
	`
	err := h.pool.QueryRow(r.Context(), query,
		tenantID, input.EntityID, input.EntityName, input.Metric, input.Value, input.WindowStart,
	).Scan(&obs.ID, &obs.TenantID, &obs.EntityID, &obs.EntityName, &obs.Metric,
		&obs.Value, &obs.WindowStart, &obs.CreatedAt)
	if err != nil {
		slog.Error("drift gözlem kayıt hatası", "error", err)
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "gözlem kaydedilemedi"})
		return
	}

	httputil.WriteJSON(w, http.StatusCreated, obs)
}

// ListObservations zaman serisini döndürür. entity_id ve metric filtreleri zorunludur.
func (h *Handler) ListObservations(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	entityID := r.URL.Query().Get("entity_id")
	metric := r.URL.Query().Get("metric")
	limit := r.URL.Query().Get("limit")

	if entityID == "" || metric == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "entity_id ve metric parametreleri zorunludur"})
		return
	}

	query := `
		SELECT id, tenant_id, entity_id, entity_name, metric, value, window_start, created_at
		FROM drift.observations
		WHERE tenant_id = $1 AND entity_id = $2 AND metric = $3
		ORDER BY window_start DESC
	`
	args := []interface{}{tenantID, entityID, metric}
	if limit != "" {
		query += ` LIMIT $4`
		args = append(args, limit)
	}

	rows, err := h.pool.Query(r.Context(), query, args...)
	if err != nil {
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"observations": []interface{}{}})
		return
	}
	defer rows.Close()

	var observations []Observation
	for rows.Next() {
		var o Observation
		if err := rows.Scan(&o.ID, &o.TenantID, &o.EntityID, &o.EntityName, &o.Metric,
			&o.Value, &o.WindowStart, &o.CreatedAt); err != nil {
			slog.Warn("drift gözlem satır okuma hatası", "error", err)
			continue
		}
		observations = append(observations, o)
	}
	if rows.Err() != nil {
		slog.Warn("drift gözlem rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"observations": observations, "total": len(observations)})
}

// ListEntities entity + metrik bazlı özet listeler (son gözlem sayısı, ortalama, drift durumu).
func (h *Handler) ListEntities(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	rows, err := h.pool.Query(r.Context(), `
		SELECT entity_id, entity_name, metric,
		       COUNT(*)::int AS observation_count,
		       AVG(value)::float8 AS mean_value,
		       MAX(window_start) AS last_observed
		FROM drift.observations
		WHERE tenant_id = $1
		GROUP BY entity_id, entity_name, metric
		ORDER BY last_observed DESC
	`, tenantID)
	if err != nil {
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"entities": []interface{}{}})
		return
	}
	defer rows.Close()

	var entities []map[string]interface{}
	for rows.Next() {
		var entityID, entityName, metric string
		var count int
		var mean float64
		var lastObserved string
		if err := rows.Scan(&entityID, &entityName, &metric, &count, &mean, &lastObserved); err != nil {
			slog.Warn("drift entity satır okuma hatası", "error", err)
			continue
		}
		entities = append(entities, map[string]interface{}{
			"entity_id":         entityID,
			"entity_name":       entityName,
			"metric":            metric,
			"observation_count": count,
			"mean_value":        mean,
			"last_observed":     lastObserved,
		})
	}
	if rows.Err() != nil {
		slog.Warn("drift entity rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"entities": entities})
}

// Analyze referans pencere (ilk gözlemler) ile güncel pencere (son gözlemler)
// arasındaki istatistiksel sapmayı hesaplar. Sapma eşiği aşılırsa otomatik uyarı kaydeder.
func (h *Handler) Analyze(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())
	entityID := r.URL.Query().Get("entity_id")
	metric := r.URL.Query().Get("metric")
	threshold := r.URL.Query().Get("threshold")

	if entityID == "" || metric == "" {
		httputil.WriteJSON(w, http.StatusBadRequest, map[string]string{"error": "entity_id ve metric parametreleri zorunludur"})
		return
	}

	// Tüm gözlemleri zaman sırasıyla getir (ASC — referans en eski, güncel en yeni)
	rows, err := h.pool.Query(r.Context(), `
		SELECT value FROM drift.observations
		WHERE tenant_id = $1 AND entity_id = $2 AND metric = $3
		ORDER BY window_start ASC
	`, tenantID, entityID, metric)
	if err != nil {
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "gözlem sorgu hatası"})
		return
	}
	defer rows.Close()

	var values []float64
	for rows.Next() {
		var v float64
		if err := rows.Scan(&v); err != nil {
			slog.Warn("drift değer satır okuma hatası", "error", err)
			continue
		}
		values = append(values, v)
	}
	if rows.Err() != nil {
		slog.Warn("drift değer rows iterasyon hatası", "error", rows.Err())
	}

	if len(values) < 4 {
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
			"entity_id":      entityID,
			"metric":         metric,
			"drift_score":    0,
			"severity":       "insufficient_data",
			"reference_mean": 0,
			"current_mean":   0,
			"delta":          0,
			"detail":         "drift analizi için en az 4 gözlem gerekir",
		})
		return
	}

	// Referans: ilk yarı; Güncel: son 10 gözlem (yeterli yoksa son çeyrek)
	refValues := values[:len(values)/2]
	curValues := values[len(values)/2:]
	if len(values) > 10 {
		curValues = values[len(values)-10:]
	}

	score, delta, refMean, curMean := computeDriftScore(refValues, curValues)

	severity := severityFor(score)

	// Varsayılan eşik 40; aşarsa uyarı kaydet
	thresh := 40.0
	if threshold != "" {
		if t, err := strconv.ParseFloat(threshold, 64); err == nil {
			thresh = t
		}
	}
	if score >= thresh && severity != "insufficient_data" {
		_, err := h.pool.Exec(r.Context(), `
			INSERT INTO drift.alerts (tenant_id, entity_id, entity_name, metric, drift_score, severity, reference_mean, current_mean, delta, detail)
			VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
		`, tenantID, entityID, "", metric, score, severity, refMean, curMean, delta, "güncel pencere referans ortalamasından sapıyor")
		if err != nil {
			slog.Debug("drift uyarı kayıt hatası", "error", err)
		} else {
			// O-6: DriftAlertTriggered olayını outbox üzerinden taşı (doğrudan DB yazımı yerine)
			// İçerik türevli deterministik anahtar: aynı pencere/skor için tekrarlanan analyze
			// çağrıları yinelenen olay üretmez (unique index).
			idemKey := driftIdempotencyKey(entityID, metric, score, delta)
			if err := queue.EnqueueEvent(r.Context(), h.pool, "drift.alert.triggered", queue.StreamGovernance, map[string]interface{}{
				"entity_id":   entityID,
				"metric":      metric,
				"drift_score": score,
				"severity":    severity,
				"delta":       delta,
			}, tenantID, idemKey); err != nil {
				slog.Warn("drift uyarı olayı outbox'a yazılamadı", "entity_id", entityID, "error", err)
			}
		}
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"entity_id":      entityID,
		"metric":         metric,
		"drift_score":    score,
		"severity":       severity,
		"reference_mean": refMean,
		"current_mean":   curMean,
		"delta":          delta,
		"detail":         "referans ve güncel pencereler arasındaki normalleştirilmiş ortalama sapması",
	})
}

func (h *Handler) ListAlerts(w http.ResponseWriter, r *http.Request) {
	tenantID := httpmw.GetTenantID(r.Context())

	rows, err := h.pool.Query(r.Context(), `
		SELECT id, tenant_id, entity_id, entity_name, metric, drift_score, severity,
		       reference_mean, current_mean, delta, detail, created_at
		FROM drift.alerts WHERE tenant_id = $1 ORDER BY created_at DESC
	`, tenantID)
	if err != nil {
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"alerts": []interface{}{}})
		return
	}
	defer rows.Close()

	var alerts []Alert
	for rows.Next() {
		var a Alert
		if err := rows.Scan(&a.ID, &a.TenantID, &a.EntityID, &a.EntityName, &a.Metric,
			&a.DriftScore, &a.Severity, &a.ReferenceMean, &a.CurrentMean, &a.Delta,
			&a.Detail, &a.CreatedAt); err != nil {
			slog.Warn("drift uyarı satır okuma hatası", "error", err)
			continue
		}
		alerts = append(alerts, a)
	}
	if rows.Err() != nil {
		slog.Warn("drift uyarı rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"alerts": alerts, "total": len(alerts)})
}

// driftIdempotencyKey deterministik bir outbox idempotency anahtarı üretir.
// Aynı (entity, metric, skor, delta) kombinasyonu her zaman aynı anahtarı verir —
// drift_score 2 ondalığa yuvarlandığı için aynı pencere tekrar analiz edilse bile sabittir.
func driftIdempotencyKey(entityID, metric string, score, delta float64) string {
	h := sha256.Sum256([]byte(fmt.Sprintf("%s|%s|%.2f|%.2f", entityID, metric, score, delta)))
	return fmt.Sprintf("drift:%s:%x", entityID, h[:12])
}

// computeDriftScore referans ve güncel değerler arasındaki sapmayı 0-100 aralığına ölçekler.
// Z-skoru yaklaşımı: sapma referans standart sapmasıyla normalleştirilir.
func computeDriftScore(refVals, curVals []float64) (score, delta, refMean, curMean float64) {
	if len(refVals) == 0 || len(curVals) == 0 {
		return 0, 0, 0, 0
	}

	refMean = mean(refVals)
	curMean = mean(curVals)
	delta = curMean - refMean

	std := stddev(refVals, refMean)
	if std < 1e-6 {
		// Sabit referans: göreli sapma baz alınır
		base := math.Abs(refMean)
		if base < 1 {
			base = 1
		}
		std = base * 0.1
	}

	z := math.Abs(delta) / std
	score = math.Min(100, z*25)
	score = float64(int(score*100+0.5)) / 100
	return score, delta, refMean, curMean
}

func severityFor(score float64) string {
	switch {
	case score >= 50:
		return "critical"
	case score >= 20:
		return "warning"
	default:
		return "info"
	}
}

func mean(vals []float64) float64 {
	if len(vals) == 0 {
		return 0
	}
	var sum float64
	for _, v := range vals {
		sum += v
	}
	return sum / float64(len(vals))
}

func stddev(vals []float64, m float64) float64 {
	if len(vals) < 2 {
		return 0
	}
	var sq float64
	for _, v := range vals {
		d := v - m
		sq += d * d
	}
	return math.Sqrt(sq / float64(len(vals)-1))
}
