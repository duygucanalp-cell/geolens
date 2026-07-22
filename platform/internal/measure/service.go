package measure

import (
	"context"
	"fmt"
	"log/slog"
	"math"
	"strings"
	"sync"
	"time"

	"github.com/geolens/platform/engine"
	"github.com/geolens/platform/internal/config"
	"github.com/geolens/platform/platform/db"
)

// ---- Default Component Weights (0409 §2'den) ----

var defaultWeights = ComponentWeights{
	PresenceShare:    0.35,
	PositionWeight:   0.25,
	SourceShare:      0.20,
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

	// n=3 örnekleme: her motora aynı prompt 3 kez gönderilir
	const sampleCount = 3

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

		// n=3 paralel örnekleme
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
		BrandName:    req.BrandName,
	}, nil
}

// CalculateScore computes the visibility score from measurement results.
// Dört bileşen: Varlık Payı (%35), Konum Ağırlığı (%25), Kaynak Payı (%20), Rakip Bağlamı (%20).
func (s *service) CalculateScore(ctx context.Context, panelID string, results []MeasurementResult, weights ComponentWeights) (*Score, error) {
	if weights == (ComponentWeights{}) {
		weights = defaultWeights
	}

	// Tüm raw response'ları birleştir
	var allResponses []engine.RawResponse
	for _, r := range results {
		allResponses = append(allResponses, r.RawResponses...)
	}

	if len(allResponses) == 0 {
		return nil, fmt.Errorf("calculate_score: hesaplama için veri yok")
	}

	// İlk MeasurementResult'tan marka adını al (tüm sonuçlar aynı marka içindir)
	brandName := results[0].BrandName

	// ---- Bileşen 1: Varlık Payı (Presence Share) - %35 ----
	// Markanın yanıtlarda ne sıklıkta geçtiği
	presenceScore := computePresenceShare(allResponses, brandName)

	// ---- Bileşen 2: Konum Ağırlığı (Position Weight) - %25 ----
	// Markanın yanıt içindeki ilk geçtiği konum (erken = yüksek skor)
	positionScore := computePositionWeight(allResponses)

	// ---- Bileşen 3: Kaynak Payı (Source Share) - %20 ----
	// Alıntı kaynaklarının çeşitliliği
	sourceScore := computeSourceShare(allResponses)

	// ---- Bileşen 4: Rakip Bağlamı (Competitor Context) - %20 ----
	// Markanın rakiplere karşı anılış payı
	competitorScore := computeCompetitorContext(allResponses)

	// Ağırlıklı toplam
	totalScore := weights.PresenceShare*presenceScore +
		weights.PositionWeight*positionScore +
		weights.SourceShare*sourceScore +
		weights.CompetitorContext*competitorScore

	// [0, 100] aralığına normalize et
	totalScore = math.Min(totalScore, 100.0)
	totalScore = math.Max(totalScore, 0.0)

	score := &Score{
		ID:      generateULID(),
		PanelID: panelID,
		Value:   math.Round(totalScore*100) / 100,
		CILow:   math.Max(0, totalScore-5.0),  // Basit CI: ±5 (H4'te iyileştirilecek)
		CIHigh:  math.Min(100, totalScore+5.0),
		FidelityLabel: aggregateFidelity(allResponses),
		EngineBreakdown: computeEngineBreakdown(allResponses),
		PanelVersion:    "1.0.0",
		FreshnessAt:     time.Now().UTC(),
		CreatedAt:       time.Now().UTC(),
	}

	return score, nil
}

// GetScoreByID retrieves a previously computed score from the database.
func (s *service) GetScoreByID(ctx context.Context, scoreID string) (*Score, error) {
	var score Score
	var engineBreakdownJSON, freshnessStr, createdAtStr string

	err := s.pool.QueryRow(ctx, `
		SELECT id, panel_id, brand_id, workspace_id, tenant_id,
		       value, ci_low, ci_high, fidelity_label,
		       engine_breakdown, panel_version, calculation_run_id,
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

	// TODO(H3): JSON ve zaman ayrıştırma eklenecek
	_ = engineBreakdownJSON
	_ = freshnessStr
	_ = createdAtStr

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

// computeCompetitorContext calculates how much brand stands out vs competitors.
// TODO(H4): panel'deki rakip listesine göre gerçek karşılaştırma
func computeCompetitorContext(responses []engine.RawResponse) float64 {
	if len(responses) == 0 {
		return 50 // Nötr varsayılan
	}

	// Basit yaklaşım: Kaç farklı kaynak markayı referans almış?
	sourceCount := make(map[string]struct{})
	for _, resp := range responses {
		for _, c := range resp.Citations {
			domain := extractDomain(c.URL)
			if domain != "" {
				sourceCount[domain] = struct{}{}
			}
		}
	}

	// En az 3 farklı kaynak ideal
	switch len(sourceCount) {
	case 0:
		return 30
	case 1:
		return 40
	case 2:
		return 60
	default:
		return 90
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

// generateULID creates a simple ULID-like ID using timestamp + random.
func generateULID() string {
	// TODO(H3): Gerçek ULID kütüphanesi kullan (örn. github.com/oklog/ulid/v2)
	now := time.Now().UnixMilli()
	return fmt.Sprintf("%d-%s", now, randomString(8))
}

func randomString(n int) string {
	const letters = "abcdefghijklmnopqrstuvwxyz0123456789"
	b := make([]byte, n)
	for i := range b {
		// Basit pseudo-random (H3'te iyileştir)
		b[i] = letters[(time.Now().UnixNano()+int64(i*7))%int64(len(letters))]
	}
	return string(b)
}

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
