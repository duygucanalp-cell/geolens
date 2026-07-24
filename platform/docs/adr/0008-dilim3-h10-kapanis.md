# ADR-008 · Dilim 3 (H9–H11) Kapanış Kaydı

| Alan | Değer |
|------|-------|
| ADR ID | ADR-008 |
| Durum | Kabul |
| Tarih | 24.07.2026 |
| Karar veren | TL |
| İlişkili | ADR-007, project-plan, internal/delivery, internal/pdf, internal/recommendation, internal/audit, platform/httpmw |

---

## Bağlam

Dilim 3'ün ilk üç hipotezi (H9–H11) tamamlanmıştır. Bu ADR, H9 (Delivery Çekirdek), H10 (Haftalık Özet/Digest Pipeline) ve H11 (Öneri Motoru Tamamlama, Audit Persistence) boyunca alınan kararları belgelemektedir.

---

## Kapsam

Dilim 3 (H9–H12) kapsamında tamamlanan hipotezler:

| Hipotez | Açıklama | Durum |
|---------|----------|-------|
| H9 | Delivery çekirdek: e-posta bildirim altyapısı (SendGrid), öneri motoru iskeleti | ✓ Tamam |
| H10 | Haftalık özet/digest pipeline: e-posta şablonları, PDF rapor motoru (Maroto v2), web UI bildirim/rapor sekmeleri | ✓ Tamam |
| H11 | Öneri motoru tamamlama (DB entegrasyonu, trend tabanlı eylemler), audit persistence (governance.audit_results), web UI öneri paneli | ✓ Tamam |
| H12 | Uçtan uca entegrasyon, demo güncelleme, ADR kapanışı (ADR-009) | ✓ Tamam |

---

## Kararlar

### K1: SendGrid e-posta altyapısı

| Öngörü | Gerçekleşen |
|--------|-------------|
| Transactional e-posta için bir servis seçilecek | SendGrid Go SDK (`github.com/sendgrid/sendgrid-go` v3.16.1) |

**Gerekçe:** SendGrid, geniş free tier (100/gün), Go SDK ve basit API'si nedeniyle seçildi. Mock mod (boş API anahtarı) development'da gerçek e-posta göndermeden test yapmayı sağlar.

### K2: Maroto v2 PDF motoru

| Öngörü | Gerçekleşen |
|--------|-------------|
| Haftalık özet PDF'i oluşturmak için bir Go PDF kütüphanesi | Maroto v2 (`github.com/johnfercher/maroto/v2` v2.4.0) |

**Gerekçe:** Maroto v2, Go için en popüler PDF kütüphanesidir. Kod tabanı Go 1.26.1 gerektirir (go.mod yükseltildi). API'si component tabanlıdır (col.New + text.New + row yapısı).

**Not:** Maroto v2 API'sinin keşfi sırasında önemli sürtünme yaşandı (WithMargins vs WithLeftMargin, col.New signature, Document.Save vs GetBytes).

### K3: In-memory notification settings store

| Öngörü | Gerçekleşen |
|--------|-------------|
| Bildirim ayarları için kalıcı depolama (PostgreSQL) | In-memory `sync.Map` + TODO(H11): DB persistence |

**Gerekçe:** H10 kapsamında bildirim ayarları için ayrı bir migration oluşturmak yerine, MVP hızı için `sync.Map` kullanıldı.

### K4: ValidationError tipi

| Öngörü | Gerçekleşen |
|--------|-------------|
| Standart `error` döndürme | Custom `validationError` type + type assertion ile 400/500 ayrımı |

**Gerekçe:** Handler'da validasyon hatalarını (400) internal server hatalarından (500) ayırmak için custom tip kullanıldı.

### K5: Öneri motoru — kural tabanlı iskelet (H9) → DB entegrasyonu (H11)

| Öngörü | Gerçekleşen (H11) |
|--------|-------------|
| H9: 4 default rule + evaluateConditions(always true) | H11: 8 default rule + evaluateConditions(DB sorgulu) + score/audit data yükleme |

**Gerekçe:** H9'da öneri motoru iskelet olarak planlanmıştı. H11'de `loadScore` (measure.scores) ve `loadAudit` (governance.audit_results) fonksiyonları eklendi. Artık gerçek DB verisine göre 8 kural değerlendirilir. Audit tabanlı kurallar (robots-blocked, no-structured-data, bot-inaccessible) yeniden aktifleştirildi.

### K6: NotificationSettings validasyonu (3 aşamalı)

| Öngörü | Gerçekleşen |
|--------|-------------|
| Validasyon yok (ham kayıt) | 3 katmanlı validasyon: zorunlu alan, format, aralık |

**Gerekçe:** Güvenlik ve veri bütünlüğü için validasyon eklendi. 9 test fonksiyonu, 50+ test case.

### K7: PDF handler — POST ile PDF döndürme

| Öngörü | Gerçekleşen |
|--------|-------------|
| RESTful: POST oluştur → GET indir (async) | POST direkt `application/pdf` döndürür (senkron) |

**Gerekçe:** MVP hızı için senkron PDF üretimi tercih edildi.

### K8: Audit persistence — governance.audit_results tablosu

| Öngörü | Gerçekleşen |
|--------|-------------|
| Audit sonuçları DB'ye kaydedilmez (sadece HTTP yanıtı) | 007_audit_results.sql migration + Save() metodu + handler'da kaydetme |

**Gerekçe:** Audit sonuçlarının DB'de saklanması, öneri motorunun audit tabanlı kuralları değerlendirebilmesi için gereklidir. JSONB kolonlar (robots_txt, bot_access, ssr, ssrf) nested yapıları olduğu gibi saklar. RLS policy mevcut.

### K9: Öneri motoru DB entegrasyonu

| Öngörü | Gerçekleşen |
|--------|-------------|
| evaluateConditions her zaman true döner | loadScore + loadAudit DB sorguları ile gerçek veri değerlendirmesi |

**Gerekçe:** 5 skor tabanlı kural (score.drop, trend.decline, engine.gap, citation.gap, competitor.gain) + 3 audit tabanlı kural (robots.blocked, no.structured.data, bot.inaccessible). `toBool` ve `compareFloat` helper fonksiyonları eklendi. Confidence skoru veri tazeliğine göre ayarlanır.

### K10: RecommendationsPanel web UI

| Öngörü | Gerçekleşen |
|--------|-------------|
| Öneri listesi yok | RecommendationsPanel bileşeni: kart listesi, severite badge, brand filter, confidence bar, MarkApplied/Dismissed butonları |

**Gerekçe:** Optimistic UI (butona basınca state hemen güncellenir), tüm durumlar (loading/error/empty) yönetilir.

### K11: chi.URLParam düzeltmesi (kritik bug fix)

| Öngörü | Gerçekleşen |
|--------|-------------|
| r.PathValue() ile çalışacağı varsayıldı | chi.URLParam() ile düzeltildi (3 dosyada) |

**Gerekçe:** Chi v5, Go 1.22'nin `r.PathValue()`'sini doldurmaz. `RequireWorkspace` middleware'inde `chi.URLParam(r, "ws")` kullanılmazsa workspace_id her zaman boş gelir ve `workspace_access_denied` hatası alınır. Aynı hata `panel.go` (panelID) ve `handler.go` (recId)'de de vardı — hepsi düzeltildi.

### K12: Backend recommendations route'ları

| Öngörü | Gerçekleşen |
|--------|-------------|
| Sadece GET /recommendations | POST /recommendations/{recId}/apply + /dismiss eklendi |

**Gerekçe:** Frontend'in MarkApplied/MarkDismissed butonları için backend route'ları gerekliydi.

---

## Dilim 2'den Devralınan Açık Öğelerin Durumu

| # | Açık Öğe (Dilim 2 → Dilim 3) | Dilim 3'te Yapılan | Durum |
|:-:|-------------------------------|--------------------|:-----:|
| 1 | Kafka entegrasyonu | Redis Streams yeterli | ⏳ Dilim 4 |
| 2 | Perplexity API canlı test | Mock engine yeterli | ⏳ Pilot |
| 3 | Kapsamlı RBAC | Admin/member ayrımı yeterli | ⏳ Dilim 4 |
| 4 | oapi-codegen | Henüz ihtiyaç yok | ⏳ Dilim 4 |
| 5 | Gemini groundingConfig dead code | adapter.go'da kullanılmayan struct | ❌ Açık |
| 6 | Audit birim testleri | 21 test eklendi | ✅ |
| 7 | Multi-node deployment | Tek node demo yeterli | ⏳ Dilim 4 |
| 8 | Canlı monitoring | OTel temel altyapısı var | ⏳ Dilim 4 |

---

## Açık Öğeler (Dilim 3 H12'ye devreden)

1. **Notification settings DB persistence** — sync.Map → PostgreSQL migration
2. **PDF async workflow** — S3 depolama + background job ile büyük raporlar
3. **Skor kartı ve audit raporu PDF şablonları** — generateScoreCard, generateAuditReport stub
4. **Digest scheduler cron job** — Her Pazartesi 09:00'da otomatik digest
5. **Gemini groundingConfig dead code** — adapter.go'da kalan ölü kod
6. **AuditSnapshot ölü kod** — Engine.go'da tanımlı ama service.go'da kullanılmıyor (AuditSnapshot tipi)
7. **Dockerfile Go version** — golang:1.26-alpine kullanılıyor, imaj boyutu büyük

---

## Mimari Bileşenler (H9–H11)

| Bileşen | Teknoloji | LOC (yaklaşık) |
|---------|-----------|----------------|
| Delivery domain types + interface | Go (internal/delivery/engine.go) | 100+ |
| SendGrid e-posta servisi | Go + sendgrid-go v3 | 80+ |
| Delivery HTTP handler (settings + test) | Go (internal/delivery/handler.go) | 100+ |
| NotificationSettings validasyonu | Go (engine.go: ValidateSettings) | 60+ |
| NotificationSettings validasyon testleri | Go (service_test.go) | 150+ (50+ case) |
| PDF domain types + interface | Go (internal/pdf/engine.go) | 40+ |
| Maroto v2 PDF servisi | Go + maroto/v2 v2.4.0 | 150+ |
| PDF HTTP handler | Go (internal/pdf/handler.go) | 35+ |
| Öneri motoru (H9 iskelet + H11 DB) | Go (internal/recommendation/) | 300+ |
| Öneri motoru testleri | Go (service_test.go) | 200+ (18 test) |
| Audit persistence + migration | Go + SQL (007_audit_results) | 50+ |
| NotificationSettings web UI | TypeScript + React | 150+ |
| ReportsPanel web UI | TypeScript + React | 80+ |
| RecommendationsPanel web UI | TypeScript + React | 150+ |
| CSS stiller (tüm H10–H11) | CSS | 200+ |
| chi.URLParam düzeltmesi (3 dosya) | Go | 3 satır |

---

## Bağımlılık Değişiklikleri

| Kütüphane | Versiyon | Sebep |
|-----------|----------|-------|
| `github.com/sendgrid/sendgrid-go` | v3.16.1 | H9: E-posta gönderimi |
| `github.com/johnfercher/maroto/v2` | v2.4.0 | H10: PDF üretimi |
| Go | 1.23.0 → 1.26.1 | Maroto v2 gereksinimi |

H11'de yeni dış bağımlılık eklenmemiştir (sadece migration ve mevcut kütüphaneler kullanılmıştır).

---

## Çıkış Kapısı Kriterleri

### H9–H10 kriterleri (önceki ADR'den)

| Kriter | Durum |
|--------|-------|
| SendGrid SDK go.mod'da ve import edilmiş | ✓ |
| POST /workspaces/{ws}/notifications/test calisiyor | ✓ |
| GET /workspaces/{ws}/notifications/settings döner (defaults) | ✓ |
| PUT /workspaces/{ws}/notifications/settings validasyon yapar | ✓ |
| ValidationError ile 400/500 ayrımı handler'da calisir | ✓ |
| POST /workspaces/{ws}/reports/digest PDF döndürür | ✓ |
| Maroto v2 ile PDF üretimi calisir | ✓ |
| Web UI'da Bildirimler sekmesi görünür | ✓ |
| Web UI'da Raporlar sekmesi görünür | ✓ |
| ValidateSettings birim testleri (50+ test case) | ✓ |
| tüm Go birim testleri geçer | ✓ |
| TypeScript derlemesi hatasiz | ✓ |
| Go build + vet başarili | ✓ |

### H11 kriterleri

| Kriter | Durum |
|--------|-------|
| Recommendation engine DB'den skor sorgular | ✓ |
| Recommendation engine DB'den audit sorgular | ✓ |
| 8 default rule (5 skor + 3 audit) aktif | ✓ |
| evaluateConditions gerçek veri ile çalişir | ✓ |
| confidence skoru veri tazeliğine göre ayarlanir | ✓ |
| POST /recommendations/{recId}/apply + /dismiss route'lari | ✓ |
| Audit sonuçlari governance.audit_results'a kaydedilir | ✓ |
| 007_audit_results.sql migration | ✓ |
| Web UI Öneriler sekmesi (RecommendationsPanel) | ✓ |
| MarkApplied/MarkDismissed butonlari çalişir (optimistic) | ✓ |
| chi.URLParam düzeltmesi (3 dosyada) | ✓ |
| API demo testi başarili (login/skor/öneri/settings) | ✓ |
| Öneri motoru birim testleri (18 test) | ✓ |
| tüm Go birim testleri geçer | ✓ |
| Go build + vet başarili | ✓ |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 24.07.2026 | İlk yayin: H9–H10 kapaniş kaydi |
| 1.1 | 24.07.2026 | H11 eklendi: öneri motoru DB entegrasyonu, audit persistence, web UI öneri paneli, chi.URLParam düzeltmesi |
