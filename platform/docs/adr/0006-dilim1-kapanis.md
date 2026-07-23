# ADR-006 · Dilim 1 (İskelet) Kapanış Kaydı

| Alan | Değer |
|------|-------|
| ADR ID | ADR-006 |
| Durum | Kabul |
| Tarih | 23.07.2026 |
| Karar veren | TL |
| İlişkili | 0001, 0002, 0003, 0004, 0005, project-plan |

---

## Bağlam

Dilim 1 (İskelet, H0–H4) tamamlanmıştır. Bu ADR, Dilim 1 boyunca alınan kararları, gerçekleşen mimari sapmaları ve kapanış kriterlerini belgelemektedir.

---

## Kapsam

Dilim 1 şu hipotezleri (H0–H4) kapsar:

| Hipotez | Açıklama | Durum |
|---------|----------|-------|
| H0 | Proje altyapısı, dizin yapısı, CI/CD | ✓ Tamam |
| H1 | Temel Go API iskeleti: auth, middleware, engine adapter, config CRUD | ✓ Tamam |
| H2 | Ölçüm pipeline'ı: scheduler, worker, Redis Streams, outbox, S3, panel/prompt-set yönetimi | ✓ Tamam |
| H3 | Skorlama motoru (4 bileşen + GA + fidelity), governance, platform hardening, React Skeleton + ScoreCard | ✓ Tamam |
| H4 | H4 TODO'lar, entegrasyon testleri, TrendChart, demo ortamı, ADR kapanışı | ✓ Tamam |

---

## Kararlar

### K1: Redis → PostgreSQL Outbox (orijinal plandan sapma)

| Öngörü | Gerçekleşen |
|--------|-------------|
| ADR-004: Kafka ile event-driven mimari | Hafif başlangıç: Redis Streams + PostgreSQL outbox. Kafka boşta bekler. |

**Gerekçe:** Dilim 1'de Kafka altyapısı kurmak aşırı yük getirecekti. Redis + outbox ile aynı desen korunur, Kafka'ya geçiş ADR-004'te planlanmıştır.

### K2: ULID kullanımı ertelendi

| Öngörü | Gerçekleşen |
|--------|-------------|
| ULID kütüphanesi (oklog/ulid) | Basit timestamp+random string |

**Gerekçe:** ULID kütüphanesi bağımlılığı H3'te eklenmemiştir, H4'te de eklenmemiştir. 10M prompt/gün ölçeğine ulaşmadan önce eklenmelidir.

### K3: Partial yayın deseni

Karar: Başarısız motor yanıtları tüm pipeline'ı bloke etmez. `Measure()` başarısız motorları atlar, `CalculateScore()` eksik veriyle çalışır.

### K4: Tenant ID context propagation

Karar: Tenant ID, JWT'den çıkarılır, HTTP middleware ile context'e yazılır, oradan DB session variable'ına (`app.tenant_id`) aktarılır. RLS politikaları bu session variable ile çalışır.

### K5: n=3 örnekleme stratejisi

Karar: Her motor sorgusu 3 kez paralel çalıştırılır. Sonuçlar birleştirilir, boş/başarısız örnekler atlanır.

---

## Mimari Bileşenler

| Bileşen | Teknoloji | LOC (yaklaşık) |
|---------|-----------|----------------|
| API sunucu | Go + chi | 250+ |
| Scheduler | Go | 150+ |
| Worker | Go | 200+ |
| Engine adapter | Go (Perplexity Sonar) | 100+ |
| Skorlama motoru | Go | 300+ |
| PostgreSQL migrations | SQL | 6 dosya, 500+ satır |
| Web UI | React + Vite + TypeScript | 400+ |
| CI/CD | GitHub Actions | 1 workflow |
| Demo ortamı | Docker Compose | 3 dosya |

---

## Çıkış Kapısı Kriterleri

| Kriter | Durum |
|--------|-------|
| Kullanıcı kaydolup giriş yapabilir | ✓ |
| Kullanıcı panel oluşturabilir (marka + prompt set + schedule) | ✓ |
| Ölçüm tetiklenebilir (asenkron job outbox'a yazılır) | ✓ |
| Worker engine'den yanıt alır ve işler | ✓ (mock engine ile) |
| 4 bileşenli skor hesaplanır (presence, position, source, competitor) | ✓ |
| Skor dashboard'da görünür (ScoreCard + TrendChart) | ✓ |
| Korelasyon zinciri loglarda takip edilebilir (request ID + tenant context) | ✓ |

---

## Açık Öğeler (Dilim 2'ye devreden)

1. **Gerçek ULID kütüphanesi** — `oklog/ulid` eklenmeli
2. **Kafka entegrasyonu** — ADR-004'te planlandı
3. **Perplexity API canlı test** — mock engine yerine gerçek API
4. **Kapsamlı entegrasyon testleri** — testcontainers ile (temel atıldı)
5. **oapi-codegen** — OpenAPI'den Go kod üretimi
6. **Multi-node deployment** — Kubernetes manifestleri
7. **Canlı monitoring** — Prometheus + Grafana
8. **Kapsamlı RBAC** — şu an sadece admin/member ayrımı

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 23.07.2026 | İlk yayın: Dilim 1 kapanış kaydı, kararlar, açık öğeler. |
