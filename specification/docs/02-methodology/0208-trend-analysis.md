# 0208 · Trend Analizi

| Alan | Değer |
|---|---|
| Doküman ID | 0208 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0105 (S4), 0203 |

---

## 1. Amaç

Skorların zaman içindeki değişimini analiz ederek trend yönünü ve hızını belirler.

## 2. Trend Metrikleri

| Metrik | Açıklama | Pencere |
|--------|----------|:-------:|
| Kısa vadeli | Son 7 günlük değişim | 7 gün |
| Orta vadeli | Son 30 günlük değişim | 30 gün |
| Uzun vadeli | Son 90 günlük değişim | 90 gün |

## 3. Trend Sınıflandırması

| Değişim | Etiket |
|:-------:|:------:|
| > +%10 | Yükseliş |
| +%3 ile +%10 | Hafif yükseliş |
| -%3 ile +%3 | Yatay |
| -%10 ile -%3 | Hafif düşüş |
| < -%10 | Düşüş |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 22.07.2026 | İlk yayın: trend metrikleri ve sınıflandırma. |
