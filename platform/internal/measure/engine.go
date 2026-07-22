package measure

import (
	"context"
	"time"

	"github.com/geolens/platform/engine"
)

// ---- Domain Types ----

// Score represents a computed visibility score for a brand within a panel.
type Score struct {
	ID             string    `json:"id"`
	PanelID        string    `json:"panel_id"`
	BrandID        string    `json:"brand_id"`
	WorkspaceID    string    `json:"workspace_id"`
	TenantID       string    `json:"tenant_id"`
	Value          float64   `json:"value"`
	CILow          float64   `json:"ci_low"`
	CIHigh         float64   `json:"ci_high"`
	FidelityLabel  string    `json:"fidelity_label"`
	EngineBreakdown map[string]float64 `json:"engine_breakdown,omitempty"`
	PanelVersion   string    `json:"panel_version"`
	CalculationRunID string  `json:"calculation_run_id"`
	FreshnessAt    time.Time `json:"freshness_at"`
	CreatedAt      time.Time `json:"created_at"`
}

// CalculationRun represents a single deterministic computation of a score.
type CalculationRun struct {
	ID              string    `json:"id"`
	PanelID         string    `json:"panel_id"`
	ScoreComponents map[string]float64 `json:"score_components"`
	AlgorithmVersion string   `json:"algorithm_version"`
	InputSnapshot   string    `json:"input_snapshot"`
	CreatedAt       time.Time `json:"created_at"`
}

// ComponentWeights defines the weight of each score component.
type ComponentWeights struct {
	PresenceShare   float64 // Varlık Payı — default 0.35
	PositionWeight  float64 // Konum Ağırlığı — default 0.25
	SourceShare     float64 // Kaynak Payı — default 0.20
	CompetitorContext float64 // Rakip Bağlamı — default 0.20
}

// ---- Engine Interface ----

// MeasurementRequest is the input to a single measurement operation.
type MeasurementRequest struct {
	BrandName    string            `json:"brand_name"`
	WebsiteURL   string            `json:"website_url"`
	PromptText   string            `json:"prompt_text"`
	EngineName   string            `json:"engine_name"`
	TenantID     string            `json:"tenant_id"`
	WorkspaceID  string            `json:"workspace_id"`
	PanelID      string            `json:"panel_id"`
	JobID        string            `json:"job_id"`
}

// MeasurementResult is the output of a single measurement operation.
type MeasurementResult struct {
	RawResponses []engine.RawResponse `json:"raw_responses"`
	Citations    []engine.Citation    `json:"citations"`
	EngineMeta   engine.EngineMeta    `json:"engine_meta"`
	BrandName    string               `json:"brand_name"`
}

// Service defines the interface for the measurement/scoring engine.
type Service interface {
	// Measure executes a single measurement for one engine with n=3 sampling.
	Measure(ctx context.Context, req MeasurementRequest) (*MeasurementResult, error)

	// CalculateScore computes the visibility score from measurement results.
	CalculateScore(ctx context.Context, panelID string, results []MeasurementResult, weights ComponentWeights) (*Score, error)

	// GetScoreByID retrieves a previously computed score.
	GetScoreByID(ctx context.Context, scoreID string) (*Score, error)
}
