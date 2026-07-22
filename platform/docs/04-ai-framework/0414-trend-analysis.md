# 0414 · Trend Analizi (Trend Analysis)

| Alan | Değer |
|---|---|
| Doküman ID | 0414 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0409, 0411, 0415, 0309, 0204 |

---

## 1. Amaç

Bu doküman, görünürlük skorlarının zaman serisi analizini tanımlar. Trend analizi, skor değişimlerini görselleştirir, anlamlı kırılmaları tespit eder ve fırsat/öneri motorlarına girdi sağlar.

---

## 2. Trend Türleri

| Tür | Pencere | Açıklama |
|:---:|:-------:|----------|
| **Kısa dönem** | 7 gün | Haftalık değişim |
| **Orta dönem** | 30 gün | Aylık eğilim |
| **Uzun dönem** | 90 gün | Çeyreklik trend |
| **Tüm tarihçe** | İlk ölçümden itibaren | Kümülatif görünüm |

---

## 3. Trend Hesaplama

| Metot | Açıklama | MVP |
|-------|----------|:---:|
| Basit hareketli ortalama | N günlük ortalama | ✅ |
| Doğrusal regresyon | Eğim hesaplama | ✅ |
| Anlamlı kırılma tespiti | GA ayrışması (0309 §6) | ✅ |
| Mevsimsellik | (HT2) | 🔴 |
| Tahmin | (Ufuk) | 🔴 |

---

## 4. Panel Versiyon Sınırları

Panel değişiminde trend grafiğinde görünür işaret gösterilir:

```
Skor
  ^
  |  \     /‾‾‾\
  |   \   /     \
  |    \_/       \
  |  Panel v1 │ Panel v2
  +──────────────────────→ Zaman
  Versiyon sınırı
  (dikey kesik çizgi + araç ipucu)
```

> Seriler dikişsiz birleştirilmez. Eski panel verisi okunur kalır.

---

## 5. Trend Çıktıları

| Çıktı | Açıklama | Kullanım |
|-------|----------|----------|
| Eğim (slope) | Trend yönü ve hızı | Fırsat tespiti |
| Kırılma noktaları | Anlamlı değişim anları | Uyarı kaynağı |
| Dönem karşılaştırması | Seçili iki dönemin farkı | Rapor girdisi |
| Tahmin aralığı | Olasılıksal gelecek projeksiyonu | (Ufuk) |

---

## Kaynaklar

- 0409 Visibility Score — skor girdisi
- 0411 Share of Voice — SOV trendi
- 0415 AI Observability — gözlem metrikleri
- 0309 Scoring Engine — GA, anlamlılık kuralları
- 0302 Domain Model — panel versiyonlama

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: trend türleri, hesaplama metotları, panel sınırı, çıktılar. |
