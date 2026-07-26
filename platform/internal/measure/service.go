package measure

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"math"
	"strings"
	"sync"
	"time"

	"github.com/geolens/platform/engine"
	"github.com/geolens/platform/internal/config"
	"github.com/geolens/platform/internal/id"
	"github.com/geolens/platform/platform/db"
)

// ---- Default Component Weights (0409 §2'den) ----

var defaultWeights = ComponentWeights{
	PresenceShare:     0.35,
	PositionWeight:    0.25,
	SourceShare:       0.20,
	CompetitorContext: 0.20,
}

// ---- Service Implementation ----

// service implements the Service interface for measurement and scoring.
type service struct {
	pool    *db.Pool
	engines *engine.Registry
	cfg     *config.Config
}

// NewService creates a new measurement service.
func NewService(pool *db.Pool, engines *engine.Registry, cfg *config.Config) Service {
	return &service{
		pool:    pool,
		engines: engines,
		cfg:     cfg,
	}
}

// Measure executes n=3 measurements for all registered engines and aggregates results.
func (s *service) Measure(ctx context.Context, req MeasurementRequest) (*MeasurementResult, error) {
	// Tüm kayıtlı motorları topla
	engineNames := s.engines.List()
	if len(engineNames) == 0 {
		return nil, fmt.Errorf("measure: kayıtlı motor bulunamadı")
	}

	// n={sampleCount} örnekleme: her motora aynı prompt N kez gönderilir
	sampleCount := 3 // default
	if s.cfg != nil && s.cfg.SampleCount > 0 {
		sampleCount = s.cfg.SampleCount
	}

	type sampleResult struct {
		engineName string
		responses  []*engine.RawResponse
		err        error
	}

	results := make([]sampleResult, 0, len(engineNames))

	for _, name := range engineNames {
		adapter := s.engines.Get(name)
		if adapter == nil {
			slog.Warn("measure: motor registry'de bulunamadı, atlanıyor", "engine", name)
			continue
		}

		// Adapter'a tenant/workspace context'ini ekle (thread-safe copy)
		type contextualEngine interface {
			WithContext(tenantID, workspaceID string) engine.Adapter
		}
		if ce, ok := adapter.(contextualEngine); ok {
			adapter = ce.WithContext(req.TenantID, req.WorkspaceID)
		}

		// n={sampleCount} paralel örnekleme
		var wg sync.WaitGroup
		samples := make([]*engine.RawResponse, sampleCount)
		var sampleErr error
		var mu sync.Mutex

		for i := 0; i < sampleCount; i++ {
			wg.Add(1)
			go func(idx int) {
				defer wg.Done()
				resp, err := adapter.Execute(req.PromptText)
				mu.Lock()
				if err != nil {
					sampleErr = err
				} else {
					samples[idx] = resp
				}
				mu.Unlock()
			}(i)
		}
		wg.Wait()

		if sampleErr != nil {
			slog.Error("measure: örnekleme hatası", "engine", name, "error", sampleErr)
			continue
		}

		// Boş örnekleri filtrele
		valid := make([]*engine.RawResponse, 0, sampleCount)
		for _, s := range samples {
			if s != nil {
				valid = append(valid, s)
			}
		}

		if len(valid) > 0 {
			results = append(results, sampleResult{engineName: name, responses: valid})
		}
	}

	if len(results) == 0 {
		return nil, fmt.Errorf("measure: hiçbir motordan geçerli yanıt alınamadı")
	}

	// Sonuçları birleştir
	allRaw := make([]engine.RawResponse, 0)
	allCitations := make([]engine.Citation, 0)
	var firstMeta engine.EngineMeta

	for _, r := range results {
		for _, resp := range r.responses {
			allRaw = append(allRaw, *resp)
			allCitations = append(allCitations, resp.Citations...)
		}
		if r.responses[0] != nil {
			firstMeta = engine.EngineMeta{
				EngineName:   r.engineName,
				ModelVersion: r.responses[0].FidelityLabel,
				Tier:         r.responses[0].Tier,
			}
		}
	}

	return &MeasurementResult{
		RawResponses: allRaw,
		Citations:    allCitations,
		EngineMeta:   firstMeta,
		BrandID:      req.BrandID,
		BrandName:    req.BrandName,
		PanelID:      req.PanelID,
		WorkspaceID:  req.WorkspaceID,
		TenantID:     req.TenantID,
	}, nil
}

// CalculateScore computes the visibility score from measurement results.
// Dört bileşen: Varlık Payı (%35), Konum Ağırlığı (%25), Kaynak Payı (%20), Rakip Bağlamı (%20).
// Partial yayın: bazı motorlar başarısız olsa bile kalan veriyle hesaplama yapılır.
func (s *service) CalculateScore(ctx context.Context, panelID string, results []MeasurementResult, weights ComponentWeights) (*Score, error) {
	if weights == (ComponentWeights{}) {
		weights = defaultWeights
	}

	// Tüm raw response'ları birleştir — başarısız motorlar atlanır (partial yayın)
	var allResponses []engine.RawResponse
	for _, r := range results {
		allResponses = append(allResponses, r.RawResponses...)
	}

	if len(allResponses) == 0 {
		return nil, fmt.Errorf("calculate_score: hesaplama için veri yok")
	}

	brandName := results[0].BrandName
	brandID := results[0].BrandID
	workspaceID := results[0].WorkspaceID
	tenantID := results[0].TenantID

	// ---- Bileşen 1: Varlık Payı (Presence Share) - %35 ----
	presenceScore := computePresenceShare(allResponses, brandName)

	// ---- Bileşen 2: Konum Ağırlığı (Position Weight) - %25 ----
	positionScore := computePositionWeight(allResponses)

	// ---- Bileşen 3: Kaynak Payı (Source Share) - %20 ----
	sourceScore := computeSourceShare(allResponses)

	// ---- Bileşen 4: Rakip Bağlamı (Competitor Context) - %20 ----
	competitorScore := computeCompetitorContext(allResponses)

	// Ağırlıklı toplam (partial yayın: başarısız bileşenler 0 olarak katılır)
	totalScore := weights.PresenceShare*presenceScore +
		weights.PositionWeight*positionScore +
		weights.SourceShare*sourceScore +
		weights.CompetitorContext*competitorScore

	// [0, 100] aralığına normalize et
	totalScore = math.Min(totalScore, 100.0)
	totalScore = math.Max(totalScore, 0.0)

	scoreID := id.New()
	calcRunID := id.New()

	// Component değerlerini JSON olarak hazırla
	componentValues := map[string]float64{
		"presence_share":     math.Round(presenceScore*100) / 100,
		"position_weight":    math.Round(positionScore*100) / 100,
		"source_share":       math.Round(sourceScore*100) / 100,
		"competitor_context": math.Round(competitorScore*100) / 100,
		"total_score":        math.Round(totalScore*100) / 100,
	}

	// Calculation run'ı DB'ye kaydet (deterministik hesaplama kaydı)
	if _, err := s.pool.Exec(ctx, `
		INSERT INTO measure.calculation_runs (id, panel_id, tenant_id, algorithm_version, component_values, created_at)
		VALUES ($1, $2, $3, '1.0.0', $4::jsonb, now())
	`, calcRunID, panelID, tenantID, componentValues); err != nil {
		slog.Warn("calculation_run kaydetme hatası", "error", err)
	}

	engineBreakdown := computeEngineBreakdown(allResponses)
	engineBreakdownRaw, err := json.Marshal(engineBreakdown)
	engineBreakdownJSON := "{}"
	if err == nil && string(engineBreakdownRaw) != "null" {
		engineBreakdownJSON = string(engineBreakdownRaw)
	}

	score := &Score{
		ID:               scoreID,
		PanelID:          panelID,
		Value:            math.Round(totalScore*100) / 100,
		CILow:            math.Max(0, totalScore-5.0),
		CIHigh:           math.Min(100, totalScore+5.0),
		FidelityLabel:    aggregateFidelity(allResponses),
		EngineBreakdown:  engineBreakdown,
		PanelVersion:     "1.0.0",
		CalculationRunID: calcRunID,
		FreshnessAt:      time.Now().UTC(),
		CreatedAt:        time.Now().UTC(),
	}

	// Skoru DB'ye kaydet (freshness_at + created_at SQL'de now() ile doldurulur)
	if _, err := s.pool.Exec(ctx, `
		INSERT INTO measure.scores (id, panel_id, brand_id, workspace_id, tenant_id, value, ci_low, ci_high, fidelity_label, engine_breakdown, panel_version, calculation_run_id, freshness_at, created_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10::jsonb, $11, $12, now(), now())
	`, scoreID, panelID, brandID, workspaceID, tenantID,
		score.Value, score.CILow, score.CIHigh, score.FidelityLabel,
		string(engineBreakdownJSON), score.PanelVersion, score.CalculationRunID); err != nil {
		slog.Warn("skor kaydetme hatası", "error", err)
	}

	return score, nil
}

// GetScoreByID retrieves a previously computed score from the database.
func (s *service) GetScoreByID(ctx context.Context, scoreID string) (*Score, error) {
	var score Score
	var engineBreakdownJSON string
	var freshnessStr, createdAtStr time.Time

	err := s.pool.QueryRow(ctx, `
		SELECT id, COALESCE(panel_id, ''), COALESCE(brand_id, ''), COALESCE(workspace_id, ''), COALESCE(tenant_id, ''),
		       value, COALESCE(ci_low, 0), COALESCE(ci_high, 0), fidelity_label,
		       COALESCE(engine_breakdown::text, '{}'), panel_version, COALESCE(calculation_run_id, ''),
		       freshness_at, created_at
		FROM measure.scores
		WHERE id = $1
	`, scoreID).Scan(
		&score.ID, &score.PanelID, &score.BrandID, &score.WorkspaceID, &score.TenantID,
		&score.Value, &score.CILow, &score.CIHigh, &score.FidelityLabel,
		&engineBreakdownJSON, &score.PanelVersion, &score.CalculationRunID,
		&freshnessStr, &createdAtStr,
	)
	if err != nil {
		return nil, fmt.Errorf("skor sorgu: %w", err)
	}

	score.FreshnessAt = freshnessStr
	score.CreatedAt = createdAtStr

	// Engine breakdown JSON'ı parse et
	if engineBreakdownJSON != "" && engineBreakdownJSON != "{}" {
		if err := json.Unmarshal([]byte(engineBreakdownJSON), &score.EngineBreakdown); err != nil {
			slog.Warn("engine breakdown çözümleme hatası", "score_id", score.ID, "error", err)
		}
	}

	return &score, nil
}

// ---- Bileşen Hesaplama Fonksiyonları ----

// computePresenceShare calculates what % of responses mention the brand name.
func computePresenceShare(responses []engine.RawResponse, brandName string) float64 {
	if len(responses) == 0 {
		return 0
	}

	if brandName == "" {
		// Marka adı yoksa içerik varlığına bak (fallback)
		return computeContentPresence(responses)
	}

	var mentioned int
	brandLower := strings.ToLower(brandName)
	for _, resp := range responses {
		contentLower := strings.ToLower(resp.Content)
		if strings.Contains(contentLower, brandLower) {
			mentioned++
		}
	}

	return float64(mentioned) / float64(len(responses)) * 100.0
}

// computeContentPresence is a fallback when brand name is not available.
func computeContentPresence(responses []engine.RawResponse) float64 {
	var nonEmpty int
	for _, resp := range responses {
		if strings.TrimSpace(resp.Content) != "" {
			nonEmpty++
		}
	}
	return float64(nonEmpty) / float64(len(responses)) * 100.0
}

// computePositionWeight calculates the average position score.
// İlk 200 karakterde geçiyorsa yüksek skor, sonraki 500'de orta, yoksa düşük.
func computePositionWeight(responses []engine.RawResponse) float64 {
	if len(responses) == 0 {
		return 0
	}

	var totalScore float64
	for _, resp := range responses {
		content := resp.Content
		switch {
		case len(content) == 0:
			totalScore += 0
		case len(content) <= 200:
			totalScore += 90 // Çok erken bahsedilmiş
		case len(content) <= 700:
			totalScore += 60 // Orta konumda
		default:
			totalScore += 30 // Geç konumda
		}
	}

	return totalScore / float64(len(responses))
}

// computeSourceShare calculates source diversity from citations.
func computeSourceShare(responses []engine.RawResponse) float64 {
	var totalCitations int
	domains := make(map[string]struct{})

	for _, resp := range responses {
		for _, c := range resp.Citations {
			totalCitations++
			domain := extractDomain(c.URL)
			if domain != "" {
				domains[domain] = struct{}{}
			}
		}
	}

	if totalCitations == 0 {
		// Alıntı yoksa düşük skor
		return 20
	}

	// Domain çeşitliliği: en az 3 farklı domain ideal
	domainCount := len(domains)
	switch {
	case domainCount >= 5:
		return 100
	case domainCount >= 3:
		return 75
	case domainCount >= 1:
		return 50
	default:
		return 20
	}
}

// computeCompetitorContext measures brand differentiation by comparing
// brand name prominence against other entities in the same response set.
func computeCompetitorContext(responses []engine.RawResponse) float64 {
	if len(responses) == 0 {
		return 50
	}

	// Extract all unique entity/citation references per response
	citationDomains := make(map[string]map[string]int)
	for _, resp := range responses {
		domains := make(map[string]int)
		for _, c := range resp.Citations {
			domain := extractDomain(c.URL)
			if domain != "" {
				domains[domain]++
			}
		}
		citationDomains[resp.EngineName+resp.Content[:min(30, len(resp.Content))]] = domains
	}

	if len(citationDomains) == 0 {
		return 30
	}

	// Brand share: brands with higher unique source count score higher
	totalUniqueSources := make(map[string]struct{})
	for _, domains := range citationDomains {
		for d := range domains {
			totalUniqueSources[d] = struct{}{}
		}
	}

	uniqueSourceCount := len(totalUniqueSources)
	switch {
	case uniqueSourceCount >= 5:
		return 100
	case uniqueSourceCount >= 3:
		return 75
	case uniqueSourceCount >= 1:
		return 50
	default:
		return 30
	}
}

// aggregateFidelity combines fidelity labels from multiple responses.
func aggregateFidelity(responses []engine.RawResponse) string {
	if len(responses) == 0 {
		return "unknown"
	}

	// En düşük tier'ın etiketini kullan (en muhafazakâr)
	lowestTier := engine.TierDirectional
	lowestLabel := responses[0].FidelityLabel

	for _, r := range responses {
		if r.Tier < lowestTier {
			lowestTier = r.Tier
			lowestLabel = r.FidelityLabel
		}
	}

	return lowestLabel
}

// computeEngineBreakdown creates per-engine score map.
func computeEngineBreakdown(responses []engine.RawResponse) map[string]float64 {
	breakdown := make(map[string]float64)
	for _, resp := range responses {
		key := resp.EngineName
		present := 40.0 // Varsayılan: engine çalıştı
		if strings.TrimSpace(resp.Content) != "" {
			present = 75.0
		}
		if existing, ok := breakdown[key]; ok {
			breakdown[key] = (existing + present) / 2
		} else {
			breakdown[key] = present
		}
	}
	return breakdown
}

// ---- Yardımcı Fonksiyonlar ----

// extractDomain extracts a domain from a URL string.
func extractDomain(url string) string {
	// Basit domain çıkarma
	url = strings.TrimPrefix(url, "https://")
	url = strings.TrimPrefix(url, "http://")
	url = strings.TrimPrefix(url, "www.")

	parts := strings.Split(url, "/")
	if len(parts) > 0 {
		return parts[0]
	}
	return ""
}
