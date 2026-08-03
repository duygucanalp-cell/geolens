# 0411 · Görünürlük Payı (Share of Voice)

| Alan | Değer |
|---|---|
| Doküman ID | 0411 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0409, 0410, 0419, 0309, 0204 |

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

## 4. SOV Türleri (Genişletilmiş)

| Tür | Açıklama | Kullanım |
|:---:|----------|----------|
| **Ham SOV** | Varlık payı üzerinden | Temel pazar payı analizi |
| **Ağırlıklı SOV** | Tüm skor bileşenleri üzerinden | Derin rekabet analizi |
| **Motor SOV** | Her motor için ayrı | Motor bazında güç analizi |
| **Trend SOV** | Zaman serisi olarak | Pazar payı değişimi |
| **Recommendation Rate** | Markanın önerilme/öne çıkarılma oranı | AI'nın kullanıcıya yönlendirme gücü |
| **Prompt Coverage Score** | Markanın tanımlı prompt setinde geçme oranı | Kapsam/kapsanmama analizi |
| **Sentiment SOV** | Duygu durumuna göre kırılımlı SOV (olumlu/nötr/olumsuz) | Algı yönetimi ve itibar takibi |
| **Citation SOV** | Alıntı bazlı görünürlük payı | Kaynak gücü analizi |

## 5. Competitive Gap Analysis

SOV verisi üzerinden rakip-marka farkı analizi beş boyutta yapılır. Detaylı metodoloji, algoritma, normalizasyon ve alert eşikleri için **0419 Competitive Gap Analysis** dokümanına bakınız:

| Gap Türü | Açıklama | Detaylı Doküman |
|:--------:|----------|:---------------:|
| **Visibility Gap** | Genel görünürlük farkı | 0419 §4 — Visibility Gap |
| **Citation Gap** | Alıntı farkı | 0419 §5 — Citation Gap |
| **Content Gap** | İçerik kapsama farkı | 0419 §6 — Content Gap |
| **Topic Gap** | Konu/kategori bazlı fark | 0419 §7 — Topic Gap |
| **Prompt Gap** | Prompt bazlı fark | 0419 §8 — Prompt Gap |

## 6. SOV Yorumlama

| SOV Aralığı | Anlamı |
|:-----------:|--------|
| >%50 | Pazar lideri |
| %25-50 | Güçlü oyuncu |
| %10-25 | Orta seviye |
| %5-10 | Gelişmekte |
| <%5 | Düşük görünürlük |

> **Dürüst iddia (İ4):** SOV, pazar payı veya satış başarısı değil, AI görünürlük payıdır. Hiçbir yüzeyde "pazar lideri" gibi doğrudan satış iddiası olarak kullanılmaz.

## 7. Sentiment / Algı Skoru

| Skor | Anlamı | Tespit Yöntemi |
|:----:|--------|:--------------:|
| ≥0.7 | Olumlu — marka olumlu bağlamda geçiyor | Doğal dil işleme (NLP) duygu analizi |
| 0.4-0.7 | Nötr — marka bilgilendirme amaçlı geçiyor | Bağlam analizi |
| ≤0.4 | Olumsuz — marka olumsuz/eleştirel bağlamda geçiyor | Negatif ifade tespiti |

---

## Kaynaklar

- 0409 Visibility Score — marka görünürlüğü
- 0410 Authority Score — otorite tamamlayıcısı
- 0309 Scoring Engine — rakip bağlamı hesaplaması
- 0204 PRD — FR-D3 (rakip kıyası)
- 0419 Competitive Gap Analysis — gap metodolojisi, algoritma ve normalizasyon

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: SOV tanımı, türleri, yorumlama aralıkları. |
| 1.1 | 27.07.2026 | Turkcell RFP kapsamında genişletme: Recommendation Rate, Prompt Coverage Score, Sentiment SOV, Citation SOV eklendi. Competitive Gap Analysis bölümü (Visibility/Citation/Content/Topic/Prompt Gap) eklendi. Sentiment/algı skoru tanımı ve tespit yöntemi eklendi. |
| 1.2 | 27.07.2026 | Competitive Gap Analysis bölümü referansa dönüştürüldü: detaylı metodoloji 0419'a taşındı. İlişkili ve Kaynaklar güncellendi. |
