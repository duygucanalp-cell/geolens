# 0415 · AI Gözlemlenebilirlik (AI Observability)

| Alan | Değer |
|---|---|
| Doküman ID | 0415 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0401, 0409, 0414, 0311, 0204 |

---

## 1. Amaç

Bu doküman, GeoLens'in kendi AI görünürlük ölçüm sisteminin gözlemlenebilirliğini tanımlar. Yani: GeoLens'in kendi AI varlığını (markasını) nasıl izlediği, ölçtüğü ve raporladığı.

---

## 2. Kendi Kendini Ölçme

GeoLens, kendi marka görünürlüğünü izlemek için aynı GAVF metodolojisini kullanır:

| Ölçüm | Frekans | Amaç |
|-------|:-------:|------|
| Marka varlığı (GeoLens) | Haftalık | AI motorlarında varlık takibi |
| Sektör GAVF referansı | Haftalık | Standarda atıf yapılma oranı |
| Terim görünürlüğü | Aylık | "AI görünürlük" teriminin AI'lardaki geçişi |
| Rakip kıyası | Aylık | Profound, Otterly'ye göre durum |

---

## 3. Platform Sağlık Metrikleri (0311 ile bağlantılı)

| Metrik | Açıklama | Kaynak |
|--------|----------|--------|
| Ölçüm başarı oranı | Başarılı ölçüm / toplam ölçüm | 0307 |
| Motor gecikmesi | Motor bazlı yanıt süresi | 0308 |
| Skor determinizmi | Aynı girdiyle aynı skor mu? | 0309 |
| Panel güncelliği | Panel değişim sıklığı | 0302 |
| Hata oranı | Motor bazlı hata yüzdesi | 0308 |

---

## 4. Sektör Gözlem Raporu

Periyodik olarak üretilen, AI görünürlük sektörünün genel durumunu özetleyen rapordur:

| Bölüm | İçerik |
|-------|--------|
| Motor değişiklikleri | API, erişim, kademe değişiklikleri |
| Standart uyumu | GAVF güncelleme ihtiyaçları |
| TR pazarı | TR'deki AI görünürlük trendleri |
| Rakip hareketleri | Profound, Otterly, diğer oyuncular |

---

## 5. GAVF Geri Bildirim Döngüsü

```
Platform ölçüm verisi → Metodoloji doğrulama → GAVF güncelleme → Platform'a geri
      ↓                     ↓                        ↓
  Gözlem raporu       Kalibrasyon          Specification
                                                        reposu
```

> Bu döngü, GeoLens Platform ile GeoLens Specification arasındaki sürekli iyileştirme bağını kurar.

---

## Kaynaklar

- 0401 AI Visibility Standard — GAVF metodolojisi
- 0409 Visibility Score — skor modeli
- 0414 Trend Analysis — trend tespiti
- 0311 Observability — sistem gözlem metrikleri
- 0310 Security — güvenlik ve uyum

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: kendi kendini ölçme, platform sağlık metrikleri, sektör gözlem raporu, GAVF geri bildirim döngüsü. |
