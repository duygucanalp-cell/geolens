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
