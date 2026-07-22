# 0602 · PostgreSQL Şeması

| Alan | Değer |
|---|---|
| Doküman ID | 0602 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0601, 0302, 0303, 0204, ADR-004 |

---

## 1. Amaç

Bu doküman GeoLens Platform PostgreSQL şemasını detaylandırır: tablo tanımları, kolon türleri, kısıtlar, indeksler ve RLS politikaları.

---

## 2. Tasarım Kuralları

| # | Kural |
|:-:|-------|
| K1 | Her tabloda tenant_id ve workspace_id (uygulanabilirse) NOT NULL |
| K2 | Birincil anahtarlar ULID (26 karakter metin) |
| K3 | Yalnız-ekleme tablolarında UPDATE/DELETE engelli |
| K4 | RLS tüm kiracı kapsamlı tablolarda etkin |
| K5 | Tüm zaman kolonları timestamptz (UTC) |
| K6 | Soft delete yok; durum kolonu ile yönetim |
| K7 | Skor numeric(5,2), CHECK 0-100 |
| K8 | JSONB + schema_version esnek meta için |
| K9 | Yabancı anahtarlar ON DELETE RESTRICT |
| K10 | snake_case İngilizce, çoğul tablo adları |

---

## 3. Tablo Envanteri (25 tablo)

### BC1 · identity (5 tablo)
tenants, workspaces, users, memberships, invitations, entitlements

### BC2 · configuration (5 tablo)
brands, sites, markets, prompt_sets, prompts, prompt_templates, monitoring_plans

### BC3 · measurement (7 tablo)
panel_versions, measurement_jobs, raw_responses, citations, calculation_runs, scores, site_audit_runs, audit_findings

### BC4 · insight (1 tablo)
recommendations

### BC5 · delivery (3 tablo)
alert_rules, alerts, notification_channels, reports

### BC6 · governance (3 tablo)
audit_log, usage_records, event_outbox

---

## 4. Çekirdek Tablo Kolon Detayları

### panel_versions

| Kolon | Tip | Kısıt |
|-------|-----|-------|
| id | text | PK (ULID) |
| workspace_id | text | FK → workspaces, NOT NULL |
| version_no | integer | NOT NULL |
| panel_key | text | NOT NULL |
| content | jsonb | NOT NULL (schema_version ile) |
| content_hash | text | NOT NULL |
| created_at | timestamptz | NOT NULL DEFAULT NOW() |

UNIQUE: (workspace_id, panel_key, version_no)

### measurement_jobs

| Kolon | Tip | Kısıt |
|-------|-----|-------|
| id | text | PK (ULID) |
| workspace_id | text | FK, NOT NULL |
| panel_version_id | text | FK → panel_versions, NOT NULL |
| window_start | timestamptz | NOT NULL |
| window_end | timestamptz | NOT NULL |
| status | text | CHECK (queued/running/completed/partial/failed) |
| attempt | smallint | NOT NULL DEFAULT 0 |
| idempotency_key | text | UNIQUE, NOT NULL |

### scores

| Kolon | Tip | Kısıt |
|-------|-----|-------|
| id | text | PK (ULID) |
| calculation_run_id | text | FK → calculation_runs, NOT NULL |
| panel_version_id | text | FK → panel_versions, NOT NULL |
| brand_id | text | FK → brands, NOT NULL |
| engine | text | NULL (birleşik) veya motor adı |
| value | numeric(5,2) | CHECK (0-100) |
| ci_low | numeric(5,2) | NOT NULL |
| ci_high | numeric(5,2) | NOT NULL |
| fidelity_label | text | NOT NULL |
| freshness_at | timestamptz | NOT NULL |

CHECK: ci_low <= value <= ci_high

---

## 5. İndeks Stratejisi

| Tablo | İndeks | Amaç |
|-------|--------|------|
| scores | (workspace_id, brand_id, freshness_at DESC) | Pano trend sorgusu |
| scores | (calculation_run_id) | Skor açıklama katmanı |
| measurement_jobs | WHERE status = 'queued' (window_start) | Kuyruk taraması |
| audit_log | (tenant_id, at DESC) | Denetim izi |
| audit_log | BRIN (at) | Büyük tablo aralık taraması |
| citations | (workspace_id, raw_response_id) | Alıntı analizi |
| usage_records | UNIQUE (tenant_id, period, counter_type) | Kota okuma |

---

## Kaynaklar

- 0601 Data Model — veri kategorileri
- 0302 Domain Model — varlık tanımları
- 0303 Aggregates — toplam kökleri
- 0204 PRD — FR/NFR bağları
- ADR-004 — RLS + tek şema
- archive/avip-v1/0303-database-design.md

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: 10 tasarım kuralı, 25 tablo envanteri, çekirdek tablo kolon detayları, indeks stratejisi. |
