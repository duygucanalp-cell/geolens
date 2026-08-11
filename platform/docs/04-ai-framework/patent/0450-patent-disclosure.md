# 0450 · Patent Disclosure — Cross-Engine AI Visibility Measurement

| Alan | Değer |
|---|---|
| Doküman ID | 0450 |
| Proje | GeoLens Platform |
| Versiyon | 0.1 (Draft) |
| Durum | Draft |
| Sahip | U2 AI Studio · Ar-Ge |
| Tarih | 11 Ağustos 2026 |
| İlişkili | 0420 İP-09, 0409, 0405, 0416, 0421 A4-2 |

> Bu disclosure, İP-09 (Patent Başvurusu Hazırlığı) kapsamında patent vekiline
> verilmek üzere hazırlanmış teknik dokümandır. Durum **Draft** — vekil ile
> görüşülmeden Approved yapılmaz.

---

## 1. Buluş Başlığı

**Cross-Engine AI Visibility Index: Multi-Engine, Normalized Measurement of Brand Presence in LLM Responses with Source Validation and Opportunity Scoring**

---

## 2. Teknik Alan

Bilgisayar tabanlı marka görünürlük ölçümü; özellikle yapay zeka (LLM) yanıtlarında
markaların varlığını, konumunu, kaynağını ve rakiplerine göre konumunu ölçen çok
motorlu (multi-engine) bir skorlama sistemi.

---

## 3. Mevcut Durumun Sorunları (Problem)

1. Mevcut SEO araçları web arama sonuçlarını ölçer; LLM yanıt trafiği ölçülemez.
2. Tek motor/tek yanıt bazlı değerlendirme motorlar arası farklılığı gizler.
3. Rakip karşılaştırması yapılmadan marka skoru bağlamsızdır.
4. Skor ağırlıkları keyfi seçilir; veriyle kalibre edilmez — açıklanabilirlik yok.
5. Hallüsinasyon (doğrulanamayan iddia) skoru kirletir; tespit edilmez.
6. Skor düşük olunca hangi somut aksiyonun alınacağı belirtilmez.

---

## 4. Buluşun Temel Bileşenleri

### 4.1 Çok Motorlu (Multi-Engine) Ölçüm Paneli

Bir marka için aynı prompt taksonomisi (0421 A1-1: 5×5×5×4×2 = 1000 kombinasyon)
üzerinden N adet AI motora (Perplexity, ChatGPT, Gemini, Claude, Grok, Mistral,
Copilot, Google AI Overview) paralel istek gönderilir. Her motor, kademe
(fidelity tier) etiketi üretir.

### 4.2 7 Bileşenli Visibility Index (VI)

```
VI = w₁·M + w₂·P + w₃·C + w₄·R + w₅·A + w₆·F + w₇·S
```

- **M** — Varlık Payı (Presence): markanın yanıtlarda geçme oranı
- **P** — Konum Ağırlığı (Position): yanıt içinde öne çıkma sırası
- **C** — Kaynak Payı (Citation): marka domainlerinin alıntılardaki oranı
- **R** — Rakip Bağlamı (Competitor): rakiplere göre normalize görünürlük
- **A** — Appearance Rate: prompt setinde görünme sıklığı
- **F** — Sentiment/Algı: AI yanıtlarındaki duygu durumu
- **S** — Competitive Visibility: rakiplere normalize AI görünürlük performansı

Her bileşen 0-100'e normalize edilir; VI 0-100, CI dinamik (±1..6) ile birlikte
raporlanır.

### 4.3 AHP / Veri Kalibrasyonlu Ağırlıklandırma

Ağırlıklar Saaty ölçeği (1-9) ile AHP pairwise matrisinden çözülür (CR<0.10);
skor toplamı deterministik yeniden hesap için `measure.calculation_runs` tablosuna
`component_values` + `algorithm_version` ile yazılır.

### 4.4 Cross-Source Hallüsinasyon Tespiti ve Kaynak Doğrulama

Aynı prompt'a verilen farklı motor cevapları çapraz kontrol edilir: çelişen
sayısal iddialar (T3) ve marka anılması tutarsızlıkları (T1) bayraklanır; citation
URL'leri gerçek zamanlı HEAD isteğiyle doğrulanır (T2). Şüphe eşiği aşılınca
LLM-as-Judge çevrimdışı fallback ile tetiklenir.

### 4.5 Opportunity Scoring (İP-07)

```
OpportunityScore = Impact(1-10) × Urgency(1-10) × Confidence(0-1)
```

Tespit edilen kayıplar (visibility loss, citation gap, contenido eksikliği,
otorite zafiyeti, yapısal veri hatası, rakip tehdidi) ML ile öğrenilen etki
katsayılarına göre sıralanır ve somut aksiyon önerisi üretir.

### 4.6 Feature Flag ile Geri Dönüşlü Geçiş

`SCORE_ALGORITHM_VERSION=1.0.0` (eski 4 bileşenli) / `2.0.0` (7 bileşenli) ile
algoritma değişimleri deterministik ve geri dönüşlüdür.

---

## 5. Yenilik Unsuru (Novelty)

Literatür ve mevcut ürünlerde (SEMrush, Ahrefs, ChatGPT analytics) tek başına
var olabilecek bileşenler bulunur; ancak **aşağıdaki kombinasyon bir bütün
olarak mevcut değildir**:

1. Birden çok AI motorunun yanıtını aynı prompt taksonomisinde paralel ölçmek,
2. Motor kademesi (tier) bazlı güvenilirlik etiketlemesi,
3. 7 bileşenli normalize, CI'li skor matematiksel modeli,
4. AHP ile veri kalibrasyonlu ağırlıkları,
5. Cross-source hallüsinasyon tespiti ile URL doğrulama kombinasyonu,
6. Skoru somut, önceliklendirilmiş aksiyon önerilerine (opportunity scoring) bağlama.

Hallüsinasyon tespiti tek başına literatürde olabilir; ancak **citation URL
gerçek zamanlı doğrulaması + birden çok motor çapraz kontrolü + opportunity
engine'ine doğrudan bağlantı** kombinasyonu patentlenebilir.

---

## 6. Mevcut Çözümlerden Fark (Differentiation)

| Mevcut Çözüm | Bizim Farkımız |
|---|---|
| Google SERP sıralama araçları | LLM yanıtı ölçümü, motor kademesi, cross-engine |
| Tek başına sentiment analizi | Sentiment, VI bileşeni olarak diğer 6 bileşenle ağırlıklı toplanır |
| Hallüsinasyon tespit modülleri | Cross-source + URL doğrulama + skor temizliği + opportunity bağlama |
| Basit skor ağırlığı ayarı | AHP kalibrasyon + duyarlılık raporu + sektör profilleri |
| Statik öneri motoru | ML etki öğrenimi + Impact×Urgency×Confidence sıralama |

---

## 7. En Yakın Prior Art

Ön tarama (İP-09 §6.1): USPTO/WIPO/Google Patents'te doğrudan birebir kombinasyon
bulunamadı. En yakın aday sınıflar:

- ABD patent sınıfı G06Q 30/02 (marketing) — marka ölçümü genel
- G06F 40/20 (NLP) — LLM analizi
- G06N 20/00 (ML) — model eğitimi

Detay: `0451-patent-prior-art.md`.

---

## 8. Teknik Uygulama Referansları

Bu disclosure'ın kod karşılıkları `platform/` içinde:

| Bileşen | Konum |
|---|---|
| VI model (7 bileşen) | `ml/geolens/vi/model.py` |
| AHP | `ml/geolens/vi/ahp.py` |
| Sektör profilleri | `ml/geolens/vi/profiles.py` |
| Hallüsinasyon + URL | `ml/geolens/features/hallucination.py` |
| Opportunity scoring | `ml/geolens/opportunity/` + `internal/optimize/opportunity.go` |
| Go entegrasyonu (feature flag) | `internal/measure/service.go` (`SCORE_ALGORITHM_VERSION`) |

---

## 9. Çıkarım: Patent Alabilirlik Değerlendirmesi

| Kriter | Değerlendirme |
|---|---|
| Yenilik (Novelty) | Yüksek — kombinasyon literatürde yok |
| Buluş basamağı (Inventive step) | Yüksek — beklenmedik kombinasyon, açıklanamaz ağırlıklar yerine AHP |
| Teknik karakter | Teknik — ölçüm, normalizasyon, doğrulama pipeline'ı |
| Sanayide uygulanabilirlik | Yüksek — ticari ürün (GeoLens platform) |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 0.1 | 11.08.2026 | İlk draft: İP-09 kapsamında disclosure hazırlandı. |