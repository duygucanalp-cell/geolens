# ADR-005 · Eklenti Mimarisi (Plugin/Adapter Architecture)

| Alan | Değer |
|---|---|
| ADR ID | ADR-005 |
| Durum | Kabul |
| Tarih | 22.07.2026 |
| Karar veren | TL |
| İlişkili | 0505, 0308, 0305, 0206 |

---

## Bağlam

GeoLens, farklı AI motorlarına (ChatGPT, Gemini, Perplexity, Claude vb.) bağlanmak için genişletilebilir bir bağdaştırıcı mekanizmasına ihtiyaç duyar. Her motorun farklı API yapısı, hata sınıfları ve fidelite düzeyi vardır.

---

## Kararlar

| # | Karar |
|:-:|-------|
| 1 | **Adapter interface** — `EngineAdapter` arayüzü `measure` bağlamında tanımlanır |
| 2 | **Registry pattern** — Bağdaştırıcılar derleme zamanında kayıt defterine eklenir |
| 3 | **Dependency inversion** — Arayüz measure'da, uygulamalar engines'te (D4) |
| 4 | **Tier labeling** — Her bağdaştırıcı kademe etiketi bildirir (direct/official_proxy/directional) |
| 5 | **Cost class** — Her bağdaştırıcı maliyet sınıfı bildirir (K1 girdisi) |
| 6 | **New adapter = registry change** — Yeni bağdaştırıcı eklemek mimari değil, kayıt defteri değişikliğidir |

---

## Alternatifler

| Seçenek | Red nedeni |
|---------|------------|
| **Go plugin (hashicorp go-plugin)** | IPC ek yükü; V1'de gerekli değil; HT2'de değerlendirilebilir |
| **Microservice per engine** | Aşırı karmaşık; her motor için ayrı servis V1'de anlamsız |
| **Dynamic loading** | Güvenlik riski; kod inceleme olmadan yeni bağdaştırıcı yüklenmesi |

---

## Sonuçlar

- 0505 Plugin System — bağdaştırıcı arayüzü, kayıt defteri, motor ekleme süreci
- 0308 AI Connectors — bağdaştırıcı sözleşmesi, hata sınıfları, dayanıklılık
- Yeni motor ekleme 4 adımdır ve Tip 2 karardır
- Motor ekleme/kaldırma API yüzeyini veya veri şemasını etkilemez

---

## Değişiklik Geçmişi

| Tarih | Değişiklik |
|-------|------------|
| 22.07.2026 | İlk karar: Kabul |
