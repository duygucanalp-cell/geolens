// Package competitive provides the core engine for Competitive Gap Analysis (FR-D11).
package competitive

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"math"
	"time"

	"github.com/geolens/platform/internal/id"
	"github.com/geolens/platform/platform/db"
	"github.com/geolens/platform/platform/metrics"
)

// Engine provides competitive gap analysis logic.
type Engine struct {
	pool *db.Pool
}

// NewEngine creates a new competitive gap engine.
func NewEngine(pool *db.Pool) *Engine {
	return &Engine{pool: pool}
}

// scanRow logs and tolerates row-scan errors on COALESCE queries that always return a row.
// Hata durumunda değişken varsayılan değerinde (0) kalır — gap hesaplaması devam eder.
func scanRow(err error) {
	if err != nil {
		slog.Debug("competitive gap: satır okuma hatası", "error", err)
	}
}

// GapSnapshot represents a complete gap analysis between brand and competitor.
type GapSnapshot struct {
	ID               string     `json:"id"`
	BrandID          string     `json:"brand_id"`
	BrandName        string     `json:"brand_name"`
	CompetitorID     string     `json:"competitor_id"`
	CompetitorName   string     `json:"competitor_name"`
	VisibilityGap    *GapDetail `json:"visibility_gap,omitempty"`
	CitationGap      *GapDetail `json:"citation_gap,omitempty"`
	ContentGap       *GapDetail `json:"content_gap,omitempty"`
	TopicGap         *GapDetail `json:"topic_gap,omitempty"`
	PromptGap        *GapDetail `json:"prompt_gap,omitempty"`
	CompetitiveScore float64    `json:"competitive_score"`
	PeriodStart      string     `json:"period_start"`
	PeriodEnd        string     `json:"period_end"`
	CreatedAt        time.Time  `json:"created_at"`
}

// GapDetail represents a single gap type measurement.
type GapDetail struct {
	GapValue        float64 `json:"gap_value"`
	Normalized      float64 `json:"normalized"`
	BrandValue      float64 `json:"brand_value"`
	CompetitorValue float64 `json:"competitor_value"`
	Direction       string  `json:"direction"` // brand_ahead / competitor_ahead / equal
}

// AnalyzeAllGaps performs all 5 gap analyses between a brand and its competitors.
func (e *Engine) AnalyzeAllGaps(ctx context.Context, brandID, workspaceID, tenantID string) ([]GapSnapshot, error) {
	// Get brand name
	var brandName string
	err := e.pool.QueryRow(ctx, `SELECT name FROM config.brands WHERE id = $1 AND tenant_id = $2`, brandID, tenantID).Scan(&brandName)
	if err != nil {
		return nil, fmt.Errorf("marka bulunamadı: %w", err)
	}

	// Kullanıcı tanımlı rakipleri bul (FR-B1)
	// Öncelik: brand_competitors tablosu. Yoksa: aynı workspace'teki diğer markalar (fallback)
	rows, err := e.pool.Query(ctx, `
		SELECT b.id, b.name
		FROM config.brands b
		JOIN config.brand_competitors bc ON bc.competitor_id = b.id
		WHERE bc.brand_id = $1 AND bc.tenant_id = $2 AND b.is_active = true
		UNION
		SELECT b.id, b.name
		FROM config.brands b
		WHERE b.workspace_id = $3 AND b.tenant_id = $2 AND b.id != $1 AND b.is_active = true
		  AND NOT EXISTS (SELECT 1 FROM config.brand_competitors WHERE brand_id = $1 AND tenant_id = $2)
	`, brandID, tenantID, workspaceID)
	if err != nil {
		return nil, fmt.Errorf("rakip sorgu: %w", err)
	}
	defer rows.Close()

	type competitor struct {
		ID   string
		Name string
	}

	var competitors []competitor
	for rows.Next() {
		var c competitor
		if err := rows.Scan(&c.ID, &c.Name); err != nil {
			slog.Warn("rakip satır okuma hatası", "error", err)
			continue
		}
		competitors = append(competitors, c)
	}
	if rows.Err() != nil {
		return nil, fmt.Errorf("rows iterasyon: %w", rows.Err())
	}

	if len(competitors) == 0 {
		return nil, nil
	}

	var snapshots []GapSnapshot
	for _, comp := range competitors {
		snapshot := e.analyzeCompetitor(ctx, brandID, brandName, comp.ID, comp.Name, tenantID, workspaceID)
		snapshots = append(snapshots, *snapshot)
		e.saveSnapshot(ctx, snapshot, tenantID, workspaceID)
	}

	metrics.CompetitiveGapAnalysesCompleted.WithLabelValues(tenantID).Inc()
	slog.Info("competitive gap analizi tamamlandı", "brand", brandID, "competitors", len(competitors))

	return snapshots, nil
}

// analyzeCompetitor performs all 5 gap analyses for a single brand-competitor pair.
func (e *Engine) analyzeCompetitor(ctx context.Context, brandID, brandName, compID, compName, tenantID, workspaceID string) *GapSnapshot {
	now := time.Now()
	periodStart := now.AddDate(0, 0, -30).Format("2006-01-02")
	periodEnd := now.Format("2006-01-02")

	snapshot := &GapSnapshot{
		ID:             id.New(),
		BrandID:        brandID,
		BrandName:      brandName,
		CompetitorID:   compID,
		CompetitorName: compName,
		PeriodStart:    periodStart,
		PeriodEnd:      periodEnd,
		CreatedAt:      now,
	}

	// 1. Visibility Gap (SOV-based)
	snapshot.VisibilityGap = e.calcVisibilityGap(ctx, brandID, compID, tenantID)

	// 2. Citation Gap
	snapshot.CitationGap = e.calcCitationGap(ctx, brandID, compID, tenantID)

	// 3. Content Gap
	snapshot.ContentGap = e.calcContentGap(ctx, brandID, compID, tenantID)

	// 4. Topic Gap
	snapshot.TopicGap = e.calcTopicGap(ctx, brandID, compID, tenantID)

	// 5. Prompt Gap
	snapshot.PromptGap = e.calcPromptGap(ctx, brandID, compID, tenantID)

	// Composite competitive score (weighted)
	snapshot.CompetitiveScore = e.calcCompetitiveScore(snapshot)

	return snapshot
}

// calcVisibilityGap calculates the visibility gap between brand and competitor.
func (e *Engine) calcVisibilityGap(ctx context.Context, brandID, compID, tenantID string) *GapDetail {
	var brandSOV, compSOV float64

	scanRow(e.pool.QueryRow(ctx, `
		SELECT COALESCE(AVG(value), 0) FROM measure.scores
		WHERE brand_id = $1 AND tenant_id = $2 AND freshness_at > now() - interval '30 days'
	`, brandID, tenantID).Scan(&brandSOV))

	scanRow(e.pool.QueryRow(ctx, `
		SELECT COALESCE(AVG(value), 0) FROM measure.scores
		WHERE brand_id = $1 AND tenant_id = $2 AND freshness_at > now() - interval '30 days'
	`, compID, tenantID).Scan(&compSOV))

	gap := brandSOV - compSOV
	norm := 50.0 + (gap/100.0)*50.0
	if norm < 0 {
		norm = 0
	}
	if norm > 100 {
		norm = 100
	}

	dir := "equal"
	if gap > 5 {
		dir = "brand_ahead"
	} else if gap < -5 {
		dir = "competitor_ahead"
	}

	return &GapDetail{
		GapValue:        math.Round(gap*100) / 100,
		Normalized:      math.Round(norm*100) / 100,
		BrandValue:      math.Round(brandSOV*100) / 100,
		CompetitorValue: math.Round(compSOV*100) / 100,
		Direction:       dir,
	}
}

// calcCitationGap calculates the citation gap between brand and competitor.
func (e *Engine) calcCitationGap(ctx context.Context, brandID, compID, tenantID string) *GapDetail {
	var brandCites, compCites, totalCites int

	scanRow(e.pool.QueryRow(ctx, `
		SELECT COALESCE(SUM(citation_count), 0) FROM measure.citations
		WHERE brand_id = $1 AND tenant_id = $2
	`, brandID, tenantID).Scan(&brandCites))

	scanRow(e.pool.QueryRow(ctx, `
		SELECT COALESCE(SUM(citation_count), 0) FROM measure.citations
		WHERE brand_id = $1 AND tenant_id = $2
	`, compID, tenantID).Scan(&compCites))

	scanRow(e.pool.QueryRow(ctx, `
		SELECT COALESCE(SUM(citation_count), 0) FROM measure.citations
		WHERE (brand_id = $1 OR brand_id = $2) AND tenant_id = $3
	`, brandID, compID, tenantID).Scan(&totalCites))

	brandRate := 0.0
	compRate := 0.0
	if totalCites > 0 {
		brandRate = float64(brandCites) / float64(totalCites) * 100.0
		compRate = float64(compCites) / float64(totalCites) * 100.0
	}

	gap := brandRate - compRate
	norm := 50.0 + (gap/100.0)*50.0
	if norm < 0 {
		norm = 0
	}
	if norm > 100 {
		norm = 100
	}

	dir := "equal"
	if gap > 5 {
		dir = "brand_ahead"
	} else if gap < -5 {
		dir = "competitor_ahead"
	}

	return &GapDetail{
		GapValue:        math.Round(gap*100) / 100,
		Normalized:      math.Round(norm*100) / 100,
		BrandValue:      math.Round(brandRate*100) / 100,
		CompetitorValue: math.Round(compRate*100) / 100,
		Direction:       dir,
	}
}

// calcContentGap calculates the content gap between brand and competitor.
func (e *Engine) calcContentGap(ctx context.Context, brandID, compID, tenantID string) *GapDetail {
	// Simplified: compare source domain diversity
	var brandDomains, compDomains int
	scanRow(e.pool.QueryRow(ctx, `
		SELECT COUNT(DISTINCT source_domain) FROM measure.citations
		WHERE brand_id = $1 AND tenant_id = $2
	`, brandID, tenantID).Scan(&brandDomains))

	scanRow(e.pool.QueryRow(ctx, `
		SELECT COUNT(DISTINCT source_domain) FROM measure.citations
		WHERE brand_id = $1 AND tenant_id = $2
	`, compID, tenantID).Scan(&compDomains))

	gap := float64(brandDomains - compDomains)
	norm := 50.0 + (gap/20.0)*50.0 // normalize assuming max 20 domain difference
	if norm < 0 {
		norm = 0
	}
	if norm > 100 {
		norm = 100
	}

	dir := "equal"
	if gap > 2 {
		dir = "brand_ahead"
	} else if gap < -2 {
		dir = "competitor_ahead"
	}

	return &GapDetail{
		GapValue:        gap,
		Normalized:      math.Round(norm*100) / 100,
		BrandValue:      float64(brandDomains),
		CompetitorValue: float64(compDomains),
		Direction:       dir,
	}
}

// calcTopicGap calculates the topic gap between brand and competitor.
func (e *Engine) calcTopicGap(ctx context.Context, brandID, compID, tenantID string) *GapDetail {
	// Simplified: compare score presence across topics
	var brandScore, compScore float64
	scanRow(e.pool.QueryRow(ctx, `
		SELECT COALESCE(AVG(value), 0) FROM measure.scores
		WHERE brand_id = $1 AND tenant_id = $2 AND freshness_at > now() - interval '30 days'
	`, brandID, tenantID).Scan(&brandScore))

	scanRow(e.pool.QueryRow(ctx, `
		SELECT COALESCE(AVG(value), 0) FROM measure.scores
		WHERE brand_id = $1 AND tenant_id = $2 AND freshness_at > now() - interval '30 days'
	`, compID, tenantID).Scan(&compScore))

	gap := brandScore - compScore
	norm := 50.0 + (gap/100.0)*50.0
	if norm < 0 {
		norm = 0
	}
	if norm > 100 {
		norm = 100
	}

	dir := "equal"
	if gap > 5 {
		dir = "brand_ahead"
	} else if gap < -5 {
		dir = "competitor_ahead"
	}

	return &GapDetail{
		GapValue:        math.Round(gap*100) / 100,
		Normalized:      math.Round(norm*100) / 100,
		BrandValue:      math.Round(brandScore*100) / 100,
		CompetitorValue: math.Round(compScore*100) / 100,
		Direction:       dir,
	}
}

// calcPromptGap calculates the prompt coverage gap between brand and competitor.
func (e *Engine) calcPromptGap(ctx context.Context, brandID, compID, tenantID string) *GapDetail {
	// Simplified: compare measurement job completion counts as proxy for prompt coverage
	var brandJobs, compJobs int
	scanRow(e.pool.QueryRow(ctx, `
		SELECT COUNT(*) FROM measure.measurement_jobs
		WHERE brand_id = $1 AND tenant_id = $2 AND status = 'completed'
	`, brandID, tenantID).Scan(&brandJobs))

	scanRow(e.pool.QueryRow(ctx, `
		SELECT COUNT(*) FROM measure.measurement_jobs
		WHERE brand_id = $1 AND tenant_id = $2 AND status = 'completed'
	`, compID, tenantID).Scan(&compJobs))

	brandCoverage := float64(brandJobs)
	compCoverage := float64(compJobs)
	if brandCoverage+compCoverage > 0 {
		brandCoverage = float64(brandJobs) / (float64(brandJobs) + float64(compJobs)) * 100.0
		compCoverage = float64(compJobs) / (float64(brandJobs) + float64(compJobs)) * 100.0
	}

	gap := brandCoverage - compCoverage
	norm := 50.0 + (gap/100.0)*50.0

	dir := "equal"
	if gap > 5 {
		dir = "brand_ahead"
	} else if gap < -5 {
		dir = "competitor_ahead"
	}

	return &GapDetail{
		GapValue:        math.Round(gap*100) / 100,
		Normalized:      math.Round(norm*100) / 100,
		BrandValue:      math.Round(brandCoverage*100) / 100,
		CompetitorValue: math.Round(compCoverage*100) / 100,
		Direction:       dir,
	}
}

// calcCompetitiveScore computes the weighted composite competitive score.
func (e *Engine) calcCompetitiveScore(snapshot *GapSnapshot) float64 {
	weights := map[string]float64{
		"visibility": 0.30,
		"citation":   0.25,
		"content":    0.20,
		"topic":      0.15,
		"prompt":     0.10,
	}

	score := 0.0
	gaps := map[string]*GapDetail{
		"visibility": snapshot.VisibilityGap,
		"citation":   snapshot.CitationGap,
		"content":    snapshot.ContentGap,
		"topic":      snapshot.TopicGap,
		"prompt":     snapshot.PromptGap,
	}

	for name, gap := range gaps {
		if gap != nil {
			score += gap.Normalized * weights[name]
		} else {
			score += 50.0 * weights[name]
		}
	}

	return math.Round(score*100) / 100
}

// saveSnapshot persists the gap analysis results.
func (e *Engine) saveSnapshot(ctx context.Context, snapshot *GapSnapshot, tenantID, workspaceID string) {
	breakdown := map[string]interface{}{
		"visibility": snapshot.VisibilityGap,
		"citation":   snapshot.CitationGap,
		"content":    snapshot.ContentGap,
		"topic":      snapshot.TopicGap,
		"prompt":     snapshot.PromptGap,
	}
	breakdownJSON, _ := json.Marshal(breakdown)

	_, err := e.pool.Exec(ctx, `
		INSERT INTO competitive.gap_snapshots
			(id, brand_id, competitor_id, period_start, period_end,
			 visibility_gap, citation_gap, content_gap, topic_gap, prompt_gap,
			 competitive_score, breakdown, tenant_id, workspace_id, created_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, now())
		ON CONFLICT (brand_id, competitor_id, period_start, period_end) DO UPDATE
		SET visibility_gap = EXCLUDED.visibility_gap,
		    citation_gap = EXCLUDED.citation_gap,
		    content_gap = EXCLUDED.content_gap,
		    topic_gap = EXCLUDED.topic_gap,
		    prompt_gap = EXCLUDED.prompt_gap,
		    competitive_score = EXCLUDED.competitive_score,
		    breakdown = EXCLUDED.breakdown,
		    created_at = now()
	`, snapshot.ID, snapshot.BrandID, snapshot.CompetitorID, snapshot.PeriodStart, snapshot.PeriodEnd,
		nullableGap(snapshot.VisibilityGap), nullableGap(snapshot.CitationGap),
		nullableGap(snapshot.ContentGap), nullableGap(snapshot.TopicGap), nullableGap(snapshot.PromptGap),
		snapshot.CompetitiveScore, string(breakdownJSON), tenantID, workspaceID)
	if err != nil {
		slog.Warn("gap snapshot kaydetme hatası", "error", err)
	}

	// Generate recommendations based on gaps
	e.saveRecommendations(ctx, snapshot, tenantID)
}

// nullableGap returns the gap value or nil for NULL in DB.
func nullableGap(d *GapDetail) *float64 {
	if d == nil {
		return nil
	}
	return &d.GapValue
}

// saveRecommendations generates and saves gap-based recommendations.
func (e *Engine) saveRecommendations(ctx context.Context, snapshot *GapSnapshot, tenantID string) {
	recs := []struct {
		gapType     string
		priority    string
		description string
		impact      string
		evidence    string
	}{
		{"visibility", "medium", "Görünürlük farkı kapatmak için zayıf motorlarda strateji revizyonu yapılmalı", "Visibility gap puanında +5-15 iyileşme", "korelasyonel"},
		{"citation", "high", "Alıntı oranını artırmak için blog ve editoryal içerik üretimi artırılmalı", "Citation gap puanında +10-20 iyileşme", "korelasyonel"},
		{"content", "high", "İçerik çeşitliliği artırılmalı; eksik kaynak türlerine odaklanılmalı", "Content gap puanında +5-10 iyileşme", "deneysel"},
		{"topic", "medium", "Zayıf konularda içerik güçlendirilmeli; topic cluster stratejisi uygulanmalı", "Topic gap puanında +10-25 iyileşme", "denenebilir"},
		{"prompt", "medium", "Karşılaştırma ve öneri prompt kapsamı artırılmalı", "Prompt gap puanında +5-15 iyileşme", "denenebilir"},
	}

	for _, r := range recs {
		_, err := e.pool.Exec(ctx, `
			INSERT INTO competitive.gap_recommendations
				(id, gap_id, gap_type, priority, description, impact, kanit_derecesi, related_fr, tenant_id, created_at)
			VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, now())
		`, id.New(), snapshot.ID, r.gapType, r.priority, r.description, r.impact, r.evidence, "FR-D11", tenantID)
		if err != nil {
			slog.Warn("gap recommendation kaydetme hatası", "error", err)
		}
	}
}

// GetGapDetail returns the detail for a specific gap type between brand and competitor.
func (e *Engine) GetGapDetail(ctx context.Context, brandID, competitorID, gapType, workspaceID, tenantID string) (*GapDetail, error) {
	var gapDetail GapDetail
	query := fmt.Sprintf(`
		SELECT %s_gap FROM competitive.gap_snapshots
		WHERE brand_id = $1 AND competitor_id = $2 AND tenant_id = $3
		ORDER BY created_at DESC LIMIT 1
	`, gapType)

	var gapVal *float64
	err := e.pool.QueryRow(ctx, query, brandID, competitorID, tenantID).Scan(&gapVal)
	if err != nil {
		return nil, fmt.Errorf("gap sorgu: %w", err)
	}

	if gapVal == nil {
		return nil, nil
	}

	gapDetail.GapValue = *gapVal
	gapDetail.Normalized = 50.0 + (*gapVal/100.0)*50.0

	dir := "equal"
	if *gapVal > 5 {
		dir = "brand_ahead"
	} else if *gapVal < -5 {
		dir = "competitor_ahead"
	}
	gapDetail.Direction = dir

	return &gapDetail, nil
}
