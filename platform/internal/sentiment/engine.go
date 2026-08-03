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
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/metrics"
)

// Engine provides sentiment analysis and hallucination detection logic.
type Engine struct {
	pool *db.Pool
}

// NewEngine creates a new sentiment engine.
func NewEngine(pool *db.Pool) *Engine {
	return &Engine{pool: pool}
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

	type rawResp struct {
		ID         string
		EngineName string
		Content    string
		CreatedAt  time.Time
	}

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
		combined := ""
		for _, r := range responses {
			combined += r.Content + " "
		}

		// Simple rule-based sentiment analysis (MVP)
		result := e.analyzeText(engine, brandID, combined)
		results = append(results, result)

		// Save to DB
		e.saveSentimentResult(ctx, tenantID, workspaceID, brandID, engine, result)
	}

	metrics.SentimentAnalysesCompleted.WithLabelValues(tenantID).Inc()
	slog.Info("sentiment analizi tamamlandı", "brand", brandID, "engines", len(results))

	return results, nil
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
		SELECT rr.id, rr.engine_name, rr.content_text, COALESCE(b.name, ''), COALESCE(b.website_url, '')
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

	type checkTarget struct {
		ID         string
		EngineName string
		Content    string
		BrandName  string
		WebsiteURL string
	}

	var targets []checkTarget
	for rows.Next() {
		var t checkTarget
		if err := rows.Scan(&t.ID, &t.EngineName, &t.Content, &t.BrandName, &t.WebsiteURL); err != nil {
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

	var results []HallucinationResult
	for _, t := range targets {
		halls := e.checkHallucinations(t.Content, t.BrandName, t.EngineName, brandID)
		results = append(results, halls...)
	}

	// Save results to DB
	for _, h := range results {
		e.saveHallucination(ctx, tenantID, workspaceID, brandID, h)
	}

	metrics.HallucinationsDetected.WithLabelValues(tenantID, "").Add(float64(len(results)))
	slog.Info("hallüsinasyon tespiti tamamlandı", "brand", brandID, "count", len(results))

	return results, nil
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
