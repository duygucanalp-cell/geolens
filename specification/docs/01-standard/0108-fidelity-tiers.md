# 0108 · Fidelite Kademeleri (GAVF S1)

| Alan | Değer |
|---|---|
| Doküman ID | 0108 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0101 (G3), adr/0003, platform/docs/0401 |

---

## 1. Amaç

Fidelite, bir skorun hangi güven seviyesinde üretildiğini gösteren etikettir. GAVF'ın en ayırt edici özelliklerinden biridir. Her skor mutlaka bir fidelite etiketi taşımalıdır (G3).

---

## 2. Kademe Tanımları

| Kademe | Adı | Anlamı | Etiket |
|:------:|-----|--------|--------|
| **1** | Direct | AI motoru doğrudan, API şartlarına uygun çağrılır. Yanıt ve alıntılar resmi API yanıtıdır. | `direct` |
| **2** | Official Proxy | AI motoru resmi arama/grounding API'si üzerinden çağrılır. Yanıt grounding verisine dayanır. | `official_proxy` |
| **3** | Directional | AI motoruna dolaylı yöntemlerle erişilir (trafik yönlendirme, web arayüzü). Sınırlı güven. | `directional` |

---

## 3. Kademe Kuralları

- **Kademe 1** en yüksek güveni temsil eder
- **Kademe 3** skorları yalnızca yönsel bilgi olarak kullanılmalı, mutlak değer olarak raporlanmamalıdır
- Bir skor aynı anda yalnızca bir kademe etiketi taşıyabilir
- Kademe etiketi olmayan skor hiçbir yüzeyde yayınlanamaz

---

## 4. Kademe-Motor Eşlemesi (Örnek)

| Motor | Kademe | Gerekçe |
|-------|:------:|---------|
| Perplexity (Sonar) | 1 | Doğrudan API, alıntı listesi + URI |
| ChatGPT (Responses) | 2 | Web araması + alıntı, resmi Responses API |
| Gemini (Grounding) | 2 | Google Search grounding, resmi API |
| Claude (API) | 2 | Web arama desteği, resmi API |
| Grok (API) | 2 | Web arama desteği, resmi API |
| Copilot | 3 | Dolaylı erişim, sınırlı API |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 22.07.2026 | İlk yayın: 3 kademeli fidelite modeli, kurallar, motor eşlemesi. |
