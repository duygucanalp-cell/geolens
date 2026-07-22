# 0501 · Sistem Mimarisi (System Architecture)

| Alan | Değer |
|---|---|
| Doküman ID | 0501 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0302, 0305, 0502-0510, 0204, ADR-001-005 |

---

## 1. Amaç

Bu doküman GeoLens Platform'un sistem mimarisini konteyner düzeyinde sabitler: bileşenler, sorumluluklar, izolasyon katmanları, uçtan uca ölçüm hattı ve ara katman zinciri.

---

## 2. Tasarım İlkeleri

| # | İlke | Açıklama |
|:-:|------|----------|
| P1 | Tek dağıtım, tek kod tabanı | Modüler monolit; işçiler aynı koddan ayrı süreç |
| P2 | Bağlam sınırları paket düzeyinde | Go internal dizinleri derleyici tarafından zorlanır |
| P3 | Kiracı bağlamı her katta | Veri katmanından kuyruğa kadar kiracı bağlamı taşınır |
| P4 | Deterministik hesap | Aynı girdilerle aynı sonuç; versiyonlanmış algoritmalar |
| P5 | Değişime dayanıklılık | Bağdaştırıcı ekleme/kaldırma kayıt defteri düzeyinde |

---

## 3. Konteyner Görünümü

| Süreç | Sorumluluk | Ana Bileşenler |
|-------|-----------|----------------|
| **cmd/api** | HTTP API sunucusu; auth, RBAC, kiracı bağlamı | platform/httpmw, tüm bağlam api.go yüzeyleri |
| **cmd/scheduler** | Zamanlayıcı; izleme planları, idempotent iş üretimi | platform/queue, internal/config |
| **cmd/worker** | İşçi süreçleri (measure/report/notify profilleri) | internal/measure, internal/delivery, internal/insight |

### Altyapı Bileşenleri

| Bileşen | Rol |
|---------|-----|
| PostgreSQL 16+ | Birincil veri kaynağı; tek şema + RLS (ADR-004) |
| Redis 7+ | İş kuyrukları (Streams), hız sınırları, kilitler, önbellek |
| S3-uyumlu depo | Ham yanıt arşivi, raporlar, marka varlıkları |

---

## 4. Uçtan Uca Ölçüm Hattı

| Adım | Sorumlu | Açıklama |
|:----:|---------|----------|
| 1. Tetikleme | scheduler | İzleme planı penceresi açılır |
| 2. İş üretimi | scheduler | measurement_jobs + outbox (aynı PG işlemi) |
| 3. Outbox dağıtımı | platform/queue | SKIP LOCKED → Redis Streams |
| 4. Kuyruktan okuma | worker | XREADGROUP + kiracı bağlam doğrulama |
| 5. Motor çağrısı | engines | Bağdaştırıcı Execute() |
| 6. Ham yanıt saklama | measure | S3 + meta veri |
| 7. Hesaplama | measure/calc | CalculationRun |
| 8. Skor üretimi | measure/calc | Panel + marka bağlantılı skorlar |
| 9. Korelasyon | Tümü | request_id → job_id → calculation_run_id |

---

## 5. Middleware Zinciri

Sabit sıra (değiştirilemez): Panik kurtarma → Request ID → Kimlik doğrulama → Kiracı bağlamı → RBAC → Paket hakkı → İşleyici

---

## Kaynaklar

- 0302 Domain Model — varlıklar ve bağlamlar
- 0305 Bounded Contexts — modül sınırları
- ADR-001-005 — teknoloji kararları
- 0204 PRD — FR/NFR gereksinimleri
- archive/avip-v1/0301-system-architecture.md

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: 5 tasarım ilkesi, 3 konteyner, 9 adımlı ölçüm hattı, middleware zinciri. |
