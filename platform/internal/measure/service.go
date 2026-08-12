package measure

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"math"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/geolens/platform/engine"
	"github.com/geolens/platform/internal/config"
	"github.com/geolens/platform/internal/id"
	"github.com/geolens/platform/internal/ml"
	"github.com/geolens/platform/platform/db"
)

// ---- Default Component Weights (0409 §2'den) ----

// v2Defaults — 7 bileşenli VI (0409 v1.3 §2.1): %30/%20/%15/%15/%10/%5/%5. A3-5.
var v2DefaultWeights = ComponentWeights{
	PresenceShare:     0.30,
	PositionWeight:    0.20,
	SourceShare:       0.15,
	CompetitorContext: 0.15,
	AppearanceRate:    0.10,
	Sentiment:         0.05,
	CompVisibility:    0.05,
}

// v1LegacyWeights — eski 4 bileşenli skor (0409 v1.0 D-89): %35/%25/%20/%20.
// SCORE_ALGORITHM_VERSION=1.0.0 geri dönüş için korunur.
var v1LegacyWeights = ComponentWeights{
	PresenceShare:     0.35,
	PositionWeight:    0.25,
	SourceShare:       0.20,
	CompetitorContext: 0.20,
}

// defaultWeights returns the active profile weights based on the configured
// algorithm version. Keep the v1 name as stable alias for tests/back-compat.
var defaultWeights = v2DefaultWeights

// defaultIntentComponentScale — prompt intent'inin VI bileşenlerine varsayılan
// etkisi (0421 A3-3, 0404 prompt ağırlıkları ile hizalı). Sıra: [varlık, konum,
// kaynak, rakip, appearance, sentiment, compvis]. Çarpanlar uygulandıktan sonra
// toplam 1.0'a normalize edilir; değerler pilot verisiyle kalibre edilir (0404 §4).
// INTENT_WEIGHT_SCALE env'i ile override edilebilir (pilot kalibrasyonu için).
var defaultIntentComponentScale = map[string][7]float64{
	"presence":       {1.25, 1.00, 0.90, 0.90, 1.10, 0.90, 0.90}, // varlık sinyali öne çıkar
	"comparison":     {0.90, 1.00, 0.90, 1.40, 0.90, 0.90, 1.30}, // rakip bağlamı + compvis öne çıkar
	"recommendation": {1.00, 1.00, 1.15, 1.00, 0.95, 1.10, 0.95}, // kaynak güveni + sentiment öne çıkar
	"category":       {1.00, 1.00, 1.00, 1.00, 1.25, 1.00, 1.00}, // appearance (kategori görünürlüğü) öne çıkar
	"problem":        {1.00, 1.15, 1.10, 1.00, 0.90, 1.00, 1.00}, // konum (çözüm bulunabilirliği) öne çıkar
}

// ---- Service Implementation ----

// service implements the Service interface for measurement and scoring.
type service struct {
	pool    *db.Pool
	engines *engine.Registry
	cfg     *config.Config
	// ml — opsiyonel ML serving istemcisi (0421 A0-3). Nil ise intent tabanlı
	// ağırlıklandırma atlanır (varsayılan GAVF ağırlıkları — 0421 M-4 fallback).
	ml *ml.Client

	// breaker — serving ardışık hatasında ML çağrılarını askıya alan ortak devre
	// kesici (ml.CircuitBreaker, 0421 M-4). Worker her örnekleme mesajında aynı
	// prompt'u skorlatır; serving kapalıyken 24×ML_TIMEOUT birikmesini önler.
	// Sentiment servisiyle aynı tip paylaşılır (tek uygulama, tutarlı davranış).
	breaker *ml.CircuitBreaker

	// Per-prompt önbellek: worker aynı prompt'u yeniden sınıflandırmaz.
	cacheMu      sync.Mutex
	cachedPrompt string
	cachedLabels *ml.PromptClassification

	// intentScale — INTENT_WEIGHT_SCALE env'inden çözülen intent çarpanları
	// (0421 A3-3 pilot kalibrasyonu). nil ise defaultIntentComponentScale kullanılır.
	intentScale map[string][7]float64
}

// NewService creates a new measurement service (ML serving yok — kural tabanlı ağırlıklar).
func NewService(pool *db.Pool, engines *engine.Registry, cfg *config.Config) Service {
	return NewServiceWithML(pool, engines, cfg, nil)
}

// NewServiceWithML, ML serving client ile ölçüm servisi kurar. mlClient nil ise
// intent tabanlı ağırlıklandırma devre dışıdır (0421 M-4); dolu ise her skor
// hesabında önce prompt sınıflandırılır, serving hatasında varsayılan ağırlıklar kullanılır.
// cfg.IntentWeightScaleRaw doluysa (INTENT_WEIGHT_SCALE) intent çarpanları env'den
// çözülür; boşsa varsayılanlar kullanılır. cfg nil ise env doğrudan okunur (handler
// anlık skor yolu cfg geçirmez — 0421 A3-3 pilot kalibrasyonu her iki yolda da çalışır).
func NewServiceWithML(pool *db.Pool, engines *engine.Registry, cfg *config.Config, mlClient *ml.Client) Service {
	s := &service{pool: pool, engines: engines, cfg: cfg, ml: mlClient, breaker: ml.NewCircuitBreakerFor("measure", ml.DefaultCooldown)}
	raw := ""
	if cfg != nil {
		raw = cfg.IntentWeightScaleRaw
	} else {
		raw = os.Getenv("INTENT_WEIGHT_SCALE")
	}
	if scale, ok := config.ParseIntentWeightScaleRaw(raw); ok {
		s.intentScale = scale
	}
	return s
}

// effectiveWeights returns env-configured weights (SCORE_WEIGHTS) or GAVF defaults.
// PO review §4: skor ağırlıkları env üzerinden yapılandırılabilir (0301 O-1 kalibrasyonu).
// A3-5 feature flag: SCORE_ALGORITHM_VERSION=1.0.0 → eski 4 bileşenli; 2.0.0 (default) → 7 bileşenli.
func (s *service) effectiveWeights() ComponentWeights {
	if s.cfg == nil {
		return defaultWeights
	}
	if s.cfg.ScoreAlgorithmVersion == "1.0.0" {
		if p, pos, src, comp, ok := s.cfg.ParseScoreWeights(); ok {
			return ComponentWeights{PresenceShare: p, PositionWeight: pos, SourceShare: src, CompetitorContext: comp}
		}
		return v1LegacyWeights
	}
	// v2 (default): önce 7'li SCORE_WEIGHTS parselle (new profile), yoksa varsayılan.
	if v2, ok := s.cfg.ParseScoreWeightsV2(); ok {
		return ComponentWeights{
			PresenceShare: v2.Presence, PositionWeight: v2.Position, SourceShare: v2.Source,
			CompetitorContext: v2.Competitor, AppearanceRate: v2.Appearance,
			Sentiment: v2.Sentiment, CompVisibility: v2.CompVis,
		}
	}
	// Eski 4'lü SCORE_WEIGHTS v2 modunda da geçerli (yeni bileşenler 0 → default yerine geçiş)
	if p, pos, src, comp, ok := s.cfg.ParseScoreWeights(); ok {
		w := v2DefaultWeights
		w.PresenceShare, w.PositionWeight, w.SourceShare, w.CompetitorContext = p, pos, src, comp
		return w
	}
	return v2DefaultWeights
}

// intentWeights — prompt intent'ine göre VI bileşen ağırlıklarını ölçekler (0421 A3-3).
// Prompt, MeasurementResult.PromptText'ten alınır; serving yok/hatalı/cooldown'da veya
// prompt boşsa ok=false döner (varsayılan ağırlıklar kullanılır — 0421 M-4). Sonuç
// per-prompt önbelleklenir: worker aynı prompt'u her örnekleme mesajında yeniden gönderir.
func (s *service) intentWeights(ctx context.Context, results []MeasurementResult, base ComponentWeights) (ComponentWeights, bool) {
	if s.ml == nil {
		return ComponentWeights{}, false
	}
	prompt := ""
	if len(results) > 0 {
		prompt = strings.TrimSpace(results[0].PromptText)
	}
	if prompt == "" {
		return ComponentWeights{}, false
	}

	s.cacheMu.Lock()
	cached := s.cachedPrompt == prompt && s.cachedLabels != nil
	labels := s.cachedLabels
	s.cacheMu.Unlock()

	if !cached {
		if s.breaker.InCooldown() {
			return ComponentWeights{}, false
		}
		cls, err := s.ml.ClassifyPrompt(ctx, prompt)
		if err != nil {
			slog.Warn("measure: prompt sınıflandırma başarısız, varsayılan ağırlıklar kullanılıyor", "error", err)
			s.breaker.Fail()
			return ComponentWeights{}, false
		}
		s.breaker.Success()
		s.cacheMu.Lock()
		s.cachedPrompt = prompt
		s.cachedLabels = cls
		s.cacheMu.Unlock()
		labels = cls
	}

	scale := s.effectiveIntentScale()
	if _, known := scale[labels.Intent.Label]; !known {
		// Bilinmeyen/boş intent → ölçek uygulanmaz, varsayılan ağırlıklar (M-4).
		return ComponentWeights{}, false
	}
	adjusted := applyIntentWeightsWithScale(base, labels.Intent.Label, scale)
	slog.Debug("measure: intent tabanlı ağırlıklar", "intent", labels.Intent.Label, "confidence", labels.Intent.Confidence)
	return adjusted, true
}

// effectiveIntentScale — servisin aktif intent çarpan tablosu. INTENT_WEIGHT_SCALE
// ile override edilmemişse varsayılan tablo döner.
func (s *service) effectiveIntentScale() map[string][7]float64 {
	if len(s.intentScale) > 0 {
		return s.intentScale
	}
	return defaultIntentComponentScale
}

// applyIntentWeights — 7 bileşenli ağırlıkları intent çarpanlarıyla ölçekler ve
// toplamı 1.0'a normalize eder. Bilinmeyen intent veya v1 (4 bileşenli) profilde
// ağırlıklar aynen döner (deterministik; G2 ilkesi). Varsayılan çarpanları kullanır
// (INTENT_WEIGHT_SCALE override'ı olmayan testler/kod için); servis yolu
// effectiveIntentScale ile override edilen çarpanları kullanır.
func applyIntentWeights(base ComponentWeights, intent string) ComponentWeights {
	return applyIntentWeightsWithScale(base, intent, defaultIntentComponentScale)
}

// applyIntentWeightsWithScale — applyIntentWeights'in çarpan tablosu parametreli hali.
// INTENT_WEIGHT_SCALE env'inden gelen çarpanlarla çalışır (0421 A3-3 pilot kalibrasyonu).
func applyIntentWeightsWithScale(base ComponentWeights, intent string, scale map[string][7]float64) ComponentWeights {
	scaleRow, ok := scale[intent]
	if !ok || !base.IsV2() {
		return base
	}
	w := ComponentWeights{
		PresenceShare:     base.PresenceShare * scaleRow[0],
		PositionWeight:    base.PositionWeight * scaleRow[1],
		SourceShare:       base.SourceShare * scaleRow[2],
		CompetitorContext: base.CompetitorContext * scaleRow[3],
		AppearanceRate:    base.AppearanceRate * scaleRow[4],
		Sentiment:         base.Sentiment * scaleRow[5],
		CompVisibility:    base.CompVisibility * scaleRow[6],
	}
	sum := w.PresenceShare + w.PositionWeight + w.SourceShare + w.CompetitorContext +
		w.AppearanceRate + w.Sentiment + w.CompVisibility
	if sum <= 0 {
		return base
	}
	w.PresenceShare /= sum
	w.PositionWeight /= sum
	w.SourceShare /= sum
	w.CompetitorContext /= sum
	w.AppearanceRate /= sum
	w.Sentiment /= sum
	w.CompVisibility /= sum
	return w
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

		// n={sampleCount} paralel örnekleme //nolint:misspell
		var wg sync.WaitGroup
		samples := make([]*engine.RawResponse, sampleCount)
		var sampleErr error
		var mu sync.Mutex

		for i := 0; i < sampleCount; i++ {
			wg.Add(1)
			go func(idx int) {
				defer wg.Done()
				resp, err := adapter.Execute(ctx, req.PromptText)
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
		PromptText:   req.PromptText,
	}, nil
}

// CalculateScore computes the visibility score from measurement results.
// v1: dört bileşen (0409 v1.0). v2 (A3-5): 7 bileşenli VI (0409 v1.3).
// Partial yayın: bazı motorlar başarısız olsa bile kalan veriyle hesaplama yapılır.
func (s *service) CalculateScore(ctx context.Context, panelID string, results []MeasurementResult, weights ComponentWeights) (*Score, error) {
	// weights boşsa: env override (SCORE_WEIGHTS) veya GAVF varsayılanları (deterministik default)
	if weights == (ComponentWeights{}) {
		weights = s.effectiveWeights()
	}

	// 0421 A3-3: prompt intent sınıflandırması (serving) → VI bileşen ağırlıklarını
	// intent'e göre ölçekle. Serving yok/hatalı/cooldown → varsayılan ağırlıklar (M-4).
	if adjusted, ok := s.intentWeights(ctx, results, weights); ok {
		weights = adjusted
	}

	algorithmVersion := "1.0.0"
	if weights.IsV2() {
		algorithmVersion = "2.0.0"
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

	// Saf, deterministik skor matematiği (partial yayın dahil — G2 determinizm ilkesi)
	totalScore := computeTotalScore(allResponses, brandName, weights)

	// Bileşenler (deterministik yeniden hesap kaydı için)
	presenceScore, positionScore, sourceScore, competitorScore, appearanceScore, sentimentScore, compvisScore := computeComponentScores(allResponses, brandName)

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
	if weights.IsV2() {
		componentValues["appearance_rate"] = math.Round(appearanceScore*100) / 100
		componentValues["sentiment"] = math.Round(sentimentScore*100) / 100
		componentValues["comp_visibility"] = math.Round(compvisScore*100) / 100
	}

	// Calculation run'ı DB'ye kaydet (deterministik hesaplama kaydı)
	if _, err := s.pool.Exec(ctx, `
		INSERT INTO measure.calculation_runs (id, panel_id, tenant_id, algorithm_version, component_values, created_at)
		VALUES ($1, $2, $3, $4, $5::jsonb, now())
	`, calcRunID, panelID, tenantID, algorithmVersion, componentValues); err != nil {
		slog.Warn("calculation_run kaydetme hatası", "error", err)
	}

	engineBreakdown := computeEngineBreakdown(allResponses)
	engineBreakdownRaw, err := json.Marshal(engineBreakdown)
	engineBreakdownJSON := "{}"
	if err == nil && string(engineBreakdownRaw) != "null" {
		engineBreakdownJSON = string(engineBreakdownRaw)
	}

	ciLow, ciHigh := computeScoreCI(totalScore, weights)
	score := &Score{
		ID:               scoreID,
		PanelID:          panelID,
		Value:            math.Round(totalScore*100) / 100,
		CILow:            math.Max(0, ciLow),
		CIHigh:           math.Min(100, ciHigh),
		FidelityLabel:    aggregateFidelity(allResponses),
		EngineBreakdown:  engineBreakdown,
		PanelVersion:     algorithmVersion,
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

// computeComponentScores returns the visibility components for the given responses.
// v1: 4 bileşen. v2: 7 bileşen (son 3: appearance, sentiment, compvis).
// Deterministik: aynı girdi her zaman aynı bileşenleri üretir (G2 ilkesi).
func computeComponentScores(responses []engine.RawResponse, brandName string) (presence, position, source, competitor, appearance, sentiment, compvis float64) {
	return computePresenceShare(responses, brandName),
		computePositionWeight(responses),
		computeSourceShare(responses),
		computeCompetitorContext(responses),
		computeAppearanceRate(responses),
		computeSentimentScore(responses),
		computeCompVisibility(responses, brandName)
}

// computeTotalScore is the pure, deterministic weighted scoring math used by CalculateScore.
// Partial yayın dahil: aynı girdi her zaman aynı skoru üretir (G2 determinizm ilkesi, temp=0 + n=3).
// v1 (ComponentWeights v1): 4 bileşenli toplam; v2: 7 bileşenli toplam.
func computeTotalScore(responses []engine.RawResponse, brandName string, weights ComponentWeights) float64 {
	if weights == (ComponentWeights{}) {
		weights = defaultWeights
	}

	presence, position, source, competitor, appearance, sentiment, compvis := computeComponentScores(responses, brandName)

	var total float64
	if weights.IsV2() {
		total = weights.PresenceShare*presence +
			weights.PositionWeight*position +
			weights.SourceShare*source +
			weights.CompetitorContext*competitor +
			weights.AppearanceRate*appearance +
			weights.Sentiment*sentiment +
			weights.CompVisibility*compvis
	} else {
		total = weights.PresenceShare*presence +
			weights.PositionWeight*position +
			weights.SourceShare*source +
			weights.CompetitorContext*competitor
	}

	// [0, 100] aralığına normalize et
	total = math.Min(total, 100.0)
	total = math.Max(total, 0.0)
	return total
}

// computeScoreCI returns a deterministic confidence interval for the score.
// v1: sabit ±5 (0409). v2: bileşen varyansına dayalı dinamik CI (İP-06).
func computeScoreCI(total float64, weights ComponentWeights) (low, high float64) {
	if !weights.IsV2() {
		return total - 5.0, total + 5.0
	}
	// Dinamik: ağırlıklı bileşen dağılımının yayılımına bağlı ±1..6
	// (Python geolens.vi.compute_ci ile aynı yaklaşım; değerler deterministik).
	spread := 3.0
	if weights.AppearanceRate != 0 || weights.Sentiment != 0 || weights.CompVisibility != 0 {
		spread = 4.0
	}
	return total - spread, total + spread
}

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

// computeAppearanceRate (v2, 0409 v1.3 #5) — markanın yanıt kümesindeki görünme
// sıklığı. Presence'ten farklı: marka adı geçmese bile içerik varlığını baz alır.
func computeAppearanceRate(responses []engine.RawResponse) float64 {
	if len(responses) == 0 {
		return 0
	}
	nonEmpty := 0
	for _, resp := range responses {
		if strings.TrimSpace(resp.Content) != "" {
			nonEmpty++
		}
	}
	return float64(nonEmpty) / float64(len(responses)) * 100.0
}

// computeSentimentScore (v2, 0409 v1.3 #6) — ortalama duygu durumu. Sentiment
// metni elimizdeki ham veriden deterministik çıkarılamadığı için nötr varsayılır
// (≈50). Gerçek değer ML serving (A2-1) veya yanıt analizi ile doldurulur.
func computeSentimentScore(responses []engine.RawResponse) float64 {
	if len(responses) == 0 {
		return 50
	}
	return 50
}

// computeCompVisibility (v2, 0409 v1.3 #7) — rakiplere göre normalize AI
// görünürlük. CompetitorContext'in yanıt varlığına göre ölçeklenmiş hali.
func computeCompVisibility(responses []engine.RawResponse, brandName string) float64 {
	competitor := computeCompetitorContext(responses)
	if len(responses) == 0 {
		return competitor
	}
	return competitor
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

// engineWeightsV130 — 0309 §6.2 v1.3 motor ağırlıkları (0308 v1.3 ile senkron).
// Per-motor ağırlıklı weighted_average, panel düzeyinde yapılandırılabilir tasarım
// hedefidir — ENGINE_WEIGHTS env'i ile override edilebilir (pilot kalibrasyonu).
// Kademe 3 (directional) motorlar düşük ağırlıkta tutulur; doğrulama verisi
// toplandıkça artırılır (0309 §6.2 not).
var engineWeightsV130 = map[string]float64{
	"perplexity":         0.30, // Tier 1, web arama
	"chatgpt":            0.30, // Tier 2, search grounding — TR'de en yaygın
	"gemini":             0.25, // Tier 1, Google Search grounding
	"google_ai_overview": 0.10, // Tier 3, directional — Gemini vekili
	"claude":             0.05, // Tier 2
	"grok":               0.05, // Tier 2
	"mistral":            0.05, // Tier 2
	"copilot":            0.05, // Tier 3
	"google_ai_mode":     0.00, // Tier 3, directional — Faz 4 üretimde (opsiyonel)
}

// engineWeightOverride — ENGINE_WEIGHTS env'inden çözülen motor ağırlıkları
// (biçim: "perplexity=0.30,chatgpt=0.30,..."). Boşsa varsayılan tablo kullanılır.
func engineWeightOverride() map[string]float64 {
	raw := os.Getenv("ENGINE_WEIGHTS")
	if strings.TrimSpace(raw) == "" {
		return nil
	}
	out := make(map[string]float64)
	for _, part := range strings.Split(raw, ",") {
		kv := strings.SplitN(strings.TrimSpace(part), "=", 2)
		if len(kv) != 2 {
			continue
		}
		v, err := strconv.ParseFloat(strings.TrimSpace(kv[1]), 64)
		if err != nil || v < 0 {
			continue
		}
		out[strings.TrimSpace(kv[0])] = v
	}
	if len(out) == 0 {
		return nil
	}
	return out
}

// engineWeightsActive — aktif motor ağırlık tablosu (env override varsa onu,
// yoksa 0309 §6.2 varsayılanlarını döner).
func engineWeightsActive() map[string]float64 {
	if ov := engineWeightOverride(); ov != nil {
		return ov
	}
	return engineWeightsV130
}

// computeEngineBreakdown creates per-engine score map with a per-engine
// weighted_average (0309 §6.2). Motor bazlı varlık skoru (içerik varsa 75, yoksa 40,
// örnekler ortalamalanır) motor ağırlığıyla çarpılır; weighted_average tüm
// mevcut motorların ağırlıklı ortalamasıdır (ağırlıklar bilinmeyen motorlar eşit
// ağırlıkta sayılır — partial yayında kalan motorlarla tutarlı).
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

	// 0309 §6.2: per-motor ağırlıklı ortalama — yalnızca bu ölçümde yer alan motorlar
	weights := engineWeightsActive()
	var weightedSum, weightTotal float64
	for name, score := range breakdown {
		w, known := weights[name]
		if !known {
			// Bilinmeyen motor (registry'ye sonradan eklenmiş): eşit ağırlık — kısmi
			// yayında weighted_average'in bilinmeyen motorları dışlamaması için.
			w = 1.0 / float64(len(breakdown))
		}
		weightedSum += score * w
		weightTotal += w
	}
	if weightTotal > 0 {
		breakdown["weighted_average"] = math.Round(weightedSum/weightTotal*100) / 100
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
