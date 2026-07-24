package measure

import (
	"testing"

	"github.com/geolens/platform/engine"
)

func TestComputePresenceShare_BrandMentioned(t *testing.T) {
	resp := []engine.RawResponse{
		{Content: "Acme şirketi harika bir ürün sunuyor. Acme pazar lideridir."},
		{Content: "Rakipler arasında Acme en yenilikçi olanıdır."},
		{Content: "Sektörde birçok firma var."},
	}
	score := computePresenceShare(resp, "Acme")
	if score != 66.66666666666666 {
		t.Errorf("beklenen 66.66, gerçek %f", score)
	}
}

func TestComputePresenceShare_NoBrand(t *testing.T) {
	resp := []engine.RawResponse{
		{Content: "Sektördeki en büyük firma hakkında bilgi."},
		{Content: "Pazar durumu değerlendirmesi."},
	}
	score := computePresenceShare(resp, "Acme")
	if score != 0 {
		t.Errorf("beklenen 0, gerçek %f", score)
	}
}

func TestComputePresenceShare_EmptyResponses(t *testing.T) {
	score := computePresenceShare(nil, "Acme")
	if score != 0 {
		t.Errorf("beklenen 0, gerçek %f", score)
	}
}

func TestComputePositionWeight_EarlyPosition(t *testing.T) {
	resp := []engine.RawResponse{
		{Content: "Acme pazar lideridir."},
	}
	score := computePositionWeight(resp)
	if score != 90 {
		t.Errorf("beklenen 90, gerçek %f", score)
	}
}

func TestComputePositionWeight_MidPosition(t *testing.T) {
	content := ""
	for i := 0; i < 15; i++ {
		content += "Bu bir orta konum test cümlesidir. "
	}
	resp := []engine.RawResponse{{Content: content}}
	score := computePositionWeight(resp)
	if score != 60 {
		t.Errorf("beklenen 60, gerçek %f", score)
	}
}

func TestComputePositionWeight_LatePosition(t *testing.T) {
	content := ""
	for i := 0; i < 100; i++ {
		content += "Bu bir geç konum test cümlesidir. "
	}
	resp := []engine.RawResponse{{Content: content}}
	score := computePositionWeight(resp)
	if score != 30 {
		t.Errorf("beklenen 30, gerçek %f", score)
	}
}

func TestComputeSourceShare_DiverseSources(t *testing.T) {
	resp := []engine.RawResponse{{
		Citations: []engine.Citation{
			{URL: "https://example.com"},
			{URL: "https://test.org"},
			{URL: "https://sample.net"},
			{URL: "https://demo.com"},
			{URL: "https://wiki.org"},
		},
	}}
	score := computeSourceShare(resp)
	if score != 100 {
		t.Errorf("beklenen 100, gerçek %f", score)
	}
}

func TestComputeSourceShare_NoCitations(t *testing.T) {
	resp := []engine.RawResponse{{Citations: nil}}
	score := computeSourceShare(resp)
	if score != 20 {
		t.Errorf("beklenen 20, gerçek %f", score)
	}
}

func TestAggregateFidelity_LowestTier(t *testing.T) {
	resp := []engine.RawResponse{
		{Tier: engine.TierDirect, FidelityLabel: "Kademe 1"},
		{Tier: engine.TierDirectional, FidelityLabel: "Kademe 3"},
	}
	label := aggregateFidelity(resp)
	if label != "Kademe 1" {
		t.Errorf("beklenen 'Kademe 1', gerçek %s", label)
	}
}

func TestAggregateFidelity_Empty(t *testing.T) {
	label := aggregateFidelity(nil)
	if label != "unknown" {
		t.Errorf("beklenen 'unknown', gerçek %s", label)
	}
}

func TestExtractDomain(t *testing.T) {
	tests := []struct {
		url      string
		expected string
	}{
		{"https://www.example.com/path", "example.com"},
		{"http://test.org", "test.org"},
		{"https://sub.domain.co.uk/page", "sub.domain.co.uk"},
		{"", ""},
	}
	for _, tt := range tests {
		got := extractDomain(tt.url)
		if got != tt.expected {
			t.Errorf("extractDomain(%q) = %q, beklenen %q", tt.url, got, tt.expected)
		}
	}
}

func TestGenerateULID_Unique(t *testing.T) {
	ids := make(map[string]bool)
	for i := 0; i < 100; i++ {
		id := generateULID()
		if ids[id] {
			t.Errorf("yinelenen ULID üretildi: %s", id)
		}
		ids[id] = true
	}
	if len(ids) != 100 {
		t.Errorf("beklenen 100 unique ULID, gerçek %d", len(ids))
	}
	// ULID formatı: 26 karakterli base32 (örn. 01ARZ3NDEKTSV4RRFFQ69G5FAV)
	first := ""
	for id := range ids {
		first = id
		break
	}
	if len(first) != 26 {
		t.Errorf("ULID 26 karakter olmalı, gerçek %d: %s", len(first), first)
	}
}
