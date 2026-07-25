# 0311 · Gözlemlenebilirlik (Observability)

| Alan | Değer |
|---|---|
| Doküman ID | 0311 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 25 Temmuz 2026 |
| İlişkili | 0304, 0415, 0904, 0606, 0204, 0905 |

---

## 1. Amaç

Bu doküman, GeoLens Platform'un gözlemlenebilirlik altyapısını tanımlar. Kritik metrikler, alarm kuralları, pano tasarımı ve operasyon runbook bağlantılarını kapsar.

---

## 2. Metrik Kataloğu

### 2.1 Ölçüm Hattı Metrikleri

| Metrik | Tip | Açıklama | Kaynak |
|--------|:---:|----------|--------|
| `geolens_measure_jobs_total` | Counter | Üretilen toplam job sayısı | 0307 |
| `geolens_measure_jobs_duration` | Histogram | Job tamamlanma süresi | 0307 |
| `geolens_engine_requests_total` | Counter | Motor bazında istek sayısı | 0308 |
| `geolens_engine_errors_total` | Counter | Motor bazında hata sayısı | 0308 |
| `geolens_engine_latency` | Histogram | Motor yanıt süresi (ms) | 0308 |

### 2.2 Kuyruk Metrikleri

| Metrik | Tip | Açıklama | Kaynak |
|--------|:---:|----------|--------|
| `geolens_queue_depth` | Gauge | Redis Streams kuyruk derinliği | 0307 |
| `geolens_queue_dlq_count` | Gauge | DLQ'daki iş sayısı | 0307 |
| `geolens_queue_consumer_lag` | Gauge | Tüketici gecikmesi | 0307 |

### 2.3 Skor Metrikleri

| Metrik | Tip | Açıklama | Kaynak |
|--------|:---:|----------|--------|
| `geolens_score_calculated` | Counter | Hesaplanan skor sayısı | 0309 |
| `geolens_score_partial_total` | Counter | Kısmi yayın sayısı | 0309 |
| `geolens_score_determinism_check` | Gauge | Determinizm doğrulama sonucu (0/1) | 0309 |
| `geolens_score_ci_width` | Histogram | GA genişliği (puan) | 0309 |

### 2.4 Sistem Metrikleri

| Metrik | Tip | Açıklama |
|--------|:---:|----------|
| `geolens_http_request_duration` | Histogram | API yanıt süresi |
| `geolens_db_connections` | Gauge | PostgreSQL bağlantı sayısı |
| `geolens_cache_hit_ratio` | Gauge | Redis cache isabet oranı |
| `geolens_monthly_cost` | Gauge | Tahmini aylık maliyet ($) |

---

## 3. Kritik Alarmlar

### 3.1 Alarm Seti

| Alarm | Metrik | Eşik | Şiddet | Runbook |
|-------|--------|:----:|:------:|---------|
| İzolasyon Reddi | `geolens_db_rls_errors` | >0 | Kritik | Kiracı RLS politikasını kontrol et |
| Zincir Kopukluğu | `geolens_queue_consumer_lag` | >60s | Kritik | Worker'ı restart et |
| Determinizm Hatası | `geolens_score_determinism_check` | 0 | Yüksek | 0309 determinizm kuralını kontrol et |
| Bütçe Aşımı | `geolens_monthly_cost` | >$80 | Uyarı | Kullanımı kısıtla |
| DLQ Birikmesi | `geolens_queue_dlq_count` | >10 | Uyarı | DLQ'daki işleri manuel incele |
| Motor Hatası | `geolens_engine_errors_total` | >%5 | Yüksek | API anahtarlarını kontrol et |

### 3.2 Alarm Kanalları

| Kanal | Kritik | Yüksek | Uyarı |
|-------|:------:|:------:|:-----:|
| Slack (#geolens-alerts) | ✅ | ✅ | ✅ |
| E-posta (ops@geolens.ai) | ✅ | ✅ | — |
| SMS (acil durum) | ✅ | — | — |
| PagerDuty | ✅ | — | — |

---

## 4. Pano (Grafana)

### 4.1 Sistem Durumu Panosu

| Panel | Metrik | Tip |
|-------|--------|:---:|
| Ölçüm Hattı | job üretim/tüketim hızı | Zaman serisi |
| Motor Sağlığı | latency + error rate | Zaman serisi + gauge |
| Kuyruk Durumu | depth + DLQ + consumer lag | Stat + gauge |
| Skor Dağılımı | skor histogramı | Histogram |
| Maliyet | aylık cost tracker | Gauge |

### 4.2 Health Check Endpoint'i

```
GET /v1/health
{
  "status": "ok",
  "version": "1.0.0",
  "components": {
    "postgresql": "ok",
    "redis": "ok",
    "minio": "ok"
  },
  "uptime_seconds": 3600
}
```

---

## 5. Operasyon Runbook'ları

| Durum | Runbook |
|-------|---------|
| Worker durdu | `systemctl restart geolens-worker` → log kontrolü |
| Redis bağlantısı koptu | Redis servisini restart et → stream consistency doğrula |
| Motor hatası (API anahtarı) | `.env.secrets.enc`'de anahtarı kontrol et → `make decrypt-secrets` |
| DLQ birikmesi | `q:dead`'den örnek al → neden analizi → manuel yeniden işle |
| RLS hatası | `app.tenant_id` session değişkenini kontrol et → JWT tenant_id doğrula |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 25.07.2026 | İlk yayın: metrik kataloğu, alarm seti, panolar, runbook'lar |
