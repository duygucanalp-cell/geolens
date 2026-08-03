// Package measure provides handlers and logic for measure functionality.
package measure

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/geolens/platform/engine"
	"github.com/geolens/platform/internal/benchmark"
	"github.com/geolens/platform/internal/dbiface"
	"github.com/geolens/platform/internal/id"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/httpmw"
	"github.com/geolens/platform/platform/httputil"
)

// Handler holds dependencies for measure HTTP handlers.
type Handler struct {
	pool    dbiface.DB
	rawPool *db.Pool
	engines *engine.Registry
}

// NewHandler creates a new measure handler with the given DB interface.
func NewHandler(pool dbiface.DB, engines *engine.Registry) *Handler {
	return &Handler{pool: pool, engines: engines}
}

// NewProductionHandler creates a new measure handler with a *db.Pool for production use.
func NewProductionHandler(pool *db.Pool, engines *engine.Registry) *Handler {
	return &Handler{
		pool:    dbiface.NewAdapter(pool),
		rawPool: pool,
		engines: engines,
	}
}

// immediateMeasureAndScore performs a synchronous measurement + scoring for the given brand.
// This is a demo convenience: the async worker pipeline handles the full flow,
// but immediate scoring lets the UI show results right away.
func (h *Handler) immediateMeasureAndScore(ctx context.Context, brandName, brandID, websiteURL, panelID, workspaceID, tenantID, promptText string) {
	svc := NewService(h.rawPool, h.engines, nil)

	// n=3 Measurement (tek engine — mock engine hızlı yanıt verir)
	result, err := svc.Measure(ctx, MeasurementRequest{
		BrandID:     brandID,
		BrandName:   brandName,
		WebsiteURL:  websiteURL,
		PromptText:  promptText,
		TenantID:    tenantID,
		WorkspaceID: workspaceID,
		PanelID:     panelID,
	})
	if err != nil {
		slog.Warn("anlık ölçüm başarısız (asenkron pipeline işlemeye devam eder)", "error", err)
		return
	}

	// CalculateScore
	score, err := svc.CalculateScore(ctx, panelID, []MeasurementResult{*result}, ComponentWeights{})
	if err != nil {
		slog.Warn("anlık skor hesaplama başarısız (asenkron pipeline işlemeye devam eder)", "error", err)
		return
	}

	slog.Info("anlık skor hesaplandı", "brand", brandName, "score", score.Value)
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
	runID := id.New()
	for _, engineName := range engineNames {
		for i := 0; i < 3; i++ {
			idempotencyKey := fmt.Sprintf("measure:%s:%s:%d:%s", req.BrandID, engineName, i, runID)
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
			if err := EnqueueMeasurement(r.Context(), h.rawPool, job, idempotencyKey); err != nil {
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

	// Demo: asenkron job'ların yanında senkron ölçüm + skor da hesapla
	// (mock engine ile anlık sonuç alınır, worker pipeline'ı da paralel işler) //nolint:misspell
	// context.Background() kullanılır çünkü HTTP request context'i goroutine çalışana kadar iptal olabilir
	go h.immediateMeasureAndScore(context.Background(), brandName, req.BrandID, websiteURL, panelID, workspaceID, tenantID, promptText)

	location := fmt.Sprintf("/v1/workspaces/%s/measurements/%s/status", workspaceID, runID)
	w.Header().Set("Location", location)
	httputil.WriteJSON(w, http.StatusAccepted, map[string]interface{}{
		"status":  "queued",
		"run_id":  runID,
		"brand":   brandName,
		"engines": engineNames,
	})
}

// GetMeasurementStatus handles GET /v1/workspaces/{ws}/measurements/{runId}/status
// Bir ölçüm run'ının durumunu döndürür.
func (h *Handler) GetMeasurementStatus(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	runID := chi.URLParam(r, "runId")

	var totalJobs, completedJobs int
	err := h.pool.QueryRow(r.Context(), `
		SELECT COUNT(*) FILTER (WHERE status = 'completed') AS completed,
		       COUNT(*) AS total
		FROM measure.measurement_jobs
		WHERE workspace_id = $1 AND tenant_id = $2 AND created_at > now() - interval '1 hour'
	`, workspaceID, tenantID).Scan(&completedJobs, &totalJobs)
	if err != nil {
		httputil.WriteJSON(w, http.StatusInternalServerError, map[string]string{"error": "durum sorgulanamadı"})
		return
	}

	status := "running"
	if totalJobs > 0 && completedJobs == totalJobs {
		status = "completed"
	} else if totalJobs == 0 {
		status = "pending"
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"run_id":         runID,
		"status":         status,
		"total_jobs":     totalJobs,
		"completed_jobs": completedJobs,
	})
}

// ListScores handles GET /v1/workspaces/{ws}/scores
// Skor listesini döndürür.
func (h *Handler) ListScores(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	rows, err := h.pool.Query(r.Context(), `
		SELECT s.id, b.name, s.value, s.ci_low, s.ci_high, s.fidelity_label, COALESCE(s.engine_breakdown::text, '{}'), s.freshness_at, b.id
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
		ID              string             `json:"id"`
		BrandName       string             `json:"brand_name"`
		Value           float64            `json:"value"`
		CILow           float64            `json:"ci_low"`
		CIHigh          float64            `json:"ci_high"`
		FidelityLabel   string             `json:"fidelity_label"`
		EngineBreakdown map[string]float64 `json:"engine_breakdown,omitempty"`
		FreshnessAt     time.Time          `json:"freshness_at"`
		BrandID         string             `json:"brand_id"`
	}

	var parseErrors int
	scores := make([]scoreRow, 0)
	for rows.Next() {
		var s scoreRow
		var engBreakdown string
		if err := rows.Scan(&s.ID, &s.BrandName, &s.Value, &s.CILow, &s.CIHigh, &s.FidelityLabel, &engBreakdown, &s.FreshnessAt, &s.BrandID); err != nil {
			slog.Error("skor satır okuma hatası", "error", err)
			parseErrors++
			continue
		}
		if engBreakdown != "" && engBreakdown != "{}" {
			if err := json.Unmarshal([]byte(engBreakdown), &s.EngineBreakdown); err != nil {
				slog.Warn("engine breakdown çözümleme hatası", "score_id", s.ID, "error", err)
			}
		}
		scores = append(scores, s)
	}

	if rows.Err() != nil {
		slog.Error("skor listesi rows iterasyon hatası", "error", rows.Err())
	}

	// K5: Kısmi sonuç uyarısı (parseErrors > 0 ise response header'a eklenir)
	if parseErrors > 0 {
		w.Header().Set("X-Has-More", "true")
	}

	httputil.WriteJSON(w, http.StatusOK, scores)
}

// ListTrends handles GET /v1/workspaces/{ws}/trends
// Trend verisini döndürür (isteğe bağlı brand_id filtresi ile).
func (h *Handler) ListTrends(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")

	rows, err := h.pool.Query(r.Context(), `
		SELECT s.id, b.name, s.value, s.ci_low, s.ci_high, s.fidelity_label, COALESCE(s.engine_breakdown::text, '{}'), s.freshness_at, b.id
		FROM measure.scores s
		JOIN config.brands b ON b.id = s.brand_id
		WHERE s.workspace_id = $1 AND s.tenant_id = $2 AND ($3 = '' OR s.brand_id = $3)
		ORDER BY s.freshness_at ASC
	`, workspaceID, tenantID, brandID)
	if err != nil {
		slog.Debug("trend sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, []interface{}{})
		return
	}
	defer rows.Close()

	type trendRow struct {
		ID              string             `json:"id"`
		BrandName       string             `json:"brand_name"`
		Value           float64            `json:"value"`
		CILow           float64            `json:"ci_low"`
		CIHigh          float64            `json:"ci_high"`
		FidelityLabel   string             `json:"fidelity_label"`
		EngineBreakdown map[string]float64 `json:"engine_breakdown,omitempty"`
		FreshnessAt     time.Time          `json:"freshness_at"`
		BrandID         string             `json:"brand_id"`
	}

	trends := make([]trendRow, 0)
	for rows.Next() {
		var t trendRow
		var engBreakdown string
		if err := rows.Scan(&t.ID, &t.BrandName, &t.Value, &t.CILow, &t.CIHigh, &t.FidelityLabel, &engBreakdown, &t.FreshnessAt, &t.BrandID); err != nil {
			slog.Error("trend satır okuma hatası", "error", err)
			continue
		}
		if engBreakdown != "" && engBreakdown != "{}" {
			if err := json.Unmarshal([]byte(engBreakdown), &t.EngineBreakdown); err != nil {
				slog.Warn("engine breakdown çözümleme hatası", "score_id", t.ID, "error", err)
			}
		}
		trends = append(trends, t)
	}

	if rows.Err() != nil {
		slog.Error("trend listesi rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, trends)
}

// ListBrandScores handles GET /v1/workspaces/{ws}/brands/{brandID}/scores
// Bir markanın skor geçmişini döndürür.
func (h *Handler) ListBrandScores(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := chi.URLParam(r, "brandID")

	if brandID == "" {
		httputil.WriteError(w, http.StatusBadRequest, "marka ID gerekli")
		return
	}

	var brandName string
	err := h.pool.QueryRow(r.Context(), `
		SELECT name FROM config.brands WHERE id = $1 AND workspace_id = $2 AND tenant_id = $3
	`, brandID, workspaceID, tenantID).Scan(&brandName)
	if err != nil {
		httputil.WriteError(w, http.StatusNotFound, "marka bulunamadı")
		return
	}

	rows, err := h.pool.Query(r.Context(), `
		SELECT s.id, s.value, s.ci_low, s.ci_high, s.fidelity_label, COALESCE(s.engine_breakdown::text, '{}'), s.freshness_at
		FROM measure.scores s
		WHERE s.brand_id = $1 AND s.workspace_id = $2 AND s.tenant_id = $3
		ORDER BY s.freshness_at DESC
	`, brandID, workspaceID, tenantID)
	if err != nil {
		slog.Debug("marka skor sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"brand_name": brandName, "brand_id": brandID, "scores": []interface{}{}})
		return
	}
	defer rows.Close()

	type brandScoreRow struct {
		ID              string             `json:"id"`
		Value           float64            `json:"value"`
		CILow           float64            `json:"ci_low"`
		CIHigh          float64            `json:"ci_high"`
		FidelityLabel   string             `json:"fidelity_label"`
		EngineBreakdown map[string]float64 `json:"engine_breakdown,omitempty"`
		FreshnessAt     time.Time          `json:"freshness_at"`
	}

	scores := make([]brandScoreRow, 0)
	for rows.Next() {
		var s brandScoreRow
		var engBreakdown string
		if err := rows.Scan(&s.ID, &s.Value, &s.CILow, &s.CIHigh, &s.FidelityLabel, &engBreakdown, &s.FreshnessAt); err != nil {
			slog.Error("marka skor satır okuma hatası", "error", err)
			continue
		}
		if engBreakdown != "" && engBreakdown != "{}" {
			if err := json.Unmarshal([]byte(engBreakdown), &s.EngineBreakdown); err != nil {
				slog.Warn("engine breakdown çözümleme hatası", "score_id", s.ID, "error", err)
			}
		}
		scores = append(scores, s)
	}

	if rows.Err() != nil {
		slog.Error("marka skor rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"brand_name": brandName,
		"brand_id":   brandID,
		"scores":     scores,
	})
}

// ListCitations handles GET /v1/workspaces/{ws}/citations
// FR-D2: Bir marka veya job için alıntı/kaynak analizi döndürür.
func (h *Handler) ListCitations(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	brandID := r.URL.Query().Get("brand_id")
	jobID := r.URL.Query().Get("job_id")

	if brandID == "" && jobID == "" {
		httputil.WriteError(w, http.StatusBadRequest, "brand_id veya job_id parametresi gerekli")
		return
	}

	// Önce raw_responses'dan citation'ları çek
	var err error
	var pgRows dbiface.RowsIter
	if jobID != "" {
		pgRows, err = h.pool.Query(r.Context(), `
			SELECT r.id, r.job_id, r.engine_name, r.content_text
			FROM measure.raw_responses r
			WHERE r.job_id = $1 AND r.tenant_id = $2
			ORDER BY r.created_at
		`, jobID, tenantID)
	} else {
		pgRows, err = h.pool.Query(r.Context(), `
			SELECT r.id, r.job_id, r.engine_name, r.content_text
			FROM measure.raw_responses r
			JOIN measure.measurement_jobs j ON j.id = r.job_id
			WHERE j.brand_id = $1 AND j.workspace_id = $2 AND r.tenant_id = $3
			ORDER BY r.created_at DESC
			LIMIT 100
		`, brandID, workspaceID, tenantID)
	}
	if err != nil {
		slog.Error("citation sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"citations": []interface{}{}})
		return
	}
	defer pgRows.Close()

	type citationRow struct {
		RawResponseID string `json:"raw_response_id"`
		JobID         string `json:"job_id"`
		EngineName    string `json:"engine_name"`
		SourceURL     string `json:"source_url"`
		SourceDomain  string `json:"source_domain"`
		Content       string `json:"content,omitempty"`
	}

	citations := make([]citationRow, 0)
	seen := make(map[string]bool)
	for pgRows.Next() {
		var cr citationRow
		var contentText *string
		if err := pgRows.Scan(&cr.RawResponseID, &cr.JobID, &cr.EngineName, &contentText); err != nil {
			slog.Warn("raw_response satır okuma hatası", "error", err)
			continue
		}
		if contentText != nil {
			cr.Content = *contentText
			if len(cr.Content) > 200 {
				cr.Content = cr.Content[:200]
			}
		}
		// content_text içinden URL'leri tara (basit regex)
		if contentText != nil {
			urls := extractURLs(*contentText)
			for _, u := range urls {
				if seen[u] {
					continue
				}
				seen[u] = true
				cr2 := cr
				cr2.SourceURL = u
				cr2.SourceDomain = extractDomain(u)
				citations = append(citations, cr2)
			}
		}
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"citations": citations,
		"count":     len(citations),
	})
}

// ListBenchmark handles GET /v1/workspaces/{ws}/benchmark?brand_id=xxx
// FR-D3: Bir markanın skorunu aynı çalışma alanındaki diğer markalarla karşılaştırır.
func (h *Handler) ListBenchmark(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	brandID := r.URL.Query().Get("brand_id")

	rows, err := h.pool.Query(r.Context(), `
		WITH latest AS (
			SELECT DISTINCT ON (b.id)
				b.id AS brand_id,
				b.name AS brand_name,
				s.value AS score_value,
				s.fidelity_label,
				s.freshness_at,
				s.engine_breakdown AS engine_breakdown
			FROM config.brands b
			LEFT JOIN measure.scores s ON s.brand_id = b.id AND s.workspace_id = b.workspace_id
			WHERE b.workspace_id = $1 AND b.tenant_id = $2 AND b.is_active = true
			ORDER BY b.id, s.freshness_at DESC
		)
		SELECT brand_id, brand_name,
			COALESCE(score_value, 0) AS score_value,
			COALESCE(fidelity_label, 'yok') AS fidelity_label,
			freshness_at,
			engine_breakdown
		FROM latest
		ORDER BY score_value DESC
	`, workspaceID, tenantID)
	if err != nil {
		slog.Error("benchmark sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"benchmark": []interface{}{}})
		return
	}
	defer rows.Close()

	type benchmarkRow struct {
		BrandID         string             `json:"brand_id"`
		BrandName       string             `json:"brand_name"`
		ScoreValue      float64            `json:"score_value"`
		FidelityLabel   string             `json:"fidelity_label"`
		FreshnessAt     *time.Time         `json:"freshness_at,omitempty"`
		EngineBreakdown map[string]float64 `json:"engine_breakdown,omitempty"`
		IsTarget        bool               `json:"is_target"`
	}

	benchmarks := make([]benchmarkRow, 0)
	for rows.Next() {
		var b benchmarkRow
		var freshnessAt *time.Time
		var engBreakdown []byte
		if err := rows.Scan(&b.BrandID, &b.BrandName, &b.ScoreValue, &b.FidelityLabel, &freshnessAt, &engBreakdown); err != nil {
			slog.Warn("benchmark satır okuma hatası", "error", err)
			continue
		}
		b.FreshnessAt = freshnessAt
		if len(engBreakdown) > 0 && string(engBreakdown) != "{}" && string(engBreakdown) != "null" {
			if err := json.Unmarshal(engBreakdown, &b.EngineBreakdown); err != nil {
				slog.Warn("engine breakdown çözümleme hatası", "brand_id", b.BrandID, "error", err)
			}
		}
		if brandID != "" && b.BrandID == brandID {
			b.IsTarget = true
		}
		benchmarks = append(benchmarks, b)
	}

	if rows.Err() != nil {
		slog.Warn("benchmark rows iterasyon hatası", "error", rows.Err())
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"benchmark": benchmarks,
		"count":     len(benchmarks),
	})
}

// ListRadarComparison handles GET /v1/workspaces/{ws}/radar?brand_id=xxx
// H7: Motor bazında rakip karşılaştırması (radar grafiği için).
func (h *Handler) ListRadarComparison(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())
	targetBrandID := r.URL.Query().Get("brand_id")

	// Tüm aktif brand'lerin engine_breakdown'ını al
	rows, err := h.pool.Query(r.Context(), `
		WITH latest AS (
			SELECT DISTINCT ON (b.id)
				b.id AS brand_id,
				b.name AS brand_name,
				s.value AS score_value,
				s.engine_breakdown AS engine_breakdown,
				s.freshness_at
			FROM config.brands b
			LEFT JOIN measure.scores s ON s.brand_id = b.id AND s.workspace_id = b.workspace_id
			WHERE b.workspace_id = $1 AND b.tenant_id = $2 AND b.is_active = true
			ORDER BY b.id, s.freshness_at DESC
		)
		SELECT brand_id, brand_name, score_value, engine_breakdown
		FROM latest
		WHERE score_value IS NOT NULL
		ORDER BY score_value DESC
	`, workspaceID, tenantID)
	if err != nil {
		slog.Error("radar sorgu hatası", "error", err)
		httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{"radar": []interface{}{}})
		return
	}
	defer rows.Close()

	type engineEntry struct {
		Engine string  `json:"engine"`
		Score  float64 `json:"score"`
	}

	type brandRadar struct {
		BrandID    string        `json:"brand_id"`
		BrandName  string        `json:"brand_name"`
		TotalScore float64       `json:"total_score"`
		Engines    []engineEntry `json:"engines"`
		IsTarget   bool          `json:"is_target"`
	}

	// Tüm motor adlarını topla (radar eksenleri)
	allEngines := make(map[string]bool)
	radarData := make([]brandRadar, 0)

	for rows.Next() {
		var br brandRadar
		var engJSON []byte
		if err := rows.Scan(&br.BrandID, &br.BrandName, &br.TotalScore, &engJSON); err != nil {
			slog.Warn("radar satır okuma hatası", "error", err)
			continue
		}

		if targetBrandID != "" && br.BrandID == targetBrandID {
			br.IsTarget = true
		}

		if len(engJSON) > 0 && string(engJSON) != "{}" && string(engJSON) != "null" {
			var breakdown map[string]float64
			if err := json.Unmarshal(engJSON, &breakdown); err == nil {
				for eng, sc := range breakdown {
					allEngines[eng] = true
					br.Engines = append(br.Engines, engineEntry{Engine: eng, Score: sc})
				}
			}
		}
		radarData = append(radarData, br)
	}

	if rows.Err() != nil {
		slog.Warn("radar rows iterasyon hatası", "error", rows.Err())
	}

	engineList := make([]string, 0, len(allEngines))
	for e := range allEngines {
		engineList = append(engineList, e)
	}

	httputil.WriteJSON(w, http.StatusOK, map[string]interface{}{
		"radar":   radarData,
		"engines": engineList,
		"count":   len(radarData),
	})
}

// GetBenchmarkContext handles GET /v1/workspaces/{ws}/benchmark/context
// T2: Anonim sektör kıyası — kullanıcının skorunu tüm kiracıların ortalamasıyla karşılaştırır.
// NFR-13: ≥5 kiracı eşiği altında sonuç döndürmez.
// DP: Laplace mekanizması (ε=1.0) ile diferansiyel gizlilik koruması (bkz. benchmark/privacy.go).
func (h *Handler) GetBenchmarkContext(w http.ResponseWriter, r *http.Request) {
	workspaceID := httpmw.GetWorkspaceID(r.Context())
	tenantID := httpmw.GetTenantID(r.Context())

	// Kullanıcının son skoru
	var myScore float64
	err := h.pool.QueryRow(r.Context(), `
		SELECT COALESCE(value, 0) FROM measure.scores
		WHERE workspace_id = $1 AND tenant_id = $2
		ORDER BY freshness_at DESC LIMIT 1
	`, workspaceID, tenantID).Scan(&myScore)
	if err != nil {
		myScore = 0
	}

	// Toplam kiracı sayısı (NFR-13 gizlilik eşiği)
	var tenantCount int
	_ = h.pool.QueryRow(r.Context(), `
		SELECT COUNT(DISTINCT tenant_id) FROM measure.scores
	`).Scan(&tenantCount)

	// Ham sektör istatistiklerini topla
	var sectorAvg, sectorMin, sectorMax, sectorMedian, sectorStdDev float64
	var percentile25, percentile75, percentile90 float64

	if tenantCount >= 5 {
		// Ortalama, min, max
		_ = h.pool.QueryRow(r.Context(), `
			SELECT AVG(sub.latest)::numeric(10,2),
				MIN(sub.latest)::numeric(10,2),
				MAX(sub.latest)::numeric(10,2)
			FROM (
				SELECT DISTINCT ON (brand_id) value AS latest
				FROM measure.scores
				ORDER BY brand_id, freshness_at DESC
			) sub
		`).Scan(&sectorAvg, &sectorMin, &sectorMax)

		// Medyan
		_ = h.pool.QueryRow(r.Context(), `
			WITH ranked AS (
				SELECT value, ROW_NUMBER() OVER (ORDER BY value) AS rn,
					COUNT(*) OVER () AS cnt
				FROM (
					SELECT DISTINCT ON (brand_id) value
					FROM measure.scores
					ORDER BY brand_id, freshness_at DESC
				) sub
			)
			SELECT AVG(value)::numeric(10,2)
			FROM ranked
			WHERE rn IN ((cnt + 1) / 2, (cnt + 2) / 2)
		`).Scan(&sectorMedian)

		// Standart sapma
		_ = h.pool.QueryRow(r.Context(), `
			SELECT COALESCE(STDDEV(sub.latest)::numeric(10,2), 0)
			FROM (
				SELECT DISTINCT ON (brand_id) value AS latest
				FROM measure.scores
				ORDER BY brand_id, freshness_at DESC
			) sub
		`).Scan(&sectorStdDev)

		// Yüzdelik dilimler (PG 16+ percentile_cont)
		_ = h.pool.QueryRow(r.Context(), `
			WITH distinct_scores AS (
				SELECT DISTINCT ON (brand_id) value AS latest
				FROM measure.scores
				ORDER BY brand_id, freshness_at DESC
			)
			SELECT
				PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY latest)::numeric(10,2),
				PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY latest)::numeric(10,2),
				PERCENTILE_CONT(0.90) WITHIN GROUP (ORDER BY latest)::numeric(10,2)
			FROM distinct_scores
		`).Scan(&percentile25, &percentile75, &percentile90)
	}

	// Difarensiyel gizlilik katmanı uygula
	raw := benchmark.RawSectorStats{
		MyScore:      myScore,
		SectorAvg:    sectorAvg,
		SectorMedian: sectorMedian,
		SectorMin:    sectorMin,
		SectorMax:    sectorMax,
		SectorStdDev: sectorStdDev,
		Percentile25: percentile25,
		Percentile75: percentile75,
		Percentile90: percentile90,
		TenantCount:  tenantCount,
	}

	stats := benchmark.AnonymizeSectorStats(raw, benchmark.DefaultDPConfig())

	// Response'u oluştur
	response := map[string]interface{}{
		"my_score":        stats.MyScore,
		"tenant_count":    stats.TenantCount,
		"sufficient_data": stats.SufficientData,
	}

	if stats.SufficientData {
		response["sector_avg"] = stats.SectorAvg     // backward-compatible
		response["sector_average"] = stats.SectorAvg // canonical key
		response["sector_median"] = stats.SectorMedian
		response["sector_min"] = stats.SectorMin
		response["sector_max"] = stats.SectorMax
		response["sector_stddev"] = stats.SectorStdDev
		response["percentile_25"] = stats.Percentile25
		response["percentile_75"] = stats.Percentile75
		response["percentile_90"] = stats.Percentile90
		response["difference"] = stats.Difference
		response["trend"] = stats.Trend
	} else {
		response["message"] = "yetersiz veri — anonim kıyas için en az 5 kiracı gerekli"
	}

	httputil.WriteJSON(w, http.StatusOK, response)
}

// extractURLs verilen metindeki URL'leri basit regex ile bulur.
func extractURLs(text string) []string {
	urls := make([]string, 0)
	remaining := text
	for {
		start := strings.Index(remaining, "http")
		if start == -1 {
			break
		}
		end := start
		for end < len(remaining) {
			ch := remaining[end]
			if ch == ' ' || ch == '\n' || ch == '\t' || ch == '\r' || ch == ')' || ch == ']' || ch == '}' || ch == '>' || ch == '"' || ch == '\'' {
				break
			}
			end++
		}
		url := remaining[start:end]
		if strings.HasPrefix(url, "http://") || strings.HasPrefix(url, "https://") {
			urls = append(urls, url)
		}
		remaining = remaining[end:]
	}
	return urls
}
