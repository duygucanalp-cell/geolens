package recommendation

import "time"

// ---- Data Sources for Condition Evaluation ----

// ScoreSnapshot represents a score record for evaluation.
type ScoreSnapshot struct {
	Value           float64            `json:"value"`
	PreviousValue   float64            `json:"previous_value,omitempty"`
	FreshnessAt     time.Time          `json:"freshness_at"`
	PreviousAt      time.Time          `json:"previous_at,omitempty"`
	EngineBreakdown map[string]float64 `json:"engine_breakdown,omitempty"`
}

// AuditSnapshot represents the latest audit results for a brand.
type AuditSnapshot struct {
	HasData             bool    `json:"has_data"`
	OverallScore        float64 `json:"overall_score,omitempty"`
	RobotsDisallowedAll bool    `json:"robots_disallowed_all"`
	HasStructuredData   bool    `json:"has_structured_data"`
	BotAccessible       bool    `json:"bot_accessible"`
}

// EvaluationContext holds all the data needed to evaluate conditions for a brand.
type EvaluationContext struct {
	BrandID     string         `json:"brand_id"`
	BrandName   string         `json:"brand_name"`
	WorkspaceID string         `json:"workspace_id"`
	TenantID    string         `json:"tenant_id"`
	Score       *ScoreSnapshot `json:"score,omitempty"`
	Audit       *AuditSnapshot `json:"audit,omitempty"`
}

// ---- Domain Types ----

// Severity represents the importance level of a recommendation.
type Severity string

const (
	SeverityCritical Severity = "critical"
	SeverityHigh     Severity = "high"
	SeverityMedium   Severity = "medium"
	SeverityLow      Severity = "low"
)

// Category represents the category of a recommendation.
type Category string

const (
	CategoryVisibility Category = "visibility"
	CategoryContent    Category = "content"
	CategoryTechnical  Category = "technical"
	CategoryCompetitor Category = "competitor"
)

// Condition represents a single condition in a recommendation rule.
type Condition struct {
	Field    string      `json:"field"`    // e.g. "score.value", "audit.robots_txt.disallowed_all"
	Operator string      `json:"operator"` // e.g. "lt", "gt", "eq", "contains"
	Value    interface{} `json:"value"`
}

// Recommendation represents a single actionable suggestion.
type Recommendation struct {
	ID          string    `json:"id"`
	TenantID    string    `json:"tenant_id"`
	WorkspaceID string    `json:"workspace_id"`
	BrandID     string    `json:"brand_id"`
	Category    Category  `json:"category"`
	Severity    Severity  `json:"severity"`
	Title       string    `json:"title"`
	Detail      string    `json:"detail"`
	ActionURL   string    `json:"action_url,omitempty"`
	Score       float64   `json:"score"` // confidence score 0-100
	Applied     bool      `json:"applied"`
	Dismissed   bool      `json:"dismissed"`
	CreatedAt   time.Time `json:"created_at"`
}

// Rule represents a recommendation rule (condition → suggestion).
type Rule struct {
	ID          string      `json:"id"`
	Name        string      `json:"name"`
	Description string      `json:"description"`
	Category    Category    `json:"category"`
	Severity    Severity    `json:"severity"`
	Conditions  []Condition `json:"conditions"`
	Title       string      `json:"title"`
	Detail      string      `json:"detail"`
	ActionURL   string      `json:"action_url,omitempty"`
	Active      bool        `json:"active"`
}

// ---- Service Interface ----

// Service defines the interface for the recommendation engine.
type Service interface {
	// Evaluate evaluates all rules against the given context and returns matching recommendations.
	Evaluate(brandID, workspaceID, tenantID string) ([]Recommendation, error)

	// EvaluateAll evaluates rules for all brands in a workspace.
	EvaluateAll(workspaceID, tenantID string) ([]Recommendation, error)

	// GetRules returns all registered rules.
	GetRules() []Rule

	// MarkApplied marks a recommendation as applied.
	MarkApplied(id string) error

	// MarkDismissed marks a recommendation as dismissed.
	MarkDismissed(id string) error
}
