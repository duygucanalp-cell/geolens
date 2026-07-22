# 0502 · Servis Mimarisi (Service Architecture)

| Alan | Değer |
|---|---|
| Doküman ID | 0502 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0501, 0302, 0305, 0204 |

---

## 1. Amaç

Bu doküman GeoLens'in servis mimarisini tanımlar. Modüler monolit (ADR-003) yaklaşımıyla, her bağlamın ayrı bir Go paketi olarak organize edildiği yapıyı detaylandırır.

---

## 2. Depo İskeleti

| Yol | Amaç |
|-----|------|
| cmd/api, cmd/scheduler, cmd/worker | Üç giriş noktası; aynı modülden derlenen ayrı süreçler |
| internal/identity | BC1 · Kimlik ve Kiracılık |
| internal/config | BC2 · Yapılandırma |
| internal/measure (+ calc) | BC3 · Ölçüm ve Hesap |
| internal/insight | BC4 · İçgörü |
| internal/delivery | BC5 · Bildirim ve Raporlama |
| internal/governance | BC6 · Denetim ve Kota |
| internal/engines | Motor bağdaştırıcıları (kayıt defteri) |
| internal/platform | Çapraz kesen paketler: db, queue, storage, telemetry, httpmw |
| internal/app | Kablolama: bağımlılık kurulumu, süreç yaşam döngüsü |
| web/ | React + TypeScript SPA (ayrı derleme) |

---

## 3. Bağımlılık Kuralları

| # | Kural |
|:-:|-------|
| D1 | cmd/* yalnız internal/app'i çağırır |
| D2 | Bağlam paketleri birbirini yalnız dışa açık arayüzden kullanır |
| D3 | Yön bağlam → platform'dur; platform hiçbir bağlamı import etmez |
| D4 | Bağdaştırıcı arayüzü measure tanımlar, internal/engines uygular |
| D5 | Governance yalnız çağrılan taraftır (fan-in) |
| D6 | Delivery, measure/insight'ı olay üzerinden tüketir |
| D7 | Döngüsel import yasaktır; lint CI kapısı |

---

## 4. Servis Dışa Açık Yüzeyleri

Her bağlam paketi api.go dosyasında dışa açık arayüzlerini tanımlar:

| Bağlam | Arayüzler |
|--------|-----------|
| identity | TenantRepository, MembershipService, EntitlementChecker |
| config | BrandRepository, PanelDefinitionService, TemplateLibrary |
| measure | MeasurementService, ScoreRepository, EngineRegistry |
| insight | RecommendationService |
| delivery | AlertService, ReportService, NotificationChannelRepository |
| governance | AuditWriter, UsageService, QuotaEnforcer |

---

## Kaynaklar

- 0501 System Architecture — konteyner sorumlulukları
- 0305 Bounded Contexts — bağlam haritası, iletişim kalıpları
- 0302 Domain Model — varlıklar
- archive/avip-v1/0305-services-modules.md

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: depo iskeleti, 7 bağımlılık kuralı, servis yüzeyleri. |
