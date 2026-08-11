// Package sentiment provides the core engine for sentiment analysis and hallucination detection.
package sentiment

import (
	"context"
	"database/sql"
	"fmt"
	"log/slog"
	"strings"
	"time"

	"github.com/geolens/platform/internal/id"
	"github.com/geolens/platform/internal/ml"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/metrics"
)

// Engine provides sentiment analysis and hallucination detection logic.
type Engine struct {
	pool *db.Pool
	// ml — opsiyonel ML serving istemcisi (0421 A0-3). ML_SERVING_URL boşsa
	// nil kalır ve tüm analizler kural tabanlı çalışır (0421 M-4 fallback).
	ml *ml.Client

	// breaker — serving ardışık hatasında ML çağrılarını askıya alan ortak devre
	// kesici (ml.CircuitBreaker). Sentiment ve measure servisleri paylaşır; serving
	// kapalıyken 8 motor × ML_TIMEOUT gecikme birikmez (0421 M-4).
	breaker *ml.CircuitBreaker
}

// NewEngine creates a new sentiment engine (kural tabanlı, ML serving yok).
func NewEngine(pool *db.Pool) *Engine {
	return NewEngineWithML(pool, nil)
}

// NewEngineWithML, ML serving client ile sentiment motoru kurar.
// mlClient nil ise (ML_SERVING_URL boş) kural tabanlı bileşenler kullanılır;
// dolu ise her analizde önce ML inference denenir, serving hatasında kural
// tabanlıya düşülür (0421 M-4).
func NewEngineWithML(pool *db.Pool, mlClient *ml.Client) *Engine {
	return &Engine{pool: pool, ml: mlClient, breaker: ml.NewCircuitBreakerFor("sentiment", ml.DefaultCooldown)}
}

// rawResp — ham AI yanıtı (AnalyzeSentiment sorgusundan).
type rawResp struct {
	ID         string
	EngineName string
	Content    string
	CreatedAt  time.Time
}

// checkTarget — hallüsinasyon kontrolü hedefi (raw response + marka profili).
// Prompt alanı (051_raw_responses_prompt.sql): cross-source karşılaştırması
// yalnızca aynı prompt'a ait yanıtlar arasında yapılır (yanlış pozitif riski).
type checkTarget struct {
	ID         string
	EngineName string
	Content    string
	Prompt     string
	BrandName  string
	WebsiteURL string
}

// weightedProbs — ağırlıklı ortalama girdisi: softmax olasılıkları + ağırlık.
type weightedProbs struct {
	probs  [3]float64
	weight float64
}

// SentimentResult represents the result of a sentiment analysis.
type SentimentResult struct {
	ID               string          `json:"id"`
	BrandID          string          `json:"brand_id"`
	EngineName       string          `json:"engine_name"`
	OverallSentiment float64         `json:"overall_sentiment"`
	PositiveScore    float64         `json:"positive_score"`
	NeutralScore     float64         `json:"neutral_score"`
	NegativeScore    float64         `json:"negative_score"`
	MentionCount     int             `json:"mention_count"`
	Mentions         []MentionResult `json:"mentions,omitempty"`
	AnalyzedAt       time.Time       `json:"analyzed_at"`
}

// MentionResult represents a single mention with its sentiment.
type MentionResult struct {
	Text      string  `json:"text"`
	Sentiment string  `json:"sentiment"` // positive / neutral / negative
	Score     float64 `json:"score"`
}

// HallucinationResult represents a detected hallucination.
type HallucinationResult struct {
	ID                string    `json:"id"`
	BrandID           string    `json:"brand_id"`
	EngineName        string    `json:"engine_name"`
	HallucinationType string    `json:"hallucination_type"` // T1-T5
	Severity          string    `json:"severity"`           // critical / high / medium / low
	Description       string    `json:"description"`
	Confidence        float64   `json:"confidence"`
	ReplayID          *string   `json:"replay_id,omitempty"`
	CreatedAt         time.Time `json:"created_at"`
}

// AnalyzeSentiment performs sentiment analysis for a brand across all engine responses.
func (e *Engine) AnalyzeSentiment(ctx context.Context, brandID, workspaceID, tenantID, prompt string) ([]SentimentResult, error) {
	// Get raw responses for the brand
	rows, err := e.pool.Query(ctx, `
		SELECT rr.id, rr.engine_name, rr.content_text, rr.created_at
		FROM measure.raw_responses rr
		WHERE rr.tenant_id = $1 AND rr.brand_id = $2
		ORDER BY rr.created_at DESC
		LIMIT 50
	`, tenantID, brandID)
	if err != nil {
		return nil, fmt.Errorf("raw response sorgu: %w", err)
	}
	defer rows.Close()

	var rawResponses []rawResp
	for rows.Next() {
		var r rawResp
		if err := rows.Scan(&r.ID, &r.EngineName, &r.Content, &r.CreatedAt); err != nil {
			slog.Warn("raw response satır okuma hatası", "error", err)
			continue
		}
		rawResponses = append(rawResponses, r)
	}
	if rows.Err() != nil {
		return nil, fmt.Errorf("rows iterasyon: %w", rows.Err())
	}

	if len(rawResponses) == 0 {
		return nil, nil
	}

	// Group by engine and analyze
	engineMap := make(map[string][]rawResp)
	for _, r := range rawResponses {
		engineMap[r.EngineName] = append(engineMap[r.EngineName], r)
	}

	var results []SentimentResult
	for engine, responses := range engineMap {
		// 0421 M-4: per-response ML serving; serving yok/hatalı ise kural tabanlı.
		result := e.analyzeWithML(ctx, engine, brandID, responses)
		results = append(results, result)

		// Save to DB
		e.saveSentimentResult(ctx, tenantID, workspaceID, brandID, engine, result)
	}

	metrics.SentimentAnalysesCompleted.WithLabelValues(tenantID).Inc()
	slog.Info("sentiment analizi tamamlandı", "brand", brandID, "engines", len(results))

	return results, nil
}

// analyzeWithML — 0421 M-4 uyumlu per-response ML sentiment analizi.
// Her raw response serving'e AYRI gönderilir (128 token kırpma sorunu çözülür;
// uzun birleşik metin yerine her yanıt kendi bütünlüğünde analiz edilir) ve
// sonuçlar kelime sayısıyla ağırlıklı ortalama ile birleştirilir. Serving yok,
// cooldown'da veya hiçbir çağrı başarılı olmadıysa kural tabanlı analyzeText'e
// düşülür (operasyonel dayanıklılık). Kısmi başarıda (bazı yanıtlar hatalı)
// başarılı olanlar birleştirilir.
func (e *Engine) analyzeWithML(ctx context.Context, engineName, brandID string, responses []rawResp) SentimentResult {
	if e.ml == nil || e.breaker.InCooldown() {
		return e.analyzeText(engineName, brandID, combineText(responses))
	}

	items := make([]weightedProbs, 0, len(responses))
	failed := 0
	for _, r := range responses {
		if ctx.Err() != nil {
			slog.Warn("sentiment: ML analizi iptal edildi, kısmi sonuç birleştiriliyor", "error", ctx.Err())
			break
		}
		text := strings.TrimSpace(r.Content)
		if text == "" {
			continue
		}
		pred, err := e.ml.PredictSentiment(ctx, text, "")
		if err != nil {
			failed++
			slog.Warn("sentiment: per-response ML çağrısı başarısız",
				"error", err, "engine", engineName, "response_id", r.ID)
			if len(items) == 0 {
				// Henüz başarı yok — serving erişilemez görünüyor. Cooldown başlat ve
				// kalan yanıtlar için timeout beklemeyi önle (en kötü 50×ML_TIMEOUT birikimi).
				e.breaker.Fail()
				break
			}
			continue
		}
		// Kelime sayısı ağırlığı: uzun yanıt daha fazla bilgi taşır.
		items = append(items, weightedProbs{
			probs:  pred.Probabilities,
			weight: float64(len(strings.Fields(text))),
		})
	}

	if len(items) == 0 {
		// Serving erişilemez görünüyor — cooldown başlat, kural tabanlıya düş.
		e.breaker.Fail()
		return e.analyzeText(engineName, brandID, combineText(responses))
	}

	e.breaker.Success()
	slog.Debug("sentiment: per-response ML sonucu", "engine", engineName,
		"responses", len(items), "failed", failed)
	return sentimentFromProbabilities(engineName, brandID, aggregateWeighted(items), len(items))
}

// aggregateWeighted, per-response olasılıklarını ağırlıklı ortalama ile birleştirir.
func aggregateWeighted(items []weightedProbs) [3]float64 {
	var acc [3]float64
	var total float64
	for _, it := range items {
		for i := 0; i < 3; i++ {
			acc[i] += it.probs[i] * it.weight
		}
		total += it.weight
	}
	if total == 0 {
		return [3]float64{}
	}
	return [3]float64{acc[0] / total, acc[1] / total, acc[2] / total}
}

// combineText — motor yanıtlarını kural tabanlı fallback için birleştirir.
func combineText(responses []rawResp) string {
	var sb strings.Builder
	for _, r := range responses {
		sb.WriteString(r.Content)
		sb.WriteByte(' ')
	}
	return sb.String()
}

// sentimentFromProbabilities, (ağırlıklı ortalama) softmax olasılıklarını
// [neg, nötr, poz] SentimentResult skorlarına çevirir. OverallSentiment aynı
// ağırlıklandırmayı kullanır: poz*1.0 + nötr*0.5 + neg*0.0.
func sentimentFromProbabilities(engineName, brandID string, probs [3]float64, mentionCount int) SentimentResult {
	return SentimentResult{
		BrandID:          brandID,
		EngineName:       engineName,
		OverallSentiment: probs[2] + probs[1]*0.5,
		PositiveScore:    probs[2],
		NeutralScore:     probs[1],
		NegativeScore:    probs[0],
		MentionCount:     mentionCount,
		AnalyzedAt:       time.Now(),
	}
}

// analyzeText performs a simple rule-based sentiment analysis.
func (e *Engine) analyzeText(engineName, brandID, text string) SentimentResult {
	positiveWords := []string{"harika", "mükemmel", "başarılı", "iyi", "güzel", "kaliteli", "güvenilir",
		"yenilikçi", "lider", "en iyi", "öncü", "sağlam", "hızlı", "kolay", "great", "excellent",
		"best", "reliable", "innovative", "leading", "outstanding", "quality"}
	negativeWords := []string{"kötü", "başarısız", "yetersiz", "sorunlu", "şikayet", "hatalı", "pahalı",
		"karmaşık", "yavaş", "zor", "bad", "poor", "failure", "expensive", "complicated",
		"slow", "issue", "problem", "complaint", "terrible"}

	lower := strings.ToLower(text)
	words := strings.Fields(lower)

	var positiveCount, negativeCount int
	for _, word := range words {
		for _, pw := range positiveWords {
			if word == pw || strings.Contains(word, pw) {
				positiveCount++
				break
			}
		}
		for _, nw := range negativeWords {
			if word == nw || strings.Contains(word, nw) {
				negativeCount++
				break
			}
		}
	}

	total := positiveCount + negativeCount
	if total == 0 {
		return SentimentResult{
			BrandID:          brandID,
			EngineName:       engineName,
			OverallSentiment: 0.5,
			PositiveScore:    0.0,
			NeutralScore:     1.0,
			NegativeScore:    0.0,
			MentionCount:     0,
			AnalyzedAt:       time.Now(),
		}
	}

	positiveScore := float64(positiveCount) / float64(total)
	negativeScore := float64(negativeCount) / float64(total)
	neutralScore := 1.0 - positiveScore - negativeScore
	if neutralScore < 0 {
		neutralScore = 0
	}

	overall := positiveScore*1.0 + neutralScore*0.5 + negativeScore*0.0

	return SentimentResult{
		BrandID:          brandID,
		EngineName:       engineName,
		OverallSentiment: overall,
		PositiveScore:    positiveScore,
		NeutralScore:     neutralScore,
		NegativeScore:    negativeScore,
		MentionCount:     total,
		AnalyzedAt:       time.Now(),
	}
}

// saveSentimentResult persists a sentiment analysis result.
func (e *Engine) saveSentimentResult(ctx context.Context, tenantID, workspaceID, brandID, engineName string, result SentimentResult) {
	_, err := e.pool.Exec(ctx, `
		INSERT INTO analysis.sentiment_scores
			(id, brand_id, engine_name, overall_sentiment, positive_score, neutral_score, negative_score,
			 mention_count, tenant_id, workspace_id, created_at, analyzed_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, now(), $11)
	`, id.New(), brandID, engineName, result.OverallSentiment, result.PositiveScore,
		result.NeutralScore, result.NegativeScore, result.MentionCount, tenantID, workspaceID, result.AnalyzedAt)
	if err != nil {
		slog.Warn("sentiment kaydetme hatası", "error", err)
	}
}

// DetectHallucinations detects potential hallucinations for a brand.
func (e *Engine) DetectHallucinations(ctx context.Context, brandID, workspaceID, tenantID string) ([]HallucinationResult, error) {
	// Get raw responses with brand profile for fact-checking
	rows, err := e.pool.Query(ctx, `
		SELECT rr.id, rr.engine_name, rr.content_text, COALESCE(rr.prompt_text, ''), COALESCE(b.name, ''), COALESCE(b.website_url, '')
		FROM measure.raw_responses rr
		JOIN config.brands b ON b.id = rr.brand_id
		WHERE rr.tenant_id = $1 AND rr.brand_id = $2
		ORDER BY rr.created_at DESC
		LIMIT 20
	`, tenantID, brandID)
	if err != nil {
		return nil, fmt.Errorf("hallüsinasyon sorgu: %w", err)
	}
	defer rows.Close()

	var targets []checkTarget
	for rows.Next() {
		var t checkTarget
		if err := rows.Scan(&t.ID, &t.EngineName, &t.Content, &t.Prompt, &t.BrandName, &t.WebsiteURL); err != nil {
			slog.Warn("hallüsinasyon satır okuma hatası", "error", err)
			continue
		}
		targets = append(targets, t)
	}
	if rows.Err() != nil {
		return nil, fmt.Errorf("rows iterasyon: %w", rows.Err())
	}

	if len(targets) == 0 {
		return nil, nil
	}

	// 0421 M-4: cross-source (A2-4) en az 2 yanıt gerektirir. Serving yapılandırılmış
	// ve erişilebilirse önce serving cross_source_check çağrılır ve bulguları T1-T4
	// kural sonuçlarıyla BİRLEŞTİRİLİR (kural seti T2/T4 sinyallerini de korur);
	// tek yanıtta, serving yokken veya serving hatasında yalnızca T1-T4 kuralları kullanılır.
	// 051_raw_responses_prompt.sql: cross-source karşılaştırması YALNIZCA aynı prompt'a
	// ait yanıtlar arasında yapılır (prompt_text gruplaması) — farklı prompt'lardan gelen
	// yanıtların birbiriyle çelişmesi yanlış pozitif üretir; tek yanıtlı gruplar
	// (örn. henüz tüm motorlar yanıtlamamış) kurallarla değerlendirilir.
	results := e.applyMLCrossSource(ctx, e.ruleBasedHallucinations(targets, brandID), targets, brandID)

	// Save results to DB
	for _, h := range results {
		e.saveHallucination(ctx, tenantID, workspaceID, brandID, h)
	}

	metrics.HallucinationsDetected.WithLabelValues(tenantID, "").Add(float64(len(results)))
	slog.Info("hallüsinasyon tespiti tamamlandı", "brand", brandID, "count", len(results))

	return results, nil
}

// groupByPrompt — hedefleri prompt_text'e göre gruplar (051_raw_responses_prompt.sql).
// Cross-source karşılaştırması farklı prompt'lardan gelen yanıtlara uygulanmaz;
// böylece "aynı marka, farklı soru" senaryolarında yanlış pozitif üretilmez.
// Grup sırası ilk görülme sırasına göredir (deterministik).
func groupByPrompt(targets []checkTarget) [][]checkTarget {
	order := make([]string, 0, len(targets))
	groups := make(map[string][]checkTarget, len(targets))
	for _, t := range targets {
		if _, ok := groups[t.Prompt]; !ok {
			order = append(order, t.Prompt)
		}
		groups[t.Prompt] = append(groups[t.Prompt], t)
	}
	out := make([][]checkTarget, 0, len(order))
	for _, p := range order {
		out = append(out, groups[p])
	}
	return out
}

// applyMLCrossSource — hedefleri prompt'a göre gruplar ve en az 2 yanıtlı her
// grupta serving cross_source_check çağrısı yapar; bulguları base (kural sonuçları)
// ile birleştirir. Tek yanıtlı gruplar yalnızca kurallarla değerlendirilir.
// Serving hatasında cooldown başlatılır ve kalan gruplar için ML denenmez
// (timeout birikimini önler — sentiment devre kesicisiyle aynı davranış).
// Erişilebilir / yapılandırılmış serving yoksa base aynen döner.
func (e *Engine) applyMLCrossSource(ctx context.Context, base []HallucinationResult, targets []checkTarget, brandID string) []HallucinationResult {
	results := base
	if e.ml == nil || e.breaker.InCooldown() {
		return results
	}
	mlAttempted, mlFailed := false, false
	for _, group := range groupByPrompt(targets) {
		if len(group) < 2 {
			continue // tek yanıtlı grupta cross-source yok — kurallar yeterli
		}
		mlAttempted = true
		mlResults, mlErr := e.detectHallucinationsWithML(ctx, group, brandID)
		if mlErr != nil {
			slog.Warn("hallüsinasyon: serving çağrısı başarısız, T1-T4 kurallarına düşülüyor", "error", mlErr)
			e.breaker.Fail()
			mlFailed = true
			break
		}
		results = mergeHallucinationResults(results, mlResults)
	}
	if mlAttempted && !mlFailed {
		e.breaker.Success()
		slog.Debug("hallüsinasyon: cross-source ML + kurallar birleştirildi", "brand", brandID, "findings", len(results))
	}
	return results
}

// mergeHallucinationResults — ML cross-source bulguları ile T1-T4 kural sonuçlarını
// birleştirir; aynı (tip, açıklama) çifti bir kez kaydedilir.
func mergeHallucinationResults(a, b []HallucinationResult) []HallucinationResult {
	seen := make(map[string]struct{}, len(a)+len(b))
	out := make([]HallucinationResult, 0, len(a)+len(b))
	for _, h := range append(append([]HallucinationResult{}, a...), b...) {
		key := h.HallucinationType + "|" + h.Description
		if _, ok := seen[key]; ok {
			continue
		}
		seen[key] = struct{}{}
		out = append(out, h)
	}
	return out
}

// detectHallucinationsWithML — serving cross-source tespitini çağırır ve sonucu
// HallucinationResult'a çevirir. Hata dönerse çağıran fallback'e düşer (0421 M-4).
func (e *Engine) detectHallucinationsWithML(ctx context.Context, targets []checkTarget, brandID string) ([]HallucinationResult, error) {
	responses := make([]ml.HallucinationResponse, 0, len(targets))
	for _, t := range targets {
		responses = append(responses, ml.HallucinationResponse{ID: t.ID, Engine: t.EngineName, Text: t.Content})
	}
	findings, err := e.ml.DetectHallucinations(ctx, responses)
	if err != nil {
		return nil, err
	}
	results := make([]HallucinationResult, 0, len(findings))
	now := time.Now()
	for _, f := range findings {
		results = append(results, HallucinationResult{
			BrandID:           brandID,
			EngineName:        f.Engine,
			HallucinationType: f.Type,
			Severity:          f.Severity,
			Description:       f.Description,
			Confidence:        f.Confidence,
			CreatedAt:         now,
		})
	}
	return results, nil
}

// ruleBasedHallucinations — mevcut T1-T4 kural seti (serving yok/hatalı fallback).
func (e *Engine) ruleBasedHallucinations(targets []checkTarget, brandID string) []HallucinationResult {
	var results []HallucinationResult
	for _, t := range targets {
		results = append(results, e.checkHallucinations(t.Content, t.BrandName, t.EngineName, brandID)...)
	}
	return results
}

// checkHallucinations runs rule-based checks against response content.
func (e *Engine) checkHallucinations(content, brandName, engineName, brandID string) []HallucinationResult {
	var results []HallucinationResult
	lower := strings.ToLower(content)

	// T1: Wrong factual claim about the brand
	if brandName != "" && !strings.Contains(lower, strings.ToLower(brandName)) {
		results = append(results, HallucinationResult{
			BrandID:           brandID,
			EngineName:        engineName,
			HallucinationType: "T1",
			Severity:          "high",
			Description:       fmt.Sprintf("'%s' marka adı yanıtta geçmiyor", brandName),
			Confidence:        0.6,
		})
	}

	// T2: Hallucinated citation/source
	if strings.Contains(lower, "kaynak") || strings.Contains(lower, "source") ||
		strings.Contains(lower, "according to") {
		// Potential hallucinated citation - flag for review
		results = append(results, HallucinationResult{
			BrandID:           brandID,
			EngineName:        engineName,
			HallucinationType: "T2",
			Severity:          "medium",
			Description:       "AI yanıtı kaynak/citation referansı içeriyor — doğrulama gerekli",
			Confidence:        0.4,
		})
	}

	// T3: Fabricated statistic or number
	// Check for numeric claims without clear source attribution
	hasNumber := strings.ContainsAny(lower, "0123456789")
	hasPercent := strings.Contains(lower, "%") || strings.Contains(lower, "percent") || strings.Contains(lower, "yüzde")
	if hasNumber && hasPercent && !strings.Contains(lower, "kaynak") && !strings.Contains(lower, "source") {
		results = append(results, HallucinationResult{
			BrandID:           brandID,
			EngineName:        engineName,
			HallucinationType: "T3",
			Severity:          "critical",
			Description:       "AI yanıtı kaynaksız istatistik/rakam içeriyor",
			Confidence:        0.5,
		})
	}

	// T4: Negative sentiment without basis
	negativeWords := []string{"başarısız", "kötü", "şikayet", "sorun", "skandal", "dava",
		"failure", "scandal", "lawsuit", "bad", "terrible"}
	for _, nw := range negativeWords {
		if strings.Contains(lower, nw) {
			results = append(results, HallucinationResult{
				BrandID:           brandID,
				EngineName:        engineName,
				HallucinationType: "T4",
				Severity:          "medium",
				Description:       fmt.Sprintf("AI yanıtı doğrulanmamış olumsuz ifade içeriyor: '%s'", nw),
				Confidence:        0.3,
			})
			break
		}
	}

	return results
}

// saveHallucination persists a hallucination flag.
func (e *Engine) saveHallucination(ctx context.Context, tenantID, workspaceID, brandID string, h HallucinationResult) {
	hallID := id.New()
	_, err := e.pool.Exec(ctx, `
		INSERT INTO analysis.hallucination_flags
			(id, brand_id, engine_name, hallucination_type, severity, description, confidence,
			 tenant_id, workspace_id, created_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, now())
	`, hallID, brandID, h.EngineName, h.HallucinationType, h.Severity, h.Description, h.Confidence,
		tenantID, workspaceID)
	if err != nil {
		slog.Warn("hallüsinasyon kaydetme hatası", "error", err)
	}
}

// GetLatestResult returns the latest sentiment result for a brand.
func (e *Engine) GetLatestResult(ctx context.Context, brandID, tenantID string) (*SentimentResult, error) {
	var result SentimentResult
	var mentionCount int
	err := e.pool.QueryRow(ctx, `
		SELECT id, brand_id, engine_name, overall_sentiment, positive_score, neutral_score, negative_score, mention_count, analyzed_at
		FROM analysis.sentiment_scores
		WHERE brand_id = $1 AND tenant_id = $2
		ORDER BY analyzed_at DESC LIMIT 1
	`, brandID, tenantID).Scan(&result.ID, &result.BrandID, &result.EngineName,
		&result.OverallSentiment, &result.PositiveScore, &result.NeutralScore,
		&result.NegativeScore, &mentionCount, &result.AnalyzedAt)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, nil
		}
		return nil, err
	}
	result.MentionCount = mentionCount
	return &result, nil
}
