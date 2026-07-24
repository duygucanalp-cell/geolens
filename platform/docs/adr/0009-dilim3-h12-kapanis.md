# ADR-009 · Dilim 3 (H12) Kapanış Kaydı

| Alan | Değer |
|------|-------|
| ADR ID | ADR-009 |
| Durum | Kabul |
| Tarih | 24.07.2026 |
| Karar veren | TL |
| İlişkili | ADR-008, project-plan, internal/delivery, migrations/007, migrations/008 |

---

## Bağlam

Dilim 3'ün son hipotezi (H12) tamamlanmıştır. Bu ADR, H12 boyunca yapılan çalışmaları belgelemektedir:

- Notification settings DB persistence (sync.Map → PostgreSQL)
- Audit persistence (governance.audit_results)
- Kodbase genelinde chi.URLParam düzeltmesi
- Uçtan uca demo testi
- Dilim 3 resmî kapanışı

---

## Kapsam

Dilim 3 (H9–H12) tüm hipotezleri:

| Hipotez | Açıklama | Durum |
|---------|----------|-------|
| H9 | Delivery çekirdek: e-posta bildirim altyapısı (SendGrid), öneri motoru iskeleti | ✓ Tamam |
| H10 | Haftalık özet/digest pipeline: e-posta şablonları, PDF rapor motoru (Maroto v2), web UI bildirim/rapor sekmeleri | ✓ Tamam |
| H11 | Öneri motoru tamamlama (DB entegrasyonu, trend tabanlı eylemler), audit persistence (governance.audit_results), web UI öneri paneli | ✓ Tamam |
| H12 | Notification settings DB persistence, uçtan uca demo testi, ADR kapanışı | ✓ Tamam |

---

## Kararlar

### K13: Notification settings DB persistence — sync.Map → PostgreSQL

| Öngörü | Gerçekleşen |
|--------|-------------|
| H10'da `sync.Map` ile in-memory store (TODO: H11 DB persistence) | `delivery.notification_settings` tablosu + `GetSettings`/`UpdateSettings` PostgreSQL sorguları |

**Gerekçe:** `sync.Map` development'da hızlı başlangıç için kullanılmıştı ancak container restart'ında tüm ayarlar kayboluyordu. Migration 008 ile kalıcı depolamaya geçildi. `INSERT ... ON CONFLICT DO UPDATE` (upsert) kullanıldı.

### K14: tenant_id parametresinin Service interface'ine eklenmesi

| Öngörü | Gerçekleşen |
|--------|-------------|
| `GetSettings(workspaceID string)` / `UpdateSettings(settings)` | `GetSettings(workspaceID, tenantID string)` / `UpdateSettings(settings, tenantID string)` |

**Gerekçe:** Kritik bug fix — `UpdateSettings`'te `tenant_id = ""` olarak kaydediliyordu. Bu, RLS politikası (`tenant_id = identity.get_tenant_id()`) nedeniyle kaydın okunamaz olmasına yol açıyordu. Handler `httpmw.GetTenantID(r.Context())` ile tenantID'yi context'ten alır ve service'e geçer. Bu pattern, kodbase'deki diğer handler'larla (audit, config, recommendation, pdf) tutarlıdır.

### K15: chi.URLParam kodbase genelinde standartlaştırma

| Öngörü | Gerçekleşen |
|--------|-------------|
| `r.PathValue()` ile çalışır (varsayım) | Tüm `r.PathValue()` kullanımları `chi.URLParam()` ile değiştirildi |

**Gerekçe:** Chi v5, Go 1.22'nin `r.PathValue()`'sini doldurmaz. Kodbase'de sadece 3 yerde kullanılıyordu:
1. `httpmw/middleware.go`: `RequireWorkspace` — chi.URLParam(r, "ws")
2. `internal/recommendation/handler.go`: MarkApplied/MarkDismissed — chi.URLParam(r, "recId")
3. `internal/config/panel.go`: GetPanel — chi.URLParam(r, "panelID")

Her biri düzeltildi. Build + testler geçti.

### K16: Kod base Go versiyonu Dockerfile'da

| Öngörü | Gerçekleşen |
|--------|-------------|
| `golang:1.23-alpine` kullanılıyor (varsayım) | `golang:1.26-alpine` olarak düzeltildi |

**Gerekçe:** go.mod'da `go 1.26.1` olmasına rağmen Dockerfile `golang:1.23-alpine` kullanıyordu. Build hatasına yol açıyordu.

---

## Migration Özeti (Dilim 3'te eklenenler)

| Migration | Tablo | Açıklama | LOC |
|-----------|-------|----------|:---:|
| 007_audit_results.sql | governance.audit_results | Audit sonuçları (JSONB kolonlar, RLS) | 30+ |
| 008_notification_settings.sql | delivery.notification_settings | Bildirim ayarları (upsert, RLS) | 30+ |

Toplam migration: 8 adet (001–008).

---

## Uçtan Uca Demo Testi Sonuçları

Tarih: 24.07.2026
Ortam: `deploy/docker-compose.demo.yml` (tek node)
Build: Docker images (api, worker, scheduler, web) — başarılı

### API Test Matrisi

| # | Endpoint | Metod | Durum | Açıklama |
|:-:|----------|:-----:|:-----:|----------|
| 1 | `/v1/auth/login` | POST | ✅ | Token alındı |
| 2 | `/v1/workspaces/{ws}/brands` | GET | ✅ | 3 marka döndü (Acme, BetaCorp, GammaInc) |
| 3 | `/v1/workspaces/{ws}/scores` | GET | ✅ | Veri döndü |
| 4 | `/v1/workspaces/{ws}/recommendations` | GET | ✅ | Boş liste (beklenen — henüz ölçüm yok) |
| 5 | `/v1/workspaces/{ws}/notifications/settings` | GET | ✅ | Default değerler (drop_threshold: 10) |
| 6 | `/v1/workspaces/{ws}/panels` | GET | ✅ | Liste döndü |
| 7 | `/v1/workspaces/{ws}/prompt-sets` | GET | ✅ | Liste döndü |
| 8 | `/v1/workspaces/{ws}/measurements` | POST | ✅ | Kuyruğa alındı |
| 9 | `/v1/workspaces/{ws}/notifications/settings` | PUT | ✅ | Kaydedildi (drop_threshold: 10 → 15) |
| 10 | `/v1/workspaces/{ws}/notifications/settings` | GET | ✅ | Güncel değerler doğrulandı (drop_threshold: 15) |
| 11 | `/v1/workspaces/{ws}/audit` | POST | ✅ | Audit çalıştı |

**Kritik:** Hiçbir endpoint'te `workspace_access_denied` hatası alınmadı.

### Web UI Testi

**Web UI (localhost:3000) otomatik testi:** ⚠️ Chrome/Chromium mevcut olmadığı için browser-use ile otomatik test yapılamadı. Tüm API testleri başarılı olduğu için UI'ın da çalışması beklenir. Manuel doğrulama önerilir:

1. Tarayıcıda http://localhost:3000 açın
2. `demo@acme.example.com` / `demo1234` ile giriş yapın
3. Tüm sekmeleri kontrol edin: Skorlar, Site Denetim, Raporlar, Bildirimler, Öneriler
4. Bildirimler sekmesinde ayarları güncelleyip kaydedin

---

## Açık Öğeler (Dilim 4'e devreden)

1. **Kafka entegrasyonu** — Redis Streams MVP için yeterli
2. **Perplexity/OpenAI/Gemini canlı API testi** — Mock engine yeterli
3. **Kapsamlı RBAC** — Admin/member ayrımı yeterli
4. **oapi-codegen** — Henüz ihtiyaç yok
5. **Gemini groundingConfig dead code** — adapter.go'da kullanılmayan struct
6. **AuditSnapshot ölü kod** — Engine.go'da tanımlı ama kullanılmıyor
7. **Multi-node deployment** — Tek node demo yeterli
8. **Canlı monitoring (OTel)** — Temel altyapı var
9. **PDF async workflow** — S3 depolama + background job
10. **Skor kartı ve audit raporu PDF şablonları** — generateScoreCard, generateAuditReport stub
11. **Digest scheduler cron job** — Her Pazartesi 09:00 otomatik digest

---

## Mimari Bileşenler (H12)

| Bileşen | Teknoloji | LOC (yaklaşık) |
|---------|-----------|----------------|
| Notification settings DB migration | SQL (008) | 30+ |
| GetSettings/UpdateSettings PostgreSQL | Go | 20+ (değişim) |
| Handler tenantID ekleme | Go | 4 satır |
| chi.URLParam düzeltmesi (3 dosya) | Go | 6 satır |

---

## Çıkış Kapısı Kriterleri (H12)

| Kriter | Durum |
|--------|:-----:|
| Notification settings DB'ye kaydedilir (sync.Map → PostgreSQL) | ✓ |
| tenant_id doğru değerle saklanır (RLS çalışır) | ✓ |
| GET settings DB'den okur (fallback to defaults) | ✓ |
| PUT settings upsert yapar | ✓ |
| 008_notification_settings.sql migration | ✓ |
| 007_audit_results.sql migration | ✓ |
| Tüm 8 migration uygulanmış | ✓ |
| Docker imajları rebuild edilmiş | ✓ |
| API demo testi: 11 endpoint başarılı | ✓ |
| workspace_access_denied hatası yok | ✓ |
| chi.URLParam kodbase genelinde standart | ✓ |
| Dockerfile Go versiyonu go.mod ile uyumlu | ✓ |
| Go build + vet başarılı | ✓ |
| Tüm Go birim testleri geçer | ✓ |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 24.07.2026 | İlk yayın: H12 kapanış kaydı — notification settings DB persistence, uçtan uca demo testi, çıkış kapısı |
