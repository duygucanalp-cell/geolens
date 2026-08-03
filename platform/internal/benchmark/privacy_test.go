package benchmark

import (
	"math"
	"testing"
)

func TestDefaultDPConfig_Values(t *testing.T) {
	cfg := DefaultDPConfig()
	if cfg.Epsilon != 1.0 {
		t.Errorf("Epsilon: beklenen 1.0, gerçek %f", cfg.Epsilon)
	}
	if cfg.Sensitivity != 100.0 {
		t.Errorf("Sensitivity: beklenen 100.0, gerçek %f", cfg.Sensitivity)
	}
	if cfg.ClampMin != 0.0 || cfg.ClampMax != 100.0 {
		t.Errorf("Clamp: beklenen [0,100], gerçek [%f,%f]", cfg.ClampMin, cfg.ClampMax)
	}
	if cfg.MinTenants != 5 {
		t.Errorf("MinTenants: beklenen 5, gerçek %d", cfg.MinTenants)
	}
}

func TestClamp_LowerBound(t *testing.T) {
	if got := clamp(-5.0, 0.0, 100.0); got != 0.0 {
		t.Errorf("beklenen 0.0, gerçek %f", got)
	}
}

func TestClamp_UpperBound(t *testing.T) {
	if got := clamp(150.0, 0.0, 100.0); got != 100.0 {
		t.Errorf("beklenen 100.0, gerçek %f", got)
	}
}

func TestClamp_WithinRange(t *testing.T) {
	if got := clamp(50.0, 0.0, 100.0); got != 50.0 {
		t.Errorf("beklenen 50.0, gerçek %f", got)
	}
}

func TestAddLaplaceNoise_ZeroEpsilon(t *testing.T) {
	cfg := DPConfig{Epsilon: 0, ClampMin: 0, ClampMax: 100}
	result := AddLaplaceNoise(50.0, cfg)
	// When epsilon ≤ 0, no noise is added (only clamping)
	if result != 50.0 {
		t.Errorf("beklenen 50.0 (no noise), gerçek %f", result)
	}
}

func TestAddLaplaceNoise_NegativeEpsilon(t *testing.T) {
	cfg := DPConfig{Epsilon: -1, ClampMin: 0, ClampMax: 100}
	result := AddLaplaceNoise(50.0, cfg)
	// Should be treated as zero epsilon → no noise
	if result != 50.0 {
		t.Errorf("beklenen 50.0, gerçek %f", result)
	}
}

func TestAddLaplaceNoise_ClampsToMin(t *testing.T) {
	// Use very high epsilon to produce minimal noise; but if value is already at min
	cfg := DPConfig{Epsilon: 0.01, Sensitivity: 100, ClampMin: 0, ClampMax: 100}
	result := AddLaplaceNoise(-50.0, cfg)
	if result < 0.0 {
		t.Errorf("clamp edilmeli: %f", result)
	}
}

func TestAddLaplaceNoise_ClampsToMax(t *testing.T) {
	cfg := DPConfig{Epsilon: 0.01, Sensitivity: 100, ClampMin: 0, ClampMax: 100}
	result := AddLaplaceNoise(200.0, cfg)
	if result > 100.0 {
		t.Errorf("clamp edilmeli: %f", result)
	}
}

func TestLaplaceRandom_Finite(t *testing.T) {
	// Verify that laplaceRandom always returns a finite value
	for i := 0; i < 1000; i++ {
		v := laplaceRandom(1.0)
		if math.IsInf(v, 0) || math.IsNaN(v) {
			t.Errorf("geçersiz değer: %v", v)
		}
	}
}

func TestLaplaceRandom_MeanNearZero(t *testing.T) {
	// Over many samples, the mean of Laplace(0, 1) should be approximately 0
	const samples = 10000
	var sum float64
	for i := 0; i < samples; i++ {
		sum += laplaceRandom(1.0)
	}
	mean := sum / float64(samples)
	// Within ±0.3 (statistical, may rarely fail — acceptable for unit test)
	if math.Abs(mean) > 0.3 {
		t.Errorf("Laplace mean should be near 0, got %f (samples=%d)", mean, samples)
	}
}

func TestLaplaceRandom_Scale(t *testing.T) {
	// The variance of Laplace(0, b) is 2*b^2. Standard deviation is sqrt(2)*b.
	// With b=1, expect most samples in [-5, 5]
	const samples = 10000
	var outOfRange int
	for i := 0; i < samples; i++ {
		v := laplaceRandom(1.0)
		if v < -5 || v > 5 {
			outOfRange++
		}
	}
	// With 10k samples from Laplace(0,1), P(|X|>5) ≈ 0.0067, so ~67 samples
	// Allow up to 200 to account for randomness
	ratio := float64(outOfRange) / float64(samples)
	if ratio > 0.05 {
		t.Errorf("less than 5%% should be outside [-5,5], got %.2f%% (%d/%d)", ratio*100, outOfRange, samples)
	}
}

func TestAddLaplaceNoise_StaysInRange(t *testing.T) {
	cfg := DefaultDPConfig()
	// With epsilon=1, sensitivity=100, scale=100, noise can be large
	// But clamping should keep results in [0, 100]
	const samples = 1000
	for i := 0; i < samples; i++ {
		result := AddLaplaceNoise(50.0, cfg)
		if result < 0 || result > 100 {
			t.Errorf("clamp başarısız: %f", result)
		}
	}
}

func TestAddLaplaceNoise_EpsilonEffect(t *testing.T) {
	// Lower epsilon → more noise → larger absolute deviation
	highEpsilonCfg := DPConfig{Epsilon: 10.0, Sensitivity: 100, ClampMin: 0, ClampMax: 100}
	lowEpsilonCfg := DPConfig{Epsilon: 0.1, Sensitivity: 100, ClampMin: 0, ClampMax: 100}

	const samples = 5000
	var highSum, lowSum float64
	for i := 0; i < samples; i++ {
		highSum += math.Abs(AddLaplaceNoise(50.0, highEpsilonCfg) - 50.0)
		lowSum += math.Abs(AddLaplaceNoise(50.0, lowEpsilonCfg) - 50.0)
	}
	highAvgDev := highSum / float64(samples)
	lowAvgDev := lowSum / float64(samples)

	if lowAvgDev <= highAvgDev {
		t.Errorf("düşük epsilon daha fazla gürültü eklemeli: low_avg_dev=%.2f, high_avg_dev=%.2f",
			lowAvgDev, highAvgDev)
	}
}

func TestAnonymizeSectorStats_InsufficientData(t *testing.T) {
	cfg := DefaultDPConfig()
	raw := RawSectorStats{
		MyScore:      72.0,
		SectorAvg:    54.0,
		SectorMedian: 52.0,
		SectorMin:    12.0,
		SectorMax:    95.0,
		TenantCount:  3, // < 5
	}

	stats := AnonymizeSectorStats(raw, cfg)
	if stats.SufficientData {
		t.Error("yetersiz veri için SufficientData false olmalı")
	}
	if stats.MyScore != 72.0 {
		t.Errorf("MyScore korunmalı: beklenen 72.0, gerçek %f", stats.MyScore)
	}
	if stats.TenantCount != 3 {
		t.Errorf("TenantCount: beklenen 3, gerçek %d", stats.TenantCount)
	}
	// All other fields should be zero
	if stats.SectorAvg != 0 || stats.SectorMedian != 0 {
		t.Error("yetersiz veri için istatistikler sıfırlanmalı")
	}
}

func TestAnonymizeSectorStats_AtThreshold(t *testing.T) {
	cfg := DefaultDPConfig()
	raw := RawSectorStats{
		MyScore:      72.0,
		SectorAvg:    54.0,
		SectorMedian: 52.0,
		SectorMin:    12.0,
		SectorMax:    95.0,
		SectorStdDev: 14.2,
		Percentile25: 35.0,
		Percentile75: 68.0,
		Percentile90: 82.0,
		TenantCount:  5, // = MinTenants, should be sufficient
	}

	stats := AnonymizeSectorStats(raw, cfg)
	if !stats.SufficientData {
		t.Error("5 kiracı ile SufficientData true olmalı")
	}
	if stats.MyScore != 72.0 {
		t.Errorf("MyScore korunmalı: beklenen 72.0, gerçek %f", stats.MyScore)
	}
	if stats.TenantCount != 5 {
		t.Errorf("TenantCount: beklenen 5, gerçek %d", stats.TenantCount)
	}
	// Stats should be in range [0, 100] after DP
	if stats.SectorAvg < 0 || stats.SectorAvg > 100 {
		t.Errorf("SectorAvg range dışı: %f", stats.SectorAvg)
	}
	if stats.SectorMin < 0 || stats.SectorMax > 100 {
		t.Errorf("SectorMin/Max range dışı: [%f, %f]", stats.SectorMin, stats.SectorMax)
	}
	if stats.Percentile25 < 0 || stats.Percentile90 > 100 {
		t.Errorf("Percentile range dışı: [%f, %f]", stats.Percentile25, stats.Percentile90)
	}
	// Difference should be MyScore - SectorAvg
	if stats.Difference != stats.MyScore-stats.SectorAvg {
		t.Errorf("Difference: beklenen %f, gerçek %f",
			stats.MyScore-stats.SectorAvg, stats.Difference)
	}
}

func TestAnonymizeSectorStats_MyScoreUnchanged(t *testing.T) {
	cfg := DefaultDPConfig()
	raw := RawSectorStats{
		MyScore:      42.5,
		SectorAvg:    50.0,
		SectorMedian: 48.0,
		SectorMin:    10.0,
		SectorMax:    90.0,
		TenantCount:  50,
	}

	stats := AnonymizeSectorStats(raw, cfg)
	if stats.MyScore != 42.5 {
		t.Errorf("MyScore değişmemeli: beklenen 42.5, gerçek %f", stats.MyScore)
	}
}

func TestAnonymizeSectorStats_TrendDetermination(t *testing.T) {
	// High epsilon = low noise (scale = 1 for sensitivity=100, epsilon=100)
	// Use a low-noise config so trend determination is statistically reliable
	cfg := DPConfig{
		Epsilon: 100.0, Sensitivity: 100, ClampMin: 0, ClampMax: 100, MinTenants: 5,
	}

	const iterations = 50

	// Very high above sector avg — should consistently produce "up"
	rawAbove := RawSectorStats{MyScore: 80.0, SectorAvg: 20.0, TenantCount: 10}
	upCount := 0
	for i := 0; i < iterations; i++ {
		stats := AnonymizeSectorStats(rawAbove, cfg)
		if stats.Trend == "up" {
			upCount++
		}
	}
	pct := float64(upCount) / float64(iterations) * 100
	if pct < 90.0 {
		t.Errorf("trend 'up' orani: %.1f%% (%d/%d), beklenn: >=%%90", pct, upCount, iterations)
	}

	// Very low below sector avg — should consistently produce "down"
	rawBelow := RawSectorStats{MyScore: 20.0, SectorAvg: 80.0, TenantCount: 10}
	downCount := 0
	for i := 0; i < iterations; i++ {
		stats := AnonymizeSectorStats(rawBelow, cfg)
		if stats.Trend == "down" {
			downCount++
		}
	}
	pct = float64(downCount) / float64(iterations) * 100
	if pct < 90.0 {
		t.Errorf("trend 'down' orani: %.1f%% (%d/%d), beklenn: >=%%90", pct, downCount, iterations)
	}

	// Very close to sector avg — should consistently produce "stable"
	rawStable := RawSectorStats{MyScore: 50.1, SectorAvg: 50.0, TenantCount: 10}
	stableCount := 0
	for i := 0; i < iterations; i++ {
		stats := AnonymizeSectorStats(rawStable, cfg)
		if stats.Trend == "stable" {
			stableCount++
		}
	}
	pct = float64(stableCount) / float64(iterations) * 100
	if pct < 85.0 {
		t.Errorf("trend 'stable' orani: %.1f%% (%d/%d), beklenn: >=%%85", pct, stableCount, iterations)
	}
}

func TestAnonymizeSectorStats_EdgeCaseZeroTenants(t *testing.T) {
	cfg := DefaultDPConfig()
	raw := RawSectorStats{
		MyScore:     50.0,
		TenantCount: 0,
	}
	stats := AnonymizeSectorStats(raw, cfg)
	if stats.SufficientData {
		t.Error("0 kiracı ile SufficientData false olmalı")
	}
}
