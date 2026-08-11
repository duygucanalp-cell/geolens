// Package measure provides measure functionality.
package measure

import (
	"context"
	"time"

	"github.com/geolens/platform/engine"
)

// ---- Domain Types ----

// Score represents a computed visibility score for a brand within a panel.
type Score struct {
	ID               string             `json:"id"`
	PanelID          string             `json:"panel_id"`
	BrandID          string             `json:"brand_id"`
	WorkspaceID      string             `json:"workspace_id"`
	TenantID         string             `json:"tenant_id"`
	Value            float64            `json:"value"`
	CILow            float64            `json:"ci_low"`
	CIHigh           float64            `json:"ci_high"`
	FidelityLabel    string             `json:"fidelity_label"`
	EngineBreakdown  map[string]float64 `json:"engine_breakdown,omitempty"`
	PanelVersion     string             `json:"panel_version"`
	CalculationRunID string             `json:"calculation_run_id"`
	FreshnessAt      time.Time          `json:"freshness_at"`
	CreatedAt        time.Time          `json:"created_at"`
}

// CalculationRun represents a single deterministic computation of a score.
type CalculationRun struct {
	ID               string             `json:"id"`
	PanelID          string             `json:"panel_id"`
	ScoreComponents  map[string]float64 `json:"score_components"`
	AlgorithmVersion string             `json:"algorithm_version"`
	InputSnapshot    string             `json:"input_snapshot"`
	CreatedAt        time.Time          `json:"created_at"`
}

// ComponentWeights defines the weight of each score component.
// v2 (A3-5, 0409 v1.3): 7 bileşen — Varlık %30, Konum %20, Kaynak %15, Rakip %15,
// Appearance %10, Sentiment %5, CompVis %5. v1 legacy: ilk 4 alan dolu, son 3 = 0.
type ComponentWeights struct {
	PresenceShare     float64 // Varlık Payı
	PositionWeight    float64 // Konum Ağırlığı
	SourceShare       float64 // Kaynak Payı
	CompetitorContext float64 // Rakip Bağlamı
	AppearanceRate    float64 // Appearance Rate (v2)
	Sentiment         float64 // Sentiment / Algı (v2)
	CompVisibility    float64 // Competitive Visibility (v2)
}

// IsV2 reports whether the profile uses the 7-component weights (any of the
// v2-only fields is non-zero).
func (w ComponentWeights) IsV2() bool {
	return w.AppearanceRate != 0 || w.Sentiment != 0 || w.CompVisibility != 0
}

// ---- Engine Interface ----

// MeasurementRequest is the input to a single measurement operation.
type MeasurementRequest struct {
	BrandID     string `json:"brand_id"`
	BrandName   string `json:"brand_name"`
	WebsiteURL  string `json:"website_url"`
	PromptText  string `json:"prompt_text"`
	EngineName  string `json:"engine_name"`
	TenantID    string `json:"tenant_id"`
	WorkspaceID string `json:"workspace_id"`
	PanelID     string `json:"panel_id"`
	JobID       string `json:"job_id"`
}

// MeasurementResult is the output of a single measurement operation.
type MeasurementResult struct {
	RawResponses []engine.RawResponse `json:"raw_responses"`
	Citations    []engine.Citation    `json:"citations"`
	EngineMeta   engine.EngineMeta    `json:"engine_meta"`
	BrandID      string               `json:"brand_id"`
	BrandName    string               `json:"brand_name"`
	PanelID      string               `json:"panel_id"`
	WorkspaceID  string               `json:"workspace_id"`
	TenantID     string               `json:"tenant_id"`
	// PromptText — ölçümde kullanılan prompt (0421 A3-3 intent tabanlı
	// ağırlıklandırma için serving'e gönderilir).
	PromptText string `json:"prompt_text"`
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
