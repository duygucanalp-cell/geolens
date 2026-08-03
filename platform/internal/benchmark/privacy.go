// Package benchmark provides handlers and logic for benchmark functionality,
// including differential privacy protection for sector-level aggregations.
package benchmark

import (
	"math"
	"math/rand/v2"
)

// DPConfig holds configuration for differential privacy using the Laplace mechanism.
type DPConfig struct {
	// Epsilon is the privacy budget. Lower values = stronger privacy, more noise.
	// Typical range: 0.1 (high privacy) to 2.0 (low privacy).
	// Default: 1.0
	Epsilon float64

	// Sensitivity is the maximum possible change a single tenant's data can cause.
	// Score range [0, 100] → sensitivity = 100.
	Sensitivity float64

	// ClampMin and ClampMax define the allowable value range after noise is added.
	ClampMin float64
	ClampMax float64

	// MinTenants is the minimum number of tenants required before any data is released.
	// NFR-13 mandate: ≥5.
	MinTenants int
}

// DefaultDPConfig returns a DPConfig suitable for visibility scores in [0, 100].
func DefaultDPConfig() DPConfig {
	return DPConfig{
		Epsilon:     1.0,
		Sensitivity: 100.0,
		ClampMin:    0.0,
		ClampMax:    100.0,
		MinTenants:  5,
	}
}

// AggregatedSectorStats holds differentially-private sector benchmark statistics.
type AggregatedSectorStats struct {
	// MyScore is the requesting tenant's own score (no noise — tenant's own data).
	MyScore float64 `json:"my_score"`

	// SectorAvg is the noisy DP-protected sector average.
	SectorAvg float64 `json:"sector_average"`

	// SectorMedian is the noisy DP-protected sector median.
	SectorMedian float64 `json:"sector_median"`

	// SectorMin is the noisy DP-protected minimum.
	SectorMin float64 `json:"sector_min"`

	// SectorMax is the noisy DP-protected maximum.
	SectorMax float64 `json:"sector_max"`

	// SectorStdDev is the noisy DP-protected standard deviation (if computed).
	SectorStdDev float64 `json:"sector_stddev,omitempty"`

	// Percentile25 is the noisy DP-protected 25th percentile.
	Percentile25 float64 `json:"percentile_25,omitempty"`

	// Percentile75 is the noisy DP-protected 75th percentile.
	Percentile75 float64 `json:"percentile_75,omitempty"`

	// Percentile90 is the noisy DP-protected 90th percentile.
	Percentile90 float64 `json:"percentile_90,omitempty"`

	// Difference is MyScore - SectorAvg (computed after anonymization).
	Difference float64 `json:"difference"`

	// Trend indicates direction: "up", "down", "stable" relative to sector.
	Trend string `json:"trend,omitempty"`

	// SufficientData is true when tenantCount >= MinTenants.
	SufficientData bool `json:"sufficient_data"`

	// TenantCount is the total number of tenants in the sector (raw, not anonymized).
	TenantCount int `json:"tenant_count"`
}

// AddLaplaceNoise adds Laplace-distributed noise to a value using the given config.
// Returns clamped value within [ClampMin, ClampMax].
func AddLaplaceNoise(value float64, config DPConfig) float64 {
	if config.Epsilon <= 0 {
		return clamp(value, config.ClampMin, config.ClampMax)
	}
	scale := config.Sensitivity / config.Epsilon
	noise := laplaceRandom(scale)
	return clamp(value+noise, config.ClampMin, config.ClampMax)
}

// laplaceRandom samples from Laplace(0, scale) distribution using inverse CDF.
// F^(-1)(p) = -b * sign(p-0.5) * ln(1 - 2*|p-0.5|)
func laplaceRandom(scale float64) float64 {
	// u ∈ (0, 1) exclusive to avoid ln(0) at the boundaries
	u := rand.Float64()
	// Guard against u == 0 which would give u-0.5 == -0.5, then ln(0) == -Inf
	if u <= 0 {
		u = 1e-16
	}
	// Shift to (-0.5, 0.5) then apply Laplace inverse CDF
	u = u - 0.5
	return -scale * math.Copysign(math.Log(1-2*math.Abs(u)), u)
}

// clamp restricts value to [min, max].
func clamp(value, min, max float64) float64 {
	if value < min {
		return min
	}
	if value > max {
		return max
	}
	return value
}

// AnonymizeSectorStats applies differential privacy noise to aggregated sector statistics.
// The tenant's own score (MyScore) is returned without noise.
// If tenantCount < MinTenants, stats are zeroed and SufficientData is false.
func AnonymizeSectorStats(raw RawSectorStats, config DPConfig) AggregatedSectorStats {
	if raw.TenantCount < config.MinTenants {
		return AggregatedSectorStats{
			MyScore:        raw.MyScore,
			TenantCount:    raw.TenantCount,
			SufficientData: false,
		}
	}

	stats := AggregatedSectorStats{
		MyScore:        raw.MyScore,
		SectorAvg:      AddLaplaceNoise(raw.SectorAvg, config),
		SectorMedian:   AddLaplaceNoise(raw.SectorMedian, config),
		SectorMin:      AddLaplaceNoise(raw.SectorMin, config),
		SectorMax:      AddLaplaceNoise(raw.SectorMax, config),
		SectorStdDev:   AddLaplaceNoise(raw.SectorStdDev, config),
		Percentile25:   AddLaplaceNoise(raw.Percentile25, config),
		Percentile75:   AddLaplaceNoise(raw.Percentile75, config),
		Percentile90:   AddLaplaceNoise(raw.Percentile90, config),
		SufficientData: true,
		TenantCount:    raw.TenantCount,
	}

	// Difference is computed from anonymized values
	stats.Difference = stats.MyScore - stats.SectorAvg

	// Trend determination based on difference after DP
	if stats.Difference > 5.0 {
		stats.Trend = "up"
	} else if stats.Difference < -5.0 {
		stats.Trend = "down"
	} else {
		stats.Trend = "stable"
	}

	return stats
}

// RawSectorStats holds the raw (non-anonymized) sector statistics before DP application.
// This struct is internal — only used to feed into AnonymizeSectorStats.
type RawSectorStats struct {
	MyScore      float64
	SectorAvg    float64
	SectorMedian float64
	SectorMin    float64
	SectorMax    float64
	SectorStdDev float64
	Percentile25 float64
	Percentile75 float64
	Percentile90 float64
	TenantCount  int
}
