# 0502 · API Referansı

| Alan | Değer |
|---|---|
| Doküman ID | 0502 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0503, 0104 |

---

## 1. Amaç

GAVF uyumlu bir uygulamanın sağlaması gereken API uçlarını tanımlar.

## 2. Zorunlu Uçlar

| Uç | Metot | Açıklama |
|----|:-----:|----------|
| `/v1/measure` | POST | Ölçüm başlatma |
| `/v1/score` | GET | Skor sorgulama |
| `/v1/citations` | GET | Alıntı sorgulama |
| `/v1/compliance` | GET | Uyumluluk beyanı sorgulama |

## 3. İstek/Yanıt Formatı

Detaylı format 0503'te tanımlanmıştır.

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 22.07.2026 | İlk yayın: zorunlu API uçları. |
