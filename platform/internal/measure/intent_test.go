package measure

import (
	"context"
	"math"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"

	"github.com/geolens/platform/internal/config"
	"github.com/geolens/platform/internal/ml"
)

func almostEqual(a, b float64) bool {
	return math.Abs(a-b) < 1e-9
}

// TestApplyIntentWeights_Renormalizes: comparison intenti rakip bileşenlerini
// yükseltir ve toplam ağırlık 1.0'a normalize olur.
func TestApplyIntentWeights_Renormalizes(t *testing.T) {
	base := v2DefaultWeights
	out := applyIntentWeights(base, "comparison")

	sum := out.PresenceShare + out.PositionWeight + out.SourceShare + out.CompetitorContext +
		out.AppearanceRate + out.Sentiment + out.CompVisibility
	if !almostEqual(sum, 1.0) {
		t.Errorf("toplam 1.0 olmalı, gerçek %f", sum)
	}
	if out.CompetitorContext <= base.CompetitorContext {
		t.Errorf("comparison intenti rakip ağırlığını yükseltmeli: %f vs %f", out.CompetitorContext, base.CompetitorContext)
	}
	if out.PresenceShare >= base.PresenceShare {
		t.Errorf("comparison intenti varlık ağırlığını düşürmeli: %f vs %f", out.PresenceShare, base.PresenceShare)
	}
	if out.IsV2() != true {
		t.Error("çıktı v2 profil olmalı")
	}
}

// TestApplyIntentWeights_Information: information intenti (eski presence)
// varlık ağırlığını öne çıkarır (0421-8INTENT).
func TestApplyIntentWeights_Information(t *testing.T) {
	out := applyIntentWeights(v2DefaultWeights, "information")
	sum := out.PresenceShare + out.PositionWeight + out.SourceShare + out.CompetitorContext +
		out.AppearanceRate + out.Sentiment + out.CompVisibility
	if !almostEqual(sum, 1.0) {
		t.Errorf("toplam 1.0 olmalı, gerçek %f", sum)
	}
	if out.PresenceShare <= v2DefaultWeights.PresenceShare {
		t.Errorf("information intenti varlık ağırlığını yükseltmeli: %f", out.PresenceShare)
	}
}

// TestApplyIntentWeights_NewIntents: 0421-8INTENT ile eklenen intent'lerin
// ayırt edici bileşenleri yükseltmeli ve toplam 1.0'a normalize olmalı.
func TestApplyIntentWeights_NewIntents(t *testing.T) {
	cases := []struct {
		intent    string
		component func(ComponentWeights) float64
	}{
		{"opinion", func(w ComponentWeights) float64 { return w.AppearanceRate }},     // eski category
		{"complaint", func(w ComponentWeights) float64 { return w.Sentiment }},        // sentiment ağırlıklı
		{"purchase", func(w ComponentWeights) float64 { return w.CompetitorContext }}, // karar yönlendirmesi
		{"news", func(w ComponentWeights) float64 { return w.SourceShare }},           // kaynak/güncellik
	}
	for _, tc := range cases {
		out := applyIntentWeights(v2DefaultWeights, tc.intent)
		sum := out.PresenceShare + out.PositionWeight + out.SourceShare + out.CompetitorContext +
			out.AppearanceRate + out.Sentiment + out.CompVisibility
		if !almostEqual(sum, 1.0) {
			t.Errorf("%s: toplam 1.0 olmalı, gerçek %f", tc.intent, sum)
		}
		if tc.component(out) <= tc.component(v2DefaultWeights) {
			t.Errorf("%s intenti ilgili bileşeni yükseltmeli", tc.intent)
		}
	}
}

// TestApplyIntentWeights_RemovedLegacyKeys: eski presence/category anahtarları
// 0421-8INTENT'te kaldırıldı — bilinmeyen intent gibi varsayılanı döndürmeli.
func TestApplyIntentWeights_RemovedLegacyKeys(t *testing.T) {
	for _, legacy := range []string{"presence", "category"} {
		if out := applyIntentWeights(v2DefaultWeights, legacy); out != v2DefaultWeights {
			t.Errorf("%s kaldırılmış anahtar varsayılanı döndürmeli: %+v", legacy, out)
		}
	}
}

// TestApplyIntentWeightsWithScale_EnvOverride: INTENT_WEIGHT_SCALE çarpanları
// uygulanır ve normalize edilir (0421 A3-3 pilot kalibrasyonu).
func TestApplyIntentWeightsWithScale_EnvOverride(t *testing.T) {
	scale := map[string][7]float64{
		"information": {1.50, 1.00, 1.00, 1.00, 1.00, 1.00, 1.00}, // varlığı daha fazla öne çıkar
	}
	out := applyIntentWeightsWithScale(v2DefaultWeights, "information", scale)
	sum := out.PresenceShare + out.PositionWeight + out.SourceShare + out.CompetitorContext +
		out.AppearanceRate + out.Sentiment + out.CompVisibility
	if !almostEqual(sum, 1.0) {
		t.Errorf("toplam 1.0 olmalı, gerçek %f", sum)
	}
	// 1.50 çarpanı ile varlık payı varsayılandan (0.30) belirgin şekilde büyük olmalı
	if out.PresenceShare <= 0.33 {
		t.Errorf("1.50 çarpanı varlığı yükseltmeli: %f", out.PresenceShare)
	}
	// Varsayılan tabloyla karşılaştır: 1.25 vs 1.50 çarpanı fark yaratmalı
	def := applyIntentWeights(v2DefaultWeights, "information")
	if almostEqual(out.PresenceShare, def.PresenceShare) {
		t.Errorf("env override çarpanları varsayılandan farklı sonuç üretmeli")
	}
}

// TestApplyIntentWeightsWithScale_UnknownIntent: env tablosunda olmayan intent
// ağırlıkları değiştirmez.
func TestApplyIntentWeightsWithScale_UnknownIntent(t *testing.T) {
	scale := map[string][7]float64{"information": {1.50, 1, 1, 1, 1, 1, 1}}
	base := v2DefaultWeights
	if out := applyIntentWeightsWithScale(base, "comparison", scale); out != base {
		t.Errorf("env tablosunda olmayan intent varsayılanı döndürmeli: %+v", out)
	}
}

// TestApplyIntentWeights_UnknownIntent: bilinmeyen intent ağırlıkları değiştirmez.
func TestApplyIntentWeights_UnknownIntent(t *testing.T) {
	base := v2DefaultWeights
	if out := applyIntentWeights(base, "bilinmeyen-intent"); out != base {
		t.Errorf("bilinmeyen intent varsayılanı döndürmeli: %+v", out)
	}
}

// TestApplyIntentWeights_V1NoChange: v1 (4 bileşenli) profilde intent uygulanmaz.
func TestApplyIntentWeights_V1NoChange(t *testing.T) {
	base := v1LegacyWeights
	if out := applyIntentWeights(base, "comparison"); out != base {
		t.Errorf("v1 profilde ağırlıklar değişmemeli: %+v", out)
	}
}

// TestIntentWeights_MLFirst: serving ayaktaysa prompt sınıflandırılır ve
// intent ağırlıkları döner; aynı prompt için önbellek kullanılır (tek HTTP çağrısı).
func TestIntentWeights_MLFirst(t *testing.T) {
	var calls atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		_, _ = w.Write([]byte(`{"intent":{"label":"comparison","confidence":0.92},"topic":{"label":"brand","confidence":0.8},"persona":{"label":"consumer","confidence":0.7},"funnel":{"label":"evaluation","confidence":0.6}}`))
	}))
	defer srv.Close()

	s := &service{ml: ml.NewClient(srv.URL, 0), breaker: ml.NewCircuitBreaker(ml.DefaultCooldown)}
	results := []MeasurementResult{{PromptText: "Acme'nin en iyi rakibi kim?"}}

	adjusted, ok := s.intentWeights(context.Background(), results, v2DefaultWeights)
	if !ok {
		t.Fatal("ML başarılıysa ok=true olmalı")
	}
	if adjusted.CompetitorContext <= v2DefaultWeights.CompetitorContext {
		t.Errorf("comparison intenti rakip ağırlığını yükseltmeli: %+v", adjusted)
	}

	// Aynı prompt → önbellek: ikinci çağrı HTTP yapmamalı
	if _, ok2 := s.intentWeights(context.Background(), results, v2DefaultWeights); !ok2 {
		t.Fatal("önbellekli çağrı ok=true olmalı")
	}
	if calls.Load() != 1 {
		t.Errorf("aynı prompt için tek HTTP çağrısı beklenir, gerçek %d", calls.Load())
	}
}

// TestIntentWeights_NewIntentScale (0421-8INTENT Faz D): serving 8-intent
// taksonomisinden bir etiket (opinion) döndüğünde ölçek uygulanır — fallback'e
// düşülmez (appearance yükselir, ok=true).
func TestIntentWeights_NewIntentScale(t *testing.T) {
	var calls atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		_, _ = w.Write([]byte(`{"intent":{"label":"opinion","confidence":0.81},"topic":{"label":"brand","confidence":0.7},"persona":{"label":"executive","confidence":0.6},"funnel":{"label":"consideration","confidence":0.55}}`))
	}))
	defer srv.Close()

	s := &service{ml: ml.NewClient(srv.URL, 0), breaker: ml.NewCircuitBreaker(ml.DefaultCooldown)}
	results := []MeasurementResult{{PromptText: "Acme hakkında ne düşünüyorsun?"}}

	adjusted, ok := s.intentWeights(context.Background(), results, v2DefaultWeights)
	if !ok {
		t.Fatal("8-intent etiketi (opinion) için ok=true olmalı — fallback'e düşülmemeli")
	}
	if adjusted.AppearanceRate <= v2DefaultWeights.AppearanceRate {
		t.Errorf("opinion intenti appearance ağırlığını yükseltmeli: %+v", adjusted)
	}
}

// TestIntentWeights_EmptyPromptNoCall: prompt boşsa çağrı yapılmaz.
func TestIntentWeights_EmptyPromptNoCall(t *testing.T) {
	s := &service{ml: ml.NewClient("http://localhost:1", 0), breaker: ml.NewCircuitBreaker(ml.DefaultCooldown)}
	if _, ok := s.intentWeights(context.Background(), []MeasurementResult{{PromptText: "  "}}, v2DefaultWeights); ok {
		t.Fatal("boş prompt için ok=false olmalı")
	}
}

// TestIntentWeights_FallbackOnErrorAndCooldown: serving hatası → ok=false ve
// cooldown başlar (sonraki çağrılar serving'i denemez).
func TestIntentWeights_FallbackOnErrorAndCooldown(t *testing.T) {
	var calls atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		w.WriteHeader(http.StatusInternalServerError)
		_, _ = w.Write([]byte(`{"detail":"hata"}`))
	}))
	defer srv.Close()

	s := &service{ml: ml.NewClient(srv.URL, 0), breaker: ml.NewCircuitBreaker(ml.DefaultCooldown)}
	results := []MeasurementResult{{PromptText: "Acme hakkında bilgi ver"}}

	if _, ok := s.intentWeights(context.Background(), results, v2DefaultWeights); ok {
		t.Fatal("serving hatasında ok=false olmalı")
	}
	if _, ok := s.intentWeights(context.Background(), results, v2DefaultWeights); ok {
		t.Fatal("cooldown'da ok=false olmalı")
	}
	if calls.Load() != 1 {
		t.Errorf("cooldown sonrası serving çağrısı beklenmez, gerçek %d", calls.Load())
	}
}

// TestNewServiceWithML_EnvIntentScale: INTENT_WEIGHT_SCALE env'i doluysa servis
// env çarpanlarını kullanır; nil cfg ile de env doğrudan okunur (handler yolu).
func TestNewServiceWithML_EnvIntentScale(t *testing.T) {
	t.Setenv("INTENT_WEIGHT_SCALE", "information=1.50,1,1,1,1,1,1")
	s := NewServiceWithML(nil, nil, nil, nil)
	svc := s.(*service)
	if len(svc.intentScale) != 1 {
		t.Fatalf("env çarpanları çözülmeli: %+v", svc.intentScale)
	}
	if svc.effectiveIntentScale()["information"][0] != 1.50 {
		t.Errorf("information çarpanı env'den gelmeli: %v", svc.intentScale["information"])
	}
}

// TestNewServiceWithML_CfgIntentScale: cfg üzerinden verilen INTENT_WEIGHT_SCALE
// env'den öncelikli kullanılır.
func TestNewServiceWithML_CfgIntentScale(t *testing.T) {
	t.Setenv("INTENT_WEIGHT_SCALE", "information=1.99,1,1,1,1,1,1")
	cfg := &config.Config{IntentWeightScaleRaw: "information=1.50,1,1,1,1,1,1"}
	svc := NewServiceWithML(nil, nil, cfg, nil).(*service)
	if svc.intentScale["information"][0] != 1.50 {
		t.Errorf("cfg değeri env'den öncelikli olmalı: %v", svc.intentScale["information"])
	}
}

// TestNewServiceWithML_NoEnvScale: env boşsa varsayılan tablo kullanılır.
func TestNewServiceWithML_NoEnvScale(t *testing.T) {
	t.Setenv("INTENT_WEIGHT_SCALE", "")
	svc := NewServiceWithML(nil, nil, nil, nil).(*service)
	if svc.intentScale != nil {
		t.Errorf("env boşsa intentScale nil olmalı: %+v", svc.intentScale)
	}
	if len(svc.effectiveIntentScale()) == 0 {
		t.Error("effectiveIntentScale varsayılan tabloyu döndürmeli")
	}
}

// TestIntentWeights_NilClient: ml yoksa ok=false (kural tabanlı ağırlıklar).
func TestIntentWeights_NilClient(t *testing.T) {
	s := &service{ml: nil}
	if _, ok := s.intentWeights(context.Background(), []MeasurementResult{{PromptText: "x"}}, v2DefaultWeights); ok {
		t.Fatal("nil ml client için ok=false olmalı")
	}
}
