package measure

import (
	"testing"

	"github.com/geolens/platform/engine"
	"github.com/geolens/platform/internal/config"
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
	// 0309 §6.2: tek motor da olsa weighted_average üretilir (deterministik).
	if len(breakdown) != 2 {
		t.Errorf("beklenen 1 engine + weighted_average = 2 breakdown, gerçek %d", len(breakdown))
	}
	if _, ok := breakdown["perplexity"]; !ok {
		t.Error("perplexity engine breakdown'da bulunmalı")
	}
	if _, ok := breakdown["weighted_average"]; !ok {
		t.Error("weighted_average breakdown'da bulunmalı (0309 §6.2)")
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
	// 0309 §6.2: veri yoksa weighted_average de üretilmez (boş kalır).
	if len(breakdown) != 0 {
		t.Errorf("boş data ile breakdown boş olmalı, gerçek %d", len(breakdown))
	}
	if _, ok := breakdown["weighted_average"]; ok {
		t.Error("boş data ile weighted_average üretilmemeli")
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
	// 0309 §6.2: per-motor ağırlıklı weighted_average da breakdown'a eklenir.
	if len(breakdown) != 3 {
		t.Errorf("beklenen 2 engine + weighted_average = 3 breakdown, gerçek %d", len(breakdown))
	}
	if _, ok := breakdown["weighted_average"]; !ok {
		t.Error("weighted_average breakdown'da bulunmalı (0309 §6.2)")
	}

	// Ağırlıklı toplam hesapla (CalculateScore'daki mantık) — v2 default weights
	total := 0.30*computePresenceShare(data, "Acme") +
		0.20*computePositionWeight(data) +
		0.15*computeSourceShare(data) +
		0.15*computeCompetitorContext(data) +
		0.10*computeAppearanceRate(data) +
		0.05*computeSentimentScore(data) +
		0.05*computeCompVisibility(data, "Acme")

	if total <= 0 {
		t.Errorf("partial yayın toplam skoru pozitif olmalı, gerçek %f", total)
	}
}

// ---- H15: Determinizm Testleri (ADR-012 Kriter 1) ----
// G2 ilkesi: aynı girdi → aynı skor. Partial yayın dahil hiçbir rastgelelik skoru etkilemez.

// TestCalculateScore_Deterministic verifies that identical inputs always produce identical scores.
func TestCalculateScore_Deterministic(t *testing.T) {
	data := []engine.RawResponse{
		{EngineName: "perplexity", Content: "Acme sektör lideridir ve yenilikçi ürünleriyle tanınır.",
			Citations: []engine.Citation{{URL: "https://example.com/acme", Position: 1, Engine: "perplexity"}}},
		{EngineName: "chatgpt", Content: "Acme pazar lideridir.",
			Citations: []engine.Citation{{URL: "https://test.org", Position: 1, Engine: "chatgpt"}}},
	}

	first := computeTotalScore(data, "Acme", ComponentWeights{})
	second := computeTotalScore(data, "Acme", ComponentWeights{})

	if first != second {
		t.Errorf("aynı girdi farklı skor üretti: %f != %f", first, second)
	}

	// Bileşenler de deterministik olmalı
	if computePresenceShare(data, "Acme") != computePresenceShare(data, "Acme") {
		t.Error("PresenceShare deterministik değil")
	}
	if computePositionWeight(data) != computePositionWeight(data) {
		t.Error("PositionWeight deterministik değil")
	}
	if computeSourceShare(data) != computeSourceShare(data) {
		t.Error("SourceShare deterministik değil")
	}
	if computeCompetitorContext(data) != computeCompetitorContext(data) {
		t.Error("CompetitorContext deterministik değil")
	}
}

// TestPartialPublication_DeterministicRecompute verifies that recomputing with the
// same partial data (some engines failed) yields bit-identical scores.
func TestPartialPublication_DeterministicRecompute(t *testing.T) {
	// 1/3 motor başarılı (2 motor başarısız — partial yayın)
	partial := []engine.RawResponse{
		{EngineName: "perplexity", Content: "Acme yenilikçi bir firma.",
			Citations: []engine.Citation{{URL: "https://example.com", Position: 1, Engine: "perplexity"}}},
	}

	// Aynı partial veriyle tekrar tekrar hesapla — her seferinde aynı skor
	var prev float64 = -1
	for i := 0; i < 5; i++ {
		total := computeTotalScore(partial, "Acme", ComponentWeights{})
		if i > 0 && total != prev {
			t.Fatalf("partial yayın yeniden hesapta farklı skor: %f != %f", total, prev)
		}
		prev = total
	}

	// 2/3 motor başarılı partial durum
	mixed := append([]engine.RawResponse{}, partial...)
	mixed = append(mixed, engine.RawResponse{
		EngineName: "chatgpt",
		Content:    "Acme pazar lideridir.",
		Citations:  []engine.Citation{{URL: "https://test.org", Position: 1, Engine: "chatgpt"}},
	})

	run1 := computeTotalScore(mixed, "Acme", ComponentWeights{})
	run2 := computeTotalScore(mixed, "Acme", ComponentWeights{})
	if run1 != run2 {
		t.Errorf("2 motorlu partial durum deterministik değil: %f != %f", run1, run2)
	}
}

// TestCalculateScore_ScoreRange verifies the score stays in [0, 100] for edge inputs.
func TestCalculateScore_ScoreRange(t *testing.T) {
	// Tüm bileşenler 0 (marka hiç geçmiyor, alıntı yok) → alt sınır
	empty := []engine.RawResponse{{
		EngineName: "perplexity",
		Content:    "Sektördeki en büyük firma hakkında bilgi.",
	}}
	total := computeTotalScore(empty, "Acme", ComponentWeights{})
	if total < 0 {
		t.Errorf("skor 0'ın altına düşmemeli: %f", total)
	}

	// Çok yüksek bileşenler → üst sınır 100
	high := []engine.RawResponse{{
		EngineName: "perplexity",
		Content:    "Acme Acme Acme Acme Acme Acme Acme Acme Acme Acme Acme Acme",
		Citations: []engine.Citation{
			{URL: "https://a.com"}, {URL: "https://b.org"}, {URL: "https://c.net"},
			{URL: "https://d.io"}, {URL: "https://e.co"},
		},
	}}
	if total := computeTotalScore(high, "Acme", ComponentWeights{}); total > 100 {
		t.Errorf("skor 100'ün üzerine çıkmamalı: %f", total)
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

// ---- A3-5: 7 bileşenli VI feature flag testleri ----

func TestEffectiveWeights_V2Default(t *testing.T) {
	s := &service{cfg: &config.Config{ScoreAlgorithmVersion: "2.0.0"}}
	w := s.effectiveWeights()
	if !w.IsV2() {
		t.Error("SCORE_ALGORITHM_VERSION=2.0.0 → v2 profile olmalı")
	}
}

func TestEffectiveWeights_V1Legacy(t *testing.T) {
	s := &service{cfg: &config.Config{ScoreAlgorithmVersion: "1.0.0"}}
	w := s.effectiveWeights()
	if w.IsV2() {
		t.Error("SCORE_ALGORITHM_VERSION=1.0.0 → v1 legacy profile olmalı")
	}
	if w.PresenceShare != 0.35 {
		t.Errorf("v1 PresenceShare 0.35 olmalı, gerçek %f", w.PresenceShare)
	}
}

func TestEffectiveWeights_V2EnvOverride(t *testing.T) {
	s := &service{cfg: &config.Config{
		ScoreAlgorithmVersion: "2.0.0",
		ScoreWeightsRaw:       "0.25,0.20,0.15,0.15,0.10,0.10,0.05",
	}}
	w := s.effectiveWeights()
	if w.PresenceShare != 0.25 || w.Sentiment != 0.10 {
		t.Errorf("v2 env override uygulanmadı: %+v", w)
	}
	if !w.IsV2() {
		t.Error("v2 env override IsV2() olmalı")
	}
}

func TestComputeComponentScores_V2_SevenValues(t *testing.T) {
	data := []engine.RawResponse{
		{EngineName: "perplexity", Content: "Acme yenilikçi bir firma.",
			Citations: []engine.Citation{{URL: "https://example.com", Position: 1, Engine: "perplexity"}}},
	}
	p, po, s, co, a, se, cv := computeComponentScores(data, "Acme")
	if p == 0 || po == 0 || s == 0 || co == 0 {
		t.Error("ilk 4 bileşen hesaplanmalı")
	}
	if a == 0 {
		t.Error("appearance hesaplanmalı")
	}
	if se != 50 {
		t.Errorf("sentiment nötr 50 varsayılmalı, gerçek %f", se)
	}
	if cv == 0 {
		t.Error("compvis hesaplanmalı")
	}
}

func TestComputeTotalScore_V2VsV1(t *testing.T) {
	data := []engine.RawResponse{
		{EngineName: "perplexity", Content: "Acme pazar lideri.", Citations: []engine.Citation{{URL: "https://a.com"}}},
		{EngineName: "chatgpt", Content: "Acme yenilikçi."},
	}
	v2 := computeTotalScore(data, "Acme", v2DefaultWeights)
	v1 := computeTotalScore(data, "Acme", v1LegacyWeights)
	if v2 <= 0 || v1 <= 0 {
		t.Fatalf("skorlar pozitif olmalı: v1=%f v2=%f", v1, v2)
	}
	if v1 == v2 {
		t.Logf("nota: v1 (%v) == v2 (%v) aynı bileşen seti üzerinde mümkün", v1, v2)
	}
}

func TestComputeScoreCI_V1FixedV2Dynamic(t *testing.T) {
	lo1, hi1 := computeScoreCI(50, v1LegacyWeights)
	if hi1-lo1 != 10.0 {
		t.Errorf("v1 CI ±5 olmalı, fark=%f", hi1-lo1)
	}
	lo2, hi2 := computeScoreCI(50, v2DefaultWeights)
	if hi2-lo2 <= 0 {
		t.Errorf("v2 CI geçersiz: %f-%f", lo2, hi2)
	}
}

// ---- 0309 §6.2: Per-motor ağırlıklı weighted_average testleri ----

func TestComputeEngineBreakdown_WeightedAverage(t *testing.T) {
	// perplexity ve gemini yanıt verdi (chatgpt başarısız — partial yayın)
	data := []engine.RawResponse{
		{EngineName: "perplexity", Content: "Acme yenilikçi bir firma."},
		{EngineName: "gemini", Content: "Acme pazar lideridir."},
	}
	bd := computeEngineBreakdown(data)
	// 0309 §6.2 ağırlıkları: perplexity 0.30, gemini 0.25. İkisi de 75 puan.
	// weighted_average = (75×0.30 + 75×0.25) / (0.30+0.25) = 75
	if bd["weighted_average"] != 75 {
		t.Errorf("beklenen 75, gerçek %f", bd["weighted_average"])
	}
}

func TestComputeEngineBreakdown_WeightedAverage_DifferentScores(t *testing.T) {
	// perplexity dolu (75), chatgpt boş (40). Ağırlıklar: 0.30/0.30.
	data := []engine.RawResponse{
		{EngineName: "perplexity", Content: "Acme yenilikçi."},
		{EngineName: "chatgpt", Content: ""},
	}
	bd := computeEngineBreakdown(data)
	want := (75*0.30 + 40*0.30) / 0.60 // 57.5
	if bd["weighted_average"] != want {
		t.Errorf("beklenen %v, gerçek %v", want, bd["weighted_average"])
	}
}

func TestComputeEngineBreakdown_UnknownEngineEqualWeight(t *testing.T) {
	// Bilinmeyen motor (registry'ye sonradan eklenmiş) — eşit ağırlıkla katılır.
	data := []engine.RawResponse{
		{EngineName: "perplexity", Content: "Acme yenilikçi."},
		{EngineName: "future_engine", Content: "Acme lider."},
	}
	bd := computeEngineBreakdown(data)
	// İkisi de 75; ağırlık dağılımı ne olursa olsun sonuç 75 olmalı (simetrik).
	if bd["weighted_average"] != 75 {
		t.Errorf("beklenen 75, gerçek %f", bd["weighted_average"])
	}
}

func TestParseScoreWeightsV2(t *testing.T) {
	cfg := config.Config{ScoreWeightsRaw: "0.25,0.20,0.15,0.15,0.10,0.10,0.05"}
	w, ok := cfg.ParseScoreWeightsV2()
	if !ok {
		t.Fatal("geçerli 7'li girdi parsellemeli")
	}
	if w.Presence != 0.25 || w.Sentiment != 0.10 {
		t.Errorf("parselleme hatası: %+v", w)
	}
	bad := config.Config{ScoreWeightsRaw: "0.5,0.5"}
	if _, ok := bad.ParseScoreWeightsV2(); ok {
		t.Error("4 elemanlı girdi v2 parsellememeli")
	}
}
