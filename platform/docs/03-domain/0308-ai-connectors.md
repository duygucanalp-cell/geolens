# 0308 · AI Bağlayıcıları (AI Connectors)

| Alan | Değer |
|---|---|
| Doküman ID | 0308 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 25 Temmuz 2026 |
| İlişkili | ADR-005, 0505, 0305, 0206 |

---

## 1. Amaç

Bu doküman, GeoLens'in AI motor bağdaştırıcı (adapter) mimarisini tanımlar. Bağdaştırıcı sözleşmesi, hata sınıfları, dayanıklılık mekanizmaları ve yeni motor ekleme sürecini detaylandırır.

---

## 2. Bağdaştırıcı Sözleşmesi

```go
type EngineAdapter interface {
    Name() string
    Tier() engine.FidelityTier
    Execute(ctx context.Context, req ProbeRequest) (*ProbeResult, error)
}
```

### 2.1 Adapter Özellikleri

| Motor | Tier | Maliyet Sınıfı | API |
|-------|:----:|:--------------:|-----|
| Perplexity | Tier 1 (Direct) | Düşük | sonar-pro |
| ChatGPT | Tier 2 (Official Proxy) | Orta | responses API |
| Gemini | Tier 1 (Direct) | Düşük | generateContent |

### 2.2 ProbeRequest

| Alan | Tip | Açıklama |
|------|:---:|----------|
| Prompt | string | Sorgu metni |
| BrandName | string | Marka adı |
| WebsiteURL | string | Web sitesi (citation doğrulama) |
| Market | string | Pazar/dil kodu |
| SampleCount | int | n değeri (varsayılan 3) |

### 2.3 ProbeResult

| Alan | Tip | Açıklama |
|------|:---:|----------|
| Content | string | Ham yanıt metni |
| Citations | []Citation | Çıkarılan atıflar |
| Fidelity | FidelityLabel | Güvenilirlik etiketi |
| RawOutput | json.RawMessage | Debug amaçlı ham çıktı |

---

## 3. Hata Sınıfları

| Hata | HTTP Karşılığı | Aksiyon |
|------|:--------------:|---------|
| `ErrEngineTimeout` | 504 | Yeniden dene |
| `ErrRateLimited` | 429 | Backoff + bekle |
| `ErrInvalidResponse` | 502 | Sample atla |
| `ErrAuthFailed` | 401 | Alarm (API anahtarı geçersiz) |

---

## 4. Yeni Motor Ekleme Süreci

1. `engine/{name}/adapter.go` — adapter implementasyonu
2. `engine/{name}/adapter_test.go` — birim testleri
3. `cmd/api/main.go`, `cmd/worker/main.go`, `cmd/scheduler/main.go` — registry'e kayıt
4. `internal/config/config.go` — API anahtarı env ekleme

Bu 4 adım Tip 2 karardır (mimari değişiklik gerektirmez).

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 25.07.2026 | İlk yayın: adapter sözleşmesi, hata sınıfları, ekleme süreci |
