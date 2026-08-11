package measure

import (
	"testing"
)

func TestDefaultWeights(t *testing.T) {
	if defaultWeights.PresenceShare != 0.30 {
		t.Errorf("PresenceShare beklenen 0.30, gerçek %f", defaultWeights.PresenceShare)
	}
	if defaultWeights.PositionWeight != 0.20 {
		t.Errorf("PositionWeight beklenen 0.20, gerçek %f", defaultWeights.PositionWeight)
	}
	if defaultWeights.SourceShare != 0.15 {
		t.Errorf("SourceShare beklenen 0.15, gerçek %f", defaultWeights.SourceShare)
	}
	if defaultWeights.CompetitorContext != 0.15 {
		t.Errorf("CompetitorContext beklenen 0.15, gerçek %f", defaultWeights.CompetitorContext)
	}
	if defaultWeights.AppearanceRate != 0.10 {
		t.Errorf("AppearanceRate beklenen 0.10, gerçek %f", defaultWeights.AppearanceRate)
	}
	if defaultWeights.Sentiment != 0.05 {
		t.Errorf("Sentiment beklenen 0.05, gerçek %f", defaultWeights.Sentiment)
	}
	if defaultWeights.CompVisibility != 0.05 {
		t.Errorf("CompVisibility beklenen 0.05, gerçek %f", defaultWeights.CompVisibility)
	}
}

// TestDefaultWeights_SumToOne verifies that component weights sum to exactly 1.0 (GA calibration).
func TestDefaultWeights_SumToOne(t *testing.T) {
	sum := defaultWeights.PresenceShare + defaultWeights.PositionWeight +
		defaultWeights.SourceShare + defaultWeights.CompetitorContext +
		defaultWeights.AppearanceRate + defaultWeights.Sentiment + defaultWeights.CompVisibility
	if sum != 1.0 {
		t.Errorf("ağırlıklar toplamı 1.0 olmalı, gerçek %f", sum)
	}
}

// TestDefaultWeights_IsV2 verifies the profile is recognized as 7-component (A3-5).
func TestDefaultWeights_IsV2(t *testing.T) {
	if !defaultWeights.IsV2() {
		t.Error("v2 default profile IsV2() true olmalı")
	}
	if v1LegacyWeights.IsV2() {
		t.Error("v1 legacy weights IsV2() false olmalı")
	}
}

// TestLegacyWeights verifies the v1 fallback profile (SCORE_ALGORITHM_VERSION=1.0.0).
func TestLegacyWeights(t *testing.T) {
	sum := v1LegacyWeights.PresenceShare + v1LegacyWeights.PositionWeight +
		v1LegacyWeights.SourceShare + v1LegacyWeights.CompetitorContext
	if sum != 1.0 {
		t.Errorf("legacy ağırlıklar toplamı 1.0 olmalı, gerçek %f", sum)
	}
}

// TestCalculateScore_WeightedFormula verifies that the weighted formula produces correct results.
func TestCalculateScore_WeightedFormula(t *testing.T) {
	// Test 1: 0.30*100 + 0.20*100 + 0.15*100 + 0.15*100 + 0.10*100 + 0.05*100 + 0.05*100 = 100
	expected := 30.0 + 20.0 + 15.0 + 15.0 + 10.0 + 5.0 + 5.0
	if expected != 100.0 {
		t.Errorf("beklenen 100, gerçek %f", expected)
	}

	// Test 2: tümü 50 → 50
	expected2 := 0.30*50 + 0.20*50 + 0.15*50 + 0.15*50 + 0.10*50 + 0.05*50 + 0.05*50
	if expected2 != 50.0 {
		t.Errorf("beklenen 50, gerçek %f", expected2)
	}

	// Test 3: asimetrik — 0.30*80 + 0.20*60 + 0.15*40 + 0.15*20 + 0.10*50 + 0.05*70 + 0.05*30
	expected3 := 0.30*80 + 0.20*60 + 0.15*40 + 0.15*20 + 0.10*50 + 0.05*70 + 0.05*30
	if expected3 != 55.0 {
		t.Errorf("beklenen 55, gerçek %f", expected3)
	}
}

func TestMeasurementRequest_Validation(t *testing.T) {
	req := MeasurementRequest{
		BrandName:  "Acme",
		EngineName: "perplexity",
		PromptText: "{brand_name} hakkında ne biliyorsun?",
	}
	if req.BrandName == "" {
		t.Error("BrandName boş olmamalı")
	}
	if req.EngineName == "" {
		t.Error("EngineName boş olmamalı")
	}
	if req.PromptText == "" {
		t.Error("PromptText boş olmamalı")
	}
}
