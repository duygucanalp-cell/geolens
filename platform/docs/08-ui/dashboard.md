# Pano (Dashboard)

| Alan | Değer |
|---|---|
| Doküman ID | 08-ui/dashboard |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Draft |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 08-ui/*, 0202, 0204, 0409, 0414 |

---

## 1. Amaç

Bu doküman GeoLens Platform ana pano (dashboard) tasarımını tanımlar. Pano, kullanıcının ürünle ilk karşılaştığı ana yüzeydir ve en kritik bilgileri tek ekranda sunar.

---

## 2. Pano Bileşenleri

```
┌──────────────────────────────────────────────────┐
│  Üst Çubuk: Çalışma Alanı Seçici │ Kullanıcı │  │
├──────────────────────────────────────────────────┤
│  Özet Kartı                                      │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐   │
│  │ Genel  │ │ Motor  │ │ Trend  │ │ Öneri  │   │
│  │ Skor   │ │ Kırılım│ │ (7gün) │ │ Sayısı │   │
│  └────────┘ └────────┘ └────────┘ └────────┘   │
├──────────────────────────────────────────────────┤
│  Ana İçerik                                      │
│  ┌────────────────────────┬──────────────────┐  │
│  │ Trend Grafiği          │ Motor Kırılımı   │  │
│  │ (Zaman Serisi)         │ (Halka Grafik)   │  │
│  └────────────────────────┴──────────────────┘  │
│  ┌────────────────────────┬──────────────────┐  │
│  │ Öneriler               │ Son Uyarılar     │  │
│  │ (Son 3)                │ (Son 5)          │  │
│  └────────────────────────┴──────────────────┘  │
└──────────────────────────────────────────────────┘
```

---

## 3. Özet Kartları

| Kart | Metrik | Kaynak |
|:----:|--------|--------|
| **Genel Skor** | Birleşik görünürlük skoru (0-100) | 0409 |
| **Motor Kırılımı** | Aktif motor sayısı ve en yüksek motor | 0409 |
| **Trend (7gün)** | Son hafta skor değişimi (+/-%) | 0414 |
| **Öneri Sayısı** | Açık öneri sayısı | 0413 |

---

## 4. Pano Aksiyonları

| Aksiyon | Kısayol | FR |
|---------|:-------:|:--:|
| Yeni ölçüm başlat | "+" butonu | FR-C1 |
| Rapor oluştur | "Rapor" butonu | FR-F4 |
| Marka ekle | "Marka Ekle" | FR-B1 |
| Öneriyi uygula | "Uygula" butonu | FR-E3 |
| Uyarıya git | Tıklanabilir uyarı | FR-F1 |

---

## 5. Pano Performans Hedefleri

| Metrik | Hedef |
|--------|:-----:|
| İlk yükleme süresi | <3 saniye |
| Etkileşim süresi | <1 saniye |
| Skor kartı görünür | <2 saniye |
| Trend grafiği render | <1 saniye |
| Pano veri tazeliği | <5 dakika |

---

## 6. Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|:--:|-------|:------:|
| D-59 | Performans eşikleri [K]: pano <5 sn (p50), API <1 sn, ölçüm <60 sn. Pilotta kalibre edilecek. | AVIP 0204 O-1 (TL 21.07.2026) |
| D-26 | Grafik kütüphaneleri: Recharts + TanStack Table. GA bant görselleştirme ve kırılım tabloları için. | AVIP 0304 O-3 (TL 21.07.2026) |
| D-61 | Okuma-yalnız API: skor, trend, alıntı, rapor meta uçları. /public/v1 öneki, API anahtarı ile. | AVIP 0204 O-3 (TL 21.07.2026) |

---

## Kaynaklar

- 08-ui/design-system — renk, tipografi, bileşenler
- 08-ui/navigation — pano navigasyonu
- 0202 User Journeys — pano etkileşimleri
- 0204 PRD — FR-C1, FR-D1, FR-D4, FR-F3
- 0409 Visibility Score — skor modeli
- 0414 Trend Analysis — trend verisi

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: pano düzeni, özet kartları, aksiyonlar, performans hedefleri. |
| 1.1 | 23.07.2026 | Devralınan AVIP Kararları eklendi: D-59 (perf eşikleri), D-26 (Recharts), D-61 (okuma API). |
