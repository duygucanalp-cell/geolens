# 0409 · Görünürlük Skoru (Visibility Score)

| Alan | Değer |
|---|---|
| Doküman ID | 0409 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0404, 0407, 0410, 0411, 0309, 0310, 0204 |

---

## 1. Amaç

Bu doküman, GeoLens Görünürlük Skoru'nun (0-100) hesaplama metodolojisini tanımlar. Skor, dört bileşenin ağırlıklı toplamından oluşur.

---

## 2. Skor Bileşenleri

### 2.1 Görünürlük Skoru Bileşenleri

| # | Bileşen | Açıklama | Varsayılan Ağırlık | Kaynak |
|:-:|---------|----------|:------------------:|--------|
| 1 | **Varlık Payı** | Markanın yanıtlarda geçme oranı | %30 | 0407 |
| 2 | **Konum Ağırlığı** | Yanıt içinde öne çıkma sırası | %20 | 0407 |
| 3 | **Kaynak Payı** | Marka domainlerinin alıntılardaki oranı | %15 | 0405 |
| 4 | **Rakip Bağlamı** | Rakiplere göre normalize görünürlük | %15 | 0411 |
| 5 | **Appearance Rate** | Prompt setinde markanın görünme sıklığı | %10 | 0407 |
| 6 | **Sentiment / Algı** | AI yanıtlarındaki duygu durumu puanı | %5 | 0411 |
| 7 | **Competitive Visibility** | Rakiplere göre normalize edilmiş AI görünürlük performansı | %5 | 0411 |

### 2.2 Per-Platform Metrikler

Her motor için ayrı hesaplanan metrikler:

| Metrik | Açıklama | Hesaplama |
|--------|----------|:---------:|
| **Visibility Position** | Motor yanıtındaki marka sıralaması | 1-tabanlı pozisyon |
| **Citation Presence** | Motor alıntılarında marka domainlerinin varlığı | Boolean + oran |
| **Mention Frequency** | Motor yanıtlarında marka geçme sıklığı | /toplam yanıt |
| **Recommendation Rate** | Motorun markayı önerme/öne çıkarma oranı | /toplam öneri |
| **Prompt Coverage Score** | Markanın prompt setinde kaçında geçtiği | /toplam prompt |

---

## 3. Skor Formülü

```
Görünürlük Skoru = Σ(bileşen_değeri × bileşen_ağırlığı)
```

Her bileşen 0-100 aralığına normalize edilir:

| Bileşen | Normalizasyon |
|---------|:------------:|
| Varlık Payı | (geçme_sayısı / toplam_yanıt) × 100 |
| Konum Ağırlığı | max(0, 100 - (pozisyon - 1) × (100 / maks_pozisyon)) |
| Kaynak Payı | (marka_alıntı_sayısı / toplam_alıntı) × 100 |
| Rakip Bağlamı | (marka_skoru - rakip_ortalama) / rakip_standart_sapma × 50 + 50 |

---

## 4. Motor Kırılımı

Skor her motor için ayrı hesaplanır ve birleşik skor aşağıdaki gibi üretilir:

| Motor | Varsayılan Ağırlık | Gerekçe |
|:-----:|:------------------:|---------|
| ChatGPT | %35 | TR'de en yaygın AI motoru |
| Gemini | %30 | Google ekosistemi |
| Perplexity | %20 | Teknik kitle |
| Claude | %10 | (HT1) |
| Grok | %5 | (HT1) |

---

## 5. Skor Etiketleri

Her skor aşağıdaki etiketleri taşır:

| Etiket | Anlamı |
|--------|--------|
| Fidelite | Kademe (1/2/3) + motor adı + n |
| GA alt | Güven aralığı alt sınır |
| GA üst | Güven aralığı üst sınır |
| Tazelik | En son ölçüm zamanı |
| Panel versiyonu | Hangi panel ile ölçüldüğü |
| Motor kırılımı | Motor bazında alt skorlar |
| Sentiment etiketi | Olumlu / Nötr / Olumsuz |
| Hallüsinasyon bayrağı | Varsa marka hakkında yanlış bilgi tespit edildi |
| Conversation Replay ID | Yanıtın arşiv bağlantısı |

---

## 6. Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|:--:|-------|:------:|
| D-30 | Örnekleme büyüklüğü: n=3 [K]. Her motor için 3 paralel istek gönderilir. Pilot verisiyle kalibre edilir. | AVIP 0309 O-1 (TL 21.07.2026) |
| D-31 | Anlamlılık eşiği: GA hesaplama yöntemi (0309 §4). Her skor güven aralığıyla birlikte raporlanır. | AVIP 0309 O-3 (TL 21.07.2026) |
| D-32 | Ölçüm devralma (measurement inheritance): bir panelin prompt/motor yapılandırması alt panellere devralınabilir. | AVIP 0307 O-2 (TL 21.07.2026) |
| D-89 | Skor bileşen adları [K]: Varlık Payı (%35), Konum Ağırlığı (%25), Kaynak Payı (%20), Rakip Bağlamı (%20). Bu doküman §2 ile birebir uyumlu. | AVIP 0007 D-89 (kalibrasyon, 21.07.2026) |

---

## Kaynaklar

- 0404 Prompt Weighting — prompt ağırlıkları
- 0407 Entity Recognition — varlık payı
- 0410 Authority Score — otorite skoru
- 0411 Share of Voice — görünürlük payı
- 0309 Scoring Engine — hesaplama motoru
- 0204 PRD — FR-C4..FR-C7

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: 4 bileşenli skor modeli, formül, motor kırılımı, skor etiketleri. |
| 1.1 | 23.07.2026 | Devralınan AVIP Kararları eklendi: D-30 (örnekleme n=3), D-31 (anlamlılık), D-32 (devralma), D-89 (skor bileşen adları). |
| 1.2 | 27.07.2026 | Turkcell RFP kapsamında genişletme: 7 bileşenli skor modeline geçiş (Appearance Rate, Sentiment, Competitive Visibility eklendi). Per-platform metrikler (Visibility Position, Citation Presence, Mention Frequency, Recommendation Rate, Prompt Coverage Score) eklendi. Skor etiketlerine sentiment, hallüsinasyon ve conversation replay ID eklendi. |
