package measure

import (
	"testing"

	"github.com/geolens/platform/engine"
	"github.com/geolens/platform/internal/id"
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

// ---- H15: Partial Yayın Testleri ----

// TestPartialPublication_SingleEngine validates scoring works with only 1 engine's data (2 engines failed).
func TestPartialPublication_SingleEngine(t *testing.T) {
	// Sadece 1 engine'den gelen veri (diğer 2 engine başarısız olmuş)
	partialData := []engine.RawResponse{
		{EngineName: "perplexity", Content: "Acme sektör lideridir. Yenilikçi ürünleriyle tanınır.",
			Citations: []engine.Citation{
				{URL: "https://example.com/acme", Position: 1, Engine: "perplexity", Type: "direct"},
				{URL: "https://test.org/report", Position: 2, Engine: "perplexity", Type: "direct"},
			},
		},
	}

	// Tüm component fonksiyonları partial data ile çalışmalı
	presence := computePresenceShare(partialData, "Acme")
	if presence == 0 {
		t.Error("partial data ile PresenceShare 0 olmamalı")
	}

	position := computePositionWeight(partialData)
	if position == 0 {
		t.Error("partial data ile PositionWeight 0 olmamalı")
	}

	source := computeSourceShare(partialData)
	if source == 0 {
		t.Error("partial data ile SourceShare 0 olmamalı")
	}

	competitor := computeCompetitorContext(partialData)
	if competitor == 0 {
		t.Error("partial data ile CompetitorContext 0 olmamalı")
	}

	// Engine breakdown da partial veriyle çalışmalı
	breakdown := computeEngineBreakdown(partialData)
	if len(breakdown) != 1 {
		t.Errorf("beklenen 1 engine breakdown, gerçek %d", len(breakdown))
	}
	if _, ok := breakdown["perplexity"]; !ok {
		t.Error("perplexity engine breakdown'da bulunmalı")
	}
}

// TestPartialPublication_EmptyData verifies that component functions return 0 for empty data.
func TestPartialPublication_EmptyData(t *testing.T) {
	// Hiçbir engine'dan veri gelmemesi durumu
	presence := computePresenceShare(nil, "Acme")
	if presence != 0 {
		t.Errorf("boş data ile PresenceShare 0 olmalı, gerçek %f", presence)
	}

	position := computePositionWeight(nil)
	if position != 0 {
		t.Errorf("boş data ile PositionWeight 0 olmalı, gerçek %f", position)
	}

	source := computeSourceShare(nil)
	if source != 20 {
		t.Errorf("boş data ile SourceShare 20 olmalı (default), gerçek %f", source)
	}

	competitor := computeCompetitorContext(nil)
	if competitor != 50 {
		t.Errorf("boş data ile CompetitorContext 50 olmalı (default), gerçek %f", competitor)
	}

	breakdown := computeEngineBreakdown(nil)
	if len(breakdown) != 0 {
		t.Errorf("boş data ile breakdown boş olmalı, gerçek %d", len(breakdown))
	}
}

// TestPartialPublication_MixedEngines verifies scoring with 2 engines out of 3 (one failed).
func TestPartialPublication_MixedEngines(t *testing.T) {
	data := []engine.RawResponse{
		{EngineName: "perplexity", Content: "Acme yenilikçi bir firma.",
			Citations: []engine.Citation{{URL: "https://example.com", Position: 1, Engine: "perplexity", Type: "direct"}},
		},
		{EngineName: "chatgpt", Content: "Acme pazar lideridir ve sektörde öncüdür.",
			Citations: []engine.Citation{{URL: "https://test.org", Position: 1, Engine: "chatgpt", Type: "direct"}},
		},
		// gemini: başarısız oldu, verisi yok
	}

	// 2 engine verisiyle tüm fonksiyonlar çalışmalı
	presence := computePresenceShare(data, "Acme")
	if presence == 0 {
		t.Error("mixed data ile PresenceShare 0 olmamalı")
	}

	breakdown := computeEngineBreakdown(data)
	if len(breakdown) != 2 {
		t.Errorf("beklenen 2 engine breakdown, gerçek %d", len(breakdown))
	}

	// Ağırlıklı toplam hesapla (CalculateScore'daki mantık)
	total := 0.35*computePresenceShare(data, "Acme") +
		0.25*computePositionWeight(data) +
		0.20*computeSourceShare(data) +
		0.20*computeCompetitorContext(data)

	if total <= 0 {
		t.Errorf("partial yayın toplam skoru pozitif olmalı, gerçek %f", total)
	}
}

func TestGenerateULID_Unique(t *testing.T) {
	ids := make(map[string]bool)
	for i := 0; i < 100; i++ {
		id := id.New()
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
