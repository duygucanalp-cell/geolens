# 0305 · Sınırlı Bağlamlar (Bounded Contexts)

| Alan | Değer |
|---|---|
| Doküman ID | 0305 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0302, 0303, 0304, 0301, 0306, 0309, 0310, 0403 |

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
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
   ┌──────────────────┐ ┌──────────┐ ┌──────────────┐
   │ BC2 · Config     │ │ BC3 ·   │ │ BC6 · Gov    │
   │ (marka, panel)   │ │ Measure  │ │ (denetim,    │
   └────────┬─────────┘ │ (ölçüm,  │ │  kota)       │
            │           │  skor)   │ └──────────────┘
            │           └────┬─────┘        ▲
            │                │              │
            │     ┌──────────┘              │
            │     ▼                         │
            │  ┌──────────────────┐         │
            │  │ BC4 · Insight    │─────────┘
            │  │ (öneri)          │  (usage records)
            │  └────────┬─────────┘
            │           │
            │           ▼
            │  ┌──────────────────┐
            └─►│ BC5 · Delivery   │
               │ (bildirim, rapor)│
               └──────────────────┘
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
| **Sorumluluk** | Denetim izi, kullanım kayıtları, kota sayaçları |
| **Toplam kökleri** | — (servis katmanı; toplam yok) |
| **Dışa açık yüzey** | AuditWriter, UsageService, QuotaEnforcer |
| **İletişim kalıbı** | Fan-in (tüm bağlamlar yazma olaylarını buraya gönderir) |
| **Veri paylaşımı** | Denetim kaydı, kullanım sayacı, kota durumu |
| **Bağımlılık** | Hiçbir bağlamı import etmez (D5 kuralı); platform/db |

---

## 4. Bağlamlar Arası İletişim Kalıpları

| Kalıp | Kullanım Yeri | Mekanizma |
|-------|--------------|-----------|
| **Senkron (API)** | BC1 → Tümü (auth), BC2 → BC3 (panel okuma) | Go fonksiyon çağrısı (aynı süreç) |
| **Asenkron (Olay)** | BC3 → BC4 (skor hazır), BC3 → BC5 (değişim), BC4 → BC5 (öneri) | Outbox → Redis Streams |
| **Fan-in** | Tümü → BC6 (denetim/kota) | AuditWriter arayüzü (senkron, D5) |
| **CQRS** | BC3'ten okuma (senkron API), yazma (asenkron olay) | API okuma, worker yazma |

**Kural:** İki bağlam arasında asenkron iletişim yeterliyse senkron bağımlılık eklenmez. MVP'de ölçüm okuma işlemleri senkrondur; yazma işlemleri asenkrondur.

---

## 5. Paylaşılan Veri (Shared Kernel)

| Veri | Sahip Bağlam | Tüketen Bağlamlar |
|------|:----------:|:-----------------:|
| tenant_id | BC1 | Tüm bağlamlar |
| user_id, role | BC1 | BC5 (denetim izi), BC6 |
| workspace_id | BC1 | BC2, BC3, BC4, BC5 |
| marka tanımı | BC2 | BC3 (skor), BC5 (rapor) |
| panel yapılandırması | BC2 | BC3 (ölçüm) |
| skor | BC3 | BC4 (öneri), BC5 (rapor/uyarı) |
| calculation_run_id | BC3 | BC4, BC5 |

> **Kural:** Paylaşılan veri yalnız DTO (Data Transfer Object) olarak taşınır. Sahip bağlamın iç tipine doğrudan erişilmez.

---

## 6. Bağlam Sınırı İhlal Korumaları

| # | Kural | Uygulama |
|:-:|-------|----------|
| S1 | Her bağlam kendi veritabanı tablo kümesine sahiptir (şema önekli) | `identity.*`, `config.*`, `measure.*`, `insight.*`, `delivery.*`, `gov.*` |
| S2 | Bağlamlar arası doğrudan veritabanı erişimi yasaktır | Tüm erişim bağlam API'si üzerinden |
| S3 | Bağlam içi tipler internal/ altında saklanır; dışa yalnız api.go açılır | Go derleyici zorlaması |
| S4 | Döngüsel bağımlılık yasaktır | 0403 lint kapısı (depguard) |
| S5 | Asenkron iletişim mümkünse senkron bağımlılık eklenmez | Mimari karar (0301 P5) |

---

## 7. Bağlam Olgunluk Düzeyleri

| Bağlam | MVP | HT1 | HT2 | Ufuk |
|--------|:---:|:---:|:---:|:----:|
| BC1 · Identity | ✅ Tam | ✅ | ✅ | ✅ |
| BC2 · Config | ✅ Tam | ✅ | ✅ | ✅ |
| BC3 · Measure | ✅ Tam | ✅ | ✅ | ✅ Tahmin |
| BC4 · Insight | 🟡 Daraltılmış (kural tabanlı) | ✅ Etki takibi | ✅ Öğrenen | ✅ |
| BC5 · Delivery | 🟡 Daraltılmış (varsayılan eşikler) | ✅ Tam | ✅ API | ✅ Mobil |
| BC6 · Governance | ✅ Tam (temel) | ✅ | ✅ | ✅ |

---

## 8. CODEOWNERS Eşlemesi

| Bağlam | Paket | Sahip |
|--------|-------|-------|
| BC1 · Identity | internal/identity | Backend #1 |
| BC2 · Config | internal/config | Backend #2 |
| BC3 · Measure | internal/measure | Siz (TL+CEO) |
| BC4 · Insight | internal/insight | Backend #2 |
| BC5 · Delivery | internal/delivery | Backend #2 |
| BC6 · Governance | internal/governance | Backend #1 |
| engines | internal/engines | Siz (TL+CEO) |
| platform | internal/platform | Backend #1 |

---

## 9. GeoLens İçin Çıkarımlar

1. **6 bağlam**, 5'i MVP'de tam veya daraltılmış. BC4 (Insight) HT1'de etki takibi ile genişler.
2. **Bağlam sınırları Go internal dizinleriyle zorlanır.** Derleyici, yanlış import'u izin vermeyerek sınır ihlalini engeller.
3. **Asenkron iletişimde outbox pattern kullanılır.** Olay kaybı önlenir, transaction bütünlüğü korunur.
4. **Governance (BC6) fan-in alıcıdır.** Tüm bağlamlar denetim ve kullanım kaydı için BC6'ya yazar; BC6 hiçbir bağlamı import etmez (D5).
5. **0306 (API Design)** her bağlamın dışa açık yüzeyini OpenAPI sözleşmesine dönüştürür.
6. **0403 (CI/CD)** bağlam sınırı ihlallerini lint ile CI kapısında yakalar.

---

## 10. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | BC4 (Insight) ve BC5 (Delivery) arası doğrudan bağımlılık | ⏳ Mevcut tasarım: BC4→BC5 asenkron; BC3 aradan çıkarılabilir. |
| O-2 | Benchmark (HT2) ayrı bağlam mı, BC4 altı mı? | ⏳ Ön hipotez: BC4 alt paketi. AVIP D-64 (measure/calc) ile uyumlu. |

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
