# 0308 · AI Bağlayıcıları (AI Connectors)

| Alan | Değer |
|---|---|
| Doküman ID | 0308 |
| Proje | GeoLens Platform |
| Versiyon | 1.2 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 28 Temmuz 2026 |
| İlişkili | ADR-005, 0505, 0305, 0206, 0511, engine/ |

---

## 1. Amaç

Bu doküman, GeoLens'in AI motor bağdaştırıcı (adapter) mimarisini tanımlar. Bağdaştırıcı sözleşmesi, hata sınıfları, dayanıklılık mekanizmaları, tüm motor bağdaştırıcılarının detaylı özellikleri ve yeni motor ekleme sürecini kapsar.

> **HT1 kapsamı:** MVP'deki 3 çekirdek motor (Perplexity, ChatGPT, Gemini) üzerine HT1'de 5 yeni motor eklenmiştir: Claude (Kademe 2), Grok (Kademe 2), Copilot (Kademe 3), Mistral (Kademe 2), Google AI Overview (Kademe 3). Toplam **8 AI yüzeyi** üretimdedir.

---

## 2. Bağdaştırıcı Sözleşmesi

### 2.1 Adapter Arayüzü

```go
type Adapter interface {
    Name() string
    Tier() Tier
    Execute(ctx context.Context, prompt string) (*RawResponse, error)
}

type RawResponse struct {
    EngineName    string     `json:"engine_name"`
    RequestID     string     `json:"request_id"`
    Content       string     `json:"content"`
    Citations     []Citation `json:"citations"`
    HasSearch     bool       `json:"has_search"`
    Tier          Tier       `json:"tier"`
    FidelityLabel string     `json:"fidelity_label"`
    S3Ref         string     `json:"s3_ref,omitempty"`
}

type Citation struct {
    URL      string `json:"url"`
    Position int    `json:"position"`
    Engine   string `json:"engine"`
    Type     string `json:"type"`
    Title    string `json:"title,omitempty"`
}

type Tier int
const (
    TierDirect         Tier = 1 // Doğrudan API yanıtı
    TierOfficialProxy   Tier = 2 // Resmî arama/grounding API
    TierDirectional    Tier = 3 // Dolaylı sinyal çıkarımı
)
```

### 2.2 WithContext Deseni

Her adapter, tenant ve workspace bağlamını taşımak için `WithContext(tenantID, workspaceID string) Adapter` metodu uygular. Bu, adapter'ın her tenant için ayrı bir örnek gibi davranmasını sağlar (izolasyon, rate limiting, maliyet takibi).

### 2.3 RawSaver Entegrasyonu

Adapter'lar, ham API yanıtlarını S3'e kaydetmek için `engine.RawSaver` arayüzünü kullanır. Storage varsa, her başarılı `Execute()` çağrısı sonrası ham yanıt S3'e kaydedilir ve `RawResponse.S3Ref` alanı doldurulur.

### 2.4 Mock Modu

Tüm adapter'lar, API anahtarı boş veya `"mock"` olduğunda gerçekçi sahte yanıtlar döndüren mock modunu destekler. Bu, geliştirme ve demo ortamlarında gerçek API çağrısı yapılmadan çalışmayı sağlar.

---

## 3. Motor Bağdaştırıcıları

### 3.1 Tam Motor Tablosu

| Motor | Paket | Kademe | Tier Sabiti | API | Model | Maliyet | Durum |
|-------|:-----:|:------:|:-----------:|-----|:-----:|:-------:|:-----:|
| **Perplexity** | `engine/perplexity/` | 1 (direct) | `TierDirect` | Sonar Pro API (Chat Completions) | sonar-pro | Düşük | ✅ MVP |
| **ChatGPT** | `engine/chatgpt/` | 2 (official_proxy) | `TierOfficialProxy` | OpenAI Responses API (+ web arama) | gpt-5.1 | Orta | ✅ MVP |
| **Gemini** | `engine/gemini/` | 1 (direct) | `TierDirect` | Gemini generateContent (+ Google Search grounding) | gemini-3.5-pro | Düşük | ✅ MVP |
| **Google AI Overview** | `engine/gemini/` (aiOverviewAdapter) | 3 (directional) | `TierDirectional` | Gemini grounding vekili AI Overview endpoint | gemini-3.5-pro | Değişken | ✅ **HT1** |
| **Claude** | `engine/claude/` | 2 (official_proxy) | `TierOfficialProxy` | Anthropic Messages API | claude-sonnet-4 | Orta | ✅ **HT1** |
| **Grok** | `engine/grok/` | 2 (official_proxy) | `TierOfficialProxy` | xAI Chat Completions API | grok-3-latest | Orta | ✅ **HT1** |
| **Copilot** | `engine/copilot/` | 3 (directional) | `TierDirectional` | Microsoft Copilot Chat API | copilot-gpt-4o | Düşük | ✅ **HT1** |
| **Mistral** | `engine/mistral/` | 2 (official_proxy) | `TierOfficialProxy` | Mistral Chat Completions API | mistral-large-latest | Düşük-Orta | ✅ **HT1** |

> **Not:** Google AI Mode (Kademe 3) HT1'de kodlanmamıştır. Gemini adapter üzerinden AI Mode endpoint desteği HT2 planındadır (bkz. §6).

### 3.2 Perplexity (MVP — Kademe 1)

| Özellik | Değer |
|---------|-------|
| **API** | Perplexity Sonar Pro — Chat Completions uyumlu |
| **Alıntı** | `citations` dizisi (URL + metin içi konum) |
| **Hata yönetimi** | HTTP 429 → rate limit; 4xx/5xx → engine hatası |
| **Mock modu** | Gerçekçi marka yanıtı + 3 örnek citation |
| **Timeout** | 90 sn |
| **Detay** | `engine/perplexity/adapter.go` |

### 3.3 ChatGPT (MVP — Kademe 2)

| Özellik | Değer |
|---------|-------|
| **API** | OpenAI Responses API + `web_search` tool |
| **Alıntı** | `output_annotations` içinden URL çıkarımı |
| **Hata yönetimi** | HTTP 429 → rate limit + Retry-After; timeout → ErrEngineTimeout |
| **Mock modu** | Gerçekçi marka yanıtı + 3 örnek citation |
| **Timeout** | 90 sn |
| **Detay** | `engine/chatgpt/adapter.go` |

### 3.4 Gemini (MVP — Kademe 1)

| Özellik | Değer |
|---------|-------|
| **API** | Google Gemini generateContent + `google_search` tool (grounding) |
| **Alıntı** | `groundingAttributions` içinden URI çıkarımı |
| **Hata yönetimi** | HTTP 429 → rate limit; HTTP 403 → auth hatası |
| **Mock modu** | Gerçekçi marka yanıtı + 3 örnek grounding attribution |
| **Timeout** | 60 sn |
| **Not** | Hem standart Gemini (Kademe 1) hem de Google AI Overview (Kademe 3) aynı adapter'da |
| **Detay** | `engine/gemini/adapter.go` |

### 3.5 Google AI Overview (HT1 — Kademe 3)

| Özellik | Değer |
|---------|-------|
| **Adapter** | `aiOverviewAdapter` — Gemini adapter'ını saran Kademe 3 wrapper |
| **Mekanizma** | Gemini grounding API'sini kullanır, yanıtı AI Overview etiketiyle override eder |
| **Fidelity** | `"Kademe 3 · google_ai_overview · gemini-3.5-pro (official_proxy/directional)"` |
| **Kademe gerekçesi** | Google AI Overview'un programatik erişimi yoktur; Gemini grounding vekili ile proxy'lenir |
| **Kullanım** | `adapter.WithAIOverview(tenantID, workspaceID).Execute(ctx, prompt)` |
| **Detay** | `engine/gemini/adapter.go` — `WithAIOverview()` ve `aiOverviewAdapter` struct |

### 3.6 Claude (HT1 — Kademe 2)

| Özellik | Değer |
|---------|-------|
| **API** | Anthropic Messages API (`api.anthropic.com/v1/messages`) |
| **Model** | `claude-sonnet-4-20260514` |
| **Kimlik** | `x-api-key` header ile API anahtarı |
| **Alıntı** | `content` blokları içinde `cite` (URI) ve `source` (URL) alanları |
| **Hata yönetimi** | HTTP 429 → rate limit; HTTP 401 → auth hatası; boş content → ErrInvalidResponse |
| **Mock modu** | 3 citation içeren gerçekçi marka yanıtı |
| **Timeout** | 90 sn |
| **S3 kaydı** | Her başarılı yanıt sonrası S3'e kayıt (storage varsa) |
| **Detay** | `engine/claude/adapter.go` |

### 3.7 Grok (HT1 — Kademe 2)

| Özellik | Değer |
|---------|-------|
| **API** | xAI Chat Completions API (`api.x.ai/v1/chat/completions`) — OpenAI uyumlu |
| **Model** | `grok-3-latest` |
| **Kimlik** | Bearer token ile Authorization header |
| **Alıntı** | `annotations` dizisinde `url_citation` tipinde URL çıkarımı |
| **Hata yönetimi** | HTTP 429 → rate limit; boş choices → ErrInvalidResponse |
| **Mock modu** | 3 citation içeren gerçekçi marka yanıtı |
| **Timeout** | 90 sn |
| **S3 kaydı** | Her başarılı yanıt sonrası S3'e kayıt (storage varsa) |
| **Detay** | `engine/grok/adapter.go` |

### 3.8 Copilot (HT1 — Kademe 3)

| Özellik | Değer |
|---------|-------|
| **API** | Microsoft Copilot Chat API (`copilot.microsoft.com/api/chat/completions`) |
| **Model** | `copilot-gpt-4o` |
| **Kimlik** | Bearer token ile Authorization header |
| **Alıntı** | `message.citations` dizisinden URL çıkarımı |
| **Kademe gerekçesi** | Copilot web araması Bing üzerinden yapar; doğrudan API yanıtı değil, dolaylı sinyal |
| **Hata yönetimi** | HTTP 429 → rate limit; boş choices → ErrInvalidResponse |
| **Mock modu** | 2 citation içeren gerçekçi marka yanıtı |
| **Timeout** | 120 sn (Bing araması nedeniyle daha uzun) |
| **S3 kaydı** | Her başarılı yanıt sonrası S3'e kayıt (storage varsa) |
| **Detay** | `engine/copilot/adapter.go` |

### 3.9 Mistral (HT1 — Kademe 2)

| Özellik | Değer |
|---------|-------|
| **API** | Mistral Chat Completions API (`api.mistral.ai/v1/chat/completions`) — OpenAI uyumlu |
| **Model** | `mistral-large-latest` |
| **Kimlik** | Bearer token ile Authorization header |
| **Alıntı** | Mistral standard chat'te citation döndürmez; `HasSearch = false` |
| **GDPR-safe** | `safe_prompt: true` ile güvenli prompt filtresi aktif |
| **Hata yönetimi** | HTTP 429 → rate limit; boş choices → ErrInvalidResponse |
| **Mock modu** | 3 citation içeren, GDPR/KVKK uyumlu marka yanıtı |
| **Timeout** | 60 sn |
| **Stratejik önem** | AB pazarı + KVKK/GDPR uyumu; Le Chat yüzeyi opsiyonel |
| **S3 kaydı** | Her başarılı yanıt sonrası S3'e kayıt (storage varsa) |
| **Detay** | `engine/mistral/adapter.go` |

---

## 4. Hata Sınıfları ve Dayanıklılık

| Hata | HTTP Karşılığı | Adapter | Aksiyon |
|------|:--------------:|:-------:|---------|
| `ErrEngineTimeout` | 504 | Tümü | Yeniden dene (3 deneme, üstel backoff + jitter) |
| `ErrRateLimited` | 429 | Tümü | Backoff + Retry-After header'ına uy; kota aşımıysa ertele (deneme sayılmaz) |
| `ErrInvalidResponse` | 502 | Tümü | Sample atla; belirli adapter'larda (Copilot) boş choices sık karşılaşılan durum |
| `ErrAuthFailed` | 401 | Perplexity, ChatGPT, Claude, Grok, Mistral | Alarm (API anahtarı geçersiz/süresi dolmuş) |
| `ErrAuthFailed` | 403 | Gemini | Google API anahtarı geçersiz veya grounding kapalı |
| `ErrMockMode` | — | Tümü | Mock modu aktifken gerçek API çağrısı yapılmaz; geliştirme/demo amaçlı |
| `ErrEmptyResponse` | — | Claude, Mistral | Content blokları boş; Tutarsızlık skoruna işaretlenir |

**Yeniden Deneme Politikası:**
| Katman | Mekanizma | Max Deneme |
|:------:|-----------|:----------:|
| Motor çağrısı | Adapter içi kısa deneme (aynı HTTP isteği) | 3 |
| İş (worker) | Üstel geri çekilme + jitter (kuyruğa yeniden) | 3 |
| Kota aşımı | Erteleme (deneme sayılmaz, süresiz) | Süresiz |

---

## 5. Yeni Motor Ekleme Süreci

1. `engine/{name}/adapter.go` — Adapter implementasyonu (arayüz: `Name()`, `Tier()`, `Execute()`, `WithContext()`)
2. `engine/{name}/adapter_test.go` — Birim testleri (mock mod, parse, hata senaryoları)
3. `engine/registry.go` — Kayıt defterine ekleme (derleme zamanı)
4. `cmd/api/main.go`, `cmd/worker/main.go` — API ve worker başlangıcında registry'e kayıt
5. `internal/config/config.go` — API anahtarı env değişkeni ekleme
6. `docs/04-ai-framework/0402-prompt-taxonomy.md` — Prompt taxonomy'e motor ekleme (pilot)
7. Paket hakları (Entitlement) tanımı — Hangi paketlerin hangi motorlara erişebileceği
8. K1 maliyet profili girişi (iç maliyet takibi)

Bu 8 adım, Tip 2 karardır (mimari değişiklik gerektirmez, 0007 süreciyle yönetilir).

---

## 6. HT2 Planı

### 6.1 Google AI Mode

Google AI Mode (Kademe 3, directional), Gemini API üzerinden özel bir endpoint ile proxy'lenecektir. Gemini adapter'da `WithAIMode()` metodu eklenmesi, `aiModeAdapter` ile `aiOverviewAdapter`'a benzer bir pattern izlenmesi planlanmaktadır.

| Özellik | Plan |
|---------|------|
| **Adapter** | `aiModeAdapter` — Gemini adapter wrapper'ı |
| **Endpoint** | `generateContent?alt=sse` (SSE stream) |
| **Kademe** | 3 (directional) |
| **Fidelity** | `"Kademe 3 · google_ai_mode · gemini-3.5-pro (directional)"` |
| **Risk** | Maliyet/kararlılık değerlendirmesi HT1 verisiyle yapılır |

### 6.2 Claude/Grok/Copilot/Mistral Üretim Sertleştirme

| Alan | HT2 Hedefi |
|------|------------|
| **Rate limit handling** | Dinamik backoff (kalan kota takibi, adaptive retry) |
| **Token tüketim metrikleri** | Her motor için günlük token kullanımı, maliyet takibi |
| **Model güncelleme** | Model adının env/config'den alınması (kodda sabit değil) |
| **Multi-model desteği** | Her motor için alternatif model (sonar-pro → sonar-large, claude-sonnet → claude-opus) |
| **Le Chat yüzeyi** | Mistral Le Chat API (opsiyonel, maliyet değerlendirmesi sonrası) |

### 6.3 Mistral — Bölgesel Öncelik

Mistral adapter, AB pazarı açılımında stratejik öneme sahiptir. HT2'de:
- Mistral API'nin AB merkezli veri merkezi seçeneği değerlendirilecek
- GDPR-safe prompt filtresi (`safe_prompt: true`) zorunlu olacak
- Le Chat yüzeyi (web arama + citation) eklenebilir

---

## 7. Motor Bazlı Fidelity Etiket Formatı

Her adapter, `Execute()` sonucunda şu formatta bir fidelity etiketi döndürür:

```
"Kademe {1|2|3} · {engine_name} · {model_name} ({modifier})"

Örnekler:
"Kademe 1 · perplexity · sonar-pro (direct)"
"Kademe 2 · claude · claude-sonnet-4 (official_proxy)"
"Kademe 3 · google_ai_overview · gemini-3.5-pro (official_proxy/directional)"
"Kademe 2 · mistral · mistral-large-latest (mock)"  // Mock modu
```

Mock modunda `(mock)` ibaresi eklenir. Bu, geliştirme/demo ortamında üretilen skorların gerçek ölçüm olmadığını belirtir.

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 25.07.2026 | İlk yayın: adapter sözleşmesi, hata sınıfları, ekleme süreci |
| 1.1 | 27.07.2026 | Turkcell RFP kapsamında yeni motorlar eklendi: Google AI Overview (Tier 3), Google AI Mode (Tier 3), Mistral (Tier 2). Motor tablosu güncellendi. |
| 1.2 | 28.07.2026 | **HT1 motor genişletmesi:** 5 yeni adapter eklendi (Claude, Grok, Copilot, Mistral, Google AI Overview). Toplam 8 AI yüzeyi. Her adapter için detaylı özellik tabloları (API, model, timeout, alıntı mekanizması, hata yönetimi, mock modu). WithContext deseni, RawSaver entegrasyonu ve mock modu dokümante edildi. Hata sınıfları genişletildi (ErrAuthFailed 403, ErrMockMode, ErrEmptyResponse). Yeniden deneme politikası eklendi. Motor ekleme süreci 4'ten 8 adıma çıkarıldı. HT2 planı (Google AI Mode, sertleştirme, Mistral bölgesel) eklendi. Fidelity etiket formatı standardize edildi. |
