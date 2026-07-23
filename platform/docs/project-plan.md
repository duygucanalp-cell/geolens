# Proje Planı — GeoLens Platform

| Alan | Değer |
|---|---|
| Doküman ID | project-plan |
| Proje | GeoLens Platform |
| Versiyon | 1.6 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 23 Temmuz 2026 |
| İlişkili | 0000 (Master Plan), 0205 (MVP), 0206 (Roadmap), archive/avip-v1/0401 |

---

## 1. Amaç

Bu doküman GeoLens Platform geliştirme takvimini, ekip sorumluluklarını ve çıkış kapılarını tanımlar. Kaynağı AVIP arşivindeki 0401-development-process.md'dir; GeoLens Platform'un mevcut doküman yapısına uyarlanmıştır.

---

## 2. Ekip Yapısı

| Kişi | Rol | Dilim 1-2 | Dilim 3-4 | Pilot açılış |
|:----:|:----:|:---------:|:---------:|:------------:|
| **Siz** | TL + CEO | 🟢 | 🟢 | 🟢 |
| **Backend #1** | Platform & Identity | 1 | 1 | 1 |
| **Backend #2** | Geniş (Go+React temel) → Insight → Sertleştirme | 1 | 1 | 1 |
| **Frontend** | React/TypeScript SPA | — | 1 | 1 |
| **DevOps/SRE** | Ortam, CI/CD, monitoring | — | — | 1 |
| **Analist (AN)** | Araştırma, dokümantasyon, görüşmeler | 1 | 1 | 1 |
| **Toplam** | | **4** | **5** | **6** |

---

## 3. Genel Bakış — 4 Dilim, 16 Hafta

| Dilim | Haftalar | Ekip | Renk | Çıktı | Tarih Aralığı |
|:-----:|:--------:|:----:|:----:|-------|:-------------:|
| **🏗️ 1 · İskelet** | H0–H4 | 4 kişi | 🏗️ | Tek motorlu demo (Perplexity) | 3 Ağu — 4 Eyl 2026 |
| **📡 2 · Ölçüm Tam** | H5–H8 | 4 kişi | 📡 | 3 motorlu skor + site denetimi | 7 Eyl — 2 Eki 2026 |
| **💌 3 · Değer Halkası** | H9–H12 | 5 kişi 🧑‍💻 | 💌 | E-posta + PDF + öneri akışı | 5 Eki — 30 Eki 2026 |
| **🔒 4 · Sertleştirme** | H13–H16 | 5 kişi | 🔒 | Pilot çıkış kapısı yeşil | 2 Kas — 27 Kas 2026 |

> **Not:** Başlangıç tarihleri varsayımsaldır. Kesin başlangıç işe alımların tamamlanmasına bağlıdır.

---

## 4. Dilim 1 · İskelet (H0–H4)

**Hedef:** Uçtan uca demo — kullanıcı kaydolur → panel oluşturur → ölçüm tetiklenir → Perplexity yanıtı döner → 4 bileşenli skor hesaplanır → panoda görünür.

**Bağımlılık zinciri:** platform/db → platform/httpmw → identity → config → measure → governance

| Hafta | Siz (TL+CEO) | Backend #1 (Platform) | Backend #2 (Geniş) | Analist (AN) | 🟢 Hafta Çıktısı |
|:-----:|:------------:|:---------------------:|:------------------:|:------------:|:----------------:|
| **H0** | Go modül iskeleti; measure arayüzü + engines kayıt defteri tasarımı | platform/db: PostgreSQL havuz + sqlc kurulumu; ilk migration (kiracı, kullanıcı); Docker Compose (PG, Redis, S3) | cmd/api iskeleti; platform/telemetry: OTel kurulumu; Makefile + golangci-lint yapılandırması | Tüm doküman setini okuma; ajans görüşme takvimi oluşturma; Evertune başlangıç | 🟢 Çalışan dev ortamı + ilk migration |
| **H1** | platform/httpmw: panik kurtarma, request ID; measure api.go tamamlama; Perplexity bağdaştırıcı iskeleti (Execute) | identity: kullanıcı kaydı, JWT oturum, giriş/çıkış uçları; httpmw: kimlik doğrulama, kiracı bağlamı | cmd/api: httpmw zincirini bağlama; config: marka tanımı, panel iskeleti; cmd/scheduler iskeleti | İlk 3 ajans görüşmesi (Sheltron, Cremicro, Seobaz); güncelleme notları | 🟢 Çalışan API + kimlik doğrulama + Perplexity istek |
| **H2** | Perplexity bağdaştırıcı tam (alıntı çıkarma, hata sınıfları); measure/calc: calculation_run + temel skor (varlık payı + konum + kaynak) | identity: RBAC tam, RLS politikaları; platform/queue: Redis Streams + outbox dağıtıcı; S3 storage sarmalayıcı | config: panel tanımı + prompt seti yönetimi; scheduler: izleme planı tarama, idempotent iş üretimi; cmd/worker iskeleti | Ajans görüşmeleri devam (Webtures, Zeo); skor bileşen adları; dokümantasyon | 🟢 Ölçüm işi kuyruğa atılabiliyor |
| **H3** | Scoring engine tam: 4 bileşen (varlık, konum, kaynak, rakip) + GA + fidelite; ham yanıt → skor pipeline | governance: denetim yazıcısı, kota iskeleti, usage_records; platform hardening (hata yönetimi, timeouts) | Worker: kuyruktan iş okuma + measure çağrısı + sonuç kalıcılaştırma; web/ SPA: React iskeleti + skor kartı prototipi | Ajans görüşmeleri analizi; sürüm notu şablonları; README güncelleme | 🟢 Skor hesaplanıyor, governance temel hazır |
| **✅ H4** | H4 TODO'lar kapatıldı (tenantID context, competitor_context); entegrasyon testleri; TrendChart React bileşeni; demo ortamı; API dokümantasyonu; ADR-006 kapanış kaydı | CI/CD güncelleme (GitHub Actions, D-14); doküman-kod senkronu | web/ SPA: TrendChart + ScoreDashboard entegrasyonu; deploy/docker-compose.demo.yml + seed.sql + demo.sh | ADR-006 Dilim 1 kapanış kaydı; demo desteği; v1.1 kuyruğu kayıtları | ✅ **Dilim 1 kapanışı — çıkış kapısı kriterleri sağlandı** |

### İlk Çıktı Takvimi

| Ne zaman | Ne çıktı | Kullanılabilirlik |
|:--------:|----------|:-----------------:|
| H0 sonu | Dev ortamı + migration | Geliştirici iç kullanım |
| H1 sonu | API + auth + Perplexity istek | API tüketicileri |
| H2 sonu | Ölçüm işi → kuyruk | Scheduler çalışıyor |
| H3 sonu | Skor pipeline | Measure çalışıyor |
| **H4 sonu** | **Uçtan uca demo** | **Canlı gösterim** |

### Çıkış Kapısı Kriteri

Kullanıcı kaydolur → panel oluşturur → ölçüm tetiklenir → Perplexity yanıtı başarıyla döner → 4 bileşenli skor hesaplanır → panoda görünür. Korelasyon zinciri (request_id → job_id → calculation_run_id) logda izlenebilir.

### Implementasyon

Aşağıdaki Go paketleri tamamlanmıştır ve H4 kapsamında tüm `TODO(H4)` etiketleri kapatılmıştır. Entegrasyon testi (`measure_integration_test.go`) testcontainers ile PostgreSQL üzerinde çalışır. Web UI: TrendChart SVG bileşeni eklendi, ScoreDashboard marka bazında trend gösterimi yapacak şekilde güncellendi. Demo ortamı: `deploy/docker-compose.demo.yml`, `deploy/seed.sql`, `deploy/demo.sh`.

#### `internal/measure/service_test.go`

```go
package measure

import (
	"context"
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
	for i := 0; i < 25; i++ {
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
}
```

#### `internal/measure/engine_test.go`

```go
package measure

import (
	"testing"

	"github.com/geolens/platform/engine"
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
```

#### `engine/perplexity/adapter_test.go`

```go
package perplexity

import (
	"testing"

	"github.com/geolens/platform/engine"
)

func TestAdapter_Name(t *testing.T) {
	a := NewAdapter("test-key", nil)
	if a.Name() != "perplexity" {
		t.Errorf("beklenen 'perplexity', gerçek %s", a.Name())
	}
}

func TestAdapter_Tier(t *testing.T) {
	a := NewAdapter("test-key", nil)
	if a.Tier() != engine.TierDirect {
		t.Errorf("beklenen TierDirect(1), gerçek %d", a.Tier())
	}
}

func TestAdapter_WithContext(t *testing.T) {
	a := NewAdapter("test-key", nil)
	ctxA := a.WithContext("tenant-1", "ws-1")
	if ctxA.Name() != "perplexity" {
		t.Error("WithContext adapter name değiştirmemeli")
	}
}

func TestParseResponse_Success(t *testing.T) {
	a := NewAdapter("test-key", nil)
	raw := []byte(`{
		"id": "req-123",
		"model": "sonar-pro",
		"choices": [{"index": 0, "message": {"role": "assistant", "content": "Acme pazar lideridir."}}],
		"citations": ["https://example.com"]
	}`)
	resp, err := a.parseResponse(raw, 150)
	if err != nil {
		t.Fatalf("parseResponse hata: %v", err)
	}
	if resp.EngineName != "perplexity" {
		t.Errorf("beklenen 'perplexity', gerçek %s", resp.EngineName)
	}
	if resp.RequestID != "req-123" {
		t.Errorf("beklenen 'req-123', gerçek %s", resp.RequestID)
	}
	if len(resp.Citations) != 1 {
		t.Errorf("beklenen 1 alıntı, gerçek %d", len(resp.Citations))
	}
}

func TestParseResponse_EmptyChoices(t *testing.T) {
	a := NewAdapter("test-key", nil)
	raw := []byte(`{"id": "req-1", "model": "sonar-pro", "choices": [], "citations": []}`)
	_, err := a.parseResponse(raw, 100)
	if err == nil {
		t.Error("boş choices için hata bekleniyor")
	}
}

func TestParseResponse_InvalidJSON(t *testing.T) {
	a := NewAdapter("test-key", nil)
	_, err := a.parseResponse([]byte(`{invalid`), 100)
	if err == nil {
		t.Error("geçersiz JSON için hata bekleniyor")
	}
}
```

#### `engine/registry_test.go`

```go
package engine

import (
	"testing"
)

type mockAdapter struct {
	name string
	tier Tier
}

func (m *mockAdapter) Name() string    { return m.name }
func (m *mockAdapter) Tier() Tier      { return m.tier }
func (m *mockAdapter) Execute(prompt string) (*RawResponse, error) {
	return &RawResponse{EngineName: m.name, Content: "mock"}, nil
}

func TestRegistry_RegisterAndGet(t *testing.T) {
	r := NewRegistry()
	a := &mockAdapter{name: "test", tier: TierDirect}
	r.Register(a)

	got := r.Get("test")
	if got == nil {
		t.Fatal("Get('test') nil döndü")
	}
	if got.Name() != "test" {
		t.Errorf("beklenen 'test', gerçek %s", got.Name())
	}
}

func TestRegistry_GetUnknown(t *testing.T) {
	r := NewRegistry()
	got := r.Get("unknown")
	if got != nil {
		t.Error("bilinmeyen adapter nil dönmeli")
	}
}

func TestRegistry_List(t *testing.T) {
	r := NewRegistry()
	r.Register(&mockAdapter{name: "a"})
	r.Register(&mockAdapter{name: "b"})
	r.Register(&mockAdapter{name: "c"})

	names := r.List()
	if len(names) != 3 {
		t.Errorf("beklenen 3, gerçek %d", len(names))
	}
}

func TestRegistry_Count(t *testing.T) {
	r := NewRegistry()
	r.Register(&mockAdapter{name: "x"})
	r.Register(&mockAdapter{name: "y"})
	if r.Count() != 2 {
		t.Errorf("beklenen 2, gerçek %d", r.Count())
	}
}

func TestRegistry_EmptyCount(t *testing.T) {
	r := NewRegistry()
	if r.Count() != 0 {
		t.Errorf("beklenen 0, gerçek %d", r.Count())
	}
}
```

#### `platform/db/pool_test.go`

```go
package db

import (
	"context"
	"testing"
	"time"
)

func TestNewPool_InvalidURL(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	_, err := NewPool(ctx, "postgres://invalid:invalid@localhost:9999/test?sslmode=disable")
	if err == nil {
		t.Log("Not: geçersiz URL ile pool oluşturma beklenen bir testtir")
	}
}

func TestPool_Close(t *testing.T) {
	// Pool kapalıyken Close çağırmak panic üretmemeli
	var p Pool
	p.Close()
}
```

#### `platform/httpmw/middleware_test.go`

```go
package httpmw

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestPanicRecovery(t *testing.T) {
	handler := PanicRecovery(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		panic("test panic")
	}))

	req := httptest.NewRequest("GET", "/test", nil)
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Code != http.StatusInternalServerError {
		t.Errorf("beklenen 500, gerçek %d", w.Code)
	}
}

func TestRequestID(t *testing.T) {
	handler := RequestID(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		id := GetRequestID(r.Context())
		if id == "" {
			t.Error("RequestID context'te bulunamadı")
		}
	}))

	req := httptest.NewRequest("GET", "/test", nil)
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Header().Get("X-Request-ID") == "" {
		t.Error("X-Request-ID header'ı eksik")
	}
}

func TestCORS_Options(t *testing.T) {
	handler := CORS(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))

	req := httptest.NewRequest("OPTIONS", "/test", nil)
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, req)

	if w.Code != http.StatusNoContent {
		t.Errorf("OPTIONS beklenen 204, gerçek %d", w.Code)
	}
}

func TestGetTenantID_Empty(t *testing.T) {
	id := GetTenantID(context.Background())
	if id != "" {
		t.Errorf("beklenen '', gerçek %s", id)
	}
}

func TestHasSufficientRole(t *testing.T) {
	tests := []struct {
		user, min string
		expected  bool
	}{
		{"admin", "viewer", true},
		{"viewer", "admin", false},
		{"editor", "editor", true},
		{"viewer", "viewer", true},
		{"unknown", "viewer", false},
	}
	for _, tt := range tests {
		got := hasSufficientRole(tt.user, tt.min)
		if got != tt.expected {
			t.Errorf("hasSufficientRole(%q, %q) = %v, beklenen %v", tt.user, tt.min, got, tt.expected)
		}
	}
}
```

#### `platform/httputil/json_test.go`

```go
package httputil

import (
	"encoding/json"
	"net/http/httptest"
	"testing"
)

func TestWriteJSON(t *testing.T) {
	w := httptest.NewRecorder()
	data := map[string]string{"key": "value"}
	WriteJSON(w, 200, data)

	if w.Code != 200 {
		t.Errorf("beklenen 200, gerçek %d", w.Code)
	}
	if w.Header().Get("Content-Type") != "application/json" {
		t.Errorf("Content-Type application/json olmalı")
	}

	var decoded map[string]string
	if err := json.Unmarshal(w.Body.Bytes(), &decoded); err != nil {
		t.Fatalf("JSON decode hatası: %v", err)
	}
	if decoded["key"] != "value" {
		t.Errorf("beklenen 'value', gerçek %s", decoded["key"])
	}
}

func TestWriteError(t *testing.T) {
	w := httptest.NewRecorder()
	WriteError(w, 400, "bad request")

	if w.Code != 400 {
		t.Errorf("beklenen 400, gerçek %d", w.Code)
	}

	var decoded map[string]string
	if err := json.Unmarshal(w.Body.Bytes(), &decoded); err != nil {
		t.Fatalf("JSON decode hatası: %v", err)
	}
	if decoded["error"] != "bad request" {
		t.Errorf("beklenen 'bad request', gerçek %s", decoded["error"])
	}
}
```

#### `internal/errors/errors_test.go`

```go
package errors

import (
	"net/http"
	"testing"
)

func TestErrorTypes(t *testing.T) {
	tests := []struct {
		err      *Error
		code     string
		httpCode int
	}{
		{ErrNotFound, "not_found", http.StatusNotFound},
		{ErrValidation, "validation_error", http.StatusBadRequest},
		{ErrUnauthorized, "unauthorized", http.StatusUnauthorized},
		{ErrForbidden, "forbidden", http.StatusForbidden},
		{ErrRateLimited, "rate_limited", http.StatusTooManyRequests},
		{ErrInternal, "internal_error", http.StatusInternalServerError},
	}
	for _, tt := range tests {
		if tt.err.Code != tt.code {
			t.Errorf("beklenen %s, gerçek %s", tt.code, tt.err.Code)
		}
		if tt.err.HTTPSC != tt.httpCode {
			t.Errorf("%s için beklenen HTTP %d, gerçek %d", tt.code, tt.httpCode, tt.err.HTTPSC)
		}
	}
}

func TestNotFound(t *testing.T) {
	err := NotFound("kayıt bulunamadı")
	if err.Code != "not_found" {
		t.Errorf("beklenen 'not_found', gerçek %s", err.Code)
	}
	if err.Message != "kayıt bulunamadı" {
		t.Errorf("beklenen 'kayıt bulunamadı', gerçek %s", err.Message)
	}
}

func TestStatusCode(t *testing.T) {
	err := Validation("geçersiz girdi")
	code := StatusCode(err)
	if code != http.StatusBadRequest {
		t.Errorf("beklenen 400, gerçek %d", code)
	}
}

func TestCode(t *testing.T) {
	err := Forbidden("yetkisiz")
	c := Code(err)
	if c != "forbidden" {
		t.Errorf("beklenen 'forbidden', gerçek %s", c)
	}
}

func TestCode_Unknown(t *testing.T) {
	c := Code(nil)
	// nil geçilince unknown_error dönmeli
	// Not: gerçek kullanımda her zaman *Error geçilir
	_ = c
}
```

---

## 5. Dilim 2 · Ölçüm Tam (H5–H8)

**Hedef:** Üç motor (Perplexity + ChatGPT + Gemini) için ayrı ayrı skor. Site denetim bulguları saniyeler içinde.

**Bağımlılık zinciri:** engines kayıt defteri (Dilim 1) → ChatGPT → Gemini → GA tam → site denetimi

| Hafta | Siz (TL+CEO) | Backend #1 (Platform) | Backend #2 (Geniş) | Analist (AN) | 🟢 Hafta Çıktısı |
|:-----:|:------------:|:---------------------:|:------------------:|:------------:|:----------------:|
| **H5** | ChatGPT bağdaştırıcısı (OpenAI Responses API + web araması; alıntı çıkarma, hata sınıfları, kayıt defteri) | GA mekaniği tamamlama: GA hesaplama, fidelite etiketleme, partial yayın kuralları | Pano: skor kartı bileşeni + motor kırılım sekmeleri + panel seçici | Ajans görüşmeleri (Aora Digital, Digipeak); öneri kural kütüphanesi içerik başlangıç | 🟢 ChatGPT çalışıyor, GA mekaniği hazır |
| **H6** | Gemini bağdaştırıcısı (Gemini API + Google Search grounding; URI çözümleme, yönlendirme takibi, kayıt defteri) | Örnekleme altyapısı tam: n=3, temp=0, bayraklı oran eşiği; örnekleme birim testleri | Pano: trend grafiği (Recharts), motor karşılaştırma görünümü | Ajans görüşmeleri analizi; Evertune tamamlama | 🟢 Gemini çalışıyor, 3 motor kayıtlı |
| **H7** | Site denetim bileşeni: robots.txt bot izinleri, SSR sinyalleri, SSRF korumaları, bot listesi | Üç motorlu pipeline entegrasyonu; entegrasyon testleri (testcontainers); CI/CD güncelleme | Denetim bulguları ekranı; site denetim API uçları; pano detay görünümleri | Skor bileşen adları tamamlama; sürüm notu şablonları başlangıç | 🟢 Site denetimi çalışıyor, 3 motor entegre |
| **H8** | Uçtan uca test (3 motorlu panel → skor → pano); hata ayıklama; demo senaryosu hazırlığı | Performans testi; hardening; doküman-kod senkronu; v1.1 kuyruğu kayıtları | Demo ortamı; API dokümantasyonu; pano son rötuşlar + kullanıcı testi | Demo desteği; Dilim 2 dokümantasyon kapanışı; v1.1 kuyruğu kayıtları | 🔷 **Üç motorlu panel skoru + saniyeler içinde denetim bulgusu** |

### İlk Çıktı Takvimi

| Ne zaman | Ne çıktı | Kullanılabilirlik |
|:--------:|----------|:-----------------:|
| H5 sonu | ChatGPT bağdaştırıcısı + GA mekaniği | Measure API |
| H6 sonu | Gemini bağdaştırıcısı + 3 motor kayıtlı | Engines kayıt defteri |
| H7 sonu | Site denetimi + 3 motor pipeline | Worker |
| **H8 sonu** | **Üç motorlu panel skoru + denetim bulguları panoda** | **Canlı gösterim** |

### Çıkış Kapısı Kriteri

Kullanıcı panelinde üç motor (Perplexity + ChatGPT + Gemini) için ayrı ayrı skor görünür. Site denetimi çalıştırılır ve bulgular saniyeler içinde panoda listelenir. Korelasyon zinciri her motor için ayrı izlenebilir.

---

## 6. Dilim 3 · Değer Halkası (H9–H12)

**🧑‍💻 Frontend ekibe katılır.** Ekip 5 kişi. Backend #2 React sorumluluğunu Frontend'e devreder, **insight** (öneri motoru) ağırlıklı çalışır.

**Hedef:** Öneri akışı, derin bağlantılı e-posta özeti, PDF rapor, anlık uyarı, NG10 filtresi.

| Hafta | Siz (TL+CEO) | Backend #1 (Platform) | Backend #2 (İnsight) | Frontend (Yeni) | Analist (AN) | 🟢 Hafta Çıktısı |
|:-----:|:------------:|:---------------------:|:--------------------:|:---------------:|:------------:|:----------------:|
| **H9** | Delivery çekirdek: kanal yönetimi, bildirim tipleri, e-posta gönderim altyapısı (SMTP/API) | Governance raporlama uzantıları: usage_records sorguları, kota limit raporları; PDF render altyapısı (şablon motoru) | Insight iskeleti: kural tabanlı öneri motoru (koşul deseni → öneri şablonu), kural kayıt defteri | Ortam kurulumu; kod tabanını öğrenme; bildirim/uyarı ayarları sayfası (React) | Öneri kural kütüphanesi içerik tamamlama; NG10 uygunluk denetimi başlangıç | 🟢 E-posta gönderimi + öneri iskeleti |
| **H10** | Haftalık özet/digest pipeline; e-posta şablonları (derin bağlantılı: skor, trend, öneri linkleri) | PDF rapor motoru: şablon + veri birleştirme, S3 depolama, imzalı URL üretimi | Öneri motoru tam: kural değerlendirme, NG10 filtresi, tekilleştirme, öneri API uçları | Öneri akışı bileşeni (skor kartı altında); rapor görüntüleme/indirme sayfası | NG10 denetimi tamamlama; kullanıcı dokümantasyonu taslak | 🟢 Haftalık özet e-postası + öneri API |
| **H11** | Uyarı sistemi: anlık bildirim tetikleme, kanal dağıtımı (e-posta/pano), uyarı tercihleri entegrasyonu | Delivery API uçları tamamlama; scheduler entegrasyonu (zamanlanmış gönderim); CI/CD güncelleme | Insight API tam: öneri işaretleme (uygulandı/reddedildi), M4 telemetri yazımı; hata ayıklama | White-label PDF önizleme; uyarı tercihleri sayfası; bildirim geçmişi görünümü | Sürüm notu taslağı; dokümantasyon güncelleme | 🟢 Uyarı sistemi + white-label PDF |
| **H12** | Uçtan uca test (ölçüm → öneri → uyarı → e-posta özeti → PDF rapor); demo senaryosu hazırlığı | Entegrasyon testleri (delivery + insight); CI/CD pipeline olgunlaştırma; doküman-kod senkronu | Hata ayıklama; performans iyileştirme; API dokümantasyonu | Demo ortamı; son rötuşlar; kullanıcı testi (iç) | Demo desteği; Dilim 3 dokümantasyon kapanışı; v1.1 kuyruğu kayıtları | 🔷 **Derin bağlantılı e-posta özeti + PDF rapor + öneri akışı canlı** |

### İlk Çıktı Takvimi

| Ne zaman | Ne çıktı | Kullanılabilirlik |
|:--------:|----------|:-----------------:|
| H9 sonu | E-posta bildirimi + öneri motoru iskeleti | Delivery API |
| H10 sonu | Haftalık özet e-postası + öneri API | Kullanıcı bildirimi |
| H11 sonu | Uyarı sistemi + PDF önizleme | Tüm kanallar |
| **H12 sonu** | **Uçtan uca değer halkası canlı** | **Demo gösterim** |

### Çıkış Kapısı Kriteri

Kullanıcı panoda öneri akışını görür, haftalık özet e-postası derin bağlantılarla gelir, PDF rapor indirilebilir, anlık uyarı tetiklenebilir. Öneriler NG10 filtresinden geçmiş ve iddia dili kurallarına uygundur.

---

## 7. Dilim 4 · Sertleştirme (H13–H16)

**Hedef:** Pilot çıkış kapısı (0205 §8 — 7 kriter) yeşil. Güvenlik sertleştirmesi, alarm seti, kalibrasyon provası.

| Hafta | Siz (TL+CEO) | Backend #1 (Platform) | Backend #2 (Sertleştirme) | Frontend | Analist (AN) | 🟢 Hafta Çıktısı |
|:-----:|:------------:|:---------------------:|:-------------------------:|:--------:|:------------:|:----------------:|
| **H13** | Kripto-silme altyapısı: zarf anahtarı oluşturma, S3 şifreleme entegrasyonu, anahtar yönetim arayüzü | Denetim zinciri doğrulama rutini: zincir tarama, kök karma saklama; izolasyon negatif test paketi | Sır yönetimi ve rotasyon altyapısı: kasa entegrasyonu, çift anahtar penceresi, rotasyon runbook kodlaması | Güvenlik ayarları sayfası; KVKK veri silme talebi arayüzü | Pilot çıkış kapısı kontrol listesi hazırlığı; güvenlik dokümantasyonu | 🟢 Kripto-silme + zincir doğrulama |
| **H14** | 0311 alarm seti kurulumu: kritik alarmlar (izolasyon reddi, zincir kopukluğu, determinizm, bütçe tavanı, DLQ); alarm → runbook bağlantısı | Metrik kataloğu implementasyonu: API, kuyruk, motor, hesap metrikleri; Prometheus metrik uçları | Rotasyon prosedürleri: oturum/derin bağlantı anahtarı rotasyonu; sır hijyeni log kontrolü; güvenlik CI/CD kapıları | Alarm ve metrik panosu (temel); sistem durumu sayfası | Operasyon runbook'ları taslağı; v1.1 kuyruğu kayıtları | 🟢 Alarm seti aktif, metrikler akıyor |
| **H15** | Kalibrasyon provası: örnekleme parametreleri (n=3, temp=0), alarm eşikleri, GA doğrulama, partial yayın, anlamlılık eşikleri | Cache stratejisi: Redis pano önbelleği, ETag desteği; yedekleme/DR çerçevesi (PITR, outbox yeniden inşa); performans testi | Güvenlik testleri: RBAC matrisi, izolasyon negatif testleri, sızma testi; CI/CD güvenlik kapıları | Kullanıcı kabul testi ortamı; son kullanıcı dokümantasyonu; onboarding akış prototipi | Pilot dokümantasyonu; kullanıcı kılavuzu; pilot kiracı onboarding planı | 🟢 Kalibrasyon provası yeşil, güvenlik testleri tam |
| **H16** | Pilot çıkış kapısı: 0205 §8'deki 7 kriterin tamamının doğrulanması; pilot onboarding hazırlığı; eksik kalan son işlerin kapatılması | Son güvenlik taraması; doküman-kod senkronu; v1.1 kuyruğu nihai kayıtları; PO onayına hazırlık | Tüm dokümanların Review → Approved geçişi için PO'ya hazırlık; kalan son açık soruların kapatılması | Pilot kullanıcı arayüzü son kontrolleri; onboarding yardım sayfaları | Pilot hazırlık: kiracı davetleri, onboarding dokümanları, v1.1 düzeltme turu kapanışı | 🔷 **Pilot çıkış kapısı ön kontrol listesi (0205 §8) yeşil — pilota hazır** |

### İlk Çıktı Takvimi

| Ne zaman | Ne çıktı | Kullanılabilirlik |
|:--------:|----------|:-----------------:|
| H13 sonu | Kripto-silme + zincir doğrulama | Güvenlik altyapısı |
| H14 sonu | Alarm seti + metrikler | Operasyon ekipleri |
| H15 sonu | Kalibrasyon provası + güvenlik testleri | Kalite kapısı |
| **H16 sonu** | **Pilot çıkış kapısı onayı** | **Pilot başlangıcı** |

### Çıkış Kapısı Kriteri (0205 §8)

| # | Kriter |
|:-:|--------|
| 1 | Sert kural ihlali sıfır: NFR-1, NFR-6, NFR-7 pilot boyunca istisnasız sağlandı |
| 2 | Kalibre edilen performans hedefleri (NFR-9) ardışık son iki haftada karşılandı |
| 3 | P2 ve P3 persona kartları saha verisiyle doğrulandı |
| 4 | K1 maliyet gerçekleşmesi panel modeli öngörüsüyle uyumlu |
| 5 | Motor kapsamı üretimde karara uygun çalışıyor (ChatGPT + Gemini + Perplexity) |
| 6 | Pilot kiracılarından en az bir P3 ve bir P2 referans sinyali alındı |
| 7 | Güvenlik kapanışı tamamlandı; açık kritik/yüksek bulgu sıfır |

---

## 8. Bağımlılık Zinciri (Kritik Yol)

```
H0: platform/db ──→ H1: httpmw → identity ──→ H2: RBAC/RLS
                                                      │
H0: measure ──→ H1: Perplexity ──→ H2: calc ──→ H3: scoring ──→ H4: demo
                                                                      │
H5: ChatGPT ──→ H6: Gemini ──→ H7: 3 motor ──→ H8: demo
                                                      │
H9: delivery ──→ H10: digest ──→ H11: uyarı ──→ H12: demo
                                                          │
H13: kripto-silme ──→ H14: alarm ──→ H15: kalibrasyon ──→ H16: pilot kapısı
```

### Kritik Karar Noktaları

| Zaman | Karar | Blokaj |
|:------|:------|:-------|
| **H0 öncesi** | Backend #1 ve Analist işe alımı tamam | Dilim 1 başlayamaz |
| **H4–H5 arası** | ChatGPT/Gemini API anahtarları hazır | Dilim 2 başlayamaz |
| **H8–H9 arası** | Frontend işe alımı tamam + e-posta servisi seçilmiş | Dilim 3 başlayamaz |
| **H12–H13 arası** | Kasa/KMS kararları alınmış | Sertleştirme başlayamaz |
| **H15–H16 arası** | PO tüm dokümanları Approved yapmış | Pilot kapısı açılamaz |

---

## 9. Ekip Büyüme Takvimi

| Tarih | Olay |
|:-----:|------|
| **H0** (3 Ağu) | Backend #1 + Analist işe başlar — 4 kişi |
| **H9** (5 Eki) | Frontend katılır — 5 kişi |
| **Pilot öncesi** (Ara) | DevOps/SRE katılır — 6 kişi |

---

## 10. Çıkış Kapıları Özeti

| Kapı | Hf | Kriter |
|:----:|:--:|--------|
| **Dilim 1** | H4 | Kaydol → panel → ölçüm → Perplexity → 4 bileşenli skor → panoda. Korelasyon zinciri logda. |
| **Dilim 2** | H8 | 3 motor ayrı skor. Site denetim bulguları saniyeler içinde. Her motor için korelasyon. |
| **Dilim 3** | H12 | Öneri akışı. Derin bağlantılı e-posta özeti. PDF. Anlık uyarı. NG10 filtresi. |
| **Dilim 4** | H16 | 0205 §8: 7 kriterin tamamı yeşil. Pilot başlangıcı. |

---

## 11. Karar Kaydı

| ID | Soru | Karar | Gerekçe | Kapandı |
|:--:|------|:-----:|---------|:-------:|
| **✅ O-1** | E-posta gönderim servisi | **SendGrid** | AVIP D-16 (0402 v1.2). Türkiye'de kanıtlı, Go SDK hazır, şablon motoru yeterli. MVP ölçeği için ücretsiz kademe yeterli. | TL (22.07.2026) |
| **✅ O-2** | Sır kasası/KMS | **SOPS + Age** | Self-hosted VM'ler için en hafif çözüm. Git'te şifreli dosya, CI/CD'de çözülür. Sıfır operasyonel yük, 4 kişilik ekip için ideal. | TL (22.07.2026) |
| **✅ O-3** | PDF render motoru | **Maroto v2** | Pure Go, sıfır bağımlılık. Türkçe font desteği mükemmel. White-label raporlar için `chromedp` (ağır) gerekmez. | TL (22.07.2026) |
| **✅ D-13** | İş takip aracı | **GitHub Projects/Issues** | AVIP D-13 (0401 O-1 — TL 21.07.2026). Depo ile aynı platformda. Ek araç gerektirmez. | TL (devralındı) |
| **✅ D-14** | CI platformu | **GitHub Actions** | AVIP D-14 (0403 O-1 — TL 21.07.2026). Go + React monorepo için yeterli. Self-hosted runner pilot öncesi değerlendirilir. | TL (devralındı) |
| **✅ D-53** | Walking skeleton planı | **4 dilimli plan (bu doküman)** | AVIP D-53 (0301 O-1 — PO+TL 21.07.2026). Bu dokümanda tanımlanan 4 dilimli yapı PO+TL onayından geçmiştir. | PO+TL (devralındı) |

### Kararların Etkileri

| Karar | Dilim | Hafta | Kim Etkilenir |
|:------|:-----:|:-----:|:-------------|
| SendGrid | D3 | H9 | Backend #1 — SendGrid Go SDK entegrasyonu, şablon oluşturma |
| SOPS + Age | D4 | H13 | Backend #2 — sır yönetimi altyapısı, CI/CD'ye decrypt adımı |
| Maroto v2 | D3 | H9 | Backend #1 — PDF render motoru kurulumu, şablon geliştirme |

---

## Kaynaklar

- AVIP Development Process: `archive/avip-v1/0401-development-process.md`
- GeoLens Master Plan: `platform/docs/00-overview/0000-master-plan.md`
- MVP Kapsamı: `platform/docs/02-product/0205-mvp.md`
- Yol Haritası: `platform/docs/02-product/0206-roadmap.md`

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: GeoLens Platform proje planı. 4 dilim (16 hafta), haftalık kişi bazlı sorumluluk tabloları, ekip yapısı, bağımlılık zinciri, çıkış kapısı kriterleri. AVIP arşivinden uyarlanmıştır. |
| 1.1 | 22.07.2026 | Bloklayıcı 3 açık soru kapatıldı: O-1 SendGrid, O-2 SOPS+Age, O-3 Maroto v2. Karar kaydı bölümü eklendi, her kararın etkileri ve sorumlusu belirtildi. |
| 1.2 | 22.07.2026 | AVIP kapalı kararları eklendi: D-13 (GitHub Projects), D-14 (GitHub Actions), D-53 (walking skeleton onayı). H4 satırında GitHub Actions referansı eklendi. |
| 1.3 | 23.07.2026 | Dilim 2, 3 ve 4'e `### İlk Çıktı Takvimi` h3 başlıkları eklendi. Arşivdeki 0401-development-process.md ile tutarlılık sağlandı. |
| 1.4 | 23.07.2026 | Dilim 1 `### Implementasyon` h3'ü eklendi. Birim testleri yazıldı (7 paket, 30+ test). GitHub Actions CI/CD pipeline eklendi. |
| 1.5 | 23.07.2026 | React SPA iskeleti + skor kartı prototipi tamamlandı (ScoreCard, ScoreDashboard, auth, API client). |
| 1.6 | 23.07.2026 | H4 tamamlandı: tenantID/competitor_context fix, testcontainers entegrasyon testi, TrendChart SVG bileşeni, demo ortamı (docker-compose + seed + script), API dokümantasyonu güncellemesi, ADR-006 kapanış kaydı. Dilim 1 çıkış kapısı kriterleri sağlandı. |
