# 0505 · Eklenti Sistemi (Plugin System)

| Alan | Değer |
|---|---|
| Doküman ID | 0505 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0501, 0308, 0206 |

---

## 1. Amaç

Bu doküman GeoLens'in eklenti/bağdaştırıcı sistemini tanımlar. Motor bağdaştırıcıları, kayıt defteri modeli ve genişletilebilirlik mekanizmalarını detaylandırır.

---

## 2. Bağdaştırıcı Arayüzü

```go
type EngineAdapter interface {
    Capabilities() EngineCapabilities
    Execute(ctx context.Context, req ProbeRequest) (*ProbeResult, error)
}

type EngineCapabilities struct {
    Market   []string  // desteklenen pazar/dil
    Tier     string    // direct | official_proxy | directional
    Citations bool     // alıntı desteği var mı
    Concurrency int    // önerilen eşzamanlılık
    CostClass string   // maliyet sınıfı
}
```

---

## 3. Kayıt Defteri Modeli

Bağdaştırıcılar derleme zamanında kayıt defterine eklenir:

```go
var registry = map[string]EngineAdapter{
    "chatgpt":     &ChatGPTAdapter{},
    "gemini":      &GeminiAdapter{},
    "perplexity":  &PerplexityAdapter{},
    "claude":      &ClaudeAdapter{},     // HT1 (pasif)
    "grok":        &GrokAdapter{},       // HT1 (pasif)
    "copilot":     &CopilotAdapter{},    // HT1 (pasif)
}
```

---

## 4. Motor Ekleme Süreci

| Adım | Açıklama |
|:----:|----------|
| 1 | Bağdaştırıcı implementasyonu (arayüz) |
| 2 | Kayıt defterine giriş (derleme zamanı) |
| 3 | Entitlement anahtarı tanımı |
| 4 | K1 maliyet profili girişi |

> Tip 2 karar — 0007 değişiklik süreciyle yönetilir.

---

## 5. Kademe Modeli

| Kademe | Etiket | Anlamı | Örnek |
|:------:|:------:|--------|-------|
| 1 | direct | Doğrudan API yanıtı | Perplexity |
| 2 | official_proxy | Resmî arama/grounding API | ChatGPT, Gemini |
| 3 | directional | Dolaylı sinyal çıkarımı | Copilot |

---

## Kaynaklar

- 0501 System Architecture — değişime dayanıklılık (P5)
- 0308 AI Connectors — bağdaştırıcı sözleşmesi
- 0206 Roadmap — HT1/H2 motor ekleme pencereleri
- 0305 Bounded Contexts — D4 bağımlılık kuralı
- archive/avip-v1/0308-ai-connectors.md

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: bağdaştırıcı arayüzü, kayıt defteri, motor ekleme süreci, kademe modeli. |
