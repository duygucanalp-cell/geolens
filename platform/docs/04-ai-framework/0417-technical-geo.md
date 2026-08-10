# 0417 · Teknik GEO (Technical GEO)

| Alan | Değer |
|---|---|
| Doküman ID | 0417 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 27 Temmuz 2026 |
| İlişkili | 0418, 0401, 0405, 0406, 0308, 0302, 0204, 0207, **docs/AI_Visibility_Generative_Search_Intelligence_Platform.md** |

---

## 1. Amaç

Bu doküman, web sitelerinin AI motorları tarafından **taranabilirliğini, anlaşılabilirliğini ve kaynak olarak kullanılabilirliğini** artırmak için yapılabilecek **teknik GEO (Generative Engine Optimization)** müdahalelerini tanımlar.

Turkcell RFP'deki aşağıdaki gereksinimleri karşılar:

| RFP Gereksinimi | FR Karşılığı (0204) |
|:----------------|:-------------------:|
| LLM Bot İzleme: GPTBot, Google-Extended, PerplexityBot erişim/robots.txt analizi | FR-B6 |
| Structured Data / Schema korelasyonu — Schema.org (Product, FAQ, Organization) analizi | FR-B7 |
| Kaynak ve Atıf Analizi (Citation Breakdown) — URL yapısı incelemesi | FR-D2 |
| Teknik GEO Önerileri: Schema, Entity, Knowledge Graph | FR-E7 |

---

## 2. GAVF Katmanı

Bu doküman GAVF'in **S5 (GEO Standardı)** katmanına eklenir:

| Katman | Adı | Dokümanlar |
|:------:|-----|:-----------:|
| **S5** | GEO Standardı | **0417 (Teknik GEO)**, 0418 (Content GEO) |

S5 katmanı, S1-S4 katmanlarından elde edilen bulguları eyleme dönüştüren öneri katmanıdır. Teknik GEO, sitenin AI motorları tarafından doğru şekilde taranması ve anlaşılması için gerekli altyapıyı inceler.

---

## 3. LLM Bot İzleme (FR-B6)

### 3.1 Amaç

AI motorlarının web sitesini taramak için kullandığı botların (GPTBot, Google-Extended, PerplexityBot vb.) siteye erişim durumunu, robots.txt izinlerini ve taranma sıklığını izler.

### 3.2 Desteklenen Botlar

| Bot | Sahip | Kullanan Motor(lar) | Varsayılan User-Agent |
|:---:|-------|:-------------------:|-----------------------|
| **GPTBot** | OpenAI | ChatGPT, ChatGPT Search | `Mozilla/5.0 GPTBot` |
| **Google-Extended** | Google | Gemini, Google AI Overview, AI Mode | `Google-Extended` |
| **PerplexityBot** | Perplexity | Perplexity | `PerplexityBot` |
| **ClaudeBot** | Anthropic | Claude | `ClaudeBot` |
| **Claude-Web** | Anthropic | Claude (web arama) | `Claude-Web` |
| **FacebookBot** | Meta | Meta AI | `facebookexternalhit` |
| **CCBot** | Common Crawl | Genel veri kümesi | `CCBot` |
| **ImagesBot** | OpenAI | ChatGPT (görsel) | `ImagesBot` |

### 3.3 Analiz Kategorileri

#### 3.3.1 robots.txt Erişim Durumu

| Durum | Anlamı | Öneri |
|:-----:|--------|-------|
| ✅ **İzinli** | Bot siteyi tarayabiliyor | Mevcut durum korunmalı |
| ⚠️ **Kısmen engelli** | Bazı yollar/bölümler engellenmiş | Engellenen kritik yollar kontrol edilmeli |
| 🚫 **Tam engelli** | Tüm site bot'a kapalı | robots.txt gözden geçirilmeli |
| ❓ **Tanımsız** | Bot için kural bulunamadı | Varsayılan izinli kabul edilir |

#### 3.3.2 Bot Erişim Raporu

```json
{
  "site_url": "https://www.example.com",
  "scan_date": "2026-07-27T12:00:00Z",
  "bots": [
    {
      "name": "GPTBot",
      "status": "allowed",
      "crawl_delay": null,
      "disallowed_paths": ["/admin", "/api"],
      "last_accessed": "2026-07-26T08:30:00Z",
      "access_frequency": "daily"
    },
    {
      "name": "Google-Extended",
      "status": "disallowed",
      "crawl_delay": null,
      "disallowed_paths": ["/"],
      "last_accessed": null,
      "access_frequency": "never"
    }
  ],
  "issues": [
    {
      "severity": "critical",
      "bot": "Google-Extended",
      "description": "Google-Extended tamamen engellenmiş. Gemini ve AI Overviews taramaları bu nedenle yapılamıyor.",
      "recommendation": "robots.txt'den 'Disallow: /' kuralını kaldırın veya sadece hassas yolları engelleyin.",
      "impact": "AI görünürlük kaybı"
    }
  ],
  "summary": {
    "total_bots": 8,
    "allowed": 3,
    "disallowed": 2,
    "partial": 2,
    "unknown": 1,
    "critical_issues": 1,
    "overall_access_score": 62
  }
}
```

### 3.4 Genel Erişim Skoru

```
GES = (izinli_bot_sayısı / toplam_bot) × 40
      + (kısmi_izinli_bot_sayısı / toplam_bot) × 20
      + (son_7_günde_ziyaret_eden_bot_sayısı / toplam_bot) × 40
```

| Skor Aralığı | Anlamı | Aksiyon |
|:-----------:|--------|---------|
| 80-100 | 🟢 Mükemmel | Mevcut durumu koru |
| 60-80 | 🟡 İyi | Küçük iyileştirmeler yap |
| 40-60 | 🟠 Orta | robots.txt gözden geçirilmeli |
| 20-40 | 🔴 Düşük | Kritik bot erişimleri açılmalı |
| 0-20 | ⚫ Kritik | Acil müdahale gerekli |

---

## 4. Structured Data / Schema Analizi (FR-B7)

### 4.1 Amaç

AI motorlarının web sitesi içeriğini doğru şekilde anlaması ve alıntılaması için gerekli yapılandırılmış veri (Schema.org) işaretlemelerini analiz eder.

### 4.2 Desteklenen Schema Türleri

| Schema Türü | Kullanım | AI Önemi | Önem Derecesi |
|:-----------:|----------|:--------:|:--------------:|
| **Product** | Ürün sayfaları — fiyat, stok, değerlendirme | AI alıntılama | Yüksek |
| **FAQ** | Sık sorulan sorular — doğrudan AI yanıtı | AI yanıt kaynağı | Yüksek |
| **Organization** | Kurum bilgisi — otorite sinyali | Marka tanıma | Yüksek |
| **Article** | Blog/haber içerikleri | İçerik alıntılama | Orta |
| **BreadcrumbList** | Sayfa hiyerarşisi | Site yapısı | Orta |
| **HowTo** | Rehber içerik — adım adım talimatlar | AI yanıt kaynağı | Orta |
| **Review** | Kullanıcı yorumları — sosyal kanıt | Alıntı çeşitliliği | Düşük |
| **LocalBusiness** | Yerel işletme bilgisi | Konum sorguları | Orta |
| **VideoObject** | Video içerik | Multimedya | Düşük |
| **Event** | Etkinlik sayfaları | Etkinlik sorguları | Düşük |

### 4.3 Schema Analiz Süreci

```
1. Site taraması → Ana sayfa + iç sayfalar (en fazla 5 URL)
2. HTML içinde JSON-LD, Microdata, RDFa tespiti
3. Schema türü sınıflandırma
4. Geçerlilik kontrolü (Google Structured Data Testing Tool benzeri)
5. Eksik schema tespiti (rakip karşılaştırmasıyla)
6. Optimizasyon önerileri
```

### 4.4 Schema Analiz Raporu

```json
{
  "site_url": "https://www.example.com",
  "scan_date": "2026-07-27T12:00:00Z",
  "pages_scanned": 5,
  "schemas_found": [
    {
      "type": "Organization",
      "valid": true,
      "fields": ["name", "url", "logo"],
      "missing_fields": ["sameAs", "foundingDate", "description"],
      "location": "homepage",
      "format": "JSON-LD"
    },
    {
      "type": "Product",
      "valid": false,
      "fields": ["name", "description"],
      "missing_fields": ["offers", "brand", "sku"],
      "errors": ["offers.price alanı eksik"],
      "location": "/product/123",
      "format": "JSON-LD"
    }
  ],
  "missing_schemas": [
    "FAQ (competitor has: 2 FAQ pages)",
    "Article (competitor has: 5+ article schemas)"
  ],
  "schema_score": 45,
  "recommendations": [
    {
      "priority": "high",
      "description": "Product schema'ya offers.price alanı eklenmeli",
      "impact": "AI alıntılama ve zengin sonuç görünürlüğü artar"
    },
    {
      "priority": "medium",
      "description": "FAQ schema eklenmeli — rakipler bu alanda önde",
      "impact": "AI yanıtlarında doğrudan kullanılabilir içerik"
    }
  ]
}
```

### 4.5 Schema Puanı

```
schema_puanı = (geçerli_schema_sayısı / maksimum_schema) × 50
              + (dolu_alan_oranı_ortalaması) × 30
              + (rakip_karşısında_eksik_olmayan_schema_oranı) × 20
```

---

## 5. Citation Breakdown (Kaynak ve Atıf Analizi)

### 5.1 Amaç

AI motorlarının yanıt üretirken kaynak gösterdiği URL'lerin yapısını inceler: hangi sayfa türlerinin (blog, ürün, forum) daha çok alıntılandığını analiz eder.

### 5.2 URL Kategorileri

| Kategori | Örnek | Alıntı Ağırlığı |
|:--------:|-------|:---------------:|
| **Blog / Makale** | `/blog/ai-trends-2026` | Yüksek |
| **Ürün Sayfası** | `/product/telefon-xyz` | Yüksek |
| **Kategori Sayfası** | `/kategori/elektronik` | Orta |
| **Haber / Basın** | `/press/release-2026` | Yüksek |
| **SSS / FAQ** | `/sss/hizmetler` | Yüksek |
| **Kılavuz / Rehber** | `/rehber/kurulum` | Orta |
| **Forum / Kullanıcı** | `/forum/soru-123` | Düşük |
| **Ana Sayfa** | `/` | Düşük |
| **Hakkımızda** | `/hakkimizda` | Orta |

### 5.3 Citation Breakdown Raporu

```json
{
  "brand": "Acme",
  "period": "last_30_days",
  "total_citations": 145,
  "by_category": {
    "blog": { "count": 58, "percentage": 40, "trend": "increasing" },
    "product": { "count": 32, "percentage": 22, "trend": "stable" },
    "faq": { "count": 21, "percentage": 14, "trend": "increasing" },
    "news": { "count": 15, "percentage": 10, "trend": "decreasing" },
    "category": { "count": 10, "percentage": 7, "trend": "stable" },
    "other": { "count": 9, "percentage": 6, "trend": "stable" }
  },
  "top_domains": [
    { "domain": "blog.example.com", "count": 45, "percentage": 31 },
    { "domain": "www.example.com", "count": 38, "percentage": 26 },
    { "domain": "support.example.com", "count": 22, "percentage": 15 }
  ],
  "recommendations": [
    "Blog içerikleri AI motorlarında en çok alıntılanan kaynak. Blog üretimi artırılmalı.",
    "FAQ içerikleri yükseliş trendinde. SSS sayfası genişletilmeli.",
    "Ürün sayfalarındaki structured data eksikliği giderilmeli."
  ]
}
```

---

## 6. Teknik GEO Önerileri (FR-E7)

### 6.1 Structured Data Önerileri

| # | Öneri | Öncelik | Beklenen Etki |
|:-:|-------|:-------:|:-------------:|
| 1 | Ana sayfaya **Organization** schema (JSON-LD) ekleyin | Yüksek | Marka otorite sinyali |
| 2 | Ürün sayfalarına **Product** schema (fiyat, stok, marka) ekleyin | Yüksek | Zengin sonuç + alıntı |
| 3 | FAQ sayfalarına **FAQ** schema ekleyin | Yüksek | AI yanıtlarında doğrudan kullanım |
| 4 | Blog içeriklerine **Article** schema ekleyin | Orta | İçerik alıntılama önceliği |
| 5 | Tüm sayfalara **BreadcrumbList** schema ekleyin | Orta | Site yapısı anlaşılırlığı |
| 6 | İletişim sayfasına **LocalBusiness** schema ekleyin | Düşük | Yerel AI sorguları |

### 6.2 Entity Optimizasyonu

| # | Öneri | Açıklama | Beklenen Etki |
|:-:|-------|----------|:-------------:|
| 1 | **Varlık işaretleme** | Marka, ürün, kişi varlıklarını schema ile işaretleyin | AI entity tanıma |
| 2 | **sameAs bağlantıları** | Organization schema'ya sosyal medya hesaplarını ekleyin | Knowledge Graph bağlantısı |
| 3 | **İlişkisel veri** | Ürün-marka, makale-yazar gibi ilişkileri tanımlayın | Bağlam zenginliği |
| 4 | **Eş anlamlı yönetimi** | Marka varyasyonlarını tanımlayın (kısaltma, eski ad) | Varlık eşleme doğruluğu |

### 6.3 Knowledge Graph Geliştirme

| # | Strateji | Açıklama | Zaman |
|:-:|----------|----------|:-----:|
| 1 | **Wikipedia varlığı** | Markanın Wikipedia sayfası oluşturun/güncelleyin | Uzun vadeli |
| 2 | **Wikidata kaydı** | Marka için Wikidata varlığı oluşturun | Orta vadeli |
| 3 | **Resmî veri havuzu** | Schema.org ile yapılandırılmış veri sağlayın | Kısa vadeli |
| 4 | **Sektörel platformlar** | Sektörel dizin/platformlarda marka profili oluşturun | Orta vadeli |

### 6.4 LLM Bot Erişim Önerileri

| # | Öneri | Açıklama |
|:-:|-------|----------|
| 1 | **robots.txt denetimi** | AI botlarının kritik içeriklere erişimini engellemeyin |
| 2 | **Crawl delay** | Sunucu yükü için crawl delay kullanın, tam engel koymayın |
| 3 | **Sitemap güncellemesi** | AI botlarının site yapısını anlaması için sitemap.xml kullanın |
| 4 | **SSR desteği** | AI botları JavaScript çalıştıramayabilir; SSR veya statik render sağlayın |
| 5 | **Önemli içerik** | En güncel/değerli içeriğin botlar tarafından erişilebilir olduğundan emin olun |

---

## 7. API Tasarımı

```
GET    /v1/technical-geo/scan/{site_id}        — Teknik GEO taraması başlat
GET    /v1/technical-geo/results/{scan_id}     — Tarama sonuçları
GET    /v1/technical-geo/bot-access/{site_id}  — LLM bot erişim raporu
GET    /v1/technical-geo/schema/{site_id}      — Schema analiz raporu
GET    /v1/technical-geo/citations/{brand_id}  — Citation breakdown raporu
GET    /v1/technical-geo/recommendations/{site_id}  — Teknik GEO önerileri
```

---

## 8. GeoLens İçin Çıkarımlar

1. **Teknik GEO bir ölçüm değil, bir öneri disiplinidir.** Sitenin AI motorları tarafından doğru taranması ve anlaşılması için gerekli koşulları analiz eder. Doğrudan skor üretmez, ancak FR-E7 kapsamında teknik öneriler sağlar.
2. **FR-B6 (LLM Bot izleme)** ve **FR-B7 (Schema analizi)** tüm paketlerde temel düzeyde sunulur. Detaylı analiz Pro+ paketlerdedir.
3. **Citation Breakdown**, uzun vadede içerik stratejisinin AI kanalındaki başarısını ölçmek için kullanılır. Hangi içerik türlerinin daha çok alıntılandığı, içerik üretim önceliklerini belirler.
4. **Knowledge Graph geliştirme** en uzun vadeli stratejidir ve doğrudan platform tarafından yapılamaz, ancak bu alandaki boşluklar tespit edilip öneri olarak sunulur.
5. **Specification bağlantısı:** Teknik GEO metodolojisi, GAVF S5 (GEO Standardı) kapsamında specification reposuna eklenmiştir: `specification/docs/01-standard/0111-technical-geo-standard.md`.

---

## 9. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | LLM bot listesi ne sıklıkla güncellenmeli? Yeni botlar nasıl tespit edilmeli? | ⏳ MVP'de manuel güncelleme. Otomatik bot keşfi HT1. |
| O-2 | Schema doğrulama için hangi kütüphane kullanılmalı? | ⏳ Google's SDTT API veya yerel schema.org doğrulama. |
| O-3 | Citation Breakdown için URL kategorizasyonu otomatik mi yoksa manuel mi olmalı? | ⏳ MVP'de kural tabanlı (URL pattern eşleme). ML tabanlı HT2. |

---

## Kaynaklar

- **Turkcell AI Visibility Platform RFP:** `docs/AI_Visibility_Generative_Search_Intelligence_Platform.md`
- 0204 PRD — FR-B6 (LLM bot), FR-B7 (schema), FR-E7 (teknik GEO önerileri)
- 0207 Feature Catalog — FR-B6, FR-B7, FR-E7 özellik tanımları
- 0418 Content GEO — S5 GEO Standardı içerik boyutu
- 0405 Citation Framework — alıntı türleri ve yapısı
- 0406 Answer Parser — yanıt normalizasyonu
- 0308 AI Connectors — motor bağdaştırıcıları, bot listesi
- 0302 Domain Model — Site, Bulgu varlıkları
- 0401 AI Visibility Standard — GAVF S5 katmanı

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 27.07.2026 | İlk yayın: Teknik GEO metodolojisi. LLM bot izleme (8 bot, erişim skoru), Structured Data/Schema analizi (10 schema türü, puanlama), Citation Breakdown (8 URL kategorisi), Teknik GEO önerileri (SD, entity, knowledge graph, bot erişimi). Turkcell RFP gereksinimlerini karşılar (FR-B6, FR-B7, FR-E7). |
| 1.1 | 10.08.2026 | Specification bağlantısı kapatıldı: metodoloji spec 0111'e eklendi (0111-technical-geo-standard.md). |
