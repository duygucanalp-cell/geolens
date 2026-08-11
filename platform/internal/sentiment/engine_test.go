package sentiment

import (
	"context"
	"encoding/json"
	"fmt"
	"math"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/geolens/platform/internal/ml"
)

func abs(v float64) float64 {
	return math.Abs(v)
}

// TestAnalyzeWithML_NilClientRuleBased: ML serving yapılandırılmamışsa (ml=nil)
// kural tabanlı analyzeText çalışır.
func TestAnalyzeWithML_NilClientRuleBased(t *testing.T) {
	e := &Engine{ml: nil}
	responses := []rawResp{{ID: "r1", Content: "Acme harika bir ürün"}}
	res := e.analyzeWithML(context.Background(), "chatgpt", "brand-1", responses)
	if res.OverallSentiment != 1.0 {
		t.Errorf("beklenen 1.0 (positive), gerçek %f", res.OverallSentiment)
	}
	if res.PositiveScore != 1.0 {
		t.Errorf("beklenen PositiveScore 1.0, gerçek %f", res.PositiveScore)
	}
	if res.EngineName != "chatgpt" || res.BrandID != "brand-1" {
		t.Errorf("meta alanları korunmalı: %+v", res)
	}
}

// TestAnalyzeWithML_MLFirst: serving ayakta ise transformer sonucu kullanılır.
func TestAnalyzeWithML_MLFirst(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// logits [neg, nötr, poz] → positive
		_, _ = w.Write([]byte(`{"model":"sentiment","model_version":"1.0.0","outputs":{"logits":[[0.1,0.2,2.1]]}}`))
	}))
	defer srv.Close()

	e := NewEngineWithML(nil, ml.NewClient(srv.URL, 0))
	res := e.analyzeWithML(context.Background(), "gemini", "brand-1",
		[]rawResp{{ID: "r1", Content: "bu metin serving ile analiz edilir"}})
	if res.PositiveScore < 0.7 {
		t.Errorf("ML sonucu positive olmalı, gerçek PositiveScore %f", res.PositiveScore)
	}
	if res.MentionCount != 1 {
		t.Errorf("tek yanıt için MentionCount 1 olmalı, gerçek %d", res.MentionCount)
	}
	// softmax olasılıkları [neg, nötr, poz] olarak yansımalı ve toplamı 1 olmalı
	sum := res.NegativeScore + res.NeutralScore + res.PositiveScore
	if sum < 0.999 || sum > 1.001 {
		t.Errorf("skorlar toplamı 1 olmalı, gerçek %f (%+v)", sum, res)
	}
}

// TestAnalyzeWithML_PerResponseAggregation: her yanıt ayrı analiz edilir ve
// kelime sayısıyla ağırlıklı ortalama birleştirilir (128 token kırpma sorunu çözümü).
func TestAnalyzeWithML_PerResponseAggregation(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var payload struct {
			Text string `json:"text"`
		}
		_ = json.NewDecoder(r.Body).Decode(&payload)
		if strings.Contains(payload.Text, "kötü") {
			_, _ = w.Write([]byte(`{"model":"sentiment","model_version":"1.0.0","outputs":{"logits":[[2.0,0.1,0.1]]}}`))
			return
		}
		_, _ = w.Write([]byte(`{"model":"sentiment","model_version":"1.0.0","outputs":{"logits":[[0.1,0.1,2.0]]}}`))
	}))
	defer srv.Close()

	e := NewEngineWithML(nil, ml.NewClient(srv.URL, 0))
	responses := []rawResp{
		{ID: "r1", Content: "harika ürün tavsiye ederim"}, // 4 kelime, pozitif
		{ID: "r2", Content: "kötü kalitesiz pahalı"},      // 3 kelime, negatif
	}
	res := e.analyzeWithML(context.Background(), "chatgpt", "brand-1", responses)
	if res.MentionCount != 2 {
		t.Errorf("iki yanıt analiz edilmeli, gerçek %d", res.MentionCount)
	}
	if res.PositiveScore <= res.NegativeScore {
		t.Errorf("pozitif yanıt ağırlığı büyük olduğundan pozitif ağır basmalı: %+v", res)
	}
	if res.PositiveScore >= 1.0 || res.NegativeScore <= 0.0 {
		t.Errorf("karışım skorları ara değerlerde olmalı: %+v", res)
	}
	if sum := res.NegativeScore + res.NeutralScore + res.PositiveScore; sum < 0.999 || sum > 1.001 {
		t.Errorf("skorlar toplamı 1 olmalı, gerçek %f", sum)
	}
}

// TestAnalyzeWithML_PartialFailure: bazı yanıtlar hatalıysa başarılı olanlar birleştirilir.
func TestAnalyzeWithML_PartialFailure(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var payload struct {
			Text string `json:"text"`
		}
		_ = json.NewDecoder(r.Body).Decode(&payload)
		if payload.Text == "patla" {
			w.WriteHeader(http.StatusInternalServerError)
			return
		}
		_, _ = w.Write([]byte(`{"model":"sentiment","model_version":"1.0.0","outputs":{"logits":[[0.1,0.1,2.0]]}}`))
	}))
	defer srv.Close()

	e := NewEngineWithML(nil, ml.NewClient(srv.URL, 0))
	responses := []rawResp{
		{ID: "r1", Content: "harika ürün"},
		{ID: "r2", Content: "patla"},
		{ID: "r3", Content: "mükemmel servis"},
	}
	res := e.analyzeWithML(context.Background(), "chatgpt", "brand-1", responses)
	if res.MentionCount != 2 {
		t.Errorf("başarılı iki yanıt birleştirilmeli, gerçek %d", res.MentionCount)
	}
	if res.PositiveScore < 0.7 {
		t.Errorf("başarılı yanıtlar pozitif, gerçek %f", res.PositiveScore)
	}
}

// TestAnalyzeWithML_SkipsEmptyText: boş içerikli yanıtlar ML çağrısı yapılmadan atlanır.
func TestAnalyzeWithML_SkipsEmptyText(t *testing.T) {
	var calls atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		_, _ = w.Write([]byte(`{"model":"sentiment","model_version":"1.0.0","outputs":{"logits":[[0.1,0.2,2.1]]}}`))
	}))
	defer srv.Close()

	e := NewEngineWithML(nil, ml.NewClient(srv.URL, 0))
	responses := []rawResp{
		{ID: "r1", Content: "   "}, // boş — atlanmalı
		{ID: "r2", Content: ""},    // boş — atlanmalı
		{ID: "r3", Content: "harika ürün"},
	}
	res := e.analyzeWithML(context.Background(), "chatgpt", "brand-1", responses)
	if res.MentionCount != 1 {
		t.Errorf("yalnızca dolu yanıt analiz edilmeli, gerçek %d", res.MentionCount)
	}
	if calls.Load() != 1 {
		t.Errorf("boş yanıtlar için ML çağrısı beklenmez, toplam çağrı %d", calls.Load())
	}
}

// TestAnalyzeWithML_EarlyAbortOnTotalFailure: serving erişilemezken ilk hata sonrası
// kalan yanıtlar için ML denenmez — 50×ML_TIMEOUT birikimi önlenir.
func TestAnalyzeWithML_EarlyAbortOnTotalFailure(t *testing.T) {
	var calls atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		time.Sleep(50 * time.Millisecond) // yavaş hata — timeout benzetimi
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer srv.Close()

	e := NewEngineWithML(nil, ml.NewClient(srv.URL, 200*time.Millisecond))
	responses := make([]rawResp, 10)
	for i := range responses {
		responses[i] = rawResp{ID: fmt.Sprintf("r%d", i), Content: "Acme harika ürün"}
	}
	res := e.analyzeWithML(context.Background(), "chatgpt", "brand-1", responses)
	if res.OverallSentiment != 1.0 {
		t.Errorf("tümü hatalı → kural tabanlı fallback pozitif olmalı: %+v", res)
	}
	if calls.Load() != 1 {
		t.Errorf("ilk hata sonrası kalan yanıtlar için ML denenmemeli, çağrı %d", calls.Load())
	}
}

// TestAnalyzeWithML_FallbackOnError: serving 500 dönerse kural tabanlıya düşülür.
func TestAnalyzeWithML_FallbackOnError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		_, _ = w.Write([]byte(`{"detail":"inference hatası"}`))
	}))
	defer srv.Close()

	e := NewEngineWithML(nil, ml.NewClient(srv.URL, 0))
	res := e.analyzeWithML(context.Background(), "perplexity", "brand-1",
		[]rawResp{{ID: "r1", Content: "Acme harika bir ürün"}})
	if res.OverallSentiment != 1.0 {
		t.Errorf("fallback kural tabanlı positive olmalı, gerçek %f", res.OverallSentiment)
	}
	if res.EngineName != "perplexity" || res.BrandID != "brand-1" {
		t.Errorf("meta alanları fallbackte de korunmalı: %+v", res)
	}
}

// TestAnalyzeWithML_CooldownSkipsML: serving hatası sonrası cooldown süresince
// ML çağrısı yapılmaz (latency birikimi önlenir), fallback çalışır.
func TestAnalyzeWithML_CooldownSkipsML(t *testing.T) {
	var calls atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer srv.Close()

	e := NewEngineWithML(nil, ml.NewClient(srv.URL, 0))
	responses := []rawResp{{ID: "r1", Content: "Acme harika bir ürün"}}
	// İlk çağrı: ML denenir, hata → fallback + cooldown başlar.
	res := e.analyzeWithML(context.Background(), "chatgpt", "brand-1", responses)
	if res.OverallSentiment != 1.0 {
		t.Errorf("ilk fallback pozitif olmalı: %+v", res)
	}
	// Cooldown içinde ikinci çağrı ML'yi denememeli, doğrudan fallback olmalı.
	res2 := e.analyzeWithML(context.Background(), "chatgpt", "brand-1", responses)
	if res2.OverallSentiment != 1.0 {
		t.Errorf("cooldown fallback pozitif olmalı: %+v", res2)
	}
	if calls.Load() != 1 {
		t.Errorf("cooldown'da ikinci ML çağrısı beklenmez; toplam çağrı %d", calls.Load())
	}
}

// TestDetectHallucinationsWithML_Success: serving findings HallucinationResult'a çevrilir.
func TestDetectHallucinationsWithML_Success(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v1/hallucination/detect" {
			t.Errorf("beklenen /v1/hallucination/detect, gerçek %s", r.URL.Path)
		}
		_, _ = w.Write([]byte(`{"findings":[{"type":"T3","severity":"high","description":"Çelişik sayısal claim","confidence":0.7,"engine":"chatgpt"}]}`))
	}))
	defer srv.Close()

	e := NewEngineWithML(nil, ml.NewClient(srv.URL, 0))
	targets := []checkTarget{
		{ID: "r1", EngineName: "chatgpt", Content: "MobiTel %30 büyüme bildirdi"},
		{ID: "r2", EngineName: "gemini", Content: "MobiTel %60 büyüme iddia ediyor"},
	}
	res, err := e.detectHallucinationsWithML(context.Background(), targets, "brand-1")
	if err != nil {
		t.Fatalf("detectHallucinationsWithML hata: %v", err)
	}
	if len(res) != 1 {
		t.Fatalf("beklenen 1 finding, gerçek %d", len(res))
	}
	if res[0].HallucinationType != "T3" || res[0].EngineName != "chatgpt" || res[0].BrandID != "brand-1" {
		t.Errorf("finding alanları hatalı: %+v", res[0])
	}
	if res[0].Confidence != 0.7 || res[0].Severity != "high" {
		t.Errorf("confidence/severity hatalı: %+v", res[0])
	}
}

// TestDetectHallucinationsWithML_Error: serving hatası çağırana döner (fallback tetikler).
func TestDetectHallucinationsWithML_Error(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		_, _ = w.Write([]byte(`{"detail":"hata"}`))
	}))
	defer srv.Close()

	e := NewEngineWithML(nil, ml.NewClient(srv.URL, 0))
	targets := []checkTarget{
		{ID: "r1", EngineName: "chatgpt", Content: "a"},
		{ID: "r2", EngineName: "gemini", Content: "b"},
	}
	if _, err := e.detectHallucinationsWithML(context.Background(), targets, "brand-1"); err == nil {
		t.Fatal("serving 500 için hata bekleniyor")
	}
}

// TestGroupByPrompt: hedefler prompt_text'e göre gruplanır (051), sıra korunur.
func TestGroupByPrompt(t *testing.T) {
	targets := []checkTarget{
		{ID: "r1", EngineName: "chatgpt", Prompt: "P1", Content: "a"},
		{ID: "r2", EngineName: "gemini", Prompt: "P2", Content: "b"},
		{ID: "r3", EngineName: "perplexity", Prompt: "P1", Content: "c"},
		{ID: "r4", EngineName: "grok", Prompt: "", Content: "d"},
		{ID: "r5", EngineName: "claude", Prompt: "P2", Content: "e"},
	}
	groups := groupByPrompt(targets)
	if len(groups) != 3 {
		t.Fatalf("beklenen 3 grup (P1, P2, boş), gerçek %d", len(groups))
	}
	// İlk görülme sırası: P1, P2, boş
	if groups[0][0].ID != "r1" || len(groups[0]) != 2 {
		t.Errorf("P1 grubu yanlış: %+v", groups[0])
	}
	if groups[1][0].ID != "r2" || len(groups[1]) != 2 {
		t.Errorf("P2 grubu yanlış: %+v", groups[1])
	}
	if groups[2][0].ID != "r4" || len(groups[2]) != 1 {
		t.Errorf("boş prompt grubu yanlış: %+v", groups[2])
	}
}

// TestApplyMLCrossSource_SamePromptOnly: serving'e yalnızca aynı prompt yanıtları
// gönderilir; farklı prompt'tan gelen yanıt karıştırılmaz (yanlış pozitif riski).
func TestApplyMLCrossSource_SamePromptOnly(t *testing.T) {
	var mu sync.Mutex
	var sent [][]string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var payload struct {
			Responses []struct {
				ID string `json:"id"`
			} `json:"responses"`
		}
		_ = json.NewDecoder(r.Body).Decode(&payload)
		mu.Lock()
		ids := make([]string, 0, len(payload.Responses))
		for _, resp := range payload.Responses {
			ids = append(ids, resp.ID)
		}
		sent = append(sent, ids)
		mu.Unlock()
		_, _ = w.Write([]byte(`{"findings":[{"type":"T3","severity":"high","description":"Çelişik sayısal claim","confidence":0.7,"engine":"chatgpt"}]}`))
	}))
	defer srv.Close()

	e := NewEngineWithML(nil, ml.NewClient(srv.URL, 0))
	targets := []checkTarget{
		{ID: "r1", EngineName: "chatgpt", Prompt: "P1", Content: "MobiTel %30 büyüme"},
		{ID: "r2", EngineName: "gemini", Prompt: "P1", Content: "MobiTel %60 büyüme"},
		{ID: "r3", EngineName: "perplexity", Prompt: "P2", Content: "farklı prompt yanıtı"},
	}
	res := e.applyMLCrossSource(context.Background(), nil, targets, "brand-1")
	if len(res) != 1 {
		t.Fatalf("beklenen 1 finding, gerçek %d", len(res))
	}
	// r3 (P2 tek yanıt) serving'e gitmemeli; r1+r2 birlikte gitmeli
	mu.Lock()
	defer mu.Unlock()
	if len(sent) != 1 || len(sent[0]) != 2 {
		t.Fatalf("tek çağrı, 2 yanıt beklenir, gerçek %+v", sent)
	}
	got := map[string]bool{}
	for _, id := range sent[0] {
		got[id] = true
	}
	if !got["r1"] || !got["r2"] || got["r3"] {
		t.Errorf("prompt karışımı: %v", sent[0])
	}
}

// TestApplyMLCrossSource_SingleGroupNoCall: tüm gruplar tek yanıtlıysa serving çağrısı yapılmaz.
func TestApplyMLCrossSource_SingleGroupNoCall(t *testing.T) {
	var calls atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		_, _ = w.Write([]byte(`{"findings":[]}`))
	}))
	defer srv.Close()

	e := NewEngineWithML(nil, ml.NewClient(srv.URL, 0))
	targets := []checkTarget{
		{ID: "r1", EngineName: "chatgpt", Prompt: "P1", Content: "a"},
		{ID: "r2", EngineName: "gemini", Prompt: "P2", Content: "b"},
	}
	if res := e.applyMLCrossSource(context.Background(), nil, targets, "brand-1"); len(res) != 0 {
		t.Errorf("tek yanıtlı gruplarda finding beklenmez: %+v", res)
	}
	if calls.Load() != 0 {
		t.Errorf("tek yanıtlı gruplarda serving çağrısı beklenmez, çağrı %d", calls.Load())
	}
}

// TestApplyMLCrossSource_NilClient: serving yoksa kurallar aynen döner.
func TestApplyMLCrossSource_NilClient(t *testing.T) {
	e := &Engine{ml: nil}
	targets := []checkTarget{
		{ID: "r1", EngineName: "chatgpt", Prompt: "P1", Content: "a"},
		{ID: "r2", EngineName: "gemini", Prompt: "P1", Content: "b"},
	}
	base := []HallucinationResult{{HallucinationType: "T2", Description: "kural"}}
	if res := e.applyMLCrossSource(context.Background(), base, targets, "brand-1"); len(res) != 1 {
		t.Errorf("ml nil iken base aynen dönmeli: %+v", res)
	}
}

// TestApplyMLCrossSource_Error: serving hatasında cooldown başlar, base döner.
func TestApplyMLCrossSource_Error(t *testing.T) {
	var calls atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer srv.Close()

	e := NewEngineWithML(nil, ml.NewClient(srv.URL, 0))
	targets := []checkTarget{
		{ID: "r1", EngineName: "chatgpt", Prompt: "P1", Content: "a"},
		{ID: "r2", EngineName: "gemini", Prompt: "P1", Content: "b"},
	}
	base := []HallucinationResult{{HallucinationType: "T1", Description: "kural"}}
	res := e.applyMLCrossSource(context.Background(), base, targets, "brand-1")
	if len(res) != 1 {
		t.Errorf("hata sonrası base dönmeli: %+v", res)
	}
	if !e.breaker.InCooldown() {
		t.Error("serving hatası sonrası cooldown başlamalı")
	}
	// Cooldown'da ikinci çağrı ML'yi denememeli
	if res2 := e.applyMLCrossSource(context.Background(), base, targets, "brand-1"); len(res2) != 1 {
		t.Errorf("cooldown'da base dönmeli: %+v", res2)
	}
	if calls.Load() != 1 {
		t.Errorf("cooldown'da ikinci ML çağrısı beklenmez, çağrı %d", calls.Load())
	}
}

// TestRuleBasedHallucinations: T1-T4 fallback kuralları hedefler üzerinde çalışır.
func TestRuleBasedHallucinations(t *testing.T) {
	e := &Engine{}
	targets := []checkTarget{
		// sayı + % var, kaynak yok → T3 (critical)
		{ID: "r1", EngineName: "chatgpt", Content: "Acme %30 pazar payına sahip"},
		// marka adı verilmiş ama yanıtta geçmiyor → T1
		{ID: "r2", EngineName: "gemini", Content: "rakip ürünler hakkında bilgi", BrandName: "Acme"},
	}
	res := e.ruleBasedHallucinations(targets, "brand-1")
	types := map[string]bool{}
	for _, h := range res {
		types[h.HallucinationType] = true
		if h.BrandID != "brand-1" {
			t.Errorf("BrandID korunmalı: %+v", h)
		}
	}
	if !types["T1"] || !types["T3"] {
		t.Errorf("beklenen T1 ve T3 flagleri, gerçek %v", res)
	}
}

// TestMergeHallucinationResults: ML + kural sonuçları birleştirilir, aynı
// (tip, açıklama) çifti tekrarlanmaz.
func TestMergeHallucinationResults(t *testing.T) {
	base := func(typ, desc string) HallucinationResult {
		return HallucinationResult{HallucinationType: typ, Description: desc, EngineName: "chatgpt", BrandID: "brand-1"}
	}
	rules := []HallucinationResult{
		base("T2", "AI yanıtı kaynak/citation referansı içeriyor"),
		base("T4", "AI yanıtı doğrulanmamış olumsuz ifade içeriyor"),
	}
	ml := []HallucinationResult{
		base("T3", "Çelişik sayısal claim: '%30' vs '%60'"),
		// kural ile aynı (tip, açıklama) → elenmeli
		base("T2", "AI yanıtı kaynak/citation referansı içeriyor"),
	}
	merged := mergeHallucinationResults(rules, ml)
	if len(merged) != 3 {
		t.Fatalf("beklenen 3 benzersiz sonuç, gerçek %d (%+v)", len(merged), merged)
	}
	types := map[string]int{}
	for _, h := range merged {
		types[h.HallucinationType]++
	}
	if types["T2"] != 1 || types["T3"] != 1 || types["T4"] != 1 {
		t.Errorf("her tip bir kez bulunmalı: %v", types)
	}
}

// TestAggregateWeighted: kelime sayısı ağırlıklı ortalama doğru hesaplanır.
func TestAggregateWeighted(t *testing.T) {
	items := []weightedProbs{
		{probs: [3]float64{0.5, 0.25, 0.25}, weight: 3},
		{probs: [3]float64{0.2, 0.3, 0.5}, weight: 1},
	}
	avg := aggregateWeighted(items)
	// acc: [0.5*3+0.2, 0.25*3+0.3, 0.25*3+0.5] / 4 = [0.425, 0.2625, 0.3125]
	want := [3]float64{0.425, 0.2625, 0.3125}
	for i := 0; i < 3; i++ {
		if avg[i] != want[i] {
			t.Errorf("beklenen %v, gerçek %v", want, avg)
			break
		}
	}

	// Eşit ağırlıkta basit ortalama (kayan nokta için epsilon'lu karşılaştırma)
	equal := aggregateWeighted([]weightedProbs{
		{probs: [3]float64{0.2, 0.3, 0.5}, weight: 1},
		{probs: [3]float64{0.4, 0.2, 0.4}, weight: 1},
	})
	for i, v := range [3]float64{0.3, 0.25, 0.45} {
		if abs(equal[i]-v) > 1e-9 {
			t.Errorf("eşit ağırlık ortalaması hatalı: %v", equal)
			break
		}
	}

	// Sıfır ağırlık → sıfır vektör
	if zero := aggregateWeighted(nil); zero != [3]float64{} {
		t.Errorf("boş girdi sıfır vektör dönmeli, gerçek %v", zero)
	}
}

// TestSentimentFromProbabilities: softmax olasılıklarının skorlara dönüşümü.
func TestSentimentFromProbabilities(t *testing.T) {
	res := sentimentFromProbabilities("chatgpt", "brand-1", [3]float64{0.2, 0.6, 0.2}, 3)
	if res.NeutralScore != 0.6 {
		t.Errorf("beklenen NeutralScore 0.6, gerçek %f", res.NeutralScore)
	}
	// poz*1.0 + nötr*0.5 + neg*0.0
	if want := 0.2 + 0.6*0.5; res.OverallSentiment != want {
		t.Errorf("beklenen %f, gerçek %f", want, res.OverallSentiment)
	}
	if res.MentionCount != 3 {
		t.Errorf("MentionCount 3 olmalı, gerçek %d", res.MentionCount)
	}
}
