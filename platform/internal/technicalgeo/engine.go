// Package technicalgeo provides the core engine for Technical GEO (FR-B6, FR-B7, FR-E7).
package technicalgeo

import (
	"context"
	"fmt"
	"log/slog"
	"strings"
	"time"

	"github.com/geolens/platform/internal/id"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/metrics"
)

// Engine provides technical GEO analysis logic.
type Engine struct {
	pool *db.Pool
}

// NewEngine creates a new technical GEO engine.
func NewEngine(pool *db.Pool) *Engine {
	return &Engine{pool: pool}
}

// BotAnalysisResult represents LLM bot access analysis results.
type BotAnalysisResult struct {
	ID            string    `json:"id"`
	BrandID       string    `json:"brand_id"`
	BotName       string    `json:"bot_name"`
	URL           string    `json:"url"`
	IsBlocked     bool      `json:"is_blocked"`
	RobotsTxtRule string    `json:"robots_txt_rule"`
	GESScore      float64   `json:"ges_score"`
	AnalyzedAt    time.Time `json:"analyzed_at"`
}

// SchemaAnalysisResult represents schema.org analysis results.
type SchemaAnalysisResult struct {
	ID             string    `json:"id"`
	BrandID        string    `json:"brand_id"`
	SchemaType     string    `json:"schema_type"`
	IsPresent      bool      `json:"is_present"`
	SchemaScore    float64   `json:"schema_score"`
	Recommendation string    `json:"recommendation"`
	AnalyzedAt     time.Time `json:"analyzed_at"`
}

// TechnicalGEOScore represents the overall technical GEO score.
type TechnicalGEOScore struct {
	BrandID     string  `json:"brand_id"`
	Overall     float64 `json:"overall"`
	BotScore    float64 `json:"bot_score"`
	SchemaScore float64 `json:"schema_score"`
	SourceShare float64 `json:"source_share"`
	Grade       string  `json:"grade"`
}

// AnalyzeBotAccess analyzes LLM bot access for a brand.
func (e *Engine) AnalyzeBotAccess(ctx context.Context, brandID, url, workspaceID, tenantID string) (*BotAnalysisResult, error) {
	if url == "" {
		err := e.pool.QueryRow(ctx, `SELECT website_url FROM config.brands WHERE id = $1 AND tenant_id = $2`, brandID, tenantID).Scan(&url)
		if err != nil {
			return nil, fmt.Errorf("marka URL bulunamadı: %w", err)
		}
	}

	// Simulate bot access check for each known LLM bot
	bots := []struct {
		Name      string
		UserAgent string
	}{
		{"GPTBot", "Mozilla/5.0 GPTBot"},
		{"MistralAI", "MistralAI"},
		{"Google-Extended", "Google-Extended"},
		{"PerplexityBot", "PerplexityBot"},
		{"Claude-Web", "Mozilla/5.0 AppleWebKit/537.36 Claude-Web"},
		{"CCBot", "CCBot"},
		{"FacebookBot", "facebookexternalhit"},
		{"Bytespider", "Bytespider"},
		{"Applebot", "Applebot"},
	}

	for _, bot := range bots {
		isBlocked := false
		rule := "Allow"

		// Check audit results for robots.txt info
		var robotsTxt string
		err := e.pool.QueryRow(ctx, `
			SELECT COALESCE(a.details->>'robots_txt', '')
			FROM governance.audit_results a
			WHERE a.brand_id = $1 AND a.tenant_id = $2
			ORDER BY a.created_at DESC LIMIT 1
		`, brandID, tenantID).Scan(&robotsTxt)
		if err == nil && robotsTxt != "" {
			// Simple check: if bot name appears in robots.txt with Disallow
			isBlocked = strings.Contains(robotsTxt, "Disallow: /") && strings.Contains(robotsTxt, bot.Name)
			if isBlocked {
				rule = "Disallow"
			}
		}

		// Calculate GES (Genel Erişim Skoru)
		ges := 100.0
		if isBlocked {
			ges = 0.0
		}

		result := &BotAnalysisResult{
			ID:            id.New(),
			BrandID:       brandID,
			BotName:       bot.Name,
			URL:           url,
			IsBlocked:     isBlocked,
			RobotsTxtRule: rule,
			GESScore:      ges,
			AnalyzedAt:    time.Now(),
		}

		// Save to DB
		_, saveErr := e.pool.Exec(ctx, `
			INSERT INTO technical.bot_analyses
				(id, brand_id, bot_name, url, is_blocked, robots_txt_rule, ges_score, tenant_id, workspace_id, analyzed_at)
			VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
		`, result.ID, result.BrandID, result.BotName, result.URL, result.IsBlocked,
			result.RobotsTxtRule, result.GESScore, tenantID, workspaceID, result.AnalyzedAt)
		if saveErr != nil {
			slog.Warn("bot analiz kaydetme hatası", "error", saveErr)
		}
	}

	metrics.TechnicalGEOAnalysesCompleted.WithLabelValues(tenantID).Inc()
	slog.Info("bot erişim analizi tamamlandı", "brand", brandID, "bots", len(bots))

	// Return the first bot's analysis as summary
	return &BotAnalysisResult{
		BrandID:    brandID,
		URL:        url,
		AnalyzedAt: time.Now(),
	}, nil
}

// AnalyzeSchema analyzes schema.org usage for a brand.
func (e *Engine) AnalyzeSchema(ctx context.Context, brandID, workspaceID, tenantID string) (*SchemaAnalysisResult, error) {
	schemaTypes := []string{"Product", "FAQ", "Organization", "Article", "BreadcrumbList",
		"HowTo", "LocalBusiness", "Review", "Service", "SoftwareApplication"}

	for _, st := range schemaTypes {
		isPresent := false
		score := 0.0

		// Check if schema type is referenced in audit data
		var details string
		err := e.pool.QueryRow(ctx, `
			SELECT COALESCE(a.details->>'structured_data', '')
			FROM governance.audit_results a
			WHERE a.brand_id = $1 AND a.tenant_id = $2
			ORDER BY a.created_at DESC LIMIT 1
		`, brandID, tenantID).Scan(&details)
		if err == nil && details != "" {
			isPresent = strings.Contains(details, st)
			if isPresent {
				score = 100.0
			}
		}

		rec := ""
		if !isPresent {
			switch st {
			case "Product":
				rec = "Ürün sayfalarına Product schema eklenmeli"
			case "FAQ":
				rec = "SSS sayfasına FAQ schema eklenmeli"
			case "Organization":
				rec = "Kurumsal bilgilere Organization schema eklenmeli"
			case "Article":
				rec = "Blog içeriklerine Article schema eklenmeli"
			default:
				rec = fmt.Sprintf("%s schema tipi değerlendirilmeli", st)
			}
		}

		result := &SchemaAnalysisResult{
			ID:             id.New(),
			BrandID:        brandID,
			SchemaType:     st,
			IsPresent:      isPresent,
			SchemaScore:    score,
			Recommendation: rec,
			AnalyzedAt:     time.Now(),
		}

		_, saveErr := e.pool.Exec(ctx, `
			INSERT INTO technical.schema_analyses
				(id, brand_id, schema_type, is_present, schema_score, recommendation, tenant_id, workspace_id, analyzed_at)
			VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
		`, result.ID, result.BrandID, result.SchemaType, result.IsPresent,
			result.SchemaScore, result.Recommendation, tenantID, workspaceID, result.AnalyzedAt)
		if saveErr != nil {
			slog.Warn("schema analiz kaydetme hatası", "error", saveErr)
		}
	}

	metrics.TechnicalGEOAnalysesCompleted.WithLabelValues(tenantID).Inc()
	slog.Info("schema analizi tamamlandı", "brand", brandID, "types", len(schemaTypes))

	return &SchemaAnalysisResult{
		BrandID:    brandID,
		AnalyzedAt: time.Now(),
	}, nil
}

// GetScore calculates the overall technical GEO score.
func (e *Engine) GetScore(ctx context.Context, brandID, workspaceID, tenantID string) (*TechnicalGEOScore, error) {
	var botScore, schemaScore float64
	var botCount, schemaCount int

	// Average bot GES score
	err := e.pool.QueryRow(ctx, `
		SELECT COALESCE(AVG(ges_score), 0), COUNT(*) FROM technical.bot_analyses
		WHERE brand_id = $1 AND tenant_id = $2
	`, brandID, tenantID).Scan(&botScore, &botCount)
	if err != nil {
		slog.Warn("bot skor sorgu hatası", "error", err)
	}

	// Average schema score
	err = e.pool.QueryRow(ctx, `
		SELECT COALESCE(AVG(schema_score), 0), COUNT(*) FROM technical.schema_analyses
		WHERE brand_id = $1 AND tenant_id = $2
	`, brandID, tenantID).Scan(&schemaScore, &schemaCount)
	if err != nil {
		slog.Warn("schema skor sorgu hatası", "error", err)
	}

	overall := (botScore*0.4 + schemaScore*0.4) // + sourceShare*0.2 in future

	grade := "F"
	switch {
	case overall >= 90:
		grade = "A"
	case overall >= 75:
		grade = "B"
	case overall >= 60:
		grade = "C"
	case overall >= 40:
		grade = "D"
	}

	return &TechnicalGEOScore{
		BrandID:     brandID,
		Overall:     overall,
		BotScore:    botScore,
		SchemaScore: schemaScore,
		Grade:       grade,
	}, nil
}
