# 0204 · Otorite Skoru

| Alan | Değer |
|---|---|
| Doküman ID | 0204 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0104 (S3), 0202 |

---

## 1. Amaç

Bir markanın AI motorları nezdindeki otoritesini ölçer. Otorite, görünürlüğün nitel boyutudur.

## 2. Otorite Bileşenleri

| Bileşen | Açıklama | Ağırlık |
|---------|----------|:-------:|
| Kaynak kalitesi | Markanın alıntılandığı kaynakların otoritesi | 0.40 |
| Bağlam derinliği | Markanın yanıtlardaki bağlamsal önemi | 0.30 |
| Tutarlılık | Markanın farklı motorlardaki tutarlılığı | 0.30 |

## 3. Hesaplama

Otorite skoru, görünürlük skorundan bağımsız hesaplanır ve bileşik skorun bir girdisi değil, tamamlayıcı bir metriktir.

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 22.07.2026 | İlk yayın: otorite bileşenleri ve hesaplama. |
