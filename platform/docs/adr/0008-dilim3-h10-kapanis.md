# ADR-008 · Dilim 3 (H9–H10) Kapanış Kaydı

| Alan | Değer |
|------|-------|
| ADR ID | ADR-008 |
| Durum | Kabul |
| Tarih | 24.07.2026 |
| Karar veren | TL |
| İlişkili | ADR-007, project-plan, internal/delivery, internal/pdf, internal/recommendation |

---

## Bağlam

Dilim 3'ün ilk iki hipotezi (H9–H10) tamamlanmıştır. Bu ADR, H9 (Delivery Çekirdek) ve H10 (Haftalık Özet/Digest Pipeline) boyunca alınan kararları, gerçekleşen mimari sapmaları ve kapanış kriterlerini belgelemektedir. Dilim 2'den devralınan açık öğelerin durumu da bu kayıtta değerlendirilmiştir.

---

## Kapsam

Dilim 3 (H9–H12) kapsamında tamamlanan hipotezler:

| Hipotez | Açıklama | Durum |
|---------|----------|-------|
| H9 | Delivery çekirdek: e-posta bildirim altyapısı (SendGrid), öneri motoru iskeleti | ✓ Tamam |
| H10 | Haftalık özet/digest pipeline: e-posta şablonları, PDF rapor motoru (Maroto v2), web UI bildirim/rapor sekmeleri | ✓ Tamam |
| H11 | Öneri motoru tamamlama, trend tabanlı eylemler | ⏳ Bekliyor |
| H12 | Uçtan uca entegrasyon, demo güncelleme, ADR kapanışı | ⏳ Bekliyor |

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

**Not:** Maroto v2 API'sinin keşfi sırasında önemli sürtünme yaşandı — dokümantasyon ile gerçek API arasında farklar vardı (WithMargins vs WithLeftMargin/WithTopMargin/WithRightMargin, col.New signature'ı, Document.Save vs GetBytes). Bu, Dilim 3'te daha dikkatli kütüphane seçimi yapılması gerektiğini göstermiştir.

### K3: In-memory notification settings store

| Öngörü | Gerçekleşen |
|--------|-------------|
| Bildirim ayarları için kalıcı depolama (PostgreSQL) | In-memory `sync.Map` + TODO(H11): DB persistence |

**Gerekçe:** H10 kapsamında bildirim ayarları için ayrı bir migration oluşturmak yerine, MVP hızı için `sync.Map` kullanıldı. Settings, workspace ID ile key'lenir ve sunucu restart'ında default'lara döner. Gerçek depolama H11'de eklenecek.

### K4: ValidationError tipi

| Öngörü | Gerçekleşen |
|--------|-------------|
| Standart `error` döndürme | Custom `validationError` type + type assertion ile 400/500 ayrımı |

**Gerekçe:** Handler'da validasyon hatalarını (400) internal server hatalarından (500) ayırmak için custom tip kullanıldı. `err.(*validationError)` assertion'ı ile handler doğru HTTP status code döndürür.

### K5: Öneri motoru — kural tabanlı iskelet

| Öngörü | Gerçekleşen |
|--------|-------------|
| AI/ML tabanlı öneri | Kural tabanlı motor (4 default rule) + evaluateConditions(always true) |

**Gerekçe:** H9 kapsamında öneri motoru sadece iskelet olarak planlanmıştı. 4 default rule tanımlandı (visibility drop, trend decline, citation gap, competitor gain), `evaluateConditions` her zaman `true` döner. Gerçek veri sorgulama H11'de eklenecek.

### K6: NotificationSettings validasyonu (3 aşamalı)

| Öngörü | Gerçekleşen |
|--------|-------------|
| Validasyon yok (ham kayıt) | 3 katmanlı validasyon: (1) zorunlu alanlar (email, day, time, format), (2) format doğrulama (HH:mm, valid day names), (3) aralık kontrolü (threshold 1-100) |

**Gerekçe:** Güvenlik ve veri bütünlüğü için validasyon eklendi. Tüm alanlar için birim testleri mevcut (9 test fonksiyonu, 50+ test case).

### K7: PDF handler — POST ile PDF döndürme

| Öngörü | Gerçekleşen |
|--------|-------------|
| RESTful: POST oluştur → GET indir (async) | POST direkt `application/pdf` döndürür (senkron) |

**Gerekçe:** MVP hızı için senkron PDF üretimi tercih edildi. İstek gelince PDF anında üretilir ve blob olarak döner. Büyük raporlar veya async workflow için H11'de S3 depolama + background job eklenebilir.

---

## Dilim 2'den Devralınan Açık Öğelerin Durumu

| # | Açık Öğe (Dilim 2 → Dilim 3) | Dilim 3'te Yapılan | Durum |
|:-:|-------------------------------|--------------------|:-----:|
| 1 | Kafka entegrasyonu | Redis Streams yeterli, ertelendi | ⏳ Dilim 4 |
| 2 | Perplexity API canlı test | Mock engine yeterli, gerçek API testi için API anahtarı gerekli | ⏳ Pilot |
| 3 | Kapsamlı RBAC | Admin/member ayrımı yeterli | ⏳ Dilim 4 |
| 4 | oapi-codegen | OpenAPI'den kod üretimi için henüz ihtiyaç yok | ⏳ Dilim 4 |
| 5 | Gemini groundingConfig dead code | `adapter.go`'daki kullanılmayan `groundingConfig` struct'ı | ❌ Açık |
| 6 | Audit birim testleri | `internal/audit/service_test.go` — 21 test eklendi | ✅ Tamamlandı |
| 7 | Multi-node deployment | Tek node demo yeterli | ⏳ Dilim 4 |
| 8 | Canlı monitoring (Prometheus + Grafana) | OTel temel altyapısı var | ⏳ Dilim 4 |

---

## Açık Öğeler (Dilim 3 H11–H12'ye devreden)

1. **Notification settings DB persistence** — `sync.Map` → PostgreSQL migration
2. **Öneri motoru gerçek veri sorgulama** — `evaluateConditions` → DB sorgusu
3. **PDF async workflow** — S3 depolama + background job ile büyük raporlar
4. **Skor kartı ve audit raporu PDF şablonları** — `generateScoreCard`, `generateAuditReport` stub
5. **Digest scheduler cron job** — Her Pazartesi 09:00'da otomatik digest
6. **Gemini groundingConfig dead code** — `adapter.go`'da kalan ölü kod

---

## Mimari Bileşenler (H9–H10)

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
| Öneri motoru iskeleti | Go (internal/recommendation/) | 150+ |
| NotificationSettings web UI | TypeScript + React | 150+ |
| ReportsPanel web UI | TypeScript + React | 80+ |
| CSS stiller | CSS | 100+ |
| Config + route registration | Go (config.go, main.go) | 20+ |

---

## Bağımlılık Değişiklikleri

| Kütüphane | Versiyon | Sebep |
|-----------|----------|-------|
| `github.com/sendgrid/sendgrid-go` | v3.16.1 | Yeni: E-posta gönderimi |
| `github.com/johnfercher/maroto/v2` | v2.4.0 | Yeni: PDF üretimi |
| Go | 1.23.0 → 1.26.1 | Maroto v2 gereksinimi |

---

## Çıkış Kapısı Kriterleri

| Kriter | Durum |
|--------|-------|
| SendGrid SDK go.mod'da ve import edilmiş | ✓ |
| `POST /v1/workspaces/{ws}/notifications/test` çalışıyor | ✓ |
| `GET /v1/workspaces/{ws}/notifications/settings` döner (defaults) | ✓ |
| `PUT /v1/workspaces/{ws}/notifications/settings` validasyon yapar | ✓ |
| ValidationError ile 400/500 ayrımı handler'da çalışır | ✓ |
| `POST /v1/workspaces/{ws}/reports/digest` PDF döndürür | ✓ |
| Maroto v2 ile PDF üretimi çalışır | ✓ |
| Web UI'da Bildirimler sekmesi görünür ve ayarlar kaydedilebilir | ✓ |
| Web UI'da Raporlar sekmesi görünür ve PDF indirilebilir | ✓ |
| H9 temizlik: formatScoreDrop ölü kodu kaldırıldı | ✓ |
| H9 temizlik: sendEmailNotification pointer kullanır | ✓ |
| H9 temizlik: Docker Compose SendGrid env'leri eklendi | ✓ |
| ValidateSettings birim testleri (50+ test case) | ✓ |
| tüm Go birim testleri geçer | ✓ |
| TypeScript derlemesi hatasız | ✓ |
| Go build + vet başarılı | ✓ |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 24.07.2026 | İlk yayın: H9–H10 kapanış kaydı, kararlar, açık öğeler, devralınan öğelerin durumu. |
