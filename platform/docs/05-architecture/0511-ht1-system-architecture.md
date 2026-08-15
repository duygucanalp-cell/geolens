# 0511 · HT1 Sistem Mimarisi (Faz 3 Açılışı)

| Alan | Değer |
|---|---|
| Doküman ID | 0511 (Faz 3 Açılışı — 0301 referansı) |
| Proje | GeoLens Platform |
| Versiyon | 1.2 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 04 Ağustos 2026 |
| İlişkili | 0501, 0502, 0503, 0506, 0205, 0206, 0301, 0302, 0304, 0305, 0307, 0308, 0309, 0416, 0417, 0418, 0419, ADR-014 |

---

## 1. Amaç

Bu doküman, HT1 (Hızlı Takip 1) kapsamında kod seviyesine çıkan tüm bileşenlerin sistem mimarisini tanımlar. Faz 3'ün açılış dokümanıdır. 0501 (Sistem Mimarisi) ve 0502 (Servis Mimarisi) üzerine inşa edilir; HT1'de eklenen ~40 yeni iç paket, 5 yeni motor bağdaştırıcısı, 3 yeni worker profili ve 25+ yeni API ucu için mimari kararları, bağımlılıkları ve veri akışlarını sabitler.

> **Faz 3 kapsamı:** Bu doküman, 0206'da tanımlanan HT1 penceresinin tamamlanmasının ardından gelen Faz 3'ün ilk çıktısıdır. Sıradaki Faz 3 dokümanları: 0302 (Genişletilmiş Domain Modeli), 0305 (Genişletilmiş Bağlam Haritası), 0308 (Genişletilmiş AI Bağlayıcıları).

---

## 2. Tasarım İlkeleri (HT1 Ekleri)

0501 §2'deki 5 tasarım ilkesine (P1–P5) ek olarak HT1'de şu ilkeler geçerlidir:

| # | İlke | Açıklama |
|:-:|------|----------|
| **P6** | Analiz birikimi | Sentiment, hallucination, competitive gap gibi analiz bileşenleri aynı ham yanıttan beslenir; ayrı motor çağrısı yapılmaz |
| **P7** | GEO ölçümden bağımsız | Technical/content GEO analizleri ölçüm pipeline'ından bağımsız çalışır; kendi worker profillerinde tetiklenir |
| **P8** | SEO verisi AI verisinden ayrı | Google Search Console ve GA4 verileri ayrı worker senkronizasyonuyla çekilir; AI ölçüm verisiyle korelasyon API katmanında yapılır |
| **P9** | Arşiv yalnız-ekle | Response archive ve conversation replay verileri değiştirilemez; yalnızca S3'e yazılır, asla güncellenmez |
| **P10** | Public API read-only | Dışa açık API (FR-F6) skor/trend/rapor meta verisini salt-okunur sunar; hiçbir yazma işlemi içermez |

---

## 3. Genişletilmiş Bağlam Haritası

HT1, 0502 §2'deki 6 bounded context'i (BC1–BC6) genişletir ve 4 yeni bağlam ekler:

### 3.1 Mevcut Bağlamların Genişletmesi

| BC # | Bağlam | HT1 Eklemeleri |
|:----:|--------|----------------|
| **BC2** | Config | Site denetimi (FR-B4), LLM bot izleme (FR-B6), schema korelasyonu (FR-B7), SEO bağlantı yönetimi (FR-B8), SSO yapılandırması (FR-A4), audit trail (FR-H2) |
| **BC3** | Measure | Sentiment analizi (FR-D7), hallucination tespiti (FR-D8), per-platform metrikler (FR-D9), competitive visibility (FR-D10), conversation replay (FR-D12), response archive (FR-D13) |
| **BC4** | Insight | Competitive gap analizi (FR-D11), content gap (FR-E5), GEO içerik önerileri (FR-E6), technical GEO (FR-E7), öneri-etki takibi (FR-E4) |

### 3.2 Yeni Bağlamlar (HT1)

| BC # | Bağlam | Sorumluluk | Ana Paketler |
|:----:|--------|------------|--------------|
| **BC7** | Archive | Response archive yönetimi, S3 versiyonlu saklama, toplu dışa aktarım, retention policy | `dev.geolens.archive` |
| **BC8** | Replay | Conversation replay capture, side-by-side karşılaştırma, conversation diff | `dev.geolens.replay` |
| **BC9** | SEO | Search Console + GA4 OAuth2 akışı, periyodik veri senkronizasyonu, SEO veri depolama | `dev.geolens.seo` |
| **BC10** | Audit | Denetim izi kaydı, export, sorgulama, zincir doğrulama | `dev.geolens.audit` (genişletme) |

### 3.3 Alt Modüller (Mevcut Bağlam İçi)

| Ana BC | Alt Modül | Paket | Sorumluluk |
|:------:|-----------|-------|------------|
| BC3 | Sentiment | `dev.geolens.sentiment` | TF-IDF + sözlük tabanlı duygu analizi, transformer model hazırlığı |
| BC3 | Hallucination | `dev.geolens.sentiment` (alt) | Doğruluk kontrolü, tutarsızlık skoru, kaynak çapraz doğrulama |
| BC3 | Competitive | `dev.geolens.competitive` | Visibility/Citation/Content/Topic/Prompt gap analizi (0419) |
| BC4 | ContentGEO | `dev.geolens.contentgeo` | Content gap tespiti, topic cluster önerileri, entity geliştirme (0418) |
| BC4 | TechnicalGEO | `dev.geolens.technicalgeo` | LLM bot izleme, schema.org analizi, entity optimizasyonu (0417) |
| BC2 | SSO | `dev.geolens.sso` | SAML ACS, SP metadata, IdP config yönetimi |
| BC9 | SC Sync | `dev.geolens.seo` (alt) | Google Search Console API veri çekme, işleme, depolama |
| BC9 | GA4 Sync | `dev.geolens.seo` (alt) | Google Analytics Data API veri çekme, işleme, depolama |

---

## 4. Bileşen Mimarisi

### 4.1 Motor Katmanı Genişletme

HT1'de MVP'deki 3 çekirdek motora (ChatGPT, Gemini, Perplexity) ek olarak 5 yeni motor bağdaştırıcısı eklenmiştir.

| Motor | Paket | Kademe | Tip | Bağımlılık |
|-------|-------|:------:|-----|------------|
| Claude | `dev.geolens.engine.claude.ClaudeAdapter` | 2 (official_proxy) | Anthropic Messages API + web arama | API anahtarı |
| Grok | `dev.geolens.engine.grok.GrokAdapter` | 2 (official_proxy) | xAI API + web arama | API anahtarı |
| Copilot | `dev.geolens.engine.copilot.CopilotAdapter` | 3 (directional) | Web tabanlı gözlem (Bing search) | Proxy erişim |
| Mistral | `dev.geolens.engine.mistral.MistralAdapter` | 2 (official_proxy) | Mistral AI API + Le Chat | API anahtarı, GDPR-safe prompt |
| Google AI Overview | `dev.geolens.engine.gemini.GeminiAdapter` (AI Mode/Overview varyantları) | 3 (directional) | Gemini proxy + AI Overview özel prompt | Gemini adapter üzerinden |

```java
// Tüm bağdaştırıcılar engine.Adapter arayüzünü uygular (dev.geolens.engine):
public interface Adapter {
    String name();
    Tier tier();
    RawResponse execute(String prompt) throws EngineException;
}

// Kademeler (dev.geolens.engine.Tier)
public enum Tier {
    DIRECT(1),          // Doğrudan API yanıtı
    OFFICIAL_PROXY(2),  // Resmî arama/grounding API
    DIRECTIONAL(3);     // Dolaylı sinyal çıkarımı
}
```

**Kayıt defteri:** Tüm bağdaştırıcılar `dev.geolens.engine.Registry` yapısına Spring bean olarak (`AppBeans`) kaydedilir. `get(name)` ve `list()` metodlarıyla erişilir.

### 4.2 Duygu Analizi Pipeline (FR-D7)

| Katman | Bileşen | Sorumluluk |
|:------:|---------|------------|
| 1. Girdi | Ham AI yanıtları | Measure pipeline'ından gelen marka-etiketlenmiş yanıt metinleri |
| 2. Ön işleme | Metin temizleme | Noktalama, stop-word, marka adı normalizasyonu |
| 3. Skorlama | TF-IDF + sözlük | TR sektörel duygu sözlüğü ile olumlu/nötr/olumsuz sınıflandırma |
| 4. Toplulaştırma | Skor ortalaması | Tüm mention'ların sentiment_skoru → işaretlenmiş örnekler |
| 5. Saklama | `analysis.sentiment_scores` tablosu | sentiment_id, mention_id, score, confidence, detector_version |

```
Worker pipeline:
measure_raw_response → sentiment_analysis → hallucination_check → store_results
                               ↓
                    Mention bazlı sentiment skoru
                               ↓
                    Marka bazlı toplulaştırma
```

**Algoritma:**
```
sentiment_skoru(mention) = sözlük_eşleme(mention.metin)
  → olumlu kelime sayısı > olumsuz → [0.6, 1.0]
  → olumlu ≈ olumsuz → [0.4, 0.6)
  → olumlu < olumsuz → [0.0, 0.4)

marka_sentiment = Σ(mention_skoru_i) / max(toplam_mention, 1)
```

HT2 planı: BERTurk fine-tune transformer modeli (0416 §3).

### 4.3 Hallüsinasyon Tespiti (FR-D8)

| Bileşen | Sorumluluk |
|---------|------------|
| **Doğruluk kontrolü** | AI yanıtındaki iddiaları marka bilgi grafiğiyle eşleştirme |
| **Tutarsızlık skoru** | Aynı prompt'un farklı zamanlardaki yanıtlarını karşılaştırma |
| **Kaynak çapraz doğrulama** | AI yanıtındaki iddiaları gerçek kaynak URL'lerle eşleştirme |

```
hallüsinasyon_skoru(yanıt) = 
  w₁ × doğruluk_kontrolü(yanıt, bilgi_grafiği) +
  w₂ × tutarsızlık_skoru(yanıt, önceki_yanıtlar) +
  w₃ × kaynak_doğrulama(yanıt.citations, yanıt.claims)
```

### 4.4 Competitive Gap Engine (FR-D11, 0419)

Beş gap türü için ayrı hesaplama motoru:

| Gap Türü | Hesaplama | Veri Kaynağı |
|:--------:|-----------|--------------|
| **Visibility Gap** | `marka_skoru - rakip_skoru` (normalize) | Score verileri |
| **Citation Gap** | `marka_citation_sayısı - rakip_citation_sayısı` | Citation analizi |
| **Content Gap** | AI yanıtlarında eksik konu/entity tespiti | Ham yanıt + topic classifier |
| **Topic Gap** | Konu bazlı örtüşme/boşluk vektörü | Topic classification |
| **Prompt Gap** | Hangi prompt setlerinde rakip geçiyor marka geçmiyor | Prompt coverage verisi |

```
competitive_gap = analyze(
    snapshot(brand),      // Mevcut marka durumu
    snapshot(competitor), // Rakip marka durumu
    config(gap_types),    // Hangi gap türleri hesaplanacak
    config(thresholds)    // Normalizasyon ve alert eşikleri
)
→ GapReport[]  // Her gap türü için skor, yön, priority, description, evidence
```

**Worker entegrasyonu:** Ölçüm sonrası `computeAndEvaluate` fonksiyonunda 5c adımı olarak tetiklenir. Gap snapshot'ı her ölçüm döngüsü sonunda alınır.

### 4.5 Conversation Replay (FR-D12)

| Bileşen | Sorumluluk |
|---------|------------|
| **Capture** | AI motor yanıtını measurement_job'a bağlı olarak anlık görüntü olarak yakalama |
| **Storage** | S3 + meta veritabanı (ölçüm_id, motor, prompt, timestamp, raw_response) |
| **Replay viewer** | Web UI'da geçmiş yanıtları kronolojik görüntüleme |
| **Side-by-side** | Aynı prompt'un farklı motor/zaman yanıtlarını karşılaştırma |
| **Diff** | Sürümler arası fark vurgulama (text diff) |

```
capture():
  measurement_job tamamlandı → her motor yanıtı için:
    snapshot_id = snowflake_id()
    S3.put(snapshot_id, raw_response)
    DB.insert(snapshot_meta: id, job_id, engine, prompt, ts, s3_key)
```

### 4.6 Response Archive (FR-D13)

| Bileşen | Sorumluluk |
|---------|------------|
| **S3 versiyonlu saklama** | Ham yanıtlar S3'te versiyonlu olarak saklanır |
| **Retention policy** | Zaman bazlı saklama süresi (tenant bazlı yapılandırılabilir) |
| **Toplu dışa aktarım** | Seçili dönem yanıtlarını JSON/CSV export |
| **Archive search** | İçerik bazlı arama (tarih, motor, prompt, marka filtresi) |

```java
// dev.geolens.archive — Java record (Jackson snake_case ile serileşir)
public record ArchiveEntry(
        String id,
        String tenantId,
        String jobId,          // measurement_job_id
        String engine,
        String prompt,
        String response,       // Ham AI yanıtı
        List<String> citations,
        Tier tier,
        Instant createdAt,
        String s3Key,
        int version) {        // S3 versiyon ID
}
```

### 4.7 Technical GEO (FR-B6, FR-B7, FR-E7, 0417)

| Bileşen | Sorumluluk | Metrik |
|---------|------------|--------|
| **LLM Bot Monitor** | GPTBot, Google-Extended, PerplexityBot, Claude-Web, CCBot, Amazonbot, Applebot-Extended erişim durumu | Bot Erişim Skoru (0-100) |
| **Schema Analyzer** | Site'deki Schema.org kullanımını (Product/FAQ/Organization/Article/BreadcrumbList/HowTo/LocalBusiness/VideoObject/Event) analiz | Schema Kullanım Skoru (0-100) |
| **Entity Optimizer** | Knowledge Graph entity tespiti ve geliştirme önerileri | Entity Tamlık Oranı |

```
technical_geo_skoru(site_url) = 
  w₁ × bot_erişim_skoru + 
  w₂ × schema_kullanım_skoru + 
  w₃ × entity_tamlık_oranı + 
  w₄ × yapısal_veri_validasyonu
```

### 4.8 Content GEO (FR-E5, FR-E6, 0418)

| Bileşen | Sorumluluk |
|---------|------------|
| **Content Gap Detector** | AI yanıtlarında eksik konu/entity tespiti |
| **Topic Cluster Recommender** | Eksik konu alanlarını AI yanıt analizinden türetme |
| **Entity Developer** | Knowledge Graph entity önerileri |
| **Semantic Network** | LSI terimlerle içerik ilişki grafiği |

```
content_gap_analizi(brand, competitors):
  brand_topics = extract_topics(brand.citations + brand.mentions)
  competitor_topics = extract_topics(competitors.citations + competitors.mentions)
  gaps = competitor_topics - brand_topics
  → ContentGap[]  // Her gap için: konu, önem, mevcut içerik durumu, öneri
```

### 4.9 SEO Integration Workers (FR-B8)

**Google Search Console Worker:**

```
SC Worker (6 saatte bir):
  1. OAuth2 token doğrulama / refresh
  2. Search Console API: sites/{siteUrl}/searchAnalytics/query
  3. Query bazlı tıklama/gösterim/tıklama oranı/ortalama konum
  4. Sorgu → brand eşleştirme (domain filtresi)
  5. seo.search_console_data tablosuna yazma
```

**GA4 Worker:**

```
GA4 Worker (6 saatte bir):
  1. OAuth2 token doğrulama / refresh
  2. Google Analytics Data API: properties/{propertyId}/runReport
  3. Günlük: sayfa görüntüleme, oturum, kullanıcı, hedef dönüşüm
  4. data.ga4_data tablosuna yazma
```

```java
// dev.geolens.seo — Java record
public record SeoConnection(
        String id,
        String tenantId,
        String platform,      // "search_console" | "ga4"
        String email,
        String propertyId,    // SC site URL veya GA4 property ID
        OffsetDateTime tokenExpiry,
        OffsetDateTime connectedAt) {
}
```

**HT2 planı:** OAuth token auto-refresh, exponential backoff, worker telemetrisi, veri validasyonu, tenant izolasyonu.

### 4.10 Site Audit Engine (FR-B4)

| Bileşen | Sorumluluk |
|---------|------------|
| **Robots.txt Analyzer** | Bot izinlerini denetleme (GPTBot, Google-Extended, PerplexityBot, vb.) |
| **Bot Access Checker** | SSR (Server-Side Rendering) sinyalleri, bot erişilebilirlik |
| **SSRF Scanner** | SSRF korumaları, iç yönlendirme riskleri |
| **SSR Detector** | Botların içeriği JavaScript'siz görüntüleyebilme durumu, statik HTML çıktısı varlığı |

```
audit(site_url):
  robots_txt = fetch_and_parse_robots(site_url)
  bot_access = test_bot_access(site_url, bot_list)
  ssr = detect_ssr_requirements(site_url)
  ssrf = scan_ssrf_vulnerabilities(site_url)
  
  → AuditFindingsCatalog {
      overall_score: weighted_average(robots_txt, bot_access, ssr, ssrf),
      summary: { total, critical, high, medium, low },
      catalog: {
        robots_txt: Finding[],
        bot_access: Finding[],
        ssr: Finding[],
        ssrf: Finding[]
      }
    }
```

**Performans hedefi:** Sorgu başına < 30 saniye (FR-B4).

### 4.11 Public API (FR-F6)

REST API — salt okunur, API anahtarı ile kimlik doğrulama.

| Endpoint | Metot | Açıklama |
|----------|:-----:|----------|
| `/public/v1/workspaces/{ws}/scores` | GET | Skor listesi (filtre: brand, engine, panel, date) |
| `/public/v1/workspaces/{ws}/trends` | GET | Zaman serisi (filtre: brand, engine, period) |
| `/public/v1/workspaces/{ws}/brands` | GET | Marka listesi |
| `/public/v1/workspaces/{ws}/citations` | GET | Alıntı/kaynak analizi |
| `/public/v1/workspaces/{ws}/reports` | GET | Rapor meta verisi |
| `/public/v1/workspaces/{ws}/competitive` | GET | Gap analizi sonuçları |
| `/public/v1/workspaces/{ws}/sentiment` | GET | Duygu analizi sonuçları |
| `/public/v1/workspaces/{ws}/audit` | GET | Site denetim bulguları |

API anahtarı yönetimi için `dev.geolens.apikey` paketi: `POST /v1/workspaces/{ws}/api-keys` ile anahtar oluşturma, `DELETE` ile iptal.

### 4.12 SSO/SAML (FR-A4)

| Bileşen | Sorumluluk |
|---------|------------|
| **SAML ACS** | Assertion Consumer Service — IdP'den gelen SAML Response'u işleme |
| **SP Metadata** | Service Provider metadata XML üretimi |
| **SP Keygen** | SP özel anahtarı ve sertifikası oluşturma, güvenli saklama |
| **IdP Config** | IdP entity ID, SSO URL, sertifika yönetimi |

```
SAML Flow:
  1. Kullanıcı "SSO ile Giriş" butonuna tıklar
  2. Platform → IdP'ye SAML Redirect (SP başlatmalı)
  3. IdP → ACS endpoint'ine SAML Response (HTTP-POST)
  4. ACS: cert doğrulama → assertion parse → tenant eşleme → oturum oluşturma
  5. Kullanıcı panoya yönlendirilir
```

Uygulama: `dev.geolens.sso` (SamlSupport — SP metadata, ACS doğrulama).

### 4.13 Öneri-Etki Takibi (FR-E4)

Öneri-etki takibi, uygulanan bir önerinin sonraki ölçümlerde görünürlük skoruna etkisini işaretli karşılaştırmayla izler. Detaylı mimari ve algoritma için bkz. `dev.geolens.recommendation` ve 0309 (Scoring Engine).

```
Öneri işaretlendi (uygulandı) → sonraki ölçümde:
  önceki_skor = measurement_before_recommendation
  sonraki_skor = measurement_after_recommendation
  etki = sonraki_skor - önceki_skor
  → recommendation_impact tablosuna kaydedilir
  → Dashboard'da "Öneri Etkisi" kartında gösterilir
```

### 4.14 Alerting Engine (FR-F12)

| Bileşen | Sorumluluk |
|---------|------------|
| **Rule Engine** | Kullanıcı tanımlı alert kurallarını değerlendirme (IF visibility_drop > X% THEN notify). `notify` worker'ı tarafından periyodik olarak taranır ve tetiklenen kurallar Notification Channels üzerinden iletilir. |
| **Notification Channels** | Slack, e-posta (HTTP API/SMTP), pano bildirimi |
| **Alert History** | Tüm tetiklenen uyarıların kronolojik kaydı |
| **Alert Dashboard** | Aktif uyarılar tek ekranda |

```java
// dev.geolens.alert — Java record
public record AlertRule(
        String id,
        String name,
        String condition,     // "visibility_drop > 20 AND engine = 'chatgpt'"
        String channel,       // "slack" | "email" | "webhook"
        String target,        // Slack kanalı, e-posta, webhook URL
        boolean enabled,
        int cooldownMin) {    // Tekrarlama önleme süresi
}
```

### 4.14 Audit Trail (FR-H2)

| İşlem | Kaydedilen Bilgi |
|-------|-----------------|
| workspace oluşturma | kim, ne zaman, hangi tenant |
| brand ekleme/silme | kim, ne zaman, brand ID/ad |
| competitor değişiklik | kim, brand, rakip eklendi/silindi |
| kullanıcı davet/rol | davet eden, davet edilen, rol |
| yapılandırma değişiklik | önceki değer → yeni değer |
| measurement tetikleme | kim, panel, motor seti |
| rapor/export | kim, rapor türü, format |

```
audit_trail:
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid()
  tenant_id   TEXT NOT NULL
  workspace_id TEXT
  actor_id    TEXT NOT NULL       // İşlemi yapan kullanıcı
  action      TEXT NOT NULL       // "brand.create" | "competitor.delete" | ...
  resource    TEXT NOT NULL       // "brands/brand-ulid"
  detail      JSONB               // Değişiklik detayı (before → after)
  created_at  TIMESTAMPTZ DEFAULT now()
```

---

## 5. Worker Profili Genişletme

0506 §2'deki worker profilleri HT1'de 8 profile genişletilmiştir:

| Profil | Kuyruk(lar) | Sorumluluk | HT1'de eklendi? |
|:------:|:-----------:|------------|:---------------:|
| **measure** | q:measure | Motor çağrıları, ham yanıt saklama, skor hesaplama | ✅ Mevcuttu (MVP) |
| **audit** | q:audit | Site denetimi (FR-B4) | ✅ Yeni |
| **sentiment** | q:sentiment | Duygu analizi + hallüsinasyon (FR-D7, FR-D8) | ✅ Yeni |
| **gap** | q:gap | Competitive gap analizi (FR-D11) | ✅ Yeni |
| **replay** | q:replay | Conversation replay snapshot (FR-D12) | ✅ Yeni |
| **archive** | q:archive | Response archive kaydı (FR-D13) | ✅ Yeni |
| **technical-geo** | q:technical-geo | Teknik GEO analizi — bot/schema (FR-E7, FR-B7) | ✅ Yeni |
| **content-geo** | q:content-geo | Content GEO analizi — gap/hub (FR-E5, FR-E6) | ✅ Yeni |
| **report** | q:report | PDF rapor üretimi, white-label | ✅ Mevcuttu (MVP) |
| **notify** | q:notify | Uyarı iletimi, e-posta özeti | ✅ Mevcuttu (MVP) |

> **Kod gerçeği (v1.2):** Java geçişi sonrası worker tek Spring profilidir (`worker`); ayrı `--profile` yoktur. Tüm akışlar (q:measure + q:governance) aynı süreçte virtual thread tüketicileriyle işlenir; consumer group adı `queue.consumer-group` (varsayılan `cg:measure`). SEO senkronu (Search Console/GA4) Redis Stream **kullanmaz**; zamanlayıcı tabanlıdır. Stream sabitleri `dev.geolens.queue.QueueProperties` (0307 §2.1) kaynak alınır.

**Worker başlangıcı:**
```bash
# java/Dockerfile target: worker (SPRING_PROFILES_ACTIVE=worker)
java -jar geolens-*.jar --spring.profiles.active=worker
```

### 5.1 Sentiment Worker İş Akışı

```
q:sentiment → XREADGROUP → load_measurement_results → 
  for each raw_response:
    mention_analysis(response, brand)
    sentiment_score(mention)
    hallucination_check(claim, knowledge_graph)
  store_analysis(analysis.sentiment_scores)
  store_analysis(analysis.hallucination_results)
```

### 5.2 SEO Veri Senkronu

SEO senkronu Redis Stream kullanmaz; worker profili içinde zamanlayıcı tabanlıdır:

```
SC Senkronu (ticker):
  her 6 saatte bir:
    for each active SEOConnection(platform='search_console'):
      refresh_token_if_expired()
      fetch_search_analytics(property, start_date, end_date)
      store(seo.search_console_data)
      update_last_sync()

GA4 Senkronu (ticker):
  her 6 saatte bir:
    for each active SEOConnection(platform='ga4'):
      refresh_token_if_expired()
      fetch_ga4_report(property, date_range)
      store(seo.ga4_data)
      update_last_sync()
```

> **Not (v1.1):** q:seo-sc / q:seo-ga4 stream'leri kodda yoktur (0304 §7.5). SEO veri toplama ticker tabanlıdır; analiz akışları yalnızca yukarıdaki 6 stream'den oluşur.

### 5.3 Gap Worker İş Akışı

```
q:gap → XREADGROUP → after measurement completes:
  load_brand_snapshot(brand_id)
  load_competitor_snapshots(brand_id)
  for each gap_type in [visibility, citation, content, topic, prompt]:
    score, direction, priority = compute_gap(brand, competitor, gap_type)
    store_gap_result(snapshot_id, gap_type, score, priority, description, evidence)
  generate_alerts_if_threshold_exceeded()
```

---

## 6. API Yüzey Haritası

0504 §3'teki kaynak modeli HT1'de aşağıdaki yeni uçlarla genişletilmiştir:

### 6.1 Yeni API Uçları (İç API — /v1)

| Uç | Amaç | FR |
|:--:|------|:--:|
| `POST /v1/workspaces/{ws}/audit` | Site denetimi tetikleme | FR-B4 |
| `GET /v1/workspaces/{ws}/audit/findings` | Bulgu kataloğu getirme | FR-B4 |
| `GET /v1/workspaces/{ws}/technical-geo/bots` | LLM bot erişim durumu | FR-B6 |
| `GET /v1/workspaces/{ws}/technical-geo/schema` | Schema.org analizi | FR-B7 |
| `GET /v1/workspaces/{ws}/seo/connections` | SEO bağlantılarını listele | FR-B8 |
| `GET /v1/workspaces/{ws}/seo/auth-url` | OAuth2 auth URL üret | FR-B8 |
| `POST /v1/workspaces/{ws}/seo/disconnect` | SEO bağlantısını kaldır | FR-B8 |
| `GET /v1/workspaces/{ws}/seo/search-console` | SC verilerini getir | FR-B8 |
| `GET /v1/workspaces/{ws}/seo/ga4` | GA4 verilerini getir | FR-B8 |
| `GET /v1/workspaces/{ws}/sentiment` | Duygu analizi sonuçları | FR-D7 |
| `GET /v1/workspaces/{ws}/hallucination` | Hallüsinasyon tespiti | FR-D8 |
| `GET /v1/workspaces/{ws}/per-platform` | Per-platform metrikler | FR-D9 |
| `GET /v1/workspaces/{ws}/competitive/visibility` | Competitive visibility | FR-D10 |
| `GET /v1/workspaces/{ws}/competitive/gap` | Gap analizi sonuçları | FR-D11 |
| `POST /v1/workspaces/{ws}/replay` | Conversation replay capture | FR-D12 |
| `GET /v1/workspaces/{ws}/replay/{id}` | Replay görüntüleme | FR-D12 |
| `POST /v1/workspaces/{ws}/replay/compare` | Side-by-side karşılaştırma | FR-D12 |
| `GET /v1/workspaces/{ws}/archive` | Archive sorgulama | FR-D13 |
| `POST /v1/workspaces/{ws}/archive/export` | Toplu dışa aktarım | FR-D13 |
| `GET /v1/workspaces/{ws}/content-geo/gaps` | Content gap sonuçları | FR-E5 |
| `POST /v1/workspaces/{ws}/content-geo/recommend` | Content GEO önerileri | FR-E6 |
| `GET /v1/workspaces/{ws}/content-geo/topics` | Topic cluster önerileri | FR-E6 |
| `GET /v1/workspaces/{ws}/impact` | Öneri etki takibi | FR-E4 |
| `GET /v1/workspaces/{ws}/audit-trail` | Denetim izi sorgulama (filtre: action, actor, date_range) | FR-H2 |
| `GET /v1/workspaces/{ws}/audit-trail/export` | Denetim izi CSV export (param: format=csv) | FR-H2 |
| `GET /v1/workspaces/{ws}/sso/config` | SSO yapılandırması getir | FR-A4 |
| `POST /v1/workspaces/{ws}/sso/config` | SSO yapılandırması kaydet | FR-A4 |
| `POST /v1/auth/saml/acs` | SAML ACS endpoint | FR-A4 |
| `GET /v1/auth/saml/metadata` | SP metadata XML | FR-A4 |
| `GET /v1/workspaces/{ws}/alerts/rules` | Alert kurallarını listele | FR-F12 |
| `POST /v1/workspaces/{ws}/alerts/rules` | Alert kuralı oluştur | FR-F12 |
| `PUT /v1/workspaces/{ws}/alerts/rules/{id}` | Alert kuralı güncelle | FR-F12 |
| `DELETE /v1/workspaces/{ws}/alerts/rules/{id}` | Alert kuralı sil | FR-F12 |
| `GET /v1/workspaces/{ws}/alerts/history` | Alert geçmişi | FR-F12 |

### 6.2 Public API (FR-F6 — /public/v1)

| Uç | Metot | Açıklama |
|:--:|:-----:|----------|
| `/public/v1/workspaces/{ws}/scores` | GET | Skor listesi (filtre: brand, engine, panel, date_range) |
| `/public/v1/workspaces/{ws}/trends` | GET | Zaman serisi (filtre: brand, engine, period) |
| `/public/v1/workspaces/{ws}/brands` | GET | Marka listesi |
| `/public/v1/workspaces/{ws}/citations` | GET | Citation analizi |
| `/public/v1/workspaces/{ws}/reports` | GET | Rapor meta verisi |
| `/public/v1/workspaces/{ws}/competitive` | GET | Competitive gap sonuçları |
| `/public/v1/workspaces/{ws}/sentiment` | GET | Duygu analizi |
| `/public/v1/workspaces/{ws}/audit` | GET | Site denetim bulguları |

---

## 7. Veritabanı Şema Genişletmesi

### 7.1 Yeni Tablolar (HT1)

| Tablo | Şema | Açıklama |
|-------|:----:|----------|
| `analysis.sentiment_scores` | `analysis` | Duygu analizi sonuçları (mention bazlı) |
| `analysis.hallucination_results` | `analysis` | Hallüsinasyon tespit sonuçları |
| `analysis.competitive_gaps` | `analysis` | Competitive gap snapshot'ları (0419) |
| `analysis.replay_snapshots` | `analysis` | Conversation replay anlık görüntüleri |
| `analysis.archive_entries` | `analysis` | Response archive meta verisi |
| `seo.search_console_data` | `seo` | Google Search Console sorgu verileri |
| `seo.ga4_data` | `data` | GA4 analytics verileri |
| `seo.connections` | `seo` | SEO bağlantı yönetimi (OAuth2 token) |
| `technical.bot_access` | `technical` | LLM bot erişim kayıtları |
| `technical.schema_analysis` | `technical` | Schema.org kullanım analizi |
| `content.content_gaps` | `content` | Content gap tespit sonuçları |
| `content.topic_clusters` | `content` | Topic cluster önerileri |
| `audit.sso_configs` | `audit` | SSO/SAML yapılandırmaları |
| `audit.alert_rules` | `audit` | Kullanıcı tanımlı alert kuralları |
| `audit.alert_history` | `audit` | Tetiklenen uyarı geçmişi |

### 7.2 Yeni Şemalar

| Şema | Sorumluluk |
|:----:|------------|
| `analysis` | Duygu analizi, hallüsinasyon, competitive gap, replay, archive |
| `seo` | SEO entegrasyon verileri (SC, GA4) |
| `technical` | LLM bot izleme, schema analizi |
| `content` | Content gap, topic cluster, entity analizi |
| `audit` | SSO yapılandırmaları, alert kural ve geçmişi |

---

## 8. Veri Akış Diyagramları

### 8.1 Uçtan Uca Ölçüm + Analiz Hattı (HT1 Genişletilmiş)

```
scheduler → q:measure → worker(measure) → 
  engines.execute(prompt) → raw_response → 
    ├── S3 (ham yanıt arşivi)
    ├── score (hesaplama)
    ├── q:sentiment → 
    │   ├── sentiment score (analysis.sentiment_scores)
    │   └── hallucination (analysis.hallucination_flags)
    ├── replay snapshot (replay.conversation_snapshots)
    ├── archive entry (archive.response_entries)
    ├── q:gap → competitive gap (competitive.gap_snapshots)
    ├── q:technical-geo → bot/schema (technical.*)
    └── q:content-geo → gap/hub (content.*)
```

### 8.2 SEO Veri Akışı

```
Google Search Console API ←→ worker (ticker) ←→ seo.search_console_data
                                          ↓
Google Analytics Data API ←→ worker (ticker) ←→ seo.ga4_data
                                          ↓
                          Web UI (SEODataPanel) ile görüntüleme
```

### 8.3 Site Denetim Akışı

```
Kullanıcı (UI) → POST /audit → q:audit → worker(audit) →
  robots.txt fetch & parse
  bot access test (her bot için)
  SSR detection
  SSRF scan
  → aggregation → AuditFindingsCatalog
  → store results
  → return to UI
```

### 8.4 SSO Oturum Akışı

```
Kullanıcı → "SSO ile Giriş" butonu
  → POST /auth/saml/login → IdP'ye yönlendirme
  → IdP'de kimlik doğrulama
  → POST /auth/saml/acs (SAML Response)
  → SAML doğrulama (cert, audience, expiry)
  → Tenant eşleme (email → tenant → workspace)
  → JWT oturum oluşturma
  → /dashboard yönlendirme
```

---

## 9. Bağımlılık ve Entegrasyon Noktaları

### 9.1 Harici Servis Bağımlılıkları

| Servis | Kullanım | FR Bağı | HT1'de eklendi? |
|--------|----------|:-------:|:---------------:|
| OpenAI Responses API | ChatGPT motoru (with web search) | FR-C3 | ✅ Mevcuttu |
| Gemini API | Gemini motoru (with grounding) | FR-C3 | ✅ Mevcuttu |
| Perplexity Sonar API | Perplexity motoru | FR-C3 | ✅ Mevcuttu |
| Anthropic Messages API | Claude motoru | FR-B6 (geniş) | ✅ Yeni |
| xAI API | Grok motoru | FR-B6 (geniş) | ✅ Yeni |
| Mistral AI API | Mistral motoru | FR-B6 (geniş) | ✅ Yeni |
| Google Search Console API | SC veri senkronizasyonu | FR-B8 | ✅ Yeni |
| Google Analytics Data API | GA4 veri senkronizasyonu | FR-B8 | ✅ Yeni |
| SendGrid (SMTP/API) | E-posta bildirimleri, haftalık özet | FR-F3 | ✅ Mevcuttu |
| Slack API | Uyarı kanalı | FR-F12 | ✅ Mevcuttu |
| S3-uyumlu depo | Ham yanıt arşivi, raporlar | FR-D13 | ✅ Mevcuttu |
| Redis 7+ | İş kuyrukları (Streams), önbellek | — | ✅ Mevcuttu |

### 9.2 Paket Bağımlılık Grafiği (HT1 Yeni Paketler)

```
dev.geolens.engine.claude ───┐
dev.geolens.engine.grok ─────┤
dev.geolens.engine.copilot ──┤──→ engine.Registry ──→ dev.geolens.measure
Dev.geolens.engine.mistral ──┤
dev.geolens.engine.gemini ───┘
                          
dev.geolens.sentiment ──────→ dev.geolens.measure (raw response tüketir)
dev.geolens.competitive ────→ dev.geolens.measure (score + snapshot okur)
dev.geolens.replay ─────────→ dev.geolens.measure (job sonucu dinler)
dev.geolens.archive ────────→ S3 + PostgreSQL

dev.geolens.technicalgeo ───→ harici HTTP (site tara)
dev.geolens.contentgeo ─────→ dev.geolens.measure (citation okur)
dev.geolens.seo ────────────→ Google API (OAuth2 + data fetch)

dev.geolens.sso ────────────→ SamlSupport + harici IdP
dev.geolens.audit ──────────→ dev.geolens.audit (denetim izi)
dev.geolens.apikey ─────────→ dev.geolens.auth (API key doğrulama)
```

---

## 10. Deployment Modeli (HT1 Genişletilmiş)

### 10.1 Konteyner Yapılandırması

| Konteyner | İmaj | Ölçek | Bağımlılık |
|:---------:|:----:|:-----:|------------|
| **API** | `geolens-api` | 2+ replika | PostgreSQL, Redis |
| **Scheduler** | `geolens-scheduler` | 1 replika | PostgreSQL, Redis |
| **Worker** | `geolens-worker` | 2+ replika | PostgreSQL, Redis, S3, Engines API |
| **Frontend** | `geolens-web` | 1+ replika | API |

> **Kod gerçeği (v1.2):** Java'da worker tek konteynerdir (`java/Dockerfile` target: worker); q:measure + q:governance akışlarını aynı süreçte işler. Ayrı profile konteynerleri yoktur.

### 10.2 Redis Stream Yapılandırması

| Stream | Consumer Group | Worker Profili |
|:------:|:--------------:|:--------------:|
| `q:measure` | `cg:measure` | worker profili (ana tüketici) |
| `q:governance` | `cg:measure` | worker profili (webhook iletimi + ACK) |
| `q:dead` | — | DLQ (Redis stream, manuel) |

> **Not (v1.2):** Java'da tüm akışlar aynı consumer group adını paylaşır (`queue.consumer-group`, varsayılan `cg:measure`); per-stream ayrı grup/worker tasarımı uygulanmamıştır. q:seo-sc / q:seo-ga4 yoktur.

---

## 11. Güvenlik Mimarisi Genişletmesi

0508 güvenlik mimarisine HT1 eklemeleri:

| Alan | HT1 Eklemeleri |
|:----:|----------------|
| **API Kimlik Doğrulama** | Public API anahtar doğrulaması (API key hash + scope) |
| **SSO** | SAML Response doğrulama (certificate, audience, expiry, destination) |
| **SEO OAuth2** | Google OAuth2 token yönetimi (refresh, expiry handling) |
| **Kripto-silme** | S3 zarf anahtarı ile şifreleme, anahtar imhası (KVKK) |
| **Denetim İzi** | Yalnız-ekle tablosu, değiştirilemez kayıtlar |
| **Site Denetim** | SSRF taraması (internal network güvenliği), bot access kontrolü |

---

## 12. Gözlemlenebilirlik Genişletmesi

### 12.1 Yeni Metrikler (HT1)

| Metrik | Tür | Worker |
|:------:|:---:|:------:|
| `sentiment_processed_total` | Counter | sentiment |
| `sentiment_processing_duration_ms` | Histogram | sentiment |
| `hallucination_detected_total` | Counter | sentiment |
| `hallucination_check_duration_ms` | Histogram | sentiment |
| `competitive_gap_computed_total` | Counter | gap |
| `gap_type_duration_ms` | Histogram (label: gap_type) | gap |
| `seo_sc_sync_total` | Counter (label: status=success/fail) | seo-sc |
| `seo_ga4_sync_total` | Counter (label: status=success/fail) | seo-ga4 |
| `seo_token_refresh_total` | Counter (label: platform) | seo-sc, seo-ga4 |
| `audit_completed_total` | Counter (label: brand) | audit |
| `audit_duration_seconds` | Histogram | audit |
| `replay_captured_total` | Counter | worker(measure) |
| `public_api_requests_total` | Counter (label: endpoint) | api |
| `saml_auth_total` | Counter (label: status=success/fail) | api |

### 12.2 Yeni Alarmlar

| Alarm | Koşul | Kanal | Worker |
|:------|:-----:|:-----:|:------:|
| Sentiment drop | sentiment < 0.3 threshold | Pano + e-posta | sentiment |
| Hallucination spike | hallucination > 5% rate | Pano + e-posta | sentiment |
| SEO sync failure | consecutive failures > 3 | Pano + e-posta | seo-sc, seo-ga4 |
| Token expiry imminent | expiry < 7 days | E-posta | seo-sc, seo-ga4 |
| Audit failure | audit timeout > 60s | Pano | audit |
| Competitive gap alert | gap > threshold | Pano | gap |
| Public API rate limit | > 1000 req/min | Pano | api |

---

## 13. HT1 Bileşen-Dosya Eşleme Tablosu

| Bileşen | FR | Ana Dosyalar |
|:---------|:--:|:-------------|
| Claude adapter | FR-B6 | `dev.geolens.engine.claude.ClaudeAdapter` |
| Grok adapter | FR-B6 | `dev.geolens.engine.grok.GrokAdapter` |
| Copilot adapter | FR-B6 | `dev.geolens.engine.copilot.CopilotAdapter` |
| Mistral adapter | FR-B6 | `dev.geolens.engine.mistral.MistralAdapter` |
| Google AI Overview | FR-B6 | `dev.geolens.engine.gemini.GeminiAdapter` (AI Mode/Overview varyantları) |
| Sentiment analysis | FR-D7 | `dev.geolens.sentiment.web`, `dev.geolens.sentiment.engine.SentimentEngine` |
| Hallucination detection | FR-D8 | `dev.geolens.sentiment.web`, `dev.geolens.sentiment.engine.SentimentEngine` |
| Competitive gap | FR-D11 | `dev.geolens.competitive.web`, `dev.geolens.competitive.CompetitiveEngine` |
| Conversation replay | FR-D12 | `dev.geolens.replay.web` |
| Response archive | FR-D13 | `dev.geolens.archive.web` |
| LLM bot monitoring | FR-B6 | `dev.geolens.technicalgeo.web` |
| Schema correlation | FR-B7 | `dev.geolens.technicalgeo.web` |
| SEO integrations | FR-B8 | `dev.geolens.seo.web` |
| Content gap | FR-E5 | `dev.geolens.contentgeo.web` |
| GEO content recommendations | FR-E6 | `dev.geolens.contentgeo.web` |
| Technical GEO | FR-E7 | `dev.geolens.technicalgeo.web` |
| Site audit | FR-B4 | `dev.geolens.audit.web` |
| Public API | FR-F6 | `dev.geolens.publicapi.web.PublicController` |
| SSO/SAML | FR-A4 | `dev.geolens.sso.web.SsoController`, `dev.geolens.sso.SamlSupport` |
| Audit trail | FR-H2 | `dev.geolens.audit.web` |
| Alert rules | FR-F12 | `dev.geolens.alert.web.AlertController`, `dev.geolens.delivery` |
| Executive dashboard | FR-F8 | `web/src/components/Dashboard/` |
| Operational dashboard | FR-F9 | `web/src/components/Dashboard/` |
| SEODataPanel | FR-B8 | `web/src/components/SEODataPanel.tsx` |
| SiteAuditPanel | FR-B4 | `web/src/components/SiteAuditPanel.tsx` |
| Brand management | FR-B1 | `dev.geolens.config.web.ConfigController`, `web/src/components/BrandManagement.tsx` |
| Competitor CRUD | FR-B1 | `dev.geolens.config.web.ConfigController`, `web/src/components/BrandManagement.tsx` |
| Recommendation impact | FR-E4 | `dev.geolens.recommendation.web.RecommendationController` |
| Multi-workspace panorama | FR-D6 | `dev.geolens.config.web.ConfigController` (listWorkspacePanorama) |
| Workspace archive/transfer | FR-G3 | `dev.geolens.archive.web.ArchiveController` |

---

## 14. GeoLens İçin Çıkarımlar

1. **HT1 mimarisi 4 yeni bounded context ve 8 yeni alt modül eklemiştir.** Bu genişleme, 0502'deki bağımlılık kurallarına (D1–D7) ek olarak P6–P10 ilkelerini getirmiştir.
2. **Worker tek süreçtir; stream seti genişlemiştir.** Java geçişi sonrası q:measure + q:governance aynı worker profili içinde işlenir; SEO senkronu zamanlayıcı tabanlıdır. Per-stream ayrı worker profili tasarımı uygulanmamıştır (0307 §2.1).
3. **API yüzeyi 25+ yeni uçla genişlemiştir.** Public API (salt okunur) ayrı bir routing katmanında sunulur. İç API'de tüm yeni uçlar mevcut middleware zincirinden (auth → tenant → RBAC → entitlement) geçer.
4. **Veritabanı şema sayısı artmıştır.** HT1 eklemeleri `analysis`, `seo`, `technical`, `content`, `competitive`, `replay`, `archive` şemalarını getirmiştir; Faz 4'te `registry`, `guardrail`, `policy`, `bias`, `gate`, `explain`, `agent`, `redteam`, `prompt`, `benchmark`, `cost`, `usage`, `optimize`, `version`, `incident`, `drift`, `billing` ile 30+ şemaya ulaşmıştır (0305 §9.12). RLS, tenant izole tablolarda uygulanır; BC11/BC12 şemaları handler WHERE kullanır (0310 v1.1).
5. **Gözlemlenebilirlik 15+ yeni metrik ve 7+ yeni alarmla genişlemiştir.** Her worker profili kendi metriklerini Prometheus'a yazar.
6. **Bu doküman Faz 3'ün açılışıdır.** Sıradaki Faz 3 dokümanları: `03-domain/0302` (Domain Model güncelleme — BC7–BC10 yeni entity'leri), `03-domain/0305` (Bağlam Haritası güncelleme — BC7–BC10 ekleme), `03-domain/0308` (AI Bağlayıcıları güncelleme — yeni adapter'lar). Mevcut domain dokümanları HT1 genişletmesini yansıtacak şekilde güncellenecektir.

---

## Kaynaklar

- 0501 System Architecture — konteyner sorumlulukları, ölçüm hattı
- 0502 Service Architecture — depo iskeleti, bağımlılık kuralları
- 0506 Worker Design — worker profilleri, iş yaşam döngüsü
- 0504 API Architecture — REST standartları, kaynak modeli
- 0507 Multi-Tenancy — RLS politikaları, şema yapısı
- 0508 Security — tehdit modeli, RBAC, veri koruma
- 0205 MVP Scope — HT1 kapanış durumu, kalan FR'ler
- 0206 Roadmap — HT1/HT2 pencere modeli
- 0416 Sentiment & Hallucination — duygu analizi metodolojisi
- 0417 Technical GEO — LLM bot izleme, schema analizi
- 0418 Content GEO — içerik boşluğu analizi, entity geliştirme
- 0419 Competitive Gap Analysis — gap metodolojisi, 5 gap türü
- 0300 Domain Scope — domain katmanı kapsamı
- 0301 Core Concepts — çekirdek kavramlar, kavram hiyerarşisi
- 0302 Domain Model — entity'ler ve ilişkiler
- 0305 Bounded Contexts — BC1-BC6 haritası
- 0308 AI Connectors — adapter sözleşmesi
- 0309 Scoring Engine — skor hesaplama, GA, fidelite
- 0311 Observability — metrik kataloğu, alarm seti

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 28.07.2026 | İlk yayın: HT1 Sistem Mimarisi dokümanı. Faz 3 açılışı. 10 tasarım ilkesi (P1–P10), 4 yeni bounded context (BC7–BC10), 8 alt modül, 8 worker profili, 25+ yeni API ucu, 15+ yeni metrik, 7+ yeni alarm. Tüm HT1 bileşenlerinin mimari kararları, veri akışları ve bağımlılıkları tanımlanmıştır. |
| 1.1 | 04.08.2026 | **Kod gerçeği senkronu:** Worker profili tasarımı gerçek uygulamayla hizalandı — tek `cmd/worker` süreci, ayrı `--profile` yok, tüm akışlar aynı consumer group'u paylaşır. §5 tablosu gerçek analiz akışlarıyla (replay, archive, technical-geo, content-geo) güncellendi; q:seo-sc/q:seo-ga4 kaldırıldı (SEO senkronu ticker tabanlı, 0304 §7.5). §8.1/§8.2/§10.1/§10.2 ve §14 notları güncellendi. Şema adları düzeltildi (`technical`, `content`; `technicalgeo`/`contentgeo` yok) ve Faz 4 şema genişlemesi not edildi. 0307 §2.1, 0304, 0310 ile hizalı. |
| 1.2 | 15.08.2026 | **Java geçişi:** Paket/yol referansları `dev.geolens.*` ile güncellendi; Adapter arayüzü, ArchiveEntry/SEOConnection/AlertRule örnekleri Java karşılıklarına çevrildi; worker bölümü tek Spring worker profiline (q:measure + q:governance) indirgendi; SSO kütüphane referansı `dev.geolens.sso.SamlSupport` ile değiştirildi. ADR-014 ilişkili listesine eklendi. |
