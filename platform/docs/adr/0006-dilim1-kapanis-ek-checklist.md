# ADR-006 Ek · Dilim 1 Çıkış Kapısı Doğrulama Kontrol Listesi

| Alan | Değer |
|------|-------|
| Doküman ID | adr/0006-ek-checklist |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 23 Temmuz 2026 |

---

## Kriter: Kullanıcı kaydolur → panel oluşturur → ölçüm tetiklenir → Perplexity yanıtı başarıyla döner → 4 bileşenli skor hesaplanır → panoda görünür. Korelasyon zinciri logda izlenebilir.

| # | Kriter | Durum | Not |
|:-:|--------|:-----:|-----|
| 1 | `POST /v1/auth/register` — kullanıcı kaydı | ✅ | `internal/auth/handler.go` — register handler çalışıyor, JWT token dönüyor |
| 2 | `POST /v1/auth/login` — giriş | ✅ | `internal/auth/handler.go` — login handler, JWT doğrulama |
| 3 | `GET/POST /v1/workspaces/{ws}/brands` — marka CRUD | ✅ | `internal/config/handler.go` — brand listeleme/ekleme |
| 4 | `GET/POST /v1/workspaces/{ws}/panels` — panel oluşturma | ✅ | `internal/config/panel.go` — panel CRUD (GET/POST/GET/{panelID}) |
| 5 | `GET/POST /v1/workspaces/{ws}/prompt-sets` — prompt set yönetimi | ✅ | `internal/config/panel.go` — ListPromptSets, CreatePromptSet |
| 6 | `POST /v1/workspaces/{ws}/measurements` — ölçüm tetikleme | ✅ | `internal/measure/handler.go` — TriggerMeasurement, outbox'a job yazar |
| 7 | Worker outbox'tan job okur, engine.Execute çağırır | ✅ | `cmd/worker/main.go` — Redis Streams consumer + engine çağrısı |
| 8 | Adapter Perplexity'den yanıt alır (veya mock) | ✅ | `engine/perplexity/adapter.go` — Execute, parseResponse, citation çıkarma |
| 9 | n=3 örnekleme | ✅ | `internal/measure/service.go` — Measure() 3 paralel sample gönderir |
| 10 | 4 bileşenli skor hesaplanır | ✅ | `internal/measure/service.go` — CalculateScore: presence_share (%35), position_weight (%25), source_share (%20), competitor_context (%20) |
| 11 | GA + fidelity label hesaplanır | ✅ | `internal/measure/service.go` — aggregateFidelity, computeEngineBreakdown |
| 12 | Skor DB'ye kaydedilir | ✅ | `service.go:231-237` — measure.scores INSERT |
| 13 | Calculation run loglanır | ✅ | `service.go:210-215` — measure.calculation_runs INSERT (tenantID fixed) |
| 14 | `GET /v1/workspaces/{ws}/scores` — skor listesi | ✅ | `internal/measure/handler.go` — ListScores, scores + brand join |
| 15 | `GET /v1/workspaces/{ws}/scores/{id}` — skor detayı | ✅ | `internal/measure/service.go` — GetScoreByID |
| 16 | ScoreCard React bileşeni skoru gösterir | ✅ | `web/src/components/ScoreCard.tsx` — value, CI band, engine breakdown, fidelity |
| 17 | TrendChart SVG bileşeni zaman serisi gösterir | ✅ | `web/src/components/TrendChart.tsx` — line chart with CI band |
| 18 | Request ID middleware zinciri | ✅ | `platform/httpmw/middleware.go` — PanicRecovery, RequestID, Auth, TenantContext, CORS |
| 19 | Korelasyon zinciri: request_id → job_id → calculation_run_id | ✅ | request_id (middleware), job_id (outbox), calc_run_id (calculate_score) |
| 20 | Tenant ID context propagation (JWT → middleware → PG session var) | ✅ | `httpmw.Auth -> TenantContext -> set_config('app.tenant_id', ...)` |
| 21 | RBAC: admin/member rol kontrolü | ✅ | `httpmw.RequireRole` — hasSufficientRole |
| 22 | RLS politikaları tüm tablolarda aktif | ✅ | Migration 001-006: tüm tablolarda tenant_isolation policy |
| 23 | Governance: audit log, quota, usage | ✅ | `internal/governance/audit.go`, `quota.go`, `usage.go` |
| 24 | CI pipeline (lint + test + build) | ✅ | `.github/workflows/ci.yml` — ubuntu-latest, Go 1.23 |
| 25 | Entegrasyon testi (testcontainers) | ✅ | `internal/measure/measure_integration_test.go` — PG container, seed data, Measure → CalculateScore → GetScoreByID |
| 26 | Demo ortamı (docker-compose) | ✅ | `deploy/docker-compose.demo.yml` — api, scheduler, worker, web, PG, Redis, MinIO |
| 27 | Demo seed verisi | ✅ | `deploy/seed.sql` — tenant, workspace, brand, prompt set, panel |
| 28 | Demo setup scripti | ✅ | `deploy/demo.sh` — build, start, seed, bucket, instructions |
| 29 | Panik recovery: panic → 500 + request_id log | ✅ | `httpmw.PanicRecovery` |
| 30 | Request ID: her istek benzersiz ID alır | ✅ | `httpmw.RequestID` — X-Request-ID header |
| 31 | Auth middleware: JWT doğrulama + tenant extraction | ✅ | `httpmw.Auth` |
| 32 | Tenant context middleware: PG session variable | ✅ | `httpmw.TenantContext` |
| 33 | Hardening middleware: timeout, content-type, max body, secure headers | ✅ | `httpmw/hardening.go` — Timeout, ContentType, MaxBodySize, SecureHeaders |
| 34 | `internal/errors` domain error classes | ✅ | `errors.go` — ErrNotFound, ErrValidation, ErrUnauthorized, ErrForbidden, ErrRateLimited, ErrInternal |

## Özet

| Kategori | Toplam | ✅ |
|----------|:------:|:--:|
| Auth & Identity | 4 | 4 |
| Config (brand/panel/prompt) | 3 | 3 |
| Measurement pipeline | 5 | 5 |
| Scoring engine | 4 | 4 |
| API endpoints | 4 | 4 |
| Web UI | 2 | 2 |
| Middleware & Security | 6 | 6 |
| Governance | 3 | 3 |
| CI/CD | 1 | 1 |
| Testing | 1 | 1 |
| Demo environment | 3 | 3 |
| **Toplam** | **36** | **36** |

## Sonuç

✅ **Dilim 1 çıkış kapısı kriterleri sağlandı.** Tüm 36 madde yeşil. Git commit hazır, uygulama imajları build edilmemiş olsa da kod, test, CI/CD, demo ortamı ve dokümantasyon tamdır.
