# 0419 · Rekabetçi Boşluk Analizi (Competitive Gap Analysis)

| Alan | Değer |
|---|---|
| Doküman ID | 0419 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 27 Temmuz 2026 |
| İlişkili | 0409, 0411, 0418, 0412, 0413, 0309, 0204, 0207, **docs/AI_Visibility_Generative_Search_Intelligence_Platform.md** |

---

## 1. Amaç

Bu doküman, bir markanın rakiplerine göre AI görünürlüğündeki **boşluklarını (gap)** sistematik olarak tespit eden, hesaplayan ve raporlayan **Competitive Gap Analysis** metodolojisini tanımlar.

Turkcell RFP'deki aşağıdaki gereksinimleri karşılar:

| RFP Gereksinimi | FR Karşılığı (0204) |
|:----------------|:-------------------:|
| Competitive Gap Analysis: Visibility/Citation/Content/Topic/Prompt Gap | FR-D11 |
| Rakip görünürlük analizi | FR-D3 |
| Rakip citation analizi | FR-D2, FR-D11 |
| Prompt bazlı karşılaştırma | FR-D11 |

---

## 2. GAVF Katmanı

Bu doküman GAVF'in **S3 (Skor Standardı)** ve **S4 (Aksiyon Standardı)** katmanları arasında köprü görevi görür:

```
S3 (Skor) → Competitive Gap (0419) → S4 (Aksiyon/Öneri)
                ↓
           0412 Fırsat Motoru
           0413 Öneri Motoru
           0418 Content GEO
```

---

## 3. Genel Çerçeve

### 3.1 Gap Tanımı

Bir gap (boşluk), marka ile rakip arasındaki belirli bir boyuttaki farktır:

```
gap = rakip_değer - marka_değer

Pozitif gap (+):  Rakip markadan önde (rekabet avantajı)
Negatif gap (-):  Rakip markanın gerisinde (iyileştirme alanı)
Sıfır gap (0):    Eşit durumda
```

### 3.2 Gap Ağırlıkları

Her gap türü, genel rekabet skoruna farklı ağırlıkla katkıda bulunur:

| Gap Türü | Varsayılan Ağırlık | Gerekçe |
|:--------:|:------------------:|---------|
| Visibility Gap | %30 | En genel rekabet göstergesi |
| Citation Gap | %25 | Kaynak gücünün göstergesi |
| Content Gap | %20 | İçerik stratejisi farkı |
| Topic Gap | %15 | Konu bazlı uzmanlık farkı |
| Prompt Gap | %10 | Prompt kapsama farkı |

### 3.3 Genel Competitive Score

```
competitive_score = Σ(gap_puanı_türü × gap_ağırlığı_türü)

Her gap_puanı 0-100 aralığına normalize edilir:
  gap_puanı = 50 + (gap_değeri / max_gap) × 50

Yorum:
  >70: Güçlü rekabet avantajı
  50-70: Rekabetçi konum
  30-50: İyileştirme alanı
  <30: Kritik iyileştirme alanı
```

---

## 4. Visibility Gap

### 4.1 Tanım

Marka ile rakip arasındaki **genel AI görünürlük farkı**. En temel rekabet göstergesidir.

### 4.2 Hesaplama

```
visibility_gap = marka_SOV - rakip_SOV

SOV kaynağı: 0411 Share of Voice
             Ham SOV veya Ağırlıklı SOV kullanılabilir
```

### 4.3 Motor Kırılımlı Visibility Gap

Her motor için ayrı visibility gap hesaplanır:

```
visibility_gap_motor = marka_SOV_motor - rakip_SOV_motor

Örnek:
  ChatGPT:   marka %35 - rakip %40 = -5 puan (rakip önde)
  Gemini:    marka %42 - rakip %30 = +12 puan (marka önde)
  Perplexity: marka %28 - rakip %35 = -7 puan (rakip önde)
```

### 4.4 Normalizasyon

```
visibility_gap_normalize = 50 + (visibility_gap / 100) × 50

  -100 puan gap → 0 (en kötü)
    0 puan gap  → 50 (eşit)
  +100 puan gap → 100 (en iyi)
```

### 4.5 Alert Eşikleri

| Gap Değeri | Anlamı | Aksiyon |
|:----------:|--------|---------|
| < -20 | Kritik — rakip çok önde | Acil strateji gözden geçirmesi |
| -20 – -5 | Orta — rakip önde | Stratejik iyileştirme planı |
| -5 – +5 | Nötr — eşit durum | Mevcut durumu koru |
| +5 – +20 | Orta — marka önde | Avantajı koru ve büyüt |
| > +20 | Güçlü — marka çok önde | Rekabet avantajını sürdür |

### 4.6 Visibility Gap Raporu

```json
{
  "brand": "Acme",
  "competitor": "BetaCorp",
  "period": "last_30_days",
  "overall_visibility_gap": -8.5,
  "normalized_score": 45.75,
  "by_engine": {
    "chatgpt": { "gap": -5.0, "normalized": 47.5 },
    "gemini": { "gap": 12.0, "normalized": 56.0 },
    "perplexity": { "gap": -7.0, "normalized": 46.5 }
  },
  "trend": {
    "30d_change": -2.1,
    "direction": "worsening"
  },
  "summary": "BetaCorp, ChatGPT ve Perplexity'de önde. Gemini'de avantajlıyız ancak son 30 günde fark kapanıyor."
}
```

---

## 5. Citation Gap

### 5.1 Tanım

Marka ile rakip arasındaki **AI alıntılarındaki (citation) fark**. Hangi markanın kaynaklarının AI motorları tarafından daha sık alıntılandığını gösterir.

### 5.2 Hesaplama

```
citation_gap = marka_citation_oranı - rakip_citation_oranı

marka_citation_oranı = marka_domainlerine_yapılan_alıntı_sayısı / toplam_alıntı
rakip_citation_oranı = rakip_domainlerine_yapılan_alıntı_sayısı / toplam_alıntı
```

### 5.3 Domain Kırılımlı Citation Gap

Marka ve rakip arasındaki alıntı farkı **domain türüne göre** de analiz edilebilir:

| Domain Türü | Açıklama | Örnek |
|:-----------:|----------|-------|
| **Resmî site** | Kendi domaini | acme.com |
| **Blog/editoryal** | Blog altyapısı | blog.acme.com |
| **Haber/basin** | Basın bültenleri | basın.acme.com |
| **Sosyal medya** | Sosyal platformlar | linkedin.com/company/acme |
| **Uygulama magazasi** | App store | apps.apple.com/acme |

```
citation_gap_domain = marka_domain_alıntı_oranı - rakip_domain_alıntı_oranı
```

### 5.4 Normalizasyon

```
citation_gap_normalize = 50 + (citation_gap / 100) × 50
```

### 5.5 Citation Gap Raporu

```json
{
  "brand": "Acme",
  "competitor": "BetaCorp",
  "period": "last_30_days",
  "citation_stats": {
    "brand_citations": 145,
    "competitor_citations": 203,
    "total_citations_in_set": 500
  },
  "citation_rates": {
    "brand": 29.0,
    "competitor": 40.6
  },
  "citation_gap": -11.6,
  "normalized_score": 44.2,
  "by_domain_type": {
    "resmi_site": { "brand": 35, "competitor": 28, "gap": 7.0 },
    "blog": { "brand": 40, "competitor": 55, "gap": -15.0 },
    "basin": { "brand": 25, "competitor": 20, "gap": 5.0 }
  },
  "recommendations": [
    "Blog icerik sayisi artirilmali (rakip 15 puan onde)",
    "Resmi site alintilamasi iyi durumda, korunmali"
  ]
}
```

---

## 6. Content Gap

### 6.1 Tanım

Marka ile rakip arasındaki **içerik kapsama farkı**. AI motorlarının hangi markanın içeriklerini hangi konularda daha çok kullandığını analiz eder.

### 6.2 Hesaplama

```
content_gap = |marka_kaynak_türleri| - |rakip_kaynak_türleri|
              + Σ(kaynak_türü_farkı) + Σ(otorite_farkı)

Bu 3 bileşenin toplamı:
1. Kaynak türü çeşitliliği farkı
2. Her türdeki alıntı sayısı farkı
3. Kaynak otorite puanı farkı (0410 Authority Score)
```

### 6.3 Kaynak Türü Matrisi

| Kaynak Türü | Marka | Rakip | Gap | Ağırlık |
|:-----------:|:----:|:-----:|:---:|:-------:|
| Blog/Makale | 40% | 55% | -15 | %40 |
| Ürün sayfası | 22% | 15% | +7 | %25 |
| FAQ/SSS | 14% | 18% | -4 | %20 |
| Haber/Basın | 10% | 8% | +2 | %10 |
| Kategori | 7% | 4% | +3 | %5 |

```
kaynak_türü_content_gap = Σ(tür_ağırlığı × tür_gap)
```

### 6.4 Otorite Farkı

0410 Authority Score üzerinden:

```
otorite_farkı = marka_otorite_skoru - rakip_otorite_skoru

Örnek:
  Marka otorite skoru: 72
  Rakip otorite skoru: 65
  otorite_farkı = +7
```

### 6.5 Content Gap Hesaplama Örneği

```
marka_kaynak_türleri = { blog: 40, urun: 22, faq: 14, haber: 10, kategori: 7 }
rakip_kaynak_türleri = { blog: 55, urun: 15, faq: 18, haber: 8, kategori: 4 }

1. Kaynak türü çeşitliliği: 5 - 5 = 0 (her iki taraf da 5 türde içerik üretiyor)
2. Ağırlıklı tür farkı: 
   blog: (-15 × 0.40) = -6.0
   urun: (+7 × 0.25) = +1.75
   faq: (-4 × 0.20) = -0.8
   haber: (+2 × 0.10) = +0.2
   kategori: (+3 × 0.05) = +0.15
   Toplam: -4.7
3. Otorite farkı: 72 - 65 = +7 (0-100 skalasında normalize: +7 × 0.15 = +1.05)

content_gap = 0 + (-4.7) + 1.05 = -3.65
content_gap_normalize = 50 + (-3.65 / 100) × 50 = 48.2
```

### 6.6 Content Gap Raporu

```json
{
  "brand": "Acme",
  "competitor": "BetaCorp",
  "content_gap": -3.65,
  "normalized_score": 48.2,
  "components": {
    "source_type_diversity": { "brand": 5, "competitor": 5, "gap": 0 },
    "weighted_type_gap": -4.7,
    "authority_gap": { "brand": 72, "competitor": 65, "gap": 1.05 }
  },
  "type_breakdown": [
    { "type": "blog", "brand_pct": 40, "competitor_pct": 55, "gap": -15, "impact": "high" },
    { "type": "faq", "brand_pct": 14, "competitor_pct": 18, "gap": -4, "impact": "medium" }
  ],
  "recommendations": [
    "Blog icerik uretimi artirilmali (en buyuk gap kaynagi)",
    "FAQ sayfasi genisletilmeli (rakip onde)"
  ]
}
```

---

## 7. Topic Gap

### 7.1 Tanım

Marka ile rakip arasındaki **konu/kategori bazlı görünürlük farkı**. Hangi konularda markanın rakipten geride veya önde olduğunu gösterir.

### 7.2 Hesaplama

```
topic_gap_t = marka_SOV_t - rakip_SOV_t

Her konu (topic) için ayrı SOV hesaplanır:
  SOV_t = marka_görünürlüğü_t / (marka_görünürlüğü_t + Σ(rakip_görünürlüğü_t)) × 100

topic_gap_overall = Σ(topic_gap_t × topic_ağırlığı_t)
```

### 7.3 Konu Kategorileri

Konular, prompt setindeki prompt türlerine ve 0408 Topic Classification çıktısına göre belirlenir:

| Konu Kategorisi | Varsayılan Ağırlık | Örnek Prompt |
|:---------------:|:------------------:|--------------|
| Ürün/hizmet bilgisi | %25 | "X ürünü hakkında bilgi" |
| Karşılaştırma | %20 | "X vs Y karşılaştırması" |
| Kurumsal bilgi | %15 | "X şirketi hakkında" |
| Sektörel trend | %15 | "Sektördeki son gelişmeler" |
| Kullanıcı deneyimi | %10 | "X kullanıcı yorumları" |
| Fiyat/paket | %10 | "X fiyatlandırması" |
| Teknik özellik | %5 | "X teknik detayları" |

### 7.4 Topic Gap Radar Grafiği

```
          Ürün
          80
         /  \
  Teknik 60   40 Karşılaştırma
        |  \ /  |
  Fiyat  40--60  Kullanıcı
        |  / \  |
  Sektör 60   40 Kurumsal
         \  /
          Kullanıcı
          
  Kesik çizgi: Marka
  Düz çizgi:   Rakip
```

### 7.5 Topic Gap Raporu

```json
{
  "brand": "Acme",
  "competitor": "BetaCorp",
  "topic_gaps": [
    { "topic": "ürün_bilgisi", "brand_sov": 45, "competitor_sov": 55, "gap": -10, "weight": 0.25 },
    { "topic": "karşılaştırma", "brand_sov": 60, "competitor_sov": 40, "gap": 20, "weight": 0.20 },
    { "topic": "kurumsal", "brand_sov": 30, "competitor_sov": 70, "gap": -40, "weight": 0.15 },
    { "topic": "sektörel_trend", "brand_sov": 50, "competitor_sov": 50, "gap": 0, "weight": 0.15 },
    { "topic": "kullanıcı", "brand_sov": 55, "competitor_sov": 45, "gap": 10, "weight": 0.10 },
    { "topic": "fiyat", "brand_sov": 35, "competitor_sov": 65, "gap": -30, "weight": 0.10 },
    { "topic": "teknik", "brand_sov": 70, "competitor_sov": 30, "gap": 40, "weight": 0.05 }
  ],
  "weighted_topic_gap": -3.5,
  "normalized_score": 48.25,
  "strong_topics": ["karşılaştırma", "teknik", "kullanıcı"],
  "weak_topics": ["kurumsal", "fiyat", "ürün_bilgisi"],
  "recommendations": [
    "Kurumsal bilgi içerikleri güçlendirilmeli (-40 puan gap)",
    "Fiyat/paket bilgisi şeffaflaştırılmalı (-30 puan gap)"
  ]
}
```

---

## 8. Prompt Gap

### 8.1 Tanım

Marka ile rakip arasındaki **prompt bazlı kapsama farkı**. Tanımlı prompt setinde markanın hangi sorularda geçip geçmediğini analiz eder.

### 8.2 Hesaplama

```
prompt_coverage_marka = markanın_geçtiği_prompt_sayısı / toplam_prompt_sayısı × 100
prompt_coverage_rakip = rakibin_geçtiği_prompt_sayısı / toplam_prompt_sayısı × 100

prompt_gap = prompt_coverage_marka - prompt_coverage_rakip
```

### 8.3 Prompt Türü Kırılımı

| Prompt Türü (0402) | Marka Coverage | Rakip Coverage | Gap |
|:------------------:|:--------------:|:--------------:|:---:|
| Varlık (presence) | %80 | %85 | -5 |
| Karşılaştırma (comparison) | %60 | %75 | -15 |
| Öneri (recommendation) | %45 | %55 | -10 |
| Kategori (category) | %70 | %60 | +10 |
| Problem (problem) | %50 | %65 | -15 |

### 8.4 Prompt Gap Raporu

```json
{
  "brand": "Acme",
  "competitor": "BetaCorp",
  "prompt_set_id": "01J...",
  "total_prompts": 100,
  "coverage": {
    "brand": 61.0,
    "competitor": 68.0
  },
  "prompt_gap": -7.0,
  "normalized_score": 46.5,
  "by_type": [
    { "type": "presence", "brand": 80, "competitor": 85, "gap": -5, "prompts_missed": ["X hakkinda ne biliyorsun?"] },
    { "type": "comparison", "brand": 60, "competitor": 75, "gap": -15, "prompts_missed": ["X vs Y karsilastirmasi", "X yerine hangisi?"] },
    { "type": "recommendation", "brand": 45, "competitor": 55, "gap": -10, "prompts_missed": ["En iyi X cozumu hangisi?"] },
    { "type": "category", "brand": 70, "competitor": 60, "gap": 10 },
    { "type": "problem", "brand": 50, "competitor": 65, "gap": -15, "prompts_missed": ["X sorunu nasil cozulur?"] }
  ],
  "recommendations": [
    "Karsilastirma promptlarinda coverage dusuk. Karsilastirma icerikleri artirilmali.",
    "Problem/sorun bazli promptlarda rakip onde. Cozum odakli icerik uretilmeli."
  ]
}
```

---

## 9. API Tasarımı

```
GET    /v1/competitive-gap/visibility/{brand_id}?competitor_id=X       — Visibility gap analizi
GET    /v1/competitive-gap/citation/{brand_id}?competitor_id=X          — Citation gap analizi
GET    /v1/competitive-gap/content/{brand_id}?competitor_id=X           — Content gap analizi
GET    /v1/competitive-gap/topic/{brand_id}?competitor_id=X             — Topic gap analizi
GET    /v1/competitive-gap/prompt/{brand_id}?competitor_id=X            — Prompt gap analizi
GET    /v1/competitive-gap/overview/{brand_id}?competitor_id=X          — Tum gap turleri tek ekranda
GET    /v1/competitive-gap/recommendations/{brand_id}?competitor_id=X   — Gap bazli oneri listesi
```

---

## 10. Dashboard Entegrasyonu

### 10.1 Competitive Overview Paneli

| Bileşen | Açıklama | Dashboard |
|:--------|----------|:---------:|
| **Gap özet kartı** | 5 gap türünün tek ekranda özeti (renk kodlu) | Executive |
| **Visibility Gap trend** | Zaman serisi visibility gap grafiği | Executive |
| **Topic Gap radarı** | Konu bazlı gap radar grafiği | Operasyonel |
| **Citation Gap domain** | Domain türüne göre citation gap | Operasyonel |
| **Prompt Gap detayı** | Prompt türüne göre coverage karşılaştırması | Operasyonel |
| **Öncelikli gap listesi** | En büyük negatif gaplerin sıralı listesi | Operasyonel |

---

## 11. Öneri Motoru Entegrasyonu (0413 ile bağlantı)

Competitive Gap Analysis çıktıları, 0413 Öneri Motoru'na girdi sağlar:

| Gap Türü | Öneri (0413) | Kanıt Derecesi |
|:--------:|:------------:|:--------------:|
| Visibility Gap | "Rakip X, Y motorunda z %20 onde. Z motoru icin strateji gozden gecirilmeli." | Deneysel |
| Citation Gap | "Rakip X, blog iceriklerinde z once. Blog uretimi artirilarak acik kapatilabilir." | Korelasyonel |
| Content Gap | "Rakip X'in otorite skoru daha yuksek. Kaynak kalitesi iyilestirilmeli." | Korelasyonel |
| Topic Gap | "Rakip X, kurumsal bilgi konusunda onde. Kurumsal icerikler guclendirilmeli." | Deneysel |
| Prompt Gap | "Rakip X'in karsilastirma prompt coverage'i daha yuksek. Karsilastirma icerigi eklenmeli." | Denenebilir |

---

## 12. Veri Modeli

### 12.1 Migration: `041_competitive_gaps.sql`

```sql
CREATE SCHEMA IF NOT EXISTS competitive;

CREATE TABLE competitive.gap_snapshots (
    gap_id             TEXT PRIMARY KEY,   -- ULID
    brand_id           TEXT NOT NULL REFERENCES config.brands(brand_id),
    competitor_id      TEXT NOT NULL REFERENCES config.brands(brand_id),
    period_start       DATE NOT NULL,
    period_end         DATE NOT NULL,
    visibility_gap     REAL,               -- -100 .. +100
    citation_gap       REAL,
    content_gap        REAL,
    topic_gap          REAL,
    prompt_gap         REAL,
    competitive_score  REAL,               -- 0-100 normalized
    breakdown          JSONB,              -- { visibility: {...}, citation: {...}, ... }
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id          TEXT NOT NULL,
    UNIQUE(brand_id, competitor_id, period_start, period_end)
);

ALTER TABLE competitive.gap_snapshots ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON competitive.gap_snapshots
    USING (tenant_id = current_setting('app.tenant_id')::text);

CREATE INDEX idx_gap_brand_period ON competitive.gap_snapshots(brand_id, period_start DESC);
CREATE INDEX idx_gap_competitor ON competitive.gap_snapshots(competitor_id);

CREATE TABLE competitive.gap_recommendations (
    recommendation_id  TEXT PRIMARY KEY,   -- ULID
    gap_id             TEXT NOT NULL REFERENCES competitive.gap_snapshots(gap_id),
    gap_type           TEXT NOT NULL,      -- visibility / citation / content / topic / prompt
    priority           TEXT NOT NULL,      -- critical / high / medium / low
    description        TEXT NOT NULL,
    impact             TEXT,
    kanit_derecesi     TEXT,               -- deneysel / korelasyonel / denenebilir (0413)
    related_fr         TEXT,               -- ilgili FR kodu
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gap_recommendations ON competitive.gap_recommendations(gap_id);
```

---

## 13. GeoLens İçin Çıkarımlar

1. **FR-D11 (Competitive Gap Analysis)** 5 gap türüyle Turkcell RFP gereksinimini karşılar. Her gap türü ayrı bir rekabet boyutunu analiz eder.
2. **Gap'ler birbirini tamamlar:** Örn. Visibility Gap yüksekse hangi alt gap'in (Topic/Citation/Content/Prompt) buna sebep olduğu analiz edilebilir.
3. **Competitive Score (0-100)**, yönetici dashboard'unda (FR-F8) tek bir metrik olarak gösterilebilir.
4. **Öneri motoru entegrasyonu:** Her gap tespiti, 0413 Recommendation Engine'e kanıt dereceli öneri olarak beslenir.
5. **Rakip bazlı analiz:** Her marka için sınırsız sayıda rakip tanımlanabilir. Her rakip çifti için ayrı gap analizi yapılır.
6. **Specification bağlantısı:** Competitive Gap metodolojisi, GAVF S3 (Skor Standardı) kapsamında specification reposuna eklenmelidir.

---

## 14. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | Rakip sayısı arttıkça (5+ rakip) gap analizi nasıl ölçeklenmeli? | ⏳ MVP'de birebir karşılaştırma. Çoklu rakip ortalaması HT1. |
| O-2 | Gap trend analizi için minimum kaç veri noktası gerekli? | ⏳ En az 2 ölçüm (2 hafta). Anlamlı trend için 4+ hafta önerilir. |
| O-3 | Topic Gap için konu kategorileri otomatik mi belirlenmeli yoksa manuel mi? | ⏳ MVP'de 0408 Topic Classification çıktısına dayalı otomatik. Manuel override HT1. |

---

## Kaynaklar

- **Turkcell AI Visibility Platform RFP:** `docs/AI_Visibility_Generative_Search_Intelligence_Platform.md`
- 0204 PRD — FR-D11 (competitive gap analysis)
- 0207 Feature Catalog — FR-D11 özellik tanımı
- 0409 Visibility Score — skor bileşenleri, competitive visibility
- 0411 Share of Voice — SOV türleri, competitive gap tanımı (§5)
- 0418 Content GEO — content gap analizi (§3)
- 0412 Opportunity Engine — fırsat tespiti, fırsat puanlaması
- 0413 Recommendation Engine — öneri üretimi, kanıt dereceleri
- 0309 Scoring Engine — hesaplama motoru, normalizasyon
- 0204 PRD — FR-D3 (rakip kıyası)

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 27.07.2026 | İlk yayın: Competitive Gap Analysis metodolojisi. 5 gap türü (Visibility/Citation/Content/Topic/Prompt), her biri için algoritma, normalizasyon, alert eşikleri. Örnek JSON raporlar, veri modeli (gap_snapshots, gap_recommendations), Dashboard ve Öneri Motoru entegrasyonu. Turkcell RFP gereksinimini karşılar (FR-D11). |
