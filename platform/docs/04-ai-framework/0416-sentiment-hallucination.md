# 0416 · Duygu Analizi ve Hallüsinasyon Tespiti (Sentiment & Hallucination Detection)

| Alan | Değer |
|---|---|
| Doküman ID | 0416 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · AI |
| Tarih | 27 Temmuz 2026 |
| İlişkili | 0406, 0407, 0408, 0409, 0411, 0308, 0309, 0312, 0204, 0207, 0415, **docs/AI_Visibility_Generative_Search_Intelligence_Platform.md** |

---

## 1. Amaç

Bu doküman, AI motor yanıtlarında **duygu/algı analizi (sentiment)** ve **hallüsinasyon tespiti (hallucination detection)** yöntemlerini tanımlar.

Turkcell RFP'deki aşağıdaki gereksinimleri karşılar:

| RFP Gereksinimi | FR Karşılığı (0204) |
|:----------------|:-------------------:|
| Sentiment / Brand Perception Score | FR-D7 |
| Doğruluk ve itibar kontrolü — hallüsinasyon tespiti | FR-D8 |
| Negatif algı (negative sentiment) içeren yanıtların tespiti | FR-D7, FR-D8 |
| Yapay zeka sistemlerinin marka hakkında ürettiği yanıtların doğruluk payının kontrolü | FR-D8 |

---

## 2. GAVF Katmanı

Bu doküman GAVF'in **S2 (Yanıt Standardı)** katmanına eklenir:

| Katman | Adı | Dokümanlar |
|:------:|-----|:-----------:|
| S2 | Yanıt Standardı | 0405 (Alıntı), 0406 (Ayrıştırıcı), 0407 (Varlık), 0408 (Konu), **0416 (Duygu + Hallüsinasyon)** |

Skor bileşeni olarak 0409 (Görünürlük Skoru) içindeki **Sentiment / Algı** bileşenine (%5 ağırlık) girdi sağlar.

---

## 3. Duygu Analizi (Sentiment Analysis)

### 3.1 Amaç

AI motor yanıtlarında markanın hangi duygu durumuyla (olumlu/nötr/olumsuz) anıldığını tespit eder. Bu, marka itibarının AI kanalındaki yansımasını ölçmek için kullanılır.

### 3.2 Duygu Sınıflandırması

| Sınıf | Sentiment Skoru | Anlamı | Örnek Yanıt |
|:-----:|:---------------:|--------|-------------|
| **Olumlu (positive)** | ≥ 0.7 | Marka övülüyor, öneriliyor, olumlu bağlamda geçiyor | "Acme sektörünün en yenilikçi şirketidir." |
| **Nötr (neutral)** | 0.4 – 0.7 | Marka bilgilendirme amaçlı, tarafsız bağlamda geçiyor | "Acme 2005 yılında kurulmuştur." |
| **Olumsuz (negative)** | < 0.4 | Marka eleştiriliyor, olumsuz bağlamda geçiyor | "Acme'nin müşteri hizmetleri zayıftır." |

### 3.3 Tespit Yöntemi

MVP'de **kural tabanlı + sözlük bazlı** yöntem kullanılır. Makine öğrenimi tabanlı sınıflandırma HT1+ adayıdır.

| Yöntem | Açıklama | MVP |
|:------|----------|:---:|
| **Sözlük eşlemesi** | Olumlu/olumsuz kelime sözlüğü ile eşleşme | ✅ |
| **Bağlam analizi** | Olumsuz ek/olumsuz ifade + marka yakınlığı | ✅ |
| **Karşılaştırma tespiti** | Markanın rakip karşısında nasıl konumlandırıldığı | ✅ |
| **Varlık-yakınlık analizi** | Duygu ifadesinin markaya olan uzaklığı (cümle bazlı) | ✅ |
| **N-gram modeli** | İstatistiksel dil modeli ile duygu tespiti | 🔴 (HT1) |
| **Transformer sınıflandırıcı** | Fine-tuned BERT/LLM tabanlı sınıflandırma | 🔴 (Ufuk) |

> **Sözlük kapsamı:** MVP pilotu için minimum 500 Türkçe + 500 İngilizce duygu kelimesi hedeflenir. Sözlük e-ticaret, finans, teknoloji ve sağlık sektörlerini kapsayacak şekilde hazırlanır.

### 3.4 Duygu Sözlüğü

Her dil için ayrı sözlük. MVP'de TR ve EN dilleri desteklenir.

| Kategori | TR Örnekler | EN Örnekler | Ağırlık |
|:--------:|:-----------|:-----------|:-------:|
| **Güçlü olumlu** | mükemmel, lider, en iyi, harika | excellent, leader, best, outstanding | +1.0 |
| **Olumlu** | iyi, başarılı, güvenilir, kaliteli | good, successful, reliable, quality | +0.5 |
| **Nötr** | bilinen, mevcut, bulunan | known, available, found | 0.0 |
| **Olumsuz** | kötü, yetersiz, zayıf, sorunlu | bad, insufficient, weak, problematic | -0.5 |
| **Güçlü olumsuz** | berbat, skandal, tehlikeli | terrible, scandalous, dangerous | -1.0 |
| **Değilleme** | değil, yok, asla | not, never, no | -0.8 (çarpan) |

#### 3.4.1 Sözlük Yönetimi

| Kural | Açıklama |
|:-----:|----------|
| **Sektöre özgü** | Her sektörün kendi duygu sözlüğü vardır (finans, e-ticaret, sağlık vb.) |
| **Versiyonlu** | Sözlük güncellemeleri versiyonlanır ve changelog ile izlenir |
| **Genişletilebilir** | Kullanıcı kendi sözlük terimlerini ekleyebilir (HT1) |
| **Dil bazlı** | Her dil bağımsız sözlüğe sahiptir |

### 3.5 Sentiment Skoru Hesaplama

```
Her marka mention'ı için:

1. Markanın geçtiği cümle / cümleler tespit edilir (0407 Entity Recognition)
2. Cümle içindeki duygu kelimeleri taranır (sözlük eşlemesi)
3. Değilleme varsa duygu yönü ters çevrilir
4. Kelime ağırlıkları toplanır ve mention sayısına normalize edilir

sentiment_skoru = Σ(mention_skoru_i) / max(toplam_mention, 1)

**Edge case:** Marka hiçbir yanıtta geçmezse (toplam_mention = 0), sentiment_skoru = 0.5 (nötr) olarak kabul edilir.
```

#### 3.5.1 Görünürlük Skoruna Dönüşüm

Sentiment skoru (0.0-1.0), 0409 Görünürlük Skoru'nun 7 bileşeninden biri olan **Sentiment / Algı** bileşenine (%5 ağırlık) girdi sağlar. Dönüşüm:

```
visibility_sentiment_bileşeni = sentiment_skoru × 100
```

Örnek: Sentiment skoru 0.72 ise visibility sentiment bileşeni = 72 (0-100 skalasında).

#### 3.5.2 Mention Skoru Detayı

```
mention_skoru = Σ(kelime_ağırlığı × değilleme_çarpanı) / kelime_sayısı

Bir cümle için:
  "Acme mükemmel bir hizmet sunuyor."
  → mükemmel = +1.0, hizmet = nötr → mention_skoru = 1.0

  "Acme'nin hizmet kalitesi iyi değil."
  → iyi = +0.5, değil = değilleme → mention_skoru = (0.5 × -0.8) / 1 = -0.4
```

### 3.6 Marka Algı Raporu

| Bileşen | Açıklama |
|---------|----------|
| **Genel sentiment** | Tüm yanıtlardaki ortalama duygu skoru |
| **Motor kırılımı** | Her motor için ayrı sentiment skoru |
| **Zaman serisi** | Sentiment değişimi trendi |
| **Bağlam dağılımı** | Olumlu/nötr/olumsuz dağılımı (%) |
| **Rakip karşılaştırması** | Rakiplerle sentiment karşılaştırması |

### 3.7 Sentiment Alerting

0415 ve 0312 ile entegrasyon:

| Uyarı Türü | Eşik | Aksiyon |
|:----------:|:----:|---------|
| **Negatif sentiment tespiti** | Skor ≤ 0.4 | Anlık uyarı (e-posta/Slack) |
| **Sentiment düşüşü** | ≥ 0.2 puan düşüş | Uyarı + conversation replay referansı |
| **Rakip sentiment artışı** | Rakip ≥ 0.2 puan artış | Rekabet uyarısı |

---

## 4. Hallüsinasyon Tespiti (Hallucination Detection)

### 4.1 Amaç

AI motorlarının marka hakkında ürettiği **yanlış, gerçek dışı veya yanıltıcı bilgileri** otomatik olarak tespit eder. Bu, marka itibarı ve doğru bilgi yönetimi için kritiktir.

### 4.2 Hallüsinasyon Türleri

| Tür | Açıklama | Örnek | Şiddet |
|:---:|----------|-------|:------:|
| **T1 · Yanlış bilgi** | Marka hakkında doğru olmayan iddia | "Acme 1990'da kuruldu" (gerçek: 2005) | Kritik |
| **T2 · Yanlış ilişkilendirme** | Markanın olmadığı bir bağlamda geçmesi | "Acme sağlık sektöründe faaliyet gösteriyor" (gerçek: teknoloji) | Yüksek |
| **T3 · Eski bilgi** | Güncel olmayan veri | "Acme'nin CEO'su Ali Yılmaz" (gerçek: değişti) | Orta |
| **T4 · Uydurma alıntı** | Var olmayan kaynak gösterme | "Acme'nin web sitesinde belirtildiği gibi..." (kaynak yok) | Yüksek |
| **T5 · Rakip karıştırma** | Rakip marka bilgisini markaya atfetme | "Acme'nin ürünü X, ABC'nin pazar payını aldı" | Orta |

### 4.3 Tespit Yöntemi

| Yöntem | Açıklama | MVP | Hangi Tür |
|:-------|----------|:---:|:---------:|
| **Marka profili doğrulama** | Bilinen marka bilgileriyle karşılaştırma | ✅ | T1, T2 |
| **Tarih/sayısal doğrulama** | Sayısal verilerin doğruluk kontrolü | ✅ | T1, T3 |
| **Kaynak doğrulama** | Alıntı URL'lerinin varlık ve erişilebilirlik kontrolü | ✅ | T4 |
| **Rakip-marka matrisi** | Doğru rakip bilgisi eşlemesi | ✅ | T5 |
| **Tutarlılık kontrolü** | Aynı prompt'un farklı tekrarları arası tutarlılık | ✅ | T1, T2 |
| **LLM tabanlı doğrulama** | İkinci bir AI ile yanıt doğrulaması | 🔴 (HT1) | Tümü |
| **Knowledge Graph** | Yapılandırılmış bilgi grafiği ile eşleme | 🔴 (HT2) | Tümü |

### 4.4 Marka Profili

Her marka için doğrulanmış bilgilerin tutulduğu yapı:

| Alan | Tip | Zorunlu | Örnek |
|------|:---:|:-------:|-------|
| kuruluş_yılı | int | ✅ | 2005 |
| sektör | string[] | ✅ | ["teknoloji", "telekom"] |
| merkez | string | — | "İstanbul, Türkiye" |
| ceo | string | — | "Ayşe Demir" |
| çalışan_sayısı | string | — | "5.000+" |
| ürünler | string[] | ✅ | ["SuperOnline", "Turkcell TV"] |
| web_sitesi | string | ✅ | "https://www.turkcell.com.tr" |
| son_güncelleme | timestamp | ✅ | 2026-07-27 |

> Marka profili bilgileri kullanıcı tarafından girilir veya güvenilir kaynaklardan (resmî site, Wikipedia) otomatik çekilir (HT1+).

### 4.5 Hallüsinasyon Puanı

Her tespit için bir hallüsinasyon puanı hesaplanır:

| Şiddet | Puan | Aksiyon |
|:------:|:----:|---------|
| Kritik | 0.8 – 1.0 | Anlık uyarı + yönetici bildirimi |
| Yüksek | 0.5 – 0.8 | Uyarı + Conversation Replay'e bağlantı |
| Orta | 0.2 – 0.5 | Haftalık özette bildirim |
| Düşük | 0.0 – 0.2 | Log + trend verisi |

```
hallüsinasyon_puanı = doğruluk_ihtimali × etki_ağırlığı

doğruluk_ihtimali: AI ifadesinin yanlış olma olasılığı (0-1)
etki_ağırlığı:     Yanlış bilginin markaya potansiyel etkisi (0-1)
```

### 4.6 Doğrulama Süreci

```
AI Yanıtı
    ↓
1. Entity Recognition (0407) — marka mention'ları çıkarılır
    ↓
2. Marka profili ile karşılaştırma
   - Her iddia için profil bilgisiyle eşleşme kontrolü
   - Eşleşme varsa ✅ | Eşleşme yoksa ⚠️
    ↓
3. Kaynak doğrulama
   - Alıntı URL'leri HTTP HEAD ile kontrol edilir (erişilebilir mi?)
   - URL içeriği marka iddiasını destekliyor mu?
    ↓
4. Tutarlılık kontrolü
   - Aynı prompt'un N=3 örneğinde aynı bilgi mi geçiyor?
   - Farklı motorlarda tutarlı mı?
    ↓
5. Skorlama
   - Hallüsinasyon puanı hesaplanır
   - Eşik aşılırsa uyarı üretilir
```

### 4.7 Hallüsinasyon Çıktısı

```json
{
  "hallucination_id": "01J...",
  "replay_id": "01J...",
  "engine": "chatgpt",
  "brand": "Acme",
  "type": "T1",
  "severity": "kritik",
  "ai_claim": "Acme 1990 yılında kurulmuştur.",
  "actual_fact": "Acme 2005 yılında kurulmuştur.",
  "confidence": 0.92,
  "score": 0.85,
  "source": "marka_profil_doğrulama",
  "created_at": "2026-07-27T12:00:00Z"
}
```

---

## 5. Veri Modeli

> **Şema notu:** `analysis` şeması, 0302 Domain Model'deki **BC3 (Measure)** bağlamına yeni bir alt modül olarak eklenir. Migration numaralari: `039_analysis_schemas.sql` (her iki tablo tek migration'da).

### 5.1 Migration: `039_analysis_schemas.sql`

```sql
CREATE SCHEMA IF NOT EXISTS analysis;

CREATE TABLE analysis.sentiment_scores (
    sentiment_id     TEXT PRIMARY KEY,  -- ULID
    raw_response_id  TEXT NOT NULL REFERENCES measure.raw_responses(response_id),
    brand_id         TEXT NOT NULL REFERENCES config.brands(brand_id),
    engine_name      TEXT NOT NULL,
    overall_score    REAL NOT NULL,     -- 0.0 - 1.0
    positive_rate    REAL NOT NULL,     -- 0.0 - 1.0
    neutral_rate     REAL NOT NULL,
    negative_rate    REAL NOT NULL,
    total_mentions   INT NOT NULL,
    breakdown        JSONB,            -- { "positive": [...kelimeler], "negative": [...], "neutral": [...] }
    sentiment_version TEXT NOT NULL,    -- sözlük versiyonu
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id        TEXT NOT NULL
);

ALTER TABLE analysis.sentiment_scores ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON analysis.sentiment_scores
    USING (tenant_id = current_setting('app.tenant_id')::text);

CREATE INDEX idx_sentiment_response ON analysis.sentiment_scores(raw_response_id);
CREATE INDEX idx_sentiment_brand ON analysis.sentiment_scores(brand_id, created_at DESC);
CREATE INDEX idx_sentiment_engine ON analysis.sentiment_scores(engine_name);
```

### 5.2 Tablo: `analysis.hallucination_flags` (Migration: `039_analysis_schemas.sql`)

```sql
CREATE TABLE analysis.hallucination_flags (
    hallucination_id  TEXT PRIMARY KEY,   -- ULID
    replay_id         TEXT NOT NULL REFERENCES replay.conversation_snapshots(replay_id),
    raw_response_id   TEXT NOT NULL REFERENCES measure.raw_responses(response_id),
    brand_id          TEXT NOT NULL REFERENCES config.brands(brand_id),
    engine_name       TEXT NOT NULL,
    hallucination_type TEXT NOT NULL,     -- T1, T2, T3, T4, T5
    severity          TEXT NOT NULL,      -- kritik / yüksek / orta / düşük
    ai_claim          TEXT NOT NULL,      -- AI'ın ürettiği iddia
    actual_fact       TEXT,               -- Doğru bilgi (varsa)
    confidence        REAL NOT NULL,      -- 0.0 - 1.0
    score             REAL NOT NULL,      -- 0.0 - 1.0 (severity × confidence)
    detection_method  TEXT NOT NULL,      -- kullanılan tespit yöntemi
    is_verified       BOOLEAN DEFAULT FALSE,  -- manuel doğrulama
    verified_by       TEXT,               -- doğrulayan kullanıcı
    status            TEXT DEFAULT 'open',    -- open / verified / false_positive
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tenant_id         TEXT NOT NULL
);

ALTER TABLE analysis.hallucination_flags ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON analysis.hallucination_flags
    USING (tenant_id = current_setting('app.tenant_id')::text);

CREATE INDEX idx_hallucination_response ON analysis.hallucination_flags(raw_response_id);
CREATE INDEX idx_hallucination_brand ON analysis.hallucination_flags(brand_id, created_at DESC);
CREATE INDEX idx_hallucination_severity ON analysis.hallucination_flags(severity);
CREATE INDEX idx_hallucination_status ON analysis.hallucination_flags(status);
```

---

## 6. API Tasarımı

### 6.1 Sentiment API

```
GET    /v1/sentiment/responses/{response_id}    — Tek yanıt sentiment detayı
GET    /v1/sentiment/brands/{brand_id}          — Marka sentiment özeti (son N gün)
GET    /v1/sentiment/brands/{brand_id}/trend    — Sentiment trend verisi
GET    /v1/sentiment/compare                    — Markalar arası sentiment karşılaştırması
```

**GET /v1/sentiment/brands/{brand_id} yanıtı:**

```json
{
  "brand_id": "01J...",
  "brand_name": "Acme",
  "period": "last_30_days",
  "overall_sentiment": 0.72,
  "distribution": {
    "positive": 0.45,
    "neutral": 0.40,
    "negative": 0.15
  },
  "by_engine": {
    "chatgpt": 0.75,
    "gemini": 0.68,
    "perplexity": 0.71
  },
  "trend": "stable",
  "last_change": "-0.03 (last 7 days)"
}
```

### 6.2 Hallüsinasyon API

```
GET    /v1/hallucinations                       — Hallüsinasyon listesi (filtre: brand, engine, severity, status)
GET    /v1/hallucinations/{id}                  — Tekil hallüsinasyon detayı
POST   /v1/hallucinations/{id}/verify           — Manuel doğrulama (verify / false_positive)
GET    /v1/hallucinations/stats                 — Hallüsinasyon istatistikleri
GET    /v1/hallucinations/brands/{brand_id}     — Marka hallüsinasyon özeti
```

**GET /v1/hallucinations yanıtı:**

```json
{
  "total": 12,
  "by_severity": {
    "kritik": 2,
    "yüksek": 3,
    "orta": 5,
    "düşük": 2
  },
  "by_type": {
    "T1": 4,
    "T2": 3,
    "T3": 3,
    "T4": 1,
    "T5": 1
  },
  "items": [
    {
      "hallucination_id": "01J...",
      "brand": "Acme",
      "type": "T1",
      "severity": "kritik",
      "ai_claim": "Acme 1990'da kuruldu",
      "score": 0.85,
      "created_at": "2026-07-27T12:00:00Z",
      "status": "open"
    }
  ]
}
```

---

## 7. Alerting Entegrasyonu

0415 ve 0312 ile birlikte aşağıdaki uyarı senaryoları desteklenir:

| Senaryo | Tetikleyici | Kanal | Öncelik |
|:--------|-------------|:-----:|:-------:|
| **Kritik hallüsinasyon** | T1/T2 tipi, score ≥ 0.8 | E-posta + Slack + SMS | 🚨 Acil |
| **Yüksek hallüsinasyon** | Score ≥ 0.5 | E-posta + Slack | ⚠️ Yüksek |
| **Negatif sentiment** | overall_score ≤ 0.4 | E-posta + Slack | ⚠️ Yüksek |
| **Sentiment düşüş trendi** | Ardışık 2 ölçüm düşüş | Haftalık özet | ℹ️ Bilgi |
| **Rakip sentiment farkı** | Rakip sentiment > marka +0.2 | Slack | 🔍 İzleme |

> **Kalibrasyon notu:** Alert eşik değerleri pilot verisiyle kalibre edilir. Yukarıdaki eşikler başlangıç değerleridir ve [K] işaretli metrikler gibi pilot sonrası revize edilebilir.

---

## 8. Dashboard Entegrasyonu

### 8.1 Sentiment Paneli

Executive Dashboard (FR-F8) ve Operasyonel Dashboard (FR-F9) içinde:

| Bileşen | Açıklama | Dashboard |
|:--------|----------|:---------:|
| **Sentiment skor kartı** | Genel sentiment skoru + renk kodu (yeşil/sarı/kırmızı) | Executive |
| **Sentiment dağılımı** | Olumlu/nötr/olumsuz pasta grafiği | Executive |
| **Sentiment trendi** | Zaman serisi sentiment grafiği | Operasyonel |
| **Motor sentiment** | Motor bazlı sentiment karşılaştırması | Operasyonel |
| **Rakip sentiment** | Rakiplerle sentiment karşılaştırması | Operasyonel |

### 8.2 Hallüsinasyon Paneli

| Bileşen | Açıklama | Dashboard |
|:--------|----------|:---------:|
| **Hallüsinasyon sayacı** | Açık hallüsinasyon sayısı (kritik/yüksek/orta/düşük) | Executive |
| **Hallüsinasyon listesi** | Filtrelenebilir, sıralanabilir liste | Operasyonel |
| **Tür dağılımı** | T1-T5 kırılım grafiği | Operasyonel |
| **Zaman serisi** | Hallüsinasyon sayısı trendi | Operasyonel |
| **Doğrulama arayüzü** | Manuel doğrulama (verify / false_positive) butonları | Operasyonel |

---

## 9. GeoLens İçin Çıkarımlar

1. **FR-D7 ve FR-D8**, Turkcell RFP'nin sentiment ve hallüsinasyon gereksinimlerini karşılar. Bu özellikler kurumsal müşteriler için marka itibarı yönetiminin AI kanalındaki karşılığıdır.
2. **Sentiment skoru** doğrudan 0409 Görünürlük Skoru'nun 7 bileşeninden biridir (%5 ağırlık). Görünürlük skorunun nicel boyutuna nitel bir boyut ekler.
3. **Hallüsinasyon tespiti**, Conversation Replay (0312) ile sıkı entegrasyon gerektirir: her hallüsinasyon kaydı bir replay_id ile ilişkilendirilir.
4. **Marka profili cold-start:** Hallüsinasyon tespitinin çalışması için marka profili bilgilerinin doldurulması gerekir. MVP onboarding akışına marka profili oluşturma adımı eklenmelidir. Profil boşken hallüsinasyon tespiti pasif kalır.
5. **Doğrulama döngüsü:** Hallüsinasyon tespiti tam otomatik değildir. Kritik/yüksek bulgular manuel doğrulama gerektirir. Kullanıcı "verify" veya "false_positive" olarak işaretleyebilir. "false_positive" işaretleri FR-F1 anlamlı uyarı metriğini (M11 yanlis alarm orani) besler.
5. **Dil desteği:** Sentiment sözlüğü TR + EN dillerinde başlar. Her yeni dil için ayrı sözlük oluşturulması gerekir.
6. **Specification bağlantısı:** Sentiment analizi metodolojisi, GAVF Yanıt Standardı (S2) kapsamında specification reposuna eklenmiştir: `specification/docs/01-standard/0110-sentiment-hallucination-standard.md`.

---

## 10. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | Sentiment sözlüğü genişletme mekanizması — kullanıcı kendi terimlerini ekleyebilmeli mi? | ⏳ HT1 kararı. MVP'de statik sözlük yeterli. |
| O-2 | Hallüsinasyon doğrulamasında ikinci AI (LLM-as-judge) kullanımı | ⏳ HT1+ değerlendirmesi. MVP'de kural tabanlı yeterli. |
| O-3 | Marka profili verisi hangi kaynaklardan otomatik doldurulmalı? | ⏳ MVP'de kullanıcı girişi. Wikipedia/DBpedia entegrasyonu HT1. |
| O-4 | Sentiment analizi için fine-tuned model gerekecek mi? | ⏳ Sözlük tabanlı yöntem MVP için yeterli. N-gram model HT1. |

---

## Kaynaklar

- **Turkcell AI Visibility Platform RFP:** `docs/AI_Visibility_Generative_Search_Intelligence_Platform.md`
- 0204 PRD — FR-D7 (sentiment), FR-D8 (hallüsinasyon)
- 0207 Feature Catalog — FR-D7, FR-D8 özellik tanımları
- 0406 Answer Parser — normalizasyon ve ayrıştırma
- 0407 Entity Recognition — marka/rakip varlık tespiti
- 0408 Topic Classification — bağlam sınıflandırma
- 0409 Visibility Score — 7 bileşenli skor, sentiment %5 ağırlık
- 0411 Share of Voice — SOV türleri, sentiment SOV
- 0308 AI Connectors — ProbeResult, motor bağdaştırıcıları
- 0309 Scoring Engine — hesaplama, sentriment skoru (8.1)
- 0312 Conversation Replay — sentiment/hallüsinasyon → replay_id bağı
- 0415 AI Observability — alerting entegrasyonu
- 0601 Data Model — analysis schema

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 27.07.2026 | İlk yayın: Duygu analizi (sentiment) ve hallüsinasyon tespiti metodolojisi. 5 duygu sınıfı, 5 hallüsinasyon türü, sözlük bazlı tespit, marka profili doğrulama, veri modeli (sentiment_scores, hallucination_flags), API tasarımı, alerting ve dashboard entegrasyonu. Turkcell RFP gereksinimlerini karşılar (FR-D7, FR-D8). |
| 1.1 | 10.08.2026 | Specification bağlantısı kapatıldı: metodoloji spec 0110'a eklendi (0110-sentiment-hallucination-standard.md). |
