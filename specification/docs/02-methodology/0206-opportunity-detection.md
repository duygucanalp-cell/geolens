# 0206 · Fırsat Tespiti

| Alan | Değer |
|---|---|
| Doküman ID | 0206 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0105 (S4), 0202 |

---

## 1. Amaç

Skor bileşenlerindeki düşük performans alanlarını tespit ederek iyileştirme fırsatlarını belirler.

## 2. Fırsat Puanı

| Bileşen | Düşük Eşik | Fırsat Puanı |
|---------|:----------:|:------------:|
| Varlık | < 40 | Yüksek |
| Konum | < 30 | Yüksek |
| Kaynak | < 20 | Orta |
| Rakip | < 50 | Düşük |

## 3. Önceliklendirme

Fırsat Puanı × Bileşen Ağırlığı = Öncelik Skoru

En yüksek öncelik skoruna sahip alan ilk müdahale edilecek alandır.

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 22.07.2026 | İlk yayın: fırsat tespiti ve önceliklendirme. |
