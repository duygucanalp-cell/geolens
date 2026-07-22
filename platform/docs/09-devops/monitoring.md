# İzleme ve Gözlem (Monitoring & Observability)

| Alan | Değer |
|---|---|
| Doküman ID | 09-devops/monitoring |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 09-devops/*, 0311, 0509, 0204, 0004 |

---

## 1. Amaç

Bu doküman GeoLens Platform izleme ve gözlem altyapısını tanımlar. OpenTelemetry ile metrik, log ve trace toplama stratejilerini detaylandırır.

---

## 2. Telemetri Türleri

| Tür | Araç | Veri |
|:---:|:----:|------|
| **Metrikler** | Prometheus | İşlem süresi, hata oranı, kuyruk derinliği |
| **Loglar** | JSON stdout | Yapılandırılmış log (correlation_id ile) |
| **Traces** | OpenTelemetry | Uçtan uca istek izleme |
| **Uptime** | Health check | /health ucu |

---

## 3. Kritik Metrikler (0311'den)

| Metrik | Açıklama | Alarm Eşiği |
|:------:|----------|:-----------:|
| API yanıt süresi (p50) | HTTP istek süresi | >1s |
| Ölçüm süresi | Motor çağrısı + skorlama | >60s |
| Kuyruk derinliği | Bekleyen iş sayısı | >100 |
| Hata oranı | 5xx yanıt oranı | >%1 |
| Motor hata oranı | Motor başına hata | >%5 |
| Worker sağlığı | Worker yanıt vermiyor | 1 dk sessizlik |
| Determinizm | Yeniden hesaplama uyuşmazlığı | 1 olay |

---

## 4. Log Yapısı

```json
{
  "level": "info",
  "time": "2026-07-22T12:00:00Z",
  "message": "Measurement completed",
  "correlation_id": "req-ulid",
  "tenant_id": "tenant-ulid",
  "job_id": "job-ulid",
  "duration_ms": 1234,
  "engine": "chatgpt",
  "status": "completed"
}
```

---

## 5. Health Check Uçları

| Uç | Açıklama | Beklenen |
|:---|----------|:--------:|
| GET /health | Temel sağlık | `{"status": "ok"}` |
| GET /health/ready | Bağımlılıklar hazır mı? | PG, Redis, S3 durumu |
| GET /health/live | Worker canlı mı? | Worker son ping zamanı |

---

## 6. Uyarı Kanalları

| Kanal | Kullanım | Öncelik |
|:-----:|----------|:-------:|
| **Slack** | Ekip içi operasyonel uyarılar | Yüksek |
| **E-posta** | On-call bildirimleri | Kritik |
| **PagerDuty** | (HT1) kritik durum yönetimi | Kritik |

---

## Kaynaklar

- 09-devops/backup — yedekleme stratejisi
- 0311 Observability — AVIP gözlem metrikleri
- 0509 Scalability — kapasite planlaması
- 0204 PRD — NFR-9 (performans hedefleri)
- 0004 Success Metrics — M8, M10, M11

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: telemetri türleri, kritik metrikler, log yapısı, health check, uyarı kanalları. |
