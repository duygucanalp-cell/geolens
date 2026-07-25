package recommendation

import (
	"context"
	"encoding/json"
	"fmt"
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
	{
		ID:          "rule-robots-blocked",
		Name:        "robots.txt AI Engel Tespiti",
		Description: "robots.txt AI botlarını engelliyorsa uyar",
		Category:    CategoryTechnical,
		Severity:    SeverityCritical,
		Conditions: []Condition{
			{Field: "audit.robots_txt.disallowed_all", Operator: "eq", Value: true},
		},
		Title:     "AI botları robots.txt tarafından engelleniyor",
		Detail:    "Sitenizin robots.txt dosyası AI botlarının sitenizi taramasını engelliyor. Bu, AI görünürlük ölçümlerinizi doğrudan etkiler.",
		ActionURL: "/audit",
		Active:    true,
	},
	{
		ID:          "rule-no-structured-data",
		Name:        "Yapılandırılmış Veri Eksik",
		Description: "Sitede JSON-LD veya Schema.org yoksa öner",
		Category:    CategoryContent,
		Severity:    SeverityMedium,
		Conditions: []Condition{
			{Field: "audit.ssr.has_structured_data", Operator: "eq", Value: false},
		},
		Title:     "Yapılandırılmış veri ekleyin",
		Detail:    "Sitenizde JSON-LD veya Schema.org yapılandırılmış verisi bulunamadı. AI motorları içeriğinizi daha iyi anlamak için yapılandırılmış veri kullanır.",
		ActionURL: "/audit",
		Active:    true,
	},
	{
		ID:          "rule-bot-inaccessible",
		Name:        "AI Bot Erişim Engeli",
		Description: "AI botları sitenize erişemiyorsa uyar",
		Category:    CategoryTechnical,
		Severity:    SeverityCritical,
		Conditions: []Condition{
			{Field: "audit.bot_access.accessible", Operator: "eq", Value: false},
		},
		Title:     "AI botları sitenize erişemiyor",
		Detail:    "AI botları sitenize erişim sağlayamıyor. Sunucu yapılandırmanızı ve güvenlik duvarı ayarlarınızı kontrol edin.",
		ActionURL: "/audit",
		Active:    true,
	},
}

// service implements the Service interface for recommendations.
type service struct {
	rules []Rule
	pool  *db.Pool
	ng10  *NG10Filter
}

// NewService creates a new recommendation service with database access.
func NewService(pool *db.Pool) Service {
	rules := make([]Rule, len(defaultRules))
	copy(rules, defaultRules)
	return &service{rules: rules, pool: pool, ng10: NewNG10Filter()}
}

// Evaluate evaluates all rules against real data for a single brand.
// Sonuçlar NG10 filtresinden geçirilir: sadece NG (nötr) ve P (pozitif) öneriler döner.
func (s *service) Evaluate(brandID, workspaceID, tenantID string) ([]Recommendation, error) {
	if brandID == "" {
		return s.EvaluateAll(workspaceID, tenantID)
	}

	ctx := context.Background()
	snapshot := s.loadScore(ctx, brandID, workspaceID, tenantID)
	audit := s.loadAudit(ctx, brandID, tenantID)

	evalCtx := &EvaluationContext{
		BrandID:     brandID,
		WorkspaceID: workspaceID,
		TenantID:    tenantID,
		Score:       snapshot,
		Audit:       audit,
	}

	recs := s.evaluateBrand(evalCtx)

	// NG10 filtresi uygula
	filtered := s.ng10.FilterRecommendations(recs)
	if len(filtered) != len(recs) {
		slog.Debug("recommendation: NG10 filtreleme yapıldı",
			"brand", brandID,
			"before", len(recs),
			"after", len(filtered),
		)
	}

	return filtered, nil
}

// EvaluateAll evaluates all rules for every brand in a workspace.
// Sonuçlar NG10 filtresinden geçirilir: sadece NG (nötr) ve P (pozitif) öneriler döner.
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
		audit := s.loadAudit(ctx, brandID, tenantID)
		evalCtx := &EvaluationContext{
			BrandID:     brandID,
			BrandName:   brandName,
			WorkspaceID: workspaceID,
			TenantID:    tenantID,
			Score:       snapshot,
			Audit:       audit,
		}
		results = append(results, s.evaluateBrand(evalCtx)...)
	}

	// NG10 filtresi uygula
	filtered := s.ng10.FilterRecommendations(results)
	if len(filtered) != len(results) {
		slog.Debug("recommendation: NG10 filtreleme yapıldı",
			"workspace", workspaceID,
			"before", len(results),
			"after", len(filtered),
		)
	}

	slog.Debug("recommendation evaluation complete", "workspace", workspaceID, "results", len(filtered))
	return filtered, nil
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

// loadAudit fetches the latest audit result for a brand from governance.audit_results.
func (s *service) loadAudit(ctx context.Context, brandID, tenantID string) *AuditSnapshot {
	audit := &AuditSnapshot{}

	var robotsDisallowed bool
	var hasStructured bool
	var botAccessible bool
	var overallScore float64

	err := s.pool.QueryRow(ctx, `
		SELECT
			COALESCE((robots_txt->>'disallowed_all')::boolean, false),
			COALESCE((ssr->>'has_structured_data')::boolean, false),
			COALESCE((bot_access->>'accessible')::boolean, false),
			COALESCE(overall_score, 0)
		FROM governance.audit_results
		WHERE brand_id = $1 AND tenant_id = $2
		ORDER BY created_at DESC LIMIT 1
	`, brandID, tenantID).Scan(&robotsDisallowed, &hasStructured, &botAccessible, &overallScore)

	if err != nil {
		slog.Debug("recommendation: audit bulunamadı", "brand", brandID)
		return audit
	}

	audit.HasData = true
	audit.RobotsDisallowedAll = robotsDisallowed
	audit.HasStructuredData = hasStructured
	audit.BotAccessible = botAccessible
	audit.OverallScore = overallScore

	return audit
}

// evaluateBrand runs all rules against a single brand's context.
// Sonuçları DB'ye kaydeder (recommendation.results).
func (s *service) evaluateBrand(ctx *EvaluationContext) []Recommendation {
	var results []Recommendation
	for _, rule := range s.rules {
		if !rule.Active {
			continue
		}
		if s.evaluateConditions(ctx, rule.Conditions) {
			rec := Recommendation{
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
			}
			results = append(results, rec)

			// DB'ye kaydet (idempotent: aynı ID tekrar kaydedilmez)
			s.saveRecommendation(rec)
		}
	}
	return results
}

// saveRecommendation persists a recommendation to the database.
func (s *service) saveRecommendation(rec Recommendation) {
	ctx := context.Background()
	_, err := s.pool.Exec(ctx, `
		INSERT INTO recommendation.results
			(id, brand_id, workspace_id, tenant_id, category, severity,
			 title, detail, action_url, confidence, applied, dismissed, created_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13)
		ON CONFLICT (id) DO NOTHING
	`, rec.ID, rec.BrandID, rec.WorkspaceID, rec.TenantID,
		string(rec.Category), string(rec.Severity),
		rec.Title, rec.Detail, rec.ActionURL, rec.Score,
		rec.Applied, rec.Dismissed, rec.CreatedAt)
	if err != nil {
		slog.Warn("recommendation: kaydetme hatası", "id", rec.ID, "error", err)
	}
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

	case "audit.robots_txt.disallowed_all":
		if ctx.Audit == nil || !ctx.Audit.HasData {
			return false
		}
		return ctx.Audit.RobotsDisallowedAll == toBool(c.Value)

	case "audit.ssr.has_structured_data":
		if ctx.Audit == nil || !ctx.Audit.HasData {
			return false
		}
		return ctx.Audit.HasStructuredData == toBool(c.Value)

	case "audit.bot_access.accessible":
		if ctx.Audit == nil || !ctx.Audit.HasData {
			return false
		}
		return ctx.Audit.BotAccessible == toBool(c.Value)

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

// MarkApplied marks a recommendation as applied in the database.
func (s *service) MarkApplied(id string) error {
	now := time.Now().UTC()
	_, err := s.pool.Exec(context.Background(), `
		UPDATE recommendation.results
		SET applied = true, applied_at = $2, updated_at = $2
		WHERE id = $1
	`, id, now)
	if err != nil {
		slog.Error("recommendation: uygulama hatası", "id", id, "error", err)
		return fmt.Errorf("recommendation: uygulama hatası: %w", err)
	}
	slog.Info("recommendation marked as applied", "id", id)
	return nil
}

// MarkDismissed marks a recommendation as dismissed in the database.
func (s *service) MarkDismissed(id string) error {
	now := time.Now().UTC()
	_, err := s.pool.Exec(context.Background(), `
		UPDATE recommendation.results
		SET dismissed = true, dismissed_at = $2, updated_at = $2
		WHERE id = $1
	`, id, now)
	if err != nil {
		slog.Error("recommendation: gizleme hatası", "id", id, "error", err)
		return fmt.Errorf("recommendation: gizleme hatası: %w", err)
	}
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

func toBool(v interface{}) bool {
	b, ok := v.(bool)
	return ok && b
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
