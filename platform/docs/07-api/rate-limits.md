# Hız Sınırları (Rate Limits)

| Alan | Değer |
|---|---|
| Doküman ID | 07-api/rate-limits |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 07-api/rest-api, 07-api/authentication, 0508, 0204 |

---

## 1. Amaç

Bu doküman GeoLens Platform API hız sınırı ve kota politikalarını tanımlar. Hız sınırları, platformun kötüye kullanımını engeller ve tüm kiracılar için adil kullanım sağlar.

---

## 2. Hız Sınırı Katmanları

| Katman | Limit | Pencere | Uygulanan |
|:------:|:-----:|:-------:|-----------|
| **Genel API** | 1.000 istek/saat | Kayan pencere | Tüm auth'lı istekler |
| **Ölçüm** | 100 ölçüm/gün | Günlük | Ölçüm tetikleme |
| **Rapor** | 50 rapor/gün | Günlük | Rapor talep |
| **Site denetimi** | 20 denetim/gün | Günlük | Site denetimi |
| **Auth** | 5 deneme/dk | Kayan pencere | Giriş uçları |
| **Dış API (HT1)** | 500 istek/saat | Kayan pencere | Public API anahtarı |

---

## 3. Kota Yönetimi

| Kota Türü | Free | Pro | Business | Enterprise |
|:---------:|:----:|:---:|:--------:|:----------:|
| Marka sayısı | 1 | 3 | 10+ | Sınırsız |
| Ölçüm frekansı | Haftalık | Günlük | Günlük | Günlük |
| Motor sayısı | 1 | 2 | 3 | 3+ |
| Prompt sayısı | 5 | 10 | 20 | Sınırsız |
| Rapor/ay | — | — | 50 | Sınırsız |

---

## 4. Hız Sınırı Yanıt Başlıkları

```http
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 842
X-RateLimit-Reset: 2026-07-22T13:00:00Z
Retry-After: 3600
```

---

## 5. Aşım Davranışı

| Durum | HTTP | Kod | Tepki |
|:-----:|:----:|:---:|-------|
| Hız sınırı aşımı | 429 | RATE_LIMITED | İstek reddedilir, Retry-After döner |
| Kota aşımı | 429 | QUOTA_EXCEEDED | İstek reddedilir, yükseltme yolunu işaret eder |
| Bütçe tavanı | 503 | BUDGET_EXCEEDED | İstek reddedilir, platform alarmı üretilir |

---

## 6. Redis Sayaç Yapısı

```
rl:{tenant_id}:{endpoint_group}:{window}
quota:{tenant_id}:{counter_type}:{period}
```

Sayaçlar Redis'te tutulur; gerçek kaynak usage_records tablosudur. Redis kaybında sayaçlar yeniden inşa edilir.

---

## Kaynaklar

- 07-api/rest-api — REST API
- 07-api/authentication — auth yöntemleri
- 0508 Security — güvenlik mimarisi
- 0503 Event-Driven — kota kapısı
- 0204 PRD — NFR-16 (kota, hız sınırı)

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: hız sınırı katmanları, kota tablosu, yanıt başlıkları, aşım davranışı. |
