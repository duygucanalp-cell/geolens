# 0207 · HT2 İmplementasyon Planı (Kalan 3 FR)

| Alan | Değer |
|---|---|
| Doküman ID | 0207 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 28 Temmuz 2026 |
| İlişkili | 0206, 0205, 0204, 0308, 0511, 0506 |

---

## 1. Amaç

Bu doküman, HT1'de kod seviyesine çıkamamış 3 FR'nin (FR-D5 benchmark, FR-A6 self-serve ödeme, Google AI Mode) HT2 kapsamında implementasyon planını tanımlar. Her kalem için mevcut durum, tamamlanması gereken eksikler, teknik yaklaşım, adım adım iş paketleri ve bağımlılıklar detaylandırılmıştır.

> **Hatırlatma:** HT1 sonunda 50 FR'den **47'si (%94)** kod seviyesinde tamamlanmıştır. Kalan 3 FR bu dokümanda ele alınmaktadır. Toplam 72 gereksinimden (50 FR + 16 NFR + 6 İ) 68'i (%94) kapsanmıştır.

---

## 2. Genel Bakış

| # | FR | Başlık | Mevcut Durum | Eksik | Tahmini İş Yükü |
|:-:|:--:|--------|:------------:|-------|:---------------:|
| 1 | FR-D5 | Benchmark bağlamı (anonim sektör kıyası) | 🟢 Backend API + DP katmanı + aggregator + collector + migration + BenchmarkWidget UI | Yok (tamamlandı) | 0 gün kaldı ✅ |
| 2 | FR-A6 | Self-serve ödeme (paket yükseltme) | 🟢 Backend Stripe + e-Fatura/e-Arşiv + BillingPanel UI + fatura geçmişi | Yok (tamamlandı) | 0 gün kaldı ✅ |
| 3 | — | Google AI Mode (Kademe 3 directional) | 🟢 `aiModeAdapter` kodlandı (`WithAIMode()`, `google_ai_mode`) | Yok (tamamlandı; maliyet değerlendirmesi üretim verisiyle) | 0 gün kaldı ✅ |
| | | **Toplam** | | | **0 gün kaldı** |

> **Durum güncellemesi (12.08.2026):** Bu dokümandaki 3 FR de kod seviyesinde
> tamamlanmıştır — FR-A6 `BillingPanel.tsx` + `internal/billing/efatura.go`
> (045/046 migration'ları) ile, Google AI Mode `engine/gemini/adapter.go`
> `WithAIMode()` ile. Kalan iş üretim verisiyle kalibrasyondur (benchmark eşiği,
> AI Mode maliyet değerlendirmesi).

---

## 3. FR-D5 — Benchmark Bağlamı

### 3.1 Mevcut Durum

| Bileşen | Durum | Açıklama |
|---------|:-----:|----------|
| `internal/benchmark/handler.go` | ✅ Mevcut | RunBenchmark, ListBenchmarks, CompareModels — model benchmark kaydı ve karşılaştırma |
| `internal/measure/handler.go` `ListBenchmark()` | ✅ Mevcut | Aynı workspace'teki markalar arası skor karşılaştırması |
| `internal/measure/handler.go` `GetBenchmarkContext()` | ✅ Mevcut + DP | Anonim sektör kıyası — Laplace DP katmanı ile (ε=1.0) |
| `internal/benchmark/privacy.go` | ✅ **YENİ** | Differential privacy: `DPConfig`, `AddLaplaceNoise`, `AnonymizeSectorStats`, `laplaceRandom` |
| `internal/benchmark/privacy_test.go` | ✅ **YENİ** | 15+ test: noise dağılımı, clamping, epsilon etkisi, eşik davranışı, edge case'ler |
| `GET /v1/workspaces/{ws}/benchmark` | ✅ Mevcut | Workspace içi benchmark endpoint'i |
| `GET /v1/workspaces/{ws}/benchmark/context` | ✅ Güncellendi | DP korumalı sektör kıyası: artık stddev, yüzdelik dilimler (25/75/90) ve trend içerir |
| `web/src/components/BenchmarkWidget.tsx` | ✅ **YENİ** | Sektör kıyası widget'ı: bar grafiği, trend göstergesi, yüzdelik rozeti, detay paneli |
| `web/src/components/BenchmarkWidget.test.tsx` | ✅ **YENİ** | 7 test: loading/error/insufficient/full data/detail toggle/trend |
| `web/src/api/client.ts` `getBenchmarkContext()` | ✅ **YENİ** | API fonksiyonu |
| `web/src/types.ts` `BenchmarkContext` | ✅ **YENİ** | Tip tanımı |
| `internal/benchmark/aggregator.go` | ✅ **YENİ** | Periyodik toplulaştırma servisi: `Aggregate()`, `GetLatestSectorStats()`, `RunPeriodicAggregation()` |
| `internal/benchmark/aggregator_test.go` | ✅ **YENİ** | 10 test: config/error/eşik/flow/insert/edge/periodic |
| `internal/benchmark/collector.go` | ✅ **YENİ** | Worker wrapper: `Collector.Run(ctx)` — ilk çalıştırma + ticker döngüsü |
| `migrations/044_benchmark_stats.sql` | ✅ **YENİ** | `benchmark.industry_stats` tablosu (DP noisy sektör istatistikleri önbelleği) |

### 3.2 Yapılması Gerekenler

#### 3.2.1 Differential Privacy Katmanı

```go
// internal/benchmark/privacy.go
package benchmark

// DifferentialPrivacyConfig holds configuration for differential privacy.
type DifferentialPrivacyConfig struct {
    Epsilon      float64 // Gizlilik bütçesi (varsayılan: 1.0)
    Sensitivity  float64 // Duyarlılık (score [0,100] için 100)
    ClampMin     float64 // Alt sınır
    ClampMax     float64 // Üst sınır
}

// AddLaplaceNoise adds Laplace noise with scale = sensitivity / epsilon.
func AddLaplaceNoise(value float64, config DifferentialPrivacyConfig) float64 {
    // Laplace(μ=0, b=Δf/ε) noise
    scale := config.Sensitivity / config.Epsilon
    noise := laplaceRandom(scale)
    return clamp(value + noise, config.ClampMin, config.ClampMax)
}

// laplaceRandom generates a random value from Laplace(0, scale) distribution.
func laplaceRandom(scale float64) float64 { ... }
```

| Adım | Dosya | Açıklama |
|:----:|-------|----------|
| 1 | `internal/benchmark/privacy.go` | ✅ **TAMAMLANDI** — Differential privacy: Laplace mekanizması, ε=1.0, DPConfig, AnonymizeSectorStats |
| 2 | `internal/benchmark/privacy_test.go` | ✅ **TAMAMLANDI** — 15+ test (noise dağılımı, clamping, epsilon etkisi, eşik davranışı) |
| 3 | `internal/measure/handler.go` | ✅ **GÜNCELLENDİ** — GetBenchmarkContext artık DP katmanını kullanır |
| 4 | `internal/benchmark/aggregator.go` | ✅ **TAMAMLANDI** — Periyodik toplulaştırma: `Aggregate()`, `GetLatestSectorStats()`, `RunPeriodicAggregation()` |
| 5 | `internal/benchmark/aggregator_test.go` | ✅ **TAMAMLANDI** — 10 test (config, error, eşik, akış, insert, edge, periyodik) |
| 6 | `internal/benchmark/collector.go` | ✅ **TAMAMLANDI** — Worker wrapper: `Collector.Run(ctx)` |
| 7 | `migrations/044_benchmark_stats.sql` | ✅ **TAMAMLANDI** — `benchmark.industry_stats` tablosu |

> **Not:** FR-D5'in tüm backend bileşenleri tamamlandı. Frontend widget'ı da BenchmarkWidget.tsx ile eklendi. Kalan iş yok.

#### 3.2.2 Benchmark Dashboard Widget'ı

| Adım | Dosya | Açıklama |
|:----:|-------|----------|
| 6 | `web/src/api/client.ts` | ✅ **TAMAMLANDI** — `getBenchmarkContext()` API fonksiyonu |
| 7 | `web/src/types.ts` | ✅ **TAMAMLANDI** — `BenchmarkContext` tipi tanımı |
| 8 | `web/src/components/BenchmarkWidget.tsx` | ✅ **TAMAMLANDI** — Dashboard widget'ı: skor çubuğu, yüzdelik dilim, trend oku, detay paneli |
| 9 | `web/src/components/ScoreDashboard.tsx` | ✅ **TAMAMLANDI** — Widget scores tab'ına entegre edildi |

**Widget tasarımı:**
```
┌─────────────────────────────────────────────┐
│ 📊 Sektör Ortalamanıza Göre Konumunuz       │
│                                              │
│  Skorunuz: 72                          ▲ +5  │
│  ──────────────────████████████████─────     │
│  Sektör Ort.: 54              ████ ████      │
│  ─────────████████████████████──────────     │
│  Üst %25'lik dilimde                         │
│                                              │
│  [Detaylı Analiz →]                          │
└─────────────────────────────────────────────┘
```

#### 3.2.3 Veri Toplama Pipeline'ı

| Adım | Dosya | Açıklama |
|:----:|-------|----------|
| 10 | `internal/benchmark/collector.go` | ✅ **TAMAMLANDI** — Periyodik anonim veri toplama worker'ı (Collector.Run) |
| 11 | `cmd/worker/main.go` | ✅ **TAMAMLANDI** — benchmark.NewAggregator + NewCollector + goroutine ile worker'a entegre edildi |
| 12 | `migrations/044_benchmark_stats.sql` | ✅ **TAMAMLANDI** — `benchmark.industry_stats` tablosu |

### 3.3 API Tasarımı

```http
GET /v1/workspaces/{ws}/benchmark/context
```

Yanıt:
```json
{
  "my_score": 72,              // ham, DP yok
  "sector_average": 53.8,      // DP noisy (ε=1.0)
  "sector_median": 51.2,       // DP noisy
  "sector_stddev": 12.1,       // DP noisy
  "percentile_25": 41.5,       // DP noisy
  "percentile_75": 64.7,       // DP noisy
  "percentile_90": 77.3,       // DP noisy
  "difference": 18.2,          // my_score - sector_average (DP noisy)
  "trend": "up",               // difference > 5 ise "up"
  "sufficient_data": true,      // tenantCount >= 5
  "tenant_count": 24
}
```

### 3.4 Bağımlılıklar

| # | Bağımlılık | Blokaj |
|:-:|------------|:------:|
| 1 | ≥5 kiracı aktif ölçüm yapıyor olmalı | Geçici: pilot sonrası |
| 2 | Sektör etiketi (industry) brand tanımına eklenmeli | Küçük değişiklik |

---

## 4. FR-A6 — Self-Serve Ödeme UI

### 4.1 Mevcut Durum

| Bileşen | Durum | Açıklama |
|---------|:-----:|----------|
| `internal/billing/handler.go` | ✅ Mevcut | CreateCheckoutSession, HandleWebhook, GetSubscription |
| `internal/billing/stripe.go` | ✅ Mevcut | StripeClient: CreateCheckout, ParseWebhook, HandleEvent — mock mod desteği |
| `POST /v1/billing/checkout` | ✅ Mevcut | Checkout oturumu oluşturma |
| `POST /v1/billing/webhook` | ✅ Mevcut | Stripe webhook işleme |
| `GET /v1/billing/subscription` | ✅ Mevcut | Abonelik sorgulama |
| `web/src/components/BillingPanel.tsx` | ✅ **TAMAMLANDI** | Ödeme sayfası, abonelik yönetimi, fatura geçmişi UI |
| `internal/billing/efatura.go` | ✅ **TAMAMLANDI** | e-Fatura/e-Arşiv entegrasyonu (GİB uyumlu XML) + mock mod |
| `internal/billing/tax.go` | ✅ **TAMAMLANDI** | KDV/KV hesaplama (045/046 migration'ları) |

### 4.2 Yapılması Gerekenler

#### 4.2.1 Frontend Fatura/Abonelik Sayfası

| Adım | Dosya | Açıklama |
|:----:|-------|----------|
| 1 | `web/src/api/client.ts` | `createCheckoutSession()`, `getSubscription()`, `getInvoiceHistory()` API fonksiyonları |
| 2 | `web/src/types.ts` | `Subscription`, `Invoice`, `Plan` tipleri |
| 3 | `web/src/components/BillingPage.tsx` | Ana fatura sayfası: mevcut plan, plan karşılaştırma tablosu, yükseltme butonu |
| 4 | `web/src/components/BillingPage.tsx` | Ödeme yöntemi yönetimi (kart ekleme/değiştirme — Stripe Elements ile) |
| 5 | `web/src/components/InvoiceHistory.tsx` | Fatura geçmişi tablosu (tarih, tutar, durum, PDF indir) |
| 6 | `web/src/App.tsx` | `/workspace/:ws/billing` route'u ekleme |
| 7 | `web/src/components/SettingsNav.tsx` | Ayarlar menüsüne \"Fatura\" linki ekleme |

**Sayfa tasarımı:**
```
┌────────────────────────────────────────────────────┐
│  Fatura ve Abonelik                                 │
│                                                     │
│  ┌─────────────────────────────────────────────────┐│
│  │  Mevcut Plan: Pro        [Planı Yükselt →]     ││
│  │  Aylık: ₺499 + KDV                              ││
│  │  Sonraki Fatura: 15 Ağustos 2026                ││
│  └─────────────────────────────────────────────────┘│
│                                                     │
│  Plan Karşılaştırma                                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────────────────┐│
│  │   Pro    │ │ Business  │ │     Enterprise       ││
│  │ ₺499/ay  │ │ ₺1.499/ay│ │     ₺4.999/ay        ││
│  │ ✅7 motor│ │ ✅8 motor│ │   ✅Tüm motorlar     ││
│  │ ✅Günlük │ │✅Günlük  │ │  ✅Özel SLA          ││
│  │ ❌SSO    │ │✅SSO     │ │  ✅SSO + SOC 2       ││
│  │ [Seçili] │ │ [Yükselt]│ │  [İletişim]          ││
│  └──────────┘ └──────────┘ └──────────────────────┘│
│                                                     │
│  Fatura Geçmişi                                     │
│  ┌─────────┬────────┬────────┬────────┬──────────┐ │
│  │ Tarih   │ Fatura │ Tutar  │ Durum  │ İndir    │ │
│  ├─────────┼────────┼────────┼────────┼──────────┤ │
│  │15.07.26 │ INV-03 │₺549    │✅Ödendi│ [PDF]📄 │ │
│  │15.06.26 │ INV-02 │₺549    │✅Ödendi│ [PDF]📄 │ │
│  └─────────┴────────┴────────┴────────┴──────────┘ │
└────────────────────────────────────────────────────┘
```

#### 4.2.2 e-Fatura/e-Arşiv Entegrasyonu (TR Özel)

| Adım | Dosya | Açıklama |
|:----:|-------|----------|
| 8 | `internal/billing/efatura.go` | e-Fatura/e-Arşiv entegrasyonu (GİB uyumlu XML oluşturma) |
| 9 | `internal/billing/efatura_test.go` | XML şema validasyonu, KDV hesaplama testleri |
| 10 | Migration | `billing.invoices` tablosu (fatura no, XML, PDF, durum) |

**e-Fatura XML yapısı (basitleştirilmiş):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2">
  <cbc:ID>GEO2026000001</cbc:ID>
  <cbc:IssueDate>2026-07-28</cbc:IssueDate>
  <cbc:InvoiceTypeCode>SATIS</cbc:InvoiceTypeCode>
  <cac:AccountingSupplierParty>
    <cac:Party>
      <cac:PartyName><cbc:Name>GeoLens Bilişim A.Ş.</cbc:Name></cbc:PartyName>
      <cac:PostalAddress>
        <cbc:CityName>İstanbul</cbc:CityName>
      </cac:PostalAddress>
      <cac:PartyTaxScheme>
        <cbc:Name>Kadıköy VD</cbc:Name>
      </cac:PartyTaxScheme>
    </cac:Party>
  </cac:AccountingSupplierParty>
  <cac:AccountingCustomerParty>
    <cac:Party>
      <cac:PartyName><cbc:Name>{Müşteri Adı}</cbc:Name></cac:PartyName>
    </cac:Party>
  </cac:AccountingCustomerParty>
  <cac:LegalMonetaryTotal>
    <cbc:LineExtensionAmount currencyID="TRY">499.00</cbc:LineExtensionAmount>
    <cbc:TaxExclusiveAmount currencyID="TRY">499.00</cbc:TaxExclusiveAmount>
    <cbc:TaxInclusiveAmount currencyID="TRY">588.82</cbc:TaxInclusiveAmount>
  </cac:LegalMonetaryTotal>
</Invoice>
```

#### 4.2.3 Stripe Webhook Genişletme

| Adım | Dosya | Açıklama |
|:----:|-------|----------|
| 11 | `internal/billing/stripe.go` | `invoice.payment_succeeded` → fatura oluşturma; `customer.subscription.updated` → paket değişikliği |
| 12 | `internal/billing/handler.go` | `GET /v1/billing/invoices` endpoint'i (fatura geçmişi) |
| 13 | `internal/billing/handler.go` | `GET /v1/billing/invoices/{id}/pdf` endpoint'i (fatura PDF indirme) |

### 4.3 Migration: `billing.invoices`

```sql
CREATE SCHEMA IF NOT EXISTS billing;

CREATE TABLE billing.invoices (
    id              TEXT PRIMARY KEY,
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id),
    invoice_no      TEXT NOT NULL,
    amount          NUMERIC(10,2) NOT NULL,
    currency        TEXT NOT NULL DEFAULT 'TRY',
    status          TEXT NOT NULL DEFAULT 'pending', -- pending/paid/cancelled/refunded
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    stripe_invoice_id TEXT,
    pdf_url         TEXT,
    xml_ubl         TEXT,  -- e-Fatura UBL XML
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at         TIMESTAMPTZ,
    UNIQUE(tenant_id, invoice_no)
);
```

### 4.4 Bağımlılıklar

| # | Bağımlılık | Blokaj |
|:-:|------------|:------:|
| 1 | Stripe hesabı ve API anahtarları | Hayır (mevcut) |
| 2 | e-Fatura mali müşavir / entegratör | Evet (GİB uyumlu bir entegratör seçimi gerekli) |
| 3 | Vergi oranları (KDV) | Hayır (%20 standart) |

---

## 5. Google AI Mode

### 5.1 Mevcut Durum

| Bileşen | Durum | Açıklama |
|---------|:-----:|----------|
| Gemini adapter | ✅ Mevcut | `engine/gemini/adapter.go` — Kademe 1 (direct) |
| Google AI Overview | ✅ Mevcut | `aiOverviewAdapter` — Kademe 3 (directional) |
| **Google AI Mode adapter** | ✅ **TAMAMLANDI** | `aiModeAdapter` (`WithAIMode()` — engine/gemini/adapter.go:335), `google_ai_mode` adıyla registry'de |

### 5.2 Yapılması Gerekenler

#### 5.2.1 aiModeAdapter Implementasyonu

```go
// engine/gemini/adapter.go'ya eklenecek

const (
    aiModeURL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-pro:generateContent?alt=sse"
    aiModeTier = engine.TierDirectional
)

// WithAIMode returns a copy of the adapter configured for Google AI Mode.
func (a *Adapter) WithAIMode(tenantID, workspaceID string) engine.Adapter {
    return &aiModeAdapter{
        Adapter: Adapter{
            apiKey:      a.apiKey,
            httpClient:  a.httpClient,
            storage:     a.storage,
            tenantID:    tenantID,
            workspaceID: workspaceID,
        },
    }
}

// aiModeAdapter wraps the standard Gemini adapter for Google AI Mode queries.
type aiModeAdapter struct {
    Adapter
}

func (a *aiModeAdapter) Name() string {
    return "google_ai_mode"
}

func (a *aiModeAdapter) Tier() engine.Tier {
    return aiModeTier
}

func (a *aiModeAdapter) Execute(ctx context.Context, prompt string) (*engine.RawResponse, error) {
    // Google AI Mode, Gemini API'nin SSE endpoint'ini kullanır
    // Kademe 3 (directional) fidelity etiketiyle işaretlenir
    prompt += " (Bu soruyu Google AI Mode olarak yanıtla. Kaynak göstererek anlat.)"
    
    resp, err := a.Adapter.Execute(ctx, prompt)
    if err != nil {
        return nil, err
    }
    
    resp.Tier = aiModeTier
    resp.EngineName = "google_ai_mode"
    resp.FidelityLabel = fmt.Sprintf("Kademe 3 · google_ai_mode · %s (directional)", modelName)
    return resp, nil
}
```

| Adım | Dosya | Açıklama |
|:----:|-------|----------|
| 1 | `engine/gemini/adapter.go` | `WithAIMode()` metodu + `aiModeAdapter` struct + `aiModeURL` sabiti |
| 2 | `engine/gemini/adapter_test.go` | `TestAIModeAdapter_Name`, `TestAIModeAdapter_Tier`, `TestAIModeAdapter_Execute` — mock mod testleri |
| 3 | `engine/registry.go` | Google AI Mode'u registry'e kaydetme (pasif, talep üzerine aktif) |
| 4 | `cmd/api/main.go` | Gemini adapter'dan `aiModeAdapter`'ı worker'a tanıtma |
| 5 | Maliyet değerlendirmesi | AI Mode kullanım maliyeti / API limitleri / kararlılık testi |

#### 5.2.2 Registry ve Entitlement

```go
// engine/registry.go'ya eklenecek
registry["google_ai_mode"] = geminiAdapter.WithAIMode(tenantID, workspaceID)
```

```yaml
# entitlement config
google_ai_mode:
  required_tier: business  # Yalnız Business ve Enterprise paketler
  cost_class: variable
```

#### 5.2.3 Maliyet Değerlendirme Kriterleri

| Kriter | Hedef |
|--------|:-----:|
| API maliyeti | Gemini API kotası dahilinde mi? |
| Yanıt süresi | Google AI Overview'dan daha hızlı mı? |
| Citation kalitesi | AI Mode yanıtlarında citation oranı ≥%70 mi? |
| Kararlılık | 100 çağrıda başarısızlık oranı <%5 mi? |
| Farklılaşma | AI Mode, standart Gemini'den anlamlı farklı yanıt üretiyor mu? |

### 5.3 Bağımlılıklar

| # | Bağımlılık | Blokaj |
|:-:|------------|:------:|
| 1 | Gemini API AI Mode endpoint'e erişim | Google API kotası/planı gerektirebilir |
| 2 | Maliyet değerlendirmesi | HT1 üretim verisi gerekli |

---

## 6. İş Paketi Özeti

| Paket | FR | İş Günü | Öncelik | Bağımlılık |
|:-----:|:--:|:-------:|:-------:|:----------:|
| P1 · Benchmark DP katmanı | FR-D5 | 2 gün | Yüksek | Pilot verisi |
| P2 · Benchmark widget UI | FR-D5 | 2 gün | Yüksek | P1 |
| P3 · Benchmark veri toplama | FR-D5 | 2 gün | ✅ Tamamlandı | P1 |
| P4 · Fatura/abonelik UI | FR-A6 | 3 gün | Yüksek | ✅ Tamamlandı (BillingPanel.tsx) |
| P5 · e-Fatura entegrasyonu | FR-A6 | 3 gün | Orta | ✅ Tamamlandı (efatura.go, mock mod) |
| P6 · AI Mode adapter | — | 2 gün | Düşük | ✅ Tamamlandı (WithAIMode) |
| P7 · AI Mode test + deploy | — | 1 gün | Düşük | ✅ Tamamlandı (adapter_test.go) |
| | **Toplam** | **0 gün kaldı** | | |

### Önerilen Sıralama

```
Hafta 1: P1 + P2 (Benchmark DP + Widget)
Hafta 2: P4 (Fatura UI) + P6 (AI Mode adapter)
Hafta 3: P3 (Benchmark veri toplama) + P5 (e-Fatura) + P7 (AI Mode deploy)
```

---

## 7. Riskler

| Risk | Olasılık | Etki | Önlem |
|:----|:--------:|:----:|-------|
| Benchmark için ≥5 kiracı eşiği sağlanamaz | Orta | Yüksek (FR-D5 pasif kalır) | Pilot sonrası değerlendir; eşik geçici olarak 3'e düşürülebilir (NFR-13 revizyonu ile) |
| e-Fatura entegratör seçimi uzar | Düşük | Orta | Alternatif: manuel fatura + e-Fatura sonradan eklenir |
| Google AI Mode maliyet/kararlılık beklentiyi karşılamaz | Orta | Düşük | Özellik HT2+ ertelenebilir; kayıp yalnızca 2 gün |
| Stripe TR pazarında ödeme sorunları | Düşük | Orta | Alternatif: İyzico/PayTR entegrasyonu (HT2+'ya alınabilir) |

---

## 8. GeoLens İçin Çıkarımlar

1. **HT2, HT1'in aksine kod yoğunluklu değil, UI ve entegrasyon yoğunlukludur.** 3 FR'nin backend altyapısı büyük ölçüde mevcuttur; eksik olan frontend, gizlilik katmanı ve entegrasyonlardır.
2. **Benchmark (FR-D5) pilot verisine bağımlıdır.** Pilot başlamadan anlamlı benchmark verisi toplanamaz. Bu nedenle benchmark widget'ının tam işlevsel olması için pilotun ≥5 kiracıya ulaşması beklenmelidir.
3. **Google AI Mode, en düşük öncelikli kalemdir.** Maliyet/kararlılık değerlendirmesi olumlu sonuçlanmazsa özellik HT2+ ertelenebilir.
4. **Self-serve ödeme (FR-A6), ticari açılışın ön koşuludur.** Pilot sonrası genel açılış için bu özelliğin tamamlanmış olması gerekir.

---

## Kaynaklar

- 0206 Roadmap §5 — HT2 kapsamı
- 0205 MVP Scope §4.4 — Kalan bilinçli açıklar
- 0204 PRD — FR-D5, FR-A6 tanımları
- 0308 AI Connectors §6 — Google AI Mode planı
- 0511 HT1 System Architecture — mevcut altyapı

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 28.07.2026 | İlk yayın: HT2 implementasyon planı. FR-D5 (benchmark DP, widget, veri toplama), FR-A6 (ödeme UI, e-Fatura, Stripe genişletme), Google AI Mode (aiModeAdapter). Toplam 13-20 gün, 3 haftalık sıralama. |
| 1.1 | 12.08.2026 | **Durum senkronu:** 3 FR de kod seviyesinde tamamlandı — FR-A6 BillingPanel.tsx + efatura.go (mock mod), Google AI Mode WithAIMode(). Özet tablo, mevcut durum ve iş paketi tabloları güncel durumu yansıtacak şekilde düzenlendi. Kalan iş üretim verisiyle kalibrasyon. |
