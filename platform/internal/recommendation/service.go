package recommendation

import (
	"context"
	"encoding/json"
	"log/slog"
	"time"

	"github.com/geolens/platform/platform/db"
	"github.com/oklog/ulid/v2"
)

// Default rules for the recommendation engine.
var defaultRules = []Rule{
	{
		ID:          "rule-score-drop",
		Name:        "Skor Düşüşü Tespiti",
		Description: "Görünürlük skoru 10 puandan fazla düştüğünde uyar",
		Category:    CategoryVisibility,
		Severity:    SeverityHigh,
		Conditions: []Condition{
			{Field: "score.drop", Operator: "gt", Value: 10.0},
		},
		Title:  "Görünürlük skorunuz düşüyor",
		Detail: "Markanızın AI görünürlük skoru son ölçümde önemli ölçüde düştü. Rakiplerinizin AI motorlarındaki görünürlüğünü kontrol edin.",
		Active: true,
	},
	{
		ID:          "rule-trend-decline",
		Name:        "Trend Gerilemesi",
		Description: "Son iki ölçüm arasında sürekli düşüş varsa uyar",
		Category:    CategoryVisibility,
		Severity:    SeverityMedium,
		Conditions: []Condition{
			{Field: "score.trend", Operator: "eq", Value: "declining"},
		},
		Title:  "Görünürlük trendiniz geriliyor",
		Detail: "Markanızın AI görünürlük skoru son iki ölçümde de düşüş gösteriyor. Bu, rakiplerinizin sizi geçtiği anlamına gelebilir.",
		Active: true,
	},
	{
		ID:          "rule-engine-gap",
		Name:        "Motor Bazında Performans Farkı",
		Description: "Bir motorda düşük, diğerinde yüksek skor varsa uyar",
		Category:    CategoryVisibility,
		Severity:    SeverityMedium,
		Conditions: []Condition{
			{Field: "score.engine_gap", Operator: "gt", Value: 30.0},
		},
		Title:  "Motorlar arasında büyük performans farkı var",
		Detail: "Markanız bazı AI motorlarında yüksek görünürlüğe sahipken bazılarında düşük. Farkın nedenini araştırmanız önerilir.",
		Active: true,
	},
	{
		ID:          "rule-engine-citation-gap",
		Name:        "Citation Eksikliği",
		Description: "Engine breakdown'da tek motor baskınsa uyar",
		Category:    CategoryContent,
		Severity:    SeverityLow,
		Conditions: []Condition{
			{Field: "score.engine_gap", Operator: "lt", Value: 5.0},
		},
		Title:  "Motor çeşitliliği düşük",
		Detail: "Markanız yalnızca bir AI motorunda görünür durumda. Diğer motorlarda da görünürlük kazanmak için içerik stratejinizi çeşitlendirin.",
		Active: true,
	},
	{
		ID:          "rule-competitor-gain",
		Name:        "Rakip Yükselişi",
		Description: "Skor düşüş trendi varsa ve önceki skor varsa uyar",
		Category:    CategoryCompetitor,
		Severity:    SeverityHigh,
		Conditions: []Condition{
			{Field: "score.trend", Operator: "eq", Value: "declining"},
		},
		Title:  "Rakibiniz öne geçiyor olabilir",
		Detail: "Markanızın AI görünürlük skoru düşüş trendinde. Rakiplerinizin AI stratejisini analiz etmeniz önerilir.",
		Active: true,
	},
}

// service implements the Service interface for recommendations.
type service struct {
	rules []Rule
	pool  *db.Pool
}

// NewService creates a new recommendation service with database access.
func NewService(pool *db.Pool) Service {
	rules := make([]Rule, len(defaultRules))
	copy(rules, defaultRules)
	return &service{rules: rules, pool: pool}
}

// Evaluate evaluates all rules against real data for a single brand.
func (s *service) Evaluate(brandID, workspaceID, tenantID string) ([]Recommendation, error) {
	if brandID == "" {
		return s.EvaluateAll(workspaceID, tenantID)
	}

	ctx := context.Background()
	snapshot := s.loadScore(ctx, brandID, workspaceID, tenantID)

	evalCtx := &EvaluationContext{
		BrandID:     brandID,
		WorkspaceID: workspaceID,
		TenantID:    tenantID,
		Score:       snapshot,
	}

	return s.evaluateBrand(evalCtx), nil
}

// EvaluateAll evaluates all rules for every brand in a workspace.
func (s *service) EvaluateAll(workspaceID, tenantID string) ([]Recommendation, error) {
	ctx := context.Background()

	rows, err := s.pool.Query(ctx, `
		SELECT id, name FROM config.brands
		WHERE workspace_id = $1 AND tenant_id = $2 AND is_active = true
	`, workspaceID, tenantID)
	if err != nil {
		slog.Error("recommendation: marka sorgu hatası", "error", err)
		return nil, err
	}
	defer rows.Close()

	var results []Recommendation
	for rows.Next() {
		var brandID, brandName string
		if err := rows.Scan(&brandID, &brandName); err != nil {
			slog.Error("recommendation: marka satır okuma hatası", "error", err)
			continue
		}

		snapshot := s.loadScore(ctx, brandID, workspaceID, tenantID)
		evalCtx := &EvaluationContext{
			BrandID:     brandID,
			BrandName:   brandName,
			WorkspaceID: workspaceID,
			TenantID:    tenantID,
			Score:       snapshot,
		}
		results = append(results, s.evaluateBrand(evalCtx)...)
	}

	slog.Debug("recommendation evaluation complete", "workspace", workspaceID, "results", len(results))
	return results, nil
}

// loadScore fetches the latest (and previous) score values for a brand.
func (s *service) loadScore(ctx context.Context, brandID, workspaceID, tenantID string) *ScoreSnapshot {
	snapshot := &ScoreSnapshot{}

	var value float64
	var freshnessAt time.Time
	err := s.pool.QueryRow(ctx, `
		SELECT value, freshness_at FROM measure.scores
		WHERE brand_id = $1 AND workspace_id = $2 AND tenant_id = $3
		ORDER BY freshness_at DESC LIMIT 1
	`, brandID, workspaceID, tenantID).Scan(&value, &freshnessAt)
	if err != nil {
		slog.Debug("recommendation: skor bulunamadı", "brand", brandID)
		return snapshot
	}

	snapshot.Value = value
	snapshot.FreshnessAt = freshnessAt

	// Önceki skoru al (trend + drop tespiti)
	var prevValue float64
	var prevAt time.Time
	err = s.pool.QueryRow(ctx, `
		SELECT value, freshness_at FROM measure.scores
		WHERE brand_id = $1 AND workspace_id = $2 AND tenant_id = $3
		ORDER BY freshness_at DESC OFFSET 1 LIMIT 1
	`, brandID, workspaceID, tenantID).Scan(&prevValue, &prevAt)
	if err == nil {
		snapshot.PreviousValue = prevValue
		snapshot.PreviousAt = prevAt
	}

	// Engine breakdown (engine_gap tespiti)
	var breakdownJSON string
	err = s.pool.QueryRow(ctx, `
		SELECT COALESCE(engine_breakdown::text, '{}') FROM measure.scores
		WHERE brand_id = $1 AND workspace_id = $2 AND tenant_id = $3
		ORDER BY freshness_at DESC LIMIT 1
	`, brandID, workspaceID, tenantID).Scan(&breakdownJSON)
	if err == nil && breakdownJSON != "" && breakdownJSON != "{}" && breakdownJSON != "null" {
		var breakdown map[string]float64
		if json.Unmarshal([]byte(breakdownJSON), &breakdown) == nil {
			snapshot.EngineBreakdown = breakdown
		}
	}

	return snapshot
}

// evaluateBrand runs all rules against a single brand's context.
func (s *service) evaluateBrand(ctx *EvaluationContext) []Recommendation {
	var results []Recommendation
	for _, rule := range s.rules {
		if !rule.Active {
			continue
		}
		if s.evaluateConditions(ctx, rule.Conditions) {
			results = append(results, Recommendation{
				ID:          generateULID(),
				TenantID:    ctx.TenantID,
				WorkspaceID: ctx.WorkspaceID,
				BrandID:     ctx.BrandID,
				Category:    rule.Category,
				Severity:    rule.Severity,
				Title:       rule.Title,
				Detail:      rule.Detail,
				ActionURL:   rule.ActionURL,
				Score:       s.computeConfidence(ctx, rule),
				CreatedAt:   time.Now().UTC(),
			})
		}
	}
	return results
}

// evaluateConditions evaluates conditions against real score/audit data.
func (s *service) evaluateConditions(ctx *EvaluationContext, conditions []Condition) bool {
	if len(conditions) == 0 {
		return true
	}
	for _, c := range conditions {
		if !s.evaluateCondition(ctx, c) {
			return false
		}
	}
	return true
}

// evaluateCondition evaluates a single condition against context data.
func (s *service) evaluateCondition(ctx *EvaluationContext, c Condition) bool {
	if ctx.Score == nil {
		return false
	}

	switch c.Field {
	case "score.drop":
		if ctx.Score.PreviousValue == 0 {
			return false
		}
		drop := ctx.Score.PreviousValue - ctx.Score.Value
		return compareFloat(drop, c.Operator, toFloat64(c.Value))

	case "score.trend":
		if ctx.Score.PreviousValue == 0 {
			return false
		}
		diff := ctx.Score.Value - ctx.Score.PreviousValue
		trendStr := "stable"
		if diff < -5 {
			trendStr = "declining"
		} else if diff > 5 {
			trendStr = "rising"
		}
		expected, ok := c.Value.(string)
		if !ok {
			return false
		}
		return trendStr == expected

	case "score.engine_gap":
		if len(ctx.Score.EngineBreakdown) == 0 {
			return false
		}
		var min, max float64
		first := true
		for _, v := range ctx.Score.EngineBreakdown {
			if first {
				min = v
				max = v
				first = false
				continue
			}
			if v < min {
				min = v
			}
			if v > max {
				max = v
			}
		}
		return compareFloat(max-min, c.Operator, toFloat64(c.Value))

	default:
		slog.Warn("recommendation: bilinmeyen condition field", "field", c.Field)
		return false
	}
}

// computeConfidence calculates a confidence score (0-100) for a recommendation.
func (s *service) computeConfidence(ctx *EvaluationContext, rule Rule) float64 {
	score := 75.0
	if ctx.Score != nil && !ctx.Score.FreshnessAt.IsZero() {
		age := time.Since(ctx.Score.FreshnessAt)
		if age > 7*24*time.Hour {
			score -= 15
		} else if age < 24*time.Hour {
			score += 10
		}
	}
	switch rule.Severity {
	case SeverityCritical:
		score += 10
	case SeverityLow:
		score -= 10
	}
	if score > 100 {
		score = 100
	}
	if score < 0 {
		score = 0
	}
	return score
}

// GetRules returns all registered rules.
func (s *service) GetRules() []Rule {
	return s.rules
}

// MarkApplied marks a recommendation as applied.
func (s *service) MarkApplied(id string) error {
	slog.Info("recommendation marked as applied", "id", id)
	return nil
}

// MarkDismissed marks a recommendation as dismissed.
func (s *service) MarkDismissed(id string) error {
	slog.Info("recommendation marked as dismissed", "id", id)
	return nil
}

// RegisterCustomRule adds a user-defined rule to the registry.
func (s *service) RegisterCustomRule(rule Rule) error {
	if rule.ID == "" {
		rule.ID = "rule-custom-" + generateULID()
	}
	rule.Active = true
	s.rules = append(s.rules, rule)
	return nil
}

// ---- Yardımcı Fonksiyonlar ----

func generateULID() string {
	return ulid.Make().String()
}

func toFloat64(v interface{}) float64 {
	switch val := v.(type) {
	case float64:
		return val
	case int:
		return float64(val)
	case int64:
		return float64(val)
	default:
		return 0
	}
}

func compareFloat(actual float64, operator string, expected float64) bool {
	switch operator {
	case "gt":
		return actual > expected
	case "lt":
		return actual < expected
	case "eq":
		return actual == expected
	case "gte":
		return actual >= expected
	case "lte":
		return actual <= expected
	default:
		return false
	}
}

// Ensure service implements Service interface.
var _ Service = (*service)(nil)
