// Package contentgeo provides the core engine for Content GEO (FR-E5, FR-E6).
package contentgeo

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

// Engine provides content GEO analysis logic.
type Engine struct {
	pool *db.Pool
}

// NewEngine creates a new content GEO engine.
func NewEngine(pool *db.Pool) *Engine {
	return &Engine{pool: pool}
}

// ContentGapResult represents a content gap analysis result.
type ContentGapResult struct {
	ID             string    `json:"id"`
	BrandID        string    `json:"brand_id"`
	GapType        string    `json:"gap_type"`
	GapScore       float64   `json:"gap_score"`
	Description    string    `json:"description"`
	Recommendation string    `json:"recommendation"`
	Priority       string    `json:"priority"`
	AnalyzedAt     time.Time `json:"analyzed_at"`
}

// ContentHubScore represents the content hub score for a brand.
type ContentHubScore struct {
	BrandID         string  `json:"brand_id"`
	Overall         float64 `json:"overall"`
	TopicCoverage   float64 `json:"topic_coverage"`
	SourceDiversity float64 `json:"source_diversity"`
	AuthorityScore  float64 `json:"authority_score"`
	OpportunityGap  float64 `json:"opportunity_gap"`
	Grade           string  `json:"grade"`
}

type citationSource struct {
	Domain        string
	CitationCount int
}

// TopicCluster represents a topic cluster recommendation.
type TopicCluster struct {
	ID               string  `json:"id"`
	BrandID          string  `json:"brand_id"`
	TopicName        string  `json:"topic_name"`
	OpportunityScore float64 `json:"opportunity_score"`
	Relevance        string  `json:"relevance"`
	Recommendation   string  `json:"recommendation"`
	Priority         string  `json:"priority"`
}

// AnalyzeContentGap performs content gap analysis for a brand.
func (e *Engine) AnalyzeContentGap(ctx context.Context, brandID, workspaceID, tenantID string) ([]ContentGapResult, error) {
	// Get citation analysis to understand content types being used
	rows, err := e.pool.Query(ctx, `
		SELECT DISTINCT ON (c.source_domain) c.source_domain, c.citation_count, c.last_cited_at
		FROM measure.citations c
		WHERE c.tenant_id = $1 AND c.brand_id = $2
		ORDER BY c.source_domain, c.last_cited_at DESC
		LIMIT 100
	`, tenantID, brandID)
	if err != nil {
		return nil, fmt.Errorf("citation sorgu: %w", err)
	}
	defer rows.Close()

	var sources []citationSource
	for rows.Next() {
		var s citationSource
		var lastCited *time.Time
		if err := rows.Scan(&s.Domain, &s.CitationCount, &lastCited); err != nil {
			slog.Warn("citation satır okuma hatası", "error", err)
			continue
		}
		sources = append(sources, s)
	}
	if rows.Err() != nil {
		return nil, fmt.Errorf("rows iterasyon: %w", rows.Err())
	}

	// Analyze content gaps from citation patterns
	gaps := e.identifyGaps(sources)

	// Save results
	for _, g := range gaps {
		g.ID = id.New()
		g.BrandID = brandID
		g.AnalyzedAt = time.Now()
		g.Priority = "medium"

		// Determine priority based on gap score
		if g.GapScore > 0.7 {
			g.Priority = "high"
		} else if g.GapScore < 0.3 {
			g.Priority = "low"
		}

		_, saveErr := e.pool.Exec(ctx, `
			INSERT INTO content.gap_analyses
				(id, brand_id, gap_type, gap_score, description, recommendation, priority, tenant_id, workspace_id, analyzed_at)
			VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
		`, g.ID, brandID, g.GapType, g.GapScore, g.Description, g.Recommendation,
			g.Priority, tenantID, workspaceID, g.AnalyzedAt)
		if saveErr != nil {
			slog.Warn("content gap kaydetme hatası", "error", saveErr)
		}
	}

	metrics.ContentGEOAnalysesCompleted.WithLabelValues(tenantID).Inc()
	slog.Info("content gap analizi tamamlandı", "brand", brandID, "gaps", len(gaps))

	return gaps, nil
}

// identifyGaps identifies content gaps from citation sources.
func (e *Engine) identifyGaps(sources []citationSource) []ContentGapResult {
	var gaps []ContentGapResult

	domainTypes := map[string]string{
		"blog":     "Blog/Makale",
		"product":  "Ürün sayfası",
		"faq":      "FAQ/SSS",
		"news":     "Haber/Basın",
		"category": "Kategori sayfası",
	}

	for dt, label := range domainTypes {
		count := 0
		for _, s := range sources {
			if strings.Contains(s.Domain, dt) {
				count += s.CitationCount
			}
		}

		gap := 1.0 - float64(count)/100.0
		if gap < 0 {
			gap = 0
		}

		if gap > 0.5 {
			gaps = append(gaps, ContentGapResult{
				GapType:        dt,
				GapScore:       gap,
				Description:    fmt.Sprintf("%s türü içerik eksik veya yetersiz alıntılanıyor", label),
				Recommendation: fmt.Sprintf("%s içerik sayısı ve kalitesi artırılmalı", label),
			})
		}
	}

	// Add general recommendation if no specific gaps found
	if len(gaps) == 0 {
		gaps = append(gaps, ContentGapResult{
			GapType:        "general",
			GapScore:       0.3,
			Description:    "Genel içerik kapsamı yeterli, küçük iyileştirmeler mümkün",
			Recommendation: "Mevcut içerik stratejisi korunmalı, düzenli güncellemeler yapılmalı",
		})
	}

	return gaps
}

// GetContentHubScore calculates the content hub score for a brand.
func (e *Engine) GetContentHubScore(ctx context.Context, brandID, workspaceID, tenantID string) (*ContentHubScore, error) {
	var topicCount, sourceCount int
	var avgAuthority float64

	err := e.pool.QueryRow(ctx, `
		SELECT COUNT(DISTINCT gap_type), COALESCE(AVG(gap_score), 0)
		FROM content.gap_analyses
		WHERE brand_id = $1 AND tenant_id = $2
	`, brandID, tenantID).Scan(&topicCount, &avgAuthority)
	if err != nil {
		slog.Warn("content hub sorgu hatası", "error", err)
	}

	err = e.pool.QueryRow(ctx, `
		SELECT COUNT(DISTINCT source_domain)
		FROM measure.citations
		WHERE brand_id = $1 AND tenant_id = $2
	`, brandID, tenantID).Scan(&sourceCount)
	if err != nil {
		slog.Warn("kaynak çeşitlilik sorgu hatası", "error", err)
	}

	topicCoverage := float64(topicCount) / 7.0 * 100.0 // 7 possible types
	if topicCoverage > 100 {
		topicCoverage = 100
	}

	sourceDiversity := float64(sourceCount) / 10.0 * 100.0
	if sourceDiversity > 100 {
		sourceDiversity = 100
	}

	oppGap := 100.0 - (topicCoverage*0.4 + sourceDiversity*0.3 + avgAuthority*0.3)

	overall := 100.0 - oppGap
	if overall < 0 {
		overall = 0
	}

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

	return &ContentHubScore{
		BrandID:         brandID,
		Overall:         overall,
		TopicCoverage:   topicCoverage,
		SourceDiversity: sourceDiversity,
		AuthorityScore:  avgAuthority * 100,
		OpportunityGap:  oppGap,
		Grade:           grade,
	}, nil
}
