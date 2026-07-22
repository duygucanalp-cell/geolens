# 0411 · Görünürlük Payı (Share of Voice)

| Alan | Değer |
|---|---|
| Doküman ID | 0411 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0409, 0410, 0309, 0204 |

---

## 1. Amaç

Bu doküman, bir markanın rakiplerine göre AI görünürlük payını (Share of Voice / SOV) tanımlar. SOV, görünürlük skorunun rakip bağlamı bileşeninin temel girdisidir.

---

## 2. SOV Tanımı

SOV, bir prompt setinde markanın toplam görünürlüğünün, izlenen tüm markaların (kendi + rakipler) toplam görünürlüğüne oranıdır:

```
SOV = marka_görünürlüğü / (marka_görünürlüğü + Σ(rakip_görünürlüğü)) × 100
```

---

## 3. SOV Türleri

| Tür | Açıklama | Kullanım |
|:---:|----------|----------|
| **Ham SOV** | Varlık payı üzerinden | Temel pazar payı analizi |
| **Ağırlıklı SOV** | Tüm skor bileşenleri üzerinden | Derin rekabet analizi |
| **Motor SOV** | Her motor için ayrı | Motor bazında güç analizi |
| **Trend SOV** | Zaman serisi olarak | Pazar payı değişimi |

---

## 4. SOV Yorumlama

| SOV Aralığı | Anlamı |
|:-----------:|--------|
| >%50 | Pazar lideri |
| %25-50 | Güçlü oyuncu |
| %10-25 | Orta seviye |
| %5-10 | Gelişmekte |
| <%5 | Düşük görünürlük |

> **Dürüst iddia (İ4):** SOV, pazar payı veya satış başarısı değil, AI görünürlük payıdır. Hiçbir yüzeyde "pazar lideri" gibi doğrudan satış iddiası olarak kullanılmaz.

---

## Kaynaklar

- 0409 Visibility Score — marka görünürlüğü
- 0410 Authority Score — otorite tamamlayıcısı
- 0309 Scoring Engine — rakip bağlamı hesaplaması
- 0204 PRD — FR-D3 (rakip kıyası)

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: SOV tanımı, türleri, yorumlama aralıkları. |
