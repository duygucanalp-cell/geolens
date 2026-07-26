package alert

import (
	"context"
	"database/sql"
	"log/slog"
	"time"

	"github.com/geolens/platform/platform/db"
)

// RuleEngine evaluates alert rules against latest scores.
type RuleEngine struct {
	pool *db.Pool
}

// NewRuleEngine creates a new alert rule engine.
func NewRuleEngine(pool *db.Pool) *RuleEngine {
	return &RuleEngine{pool: pool}
}

// EvaluateResult represents the result of an alert rule evaluation.
type EvaluateResult struct {
	RuleID    string  `json:"rule_id"`
	BrandID   string  `json:"brand_id"`
	Metric    string  `json:"metric"`
	Threshold float64 `json:"threshold"`
	Actual    float64 `json:"actual"`
	Triggered bool    `json:"triggered"`
	Score     float64 `json:"score"`
}

// EvaluateAll checks all enabled alert rules against current data.
// Returns triggered rules that should fire notifications.
func (e *RuleEngine) EvaluateAll(tenantID string) ([]EvaluateResult, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	rows, err := e.pool.Query(ctx, `
		SELECT ar.id, ar.brand_id, ar.metric, ar.condition, ar.threshold,
			ar.cooldown_min, ar.last_fired_at
		FROM governance.alert_rules ar
		WHERE ar.tenant_id = $1 AND ar.enabled = true
	`, tenantID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var results []EvaluateResult
	for rows.Next() {
		var id, brandID, metric, condition string
		var threshold float64
		var cooldownMin int
		var lastFiredAt sql.NullTime

		if err := rows.Scan(&id, &brandID, &metric, &condition, &threshold, &cooldownMin, &lastFiredAt); err != nil {
			slog.Warn("alert rule satır okuma hatası", "error", err)
			continue
		}

		// Cooldown kontrolü
		if lastFiredAt.Valid {
			elapsed := time.Since(lastFiredAt.Time)
			if elapsed < time.Duration(cooldownMin)*time.Minute {
				continue
			}
		}

		actual, score := e.evaluateMetric(ctx, brandID, metric)
		triggered := e.compare(actual, condition, threshold)

		if triggered {
			results = append(results, EvaluateResult{
				RuleID:    id,
				BrandID:   brandID,
				Metric:    metric,
				Threshold: threshold,
				Actual:    actual,
				Triggered: true,
				Score:     score,
			})

			// last_fired_at güncelle
			_, _ = e.pool.Exec(ctx, `
				UPDATE governance.alert_rules SET last_fired_at = now() WHERE id = $1
			`, id)
		}
	}

	return results, nil
}

func (e *RuleEngine) evaluateMetric(ctx context.Context, brandID, metric string) (actual float64, score float64) {
	switch metric {
	case "score_drop":
		// Son skor ile bir önceki skor arasındaki fark
		var current, previous float64
		err := e.pool.QueryRow(ctx, `
			SELECT value FROM measure.scores
			WHERE brand_id = $1 ORDER BY freshness_at DESC LIMIT 1
		`, brandID).Scan(&current)
		if err != nil {
			return 0, 0
		}
		err = e.pool.QueryRow(ctx, `
			SELECT value FROM measure.scores
			WHERE brand_id = $1 ORDER BY freshness_at DESC OFFSET 1 LIMIT 1
		`, brandID).Scan(&previous)
		if err != nil {
			return 0, current
		}
		return previous - current, current

	case "trend_decline":
		var trendVal float64
		err := e.pool.QueryRow(ctx, `
			SELECT value FROM measure.scores
			WHERE brand_id = $1 ORDER BY freshness_at DESC LIMIT 1
		`, brandID).Scan(&trendVal)
		if err != nil {
			return 0, 0
		}
		// 3 ölçüm ortalamasına göre düşüş
		var avg float64
		err = e.pool.QueryRow(ctx, `
			SELECT COALESCE(AVG(value), 0) FROM (
				SELECT value FROM measure.scores
				WHERE brand_id = $1 ORDER BY freshness_at DESC LIMIT 3
			) sub
		`, brandID).Scan(&avg)
		if err != nil {
			return 0, trendVal
		}
		return avg - trendVal, trendVal

	case "score_absolute":
		// Skor belirli bir eşiğin altında mı?
		var val float64
		err := e.pool.QueryRow(ctx, `
			SELECT COALESCE(value, 0) FROM measure.scores
			WHERE brand_id = $1 ORDER BY freshness_at DESC LIMIT 1
		`, brandID).Scan(&val)
		if err != nil {
			return 0, 0
		}
		return val, val

	default:
		return 0, 0
	}
}

func (e *RuleEngine) compare(actual float64, condition string, threshold float64) bool {
	switch condition {
	case "gt":
		return actual > threshold
	case "lt":
		return actual < threshold
	case "gte":
		return actual >= threshold
	case "lte":
		return actual <= threshold
	case "eq":
		return actual == threshold
	default:
		return false
	}
}
