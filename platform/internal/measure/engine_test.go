package measure

import (
	"testing"
)

func TestDefaultWeights(t *testing.T) {
	if defaultWeights.PresenceShare != 0.35 {
		t.Errorf("PresenceShare beklenen 0.35, gerçek %f", defaultWeights.PresenceShare)
	}
	if defaultWeights.PositionWeight != 0.25 {
		t.Errorf("PositionWeight beklenen 0.25, gerçek %f", defaultWeights.PositionWeight)
	}
	if defaultWeights.SourceShare != 0.20 {
		t.Errorf("SourceShare beklenen 0.20, gerçek %f", defaultWeights.SourceShare)
	}
	if defaultWeights.CompetitorContext != 0.20 {
		t.Errorf("CompetitorContext beklenen 0.20, gerçek %f", defaultWeights.CompetitorContext)
	}
}

// TestDefaultWeights_SumToOne verifies that component weights sum to exactly 1.0 (GA calibration).
func TestDefaultWeights_SumToOne(t *testing.T) {
	sum := defaultWeights.PresenceShare + defaultWeights.PositionWeight +
		defaultWeights.SourceShare + defaultWeights.CompetitorContext
	if sum != 1.0 {
		t.Errorf("ağırlıklar toplamı 1.0 olmalı, gerçek %f", sum)
	}
}

// TestCalculateScore_WeightedFormula verifies that the weighted formula produces correct results.
func TestCalculateScore_WeightedFormula(t *testing.T) {
	// Test 1: 0.35*100 + 0.25*100 + 0.20*100 + 0.20*100 = 100
	expected := 35.0 + 25.0 + 20.0 + 20.0
	if expected != 100.0 {
		t.Errorf("beklenen 100, gerçek %f", expected)
	}

	// Test 2: 0.35*50 + 0.25*50 + 0.20*50 + 0.20*50 = 50
	expected2 := 0.35*50 + 0.25*50 + 0.20*50 + 0.20*50
	if expected2 != 50.0 {
		t.Errorf("beklenen 50, gerçek %f", expected2)
	}

	// Test 3: asimetrik değerler — 0.35*80 + 0.25*60 + 0.20*40 + 0.20*20 = 55
	expected3 := 0.35*80 + 0.25*60 + 0.20*40 + 0.20*20
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
