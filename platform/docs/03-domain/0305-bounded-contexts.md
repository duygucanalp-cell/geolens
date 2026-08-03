# 0305 · Sınırlı Bağlamlar (Bounded Contexts)

| Alan | Değer |
|---|---|
| Doküman ID | 0305 |
| Proje | GeoLens Platform |
| Versiyon | 1.2 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 28 Temmuz 2026 |
| İlişkili | 0302, 0303, 0304, 0301, 0306, 0309, 0310, 0312, 0403, 0511, 0416, 0417, 0418, 0419 |

---

## 1. Amaç

Bu doküman, GeoLens Platform'un sınırlı bağlamlarını (bounded contexts) ve bu bağlamlar arasındaki iletişim kalıplarını tanımlar. 0302 Domain Model'deki bağlam haritasının modül düzeyine indirgenmiş halidir. Her bağlamın sorumluluğu, diğer bağlamlarla ilişkisi, paylaştığı veri ve kullandığı iletişim kalıbı bu dokümanda sabitlenir.

> **Tasarım filtresi bağlantısı:** Bu doküman **F2** (ölçek — bağlam sınırları modüler monoliti çamurlaşmadan korur) ve **F4** (bakım — net bağlam sınırları ekip paralel çalışmasını mümkün kılar) filtrelerine kanıt sağlar.

---

## 2. Bağlam Haritası

```
                    ┌──────────────────┐
                    │   BC1 · Identity │◄─────────────── Tüm bağlamlar (auth)
                    └────────┬─────────┘
                             │ kiracı bağlamı
              ┌──────────────┼──────────────┬──────────────┐
              ▼              ▼              ▼              ▼
   ┌──────────────────┐ ┌──────────┐ ┌──────────────┐ ┌──────────────┐
   │ BC2 · Config     │ │ BC3 ·   │ │ BC6 · Gov    │ │ BC9 · SEO    │
   │ (marka, panel)   │ │ Measure  │ │ (denetim,    │ │ (SC, GA4)    │
   └────────┬─────────┘ │ (ölçüm,  │ │  kota)       │ └──────────────┘
            │           │  skor)   │ └──────────────┘
            │           └────┬─────┘        ▲
            │                │              │
            │     ┌──────────┼──────┐       │
            │     ▼          ▼      ▼       │
            │  ┌──────────┐ ┌──────┐ ┌──────────┐
            │  │ BC4 ·    │ │BC10 │ │ BC8 ·    │
            │  │ Insight  │ │Audit│ │ Replay   │
            │  │ (öneri)  │ │&Ana.│ │(snapshot)│
            │  └────┬─────┘ └─────┘ └──────────┘
            │       │                         
            │       ▼                         
            │  ┌──────────────────┐  ┌──────────────────┐
            └─►│ BC5 · Delivery   │  │ BC7 · Archive    │
               │ (bildirim, rapor)│  │ (S3 arşiv)       │
               └──────────────────┘  └──────────────────┘
```

---

## 3. Bağlam Tanımları

### 3.1 BC1 · Identity (Kimlik ve Kiracılık)

| Alan | Değer |
|------|-------|
| **Sorumluluk** | Kiracı, çalışma alanı, kullanıcı, üyelik, rol, paket hakları, davet yönetimi |
| **Toplam kökleri** | Kiracı (Tenant), Çalışma Alanı (Workspace) |
| **Dışa açık yüzey** | TenantRepository, MembershipService, EntitlementChecker |
| **İletişim kalıbı** | Senkron (API çağrısı) — tüm bağlamlar auth için çağırır |
| **Veri paylaşımı** | tenant_id, user_id, role, entitlement (diğer bağlamlara yalnız okuma) |
| **Bağımlılık** | platform/db, platform/httpmw |

### 3.2 BC2 · Config (Yapılandırma)

| Alan | Değer |
|------|-------|
| **Sorumluluk** | Marka, site, pazar, prompt seti, şablon kütüphanesi, motor kapsamı, izleme planı |
| **Toplam kökleri** | Marka (Brand), Prompt Seti (PromptSet) |
| **Dışa açık yüzey** | BrandRepository, PanelDefinitionService, TemplateLibrary |
| **İletişim kalıbı** | Senkron (API) + BC3'e olay (PanelChanged → yeni panel versiyonu) |
| **Veri paylaşımı** | Marka tanımı, panel yapılandırması, prompt seti |
| **Bağımlılık** | BC1'den tenant doğrulama, platform/db |

### 3.3 BC3 · Measure (Ölçüm ve Hesap)

| Alan | Değer |
|------|-------|
| **Sorumluluk** | Ölçüm işi, ham yanıt, alıntı, calculation_run, skor, trend, site denetimi |
| **Toplam kökleri** | Panel Versiyonu, Ölçüm İşi, Hesap Koşusu, Denetim Koşusu |
| **Dışa açık yüzey** | MeasurementService, ScoreRepository, EngineRegistry |
| **İletişim kalıbı** | Senkron (skor okuma) + Asenkron (olay: BC4, BC5) |
| **Veri paylaşımı** | Skor, güven aralığı, alıntı, trend (diğer bağlamlara salt okunur) |
| **Bağımlılık** | BC1 (tenant), BC2 (panel), internal/engines, platform/queue, platform/storage |
| **Alt paket** | measure/calc — hesap motoru (0309) |

### 3.4 BC4 · Insight (İçgörü)

| Alan | Değer |
|------|-------|
| **Sorumluluk** | Öneri üretimi, öneri-etki takibi (HT1), benchmark (HT2) |
| **Toplam kökleri** | Öneri (Recommendation) |
| **Dışa açık yüzey** | RecommendationService |
| **İletişim kalıbı** | Asenkron (MeasurementJobCompleted → RecommendationGenerated) |
| **Veri paylaşımı** | Öneri, kanıt derecesi, işaret durumu |
| **Bağımlılık** | BC1 (tenant), BC3 (skor olayları), platform/db |

### 3.5 BC5 · Delivery (Bildirim ve Raporlama)

| Alan | Değer |
|------|-------|
| **Sorumluluk** | Uyarı kuralı, uyarı, bildirim kanalı, haftalık özet, rapor üretimi (PDF) |
| **Toplam kökleri** | Uyarı (Alert), Rapor (Report) |
| **Dışa açık yüzey** | AlertService, ReportService, NotificationChannelRepository |
| **İletişim kalıbı** | Asenkron (SCOR olayları → alert/report) + Senkron (kanal yönetimi) |
| **Veri paylaşımı** | Uyarı, rapor, özet (dış kanallara çıkış) |
| **Bağımlılık** | BC1 (tenant), BC2 (marka/şablon), BC3 (skor olayları), BC4 (öneri olayları), platform/storage, platform/queue |

### 3.6 BC6 · Governance (Denetim ve Kota)

| Alan | Değer |
|------|-------|
| **Sorumluluk** | Denetim izi, kullanım kayıtları, kota sayaçları, denetim izi export (HT1) |
| **Toplam kökleri** | — (servis katmanı; toplam yok) |
| **Dışa açık yüzey** | AuditWriter, UsageService, QuotaEnforcer |
| **İletişim kalıbı** | Fan-in (tüm bağlamlar yazma olaylarını buraya gönderir) |
| **Veri paylaşımı** | Denetim kaydı, kullanım sayacı, kota durumu |
| **Bağımlılık** | Hiçbir bağlamı import etmez (D5 kuralı); platform/db |

### 3.7 BC7 · Archive (Arşiv) — HT1

| Alan | Değer |
|------|-------|
| **Sorumluluk** | Response archive yönetimi, S3 versiyonlu saklama, retention policy, toplu dışa aktarım (JSON/CSV), içerik karması ile bütünlük doğrulama |
| **Toplam kökleri** | Arşiv Girdisi (ArchiveEntry) |
| **Dışa açık yüzey** | ArchiveRepository, RetentionPolicyRepository, ExportService |
| **İletişim kalıbı** | Asenkron (BC3'ten MeasurementCompleted olayı ile tetiklenir) + Senkron (sorgulama/dışa aktarım) |
| **Veri paylaşımı** | Arşiv girdisi meta verisi, imzalı S3 URL (BC5 ve UI'ya) |
| **Bağımlılık** | BC1 (tenant), BC3 (ham yanıt olayları), platform/storage (S3), platform/db |

### 3.8 BC8 · Replay (Konuşma Tekrarı) — HT1

| Alan | Değer |
|------|-------|
| **Sorumluluk** | Conversation replay capture, snapshot yönetimi, side-by-side karşılaştırma, conversation diff, paylaşılabilir replay bağlantısı |
| **Toplam kökleri** | Konuşma Anlık Görüntüsü (ConversationSnapshot) |
| **Dışa açık yüzey** | ReplayService, SnapshotRepository, ComparisonService |
| **İletişim kalıbı** | Asenkron (ölçüm sonrası snapshot tetikleme) + Senkron (replay görüntüleme/karşılaştırma) |
| **Veri paylaşımı** | Snapshot meta verisi, diff sonucu, replay HTML (UI'ya) |
| **Bağımlılık** | BC1 (tenant), BC3 (ölçüm sonuçları), platform/db, platform/storage (opsiyonel S3) |

### 3.9 BC9 · SEO (Arama Motoru Optimizasyonu) — HT1

| Alan | Değer |
|------|-------|
| **Sorumluluk** | Google Search Console + GA4 OAuth2 bağlantı yönetimi, periyodik veri senkronizasyonu, SEO veri depolama ve sunum |
| **Toplam kökleri** | SEO Bağlantısı (SEOConnection) |
| **Dışa açık yüzey** | SEOConnectionRepository, SearchConsoleService, GA4Service, OAuth2Handler |
| **İletişim kalıbı** | Worker (periyodik — 6 saatte bir SC/GA4 API çağrısı) + Senkron (UI sorgulama) |
| **Veri paylaşımı** | SC sorgu verisi, GA4 metrik verisi, bağlantı durumu (UI'ya) |
| **Bağımlılık** | BC1 (tenant), Google API (harici), platform/db, OAuth2 kütüphanesi |

### 3.10 BC10 · Audit & Analysis (Denetim ve Analiz) — HT1

| Alan | Değer |
|------|-------|
| **Sorumluluk** | Duygu analizi (FR-D7), hallüsinasyon tespiti (FR-D8), competitive gap analizi (5 tür — FR-D11, 0419), content gap (FR-E5), topic cluster (FR-E6), denetim izi genişletme |
| **Toplam kökleri** | Duygu Skoru (SentimentScore), Gap Anlık Görüntüsü (GapSnapshot) |
| **Dışa açık yüzey** | SentimentService, HallucinationService, CompetitiveGapService, ContentGapService |
| **İletişim kalıbı** | Worker (ölçüm sonrası analiz tetikleme) + Senkron (sorgulama) |
| **Veri paylaşımı** | Sentiment skoru, hallüsinasyon işaretleri, gap raporları, gap önerileri (BC4 ve UI'ya) |
| **Bağımlılık** | BC1 (tenant), BC3 (ham yanıt ve skor), BC6 (denetim izi — export), platform/db |

---

## 4. Bağlamlar Arası İletişim Kalıpları

| Kalıp | Kullanım Yeri | Mekanizma |
|-------|--------------|-----------|
| **Senkron (API)** | BC1 → Tümü (auth), BC2 → BC3 (panel okuma), BC7/BC8/BC9/BC10 → UI (sorgulama) | Go fonksiyon çağrısı (aynı süreç) |
| **Asenkron (Olay)** | BC3 → BC4 (skor hazır), BC3 → BC5 (değişim), BC3 → BC7 (arşivle), BC3 → BC8 (snapshot), BC3 → BC10 (analiz), BC4 → BC5 (öneri) | Outbox → Redis Streams |
| **Worker (periyodik)** | BC9 (SEO — 6 saatte bir SC/GA4 sync) | Zamanlanmış worker (scheduler) |
| **Fan-in** | Tümü → BC6 (denetim/kota); BC10 → BC4 (gap sonuçları) | AuditWriter arayüzü (senkron, D5); gap sonuçları asenkron |
| **CQRS** | BC3'ten okuma (senkron API), yazma (asenkron olay); BC10 analiz okuma (senkron), yazma (worker) | API okuma, worker yazma |

**Kural:** İki bağlam arasında asenkron iletişim yeterliyse senkron bağımlılık eklenmez. MVP'de ölçüm okuma işlemleri senkrondur; yazma işlemleri asenkrondur.

---

## 5. Paylaşılan Veri (Shared Kernel)

| Veri | Sahip Bağlam | Tüketen Bağlamlar |
|------|:----------:|:-----------------:|
| tenant_id | BC1 | Tüm bağlamlar |
| user_id, role | BC1 | BC5 (denetim izi), BC6 |
| workspace_id | BC1 | BC2, BC3, BC4, BC5, BC7, BC8, BC9, BC10 |
| SSO yapılandırması | BC1 | SAML ACS (auth akışı) |
| marka tanımı | BC2 | BC3 (skor), BC5 (rapor), BC7 (arşiv), BC8 (snapshot), BC10 (analiz) |
| rakip tanımı (brand_competitors) | BC2 | BC10 (gap analizi) |
| panel yapılandırması | BC2 | BC3 (ölçüm) |
| skor | BC3 | BC4 (öneri), BC5 (rapor/uyarı), BC10 (gap/visibility) |
| ham yanıt | BC3 | BC7 (arşiv), BC8 (snapshot), BC10 (sentiment/hallüsinasyon) |
| calculation_run_id | BC3 | BC4, BC5, BC10 |
| citation verisi | BC3 | BC10 (gap/citation) |
| SEO verisi (SC/GA4) | BC9 | UI (SEODataPanel) |
| sentiment skoru | BC10 | UI (sentiment dashboard) |
| gap analizi | BC10 | BC4 (öneri), UI (competitive dashboard) |

> **Kural:** Paylaşılan veri yalnız DTO (Data Transfer Object) olarak taşınır. Sahip bağlamın iç tipine doğrudan erişilmez.

---

## 6. Bağlam Sınırı İhlal Korumaları

| # | Kural | Uygulama |
|:-:|-------|----------|
| S1 | Her bağlam kendi veritabanı tablo kümesine sahiptir (şema önekli) | `identity.*`, `config.*`, `measure.*`, `insight.*`, `delivery.*`, `gov.*`, `archive.*`, `replay.*`, `seo.*`, `analysis.*`, `technicalgeo.*`, `contentgeo.*` |
| S2 | Bağlamlar arası doğrudan veritabanı erişimi yasaktır | Tüm erişim bağlam API'si üzerinden |
| S3 | Bağlam içi tipler internal/ altında saklanır; dışa yalnız api.go açılır | Go derleyici zorlaması |
| S4 | Döngüsel bağımlılık yasaktır | 0403 lint kapısı (depguard) |
| S5 | Asenkron iletişim mümkünse senkron bağımlılık eklenmez | Mimari karar (0301 P5) |
| S6 | BC10 analiz bileşenleri yalnız BC3'ten gelen ham yanıtları tüketir; ayrı motor çağrısı yapamaz (P6) | Worker başlangıç parametresi |

---

## 7. Bağlam Olgunluk Düzeyleri

| Bağlam | MVP | HT1 | HT2 | Ufuk |
|--------|:---:|:---:|:---:|:----:|
| BC1 · Identity | ✅ Tam | ✅ SSO/SAML | ✅ | ✅ |
| BC2 · Config | ✅ Tam | ✅ LLM bot, schema, rakip | ✅ | ✅ |
| BC3 · Measure | ✅ Tam | ✅ Sentiment, hallucination, per-platform, competitive visibility | ✅ | ✅ Tahmin |
| BC4 · Insight | 🟡 Daraltılmış (kural tabanlı) | ✅ Etki takibi, competitive gap, content gap, technical GEO | ✅ Öğrenen | ✅ |
| BC5 · Delivery | 🟡 Daraltılmış (varsayılan eşikler) | ✅ Tam (alert history, webhook) | ✅ API | ✅ Mobil |
| BC6 · Governance | ✅ Tam (temel) | ✅ Export desteği | ✅ | ✅ |
| **BC7 · Archive** | — | ✅ **Arşiv + dışa aktarım** | ✅ Retention policy | ✅ |
| **BC8 · Replay** | — | ✅ **Snapshot + karşılaştırma** | ✅ Diff derinleştirme | ✅ |
| **BC9 · SEO** | — | ✅ **SC + GA4 bağlantısı** | ✅ Worker sertleştirme | ✅ Çoklu platform |
| **BC10 · Audit & Analysis** | — | ✅ **Sentiment, hallucination, gap** | ✅ Transformer model, topic derin | ✅ |

---

## 8. CODEOWNERS Eşlemesi

| Bağlam | Paket | Sahip |
|--------|-------|-------|
| BC1 · Identity | internal/identity, internal/sso (HT1) | Backend #1 |
| BC2 · Config | internal/config, internal/technicalgeo (HT1) | Backend #2 |
| BC3 · Measure | internal/measure | Siz (TL+CEO) |
| BC4 · Insight | internal/insight, internal/contentgeo (HT1) | Backend #2 |
| BC5 · Delivery | internal/delivery, internal/alert (HT1), internal/notification | Backend #2 |
| BC6 · Governance | internal/governance, internal/audit (genişletme) | Backend #1 |
| **BC7 · Archive** | **internal/archive** | **Backend #2** |
| **BC8 · Replay** | **internal/replay** | **Siz (TL+CEO)** |
| **BC9 · SEO** | **internal/seo** | **Backend #1** |
| **BC10 · Audit & Analysis** | **internal/sentiment, internal/competitive** | **Siz (TL+CEO)** |
| engines | internal/engines | Siz (TL+CEO) |
| platform | internal/platform, internal/apikey (HT1) | Backend #1 |

---

## 9. GeoLens İçin Çıkarımlar

1. **MVP'de 6 bağlam, HT1'de 10 bağlama genişlemiştir.** Yeni bağlamlar (BC7-BC10) mevcut iletişim kalıplarını ve sınır korumalarını aynen kullanır.
2. **Bağlam sınırları Go internal dizinleriyle zorlanır.** Derleyici, yanlış import'u izin vermeyerek sınır ihlalini engeller.
3. **Asenkron iletişimde outbox pattern kullanılır.** Olay kaybı önlenir, transaction bütünlüğü korunur.
4. **Governance (BC6) fan-in alıcıdır.** Tüm bağlamlar denetim ve kullanım kaydı için BC6'ya yazar; BC6 hiçbir bağlamı import etmez (D5).
5. **BC10 (Audit & Analysis), BC3'ten tükettiği ham veri üzerinde ikincil analiz yapar.** Bu, P6 ilkesinin (analiz birikimi) bağlam düzeyindeki karşılığıdır: ayrı motor çağrısı yapılmaz.
6. **BC9 (SEO), harici bir API'ye bağlanan tek bağlamdır.** OAuth2 token yönetimi ve periyodik senkronizasyon, diğer bağlamlardan farklı bir worker kalıbı gerektirir.
7. **0306 (API Design)** her bağlamın dışa açık yüzeyini OpenAPI sözleşmesine dönüştürür.
8. **0403 (CI/CD)** bağlam sınırı ihlallerini lint ile CI kapısında yakalar.

---

## 10. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | BC4 (Insight) ve BC5 (Delivery) arası doğrudan bağımlılık | ⏳ Mevcut tasarım: BC4→BC5 asenkron; BC3 aradan çıkarılabilir. |
| O-2 | Benchmark (HT2) ayrı bağlam mı, BC4 altı mı? | ⏳ Ön hipotez: BC4 alt paketi. AVIP D-64 (measure/calc) ile uyumlu. |
| O-3 | BC7 (Archive) retention policy otomasyonu — hangi eşikte hangi aksiyon? | ⏳ HT2 öncesi kararlaştırılacak. RetentionPolicy entity tasarım aşamasında. |
| O-4 | BC9 (SEO) — birden fazla Google hesabı bağlanabilir mi? (Örn: farklı SC property'leri) | ⏳ Şu an workspace başına bir bağlantı. HT2'de değerlendirilecek. |

### Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|----|-------|--------|
| **D-64** | **Hesap motoru:** measure/calc alt paketi. TL 21.07.2026. | AVIP 0305 O-1 |
| **D-65** | **Engines modülü:** Tek modül (internal/engines). TL 21.07.2026. | AVIP 0305 O-2 |
| **D-66** | **SPA monorepo:** web/ aynı depoda. TL 21.07.2026. | AVIP 0305 O-3 |
| **D-67** | **Lint kuralları:** D1-D7 depguard kuralı. TL 21.07.2026. | AVIP 0305 O-4 |

---

## Kaynaklar

- 0302 Domain Model §3 — bağlam haritası
- 0303 Aggregates — toplam kökleri, transaction sınırları
- 0304 Domain Events — bağlamlar arası olay akışı
- 0301 System Architecture — konteyner sorumlulukları, izolasyon katmanları
- 0401 Development Process — ekip yapısı, CODEOWNERS
- archive/avip-v1/0305-services-modules.md — AVIP modül referansı

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: 6 bağlam tanımı, bağlam haritası, iletişim kalıpları (senkron/asenkron/fan-in), paylaşılan veri, sınır korumaları, olgunluk düzeyleri, CODEOWNERS eşlemesi. 0302/0303/0304'ten türetilmiştir. |
| 1.1 | 22.07.2026 | AVIP kapalı kararları taşındı: D-64 (hesap motoru), D-65 (engines modülü), D-66 (SPA monorepo), D-67 (lint). Devralınan Kararlar eklendi. |
| 1.2 | 28.07.2026 | **HT1 bağlam genişletmesi:** 4 yeni bağlam (BC7 Archive, BC8 Replay, BC9 SEO, BC10 Audit & Analysis) eklendi. Bağlam haritası diyagramı, iletişim kalıpları, shared kernel, sınır korumaları, olgunluk düzeyleri, CODEOWNERS, çıkarımlar ve açık sorular güncellendi. |