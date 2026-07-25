# 0104 · Skor Standardı (GAVF S3)

| Alan | Değer |
|---|---|
| Doküman ID | 0104 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0101 (S3), 0202, 0203, 0204, 0205 |

---

## 1. Amaç

Görünürlük skorunun hesaplanmasını ve bileşenlerini tanımlar. GAVF S3 kapsamındadır.

## 2. Skor Bileşenleri

| # | Bileşen | Açıklama | Ağırlık |
|:-:|---------|----------|:-------:|
| 1 | **Varlık (Presence)** | Markanın AI yanıtında geçme sıklığı | 0.35 |
| 2 | **Konum (Position)** | Markanın yanıt içindeki sırası ve bağlamı | 0.25 |
| 3 | **Kaynak (Citations)** | Markanın alıntı kaynağı olarak görünme sıklığı | 0.25 |
| 4 | **Rakip (Competitive)** | Rakiplere göre göreceli konum | 0.15 |

## 3. Bileşik Skor Hesaplama

```
Bileşik Skor = (Varlık × 0.35) + (Konum × 0.25) + (Kaynak × 0.25) + (Rakip × 0.15)
```

Tüm bileşenler 0-100 skalasındadır. Bileşik skor da 0-100 arasındadır.

## 4. Güven Aralığı

Her skor bir güven aralığıyla birlikte raporlanmalıdır. Varsayılan: %95 güven aralığı.

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 22.07.2026 | İlk yayın: S3 skor standardı, 4 bileşen ve ağırlıklar. |
