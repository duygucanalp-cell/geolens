# 0502 · Servis Mimarisi (Service Architecture)

| Alan | Değer |
|---|---|
| Doküman ID | 0502 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0501, 0302, 0305, 0204, ADR-014 |

---

## 1. Amaç

Bu doküman GeoLens'in servis mimarisini tanımlar. Modüler monolit (ADR-003) yaklaşımıyla, her bağlamın ayrı bir Java paketi (`dev.geolens.*`) olarak organize edildiği yapıyı detaylandırır.

---

## 2. Depo İskeleti

| Yol | Amaç |
|-----|------|
| api / worker / scheduler (Spring profilleri) | Üç süreç; tek jar'dan profil ile seçilir (java/Dockerfile target) |
| dev.geolens.auth | BC1 · Kimlik ve Kiracılık (JWT, JWTService) |
| dev.geolens.config | BC2 · Yapılandırma |
| dev.geolens.measure | BC3 · Ölçüm ve Hesap |
| dev.geolens.recommendation | BC4 · İçgörü (öneriler) |
| dev.geolens.delivery | BC5 · Bildirim ve Raporlama |
| dev.geolens.audit, dev.geolens.usage, dev.geolens.billing | BC6 · Denetim ve Kota |
| dev.geolens.engine | Motor bağdaştırıcıları (Registry) |
| dev.geolens.queue, dev.geolens.ml, dev.geolens.security | Çapraz kesen paketler: queue, ml serving, auth filter |
| dev.geolens.config (AppBeans) | Kablolama: Spring bean kurulumu, profil yaşam döngüsü |
| web/ | React + TypeScript SPA (ayrı derleme) |

---

## 3. Bağımlılık Kuralları

| # | Kural |
|:-:|-------|
| D1 | Profil giriş noktaları yalnız Spring kablolamasını (AppBeans) kullanır |
| D2 | Bağlam paketleri birbirini yalnız dışa açık arayüzden kullanır |
| D3 | Yön bağlam → platform'dur; platform hiçbir bağlamı import etmez |
| D4 | Bağdaştırıcı arayüzü (engine.Adapter) dev.geolens.engine'de tanımlanır, adaptörler uygular |
| D5 | Governance yalnız çağrılan taraftır (fan-in) |
| D6 | Delivery, measure/insight'ı olay üzerinden tüketir |
| D7 | Döngüsel bağımlılık yasaktır; derleme CI kapısı |

---

## 4. Servis Dışa Açık Yüzeyleri

Her bağlam paketi dışa açık servis arayüzlerini (Spring servis/controller yüzeyleri) tanımlar:

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
| 1.1 | 15.08.2026 | **Java geçişi:** Depo iskeleti `dev.geolens.*` paketleri ve Spring profilleriyle güncellendi; bağımlılık kuralları D1/D4/D7 Java karşılıklarıyla yeniden ifade edildi. ADR-014 ilişkili listesine eklendi. |
