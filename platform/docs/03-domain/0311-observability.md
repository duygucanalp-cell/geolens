# 0311 · Gözlemlenebilirlik (Observability)

| Alan | Değer |
|---|---|
| Doküman ID | 0311 |
| Proje | GeoLens Platform |
| Versiyon | 1.2 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 04 Ağustos 2026 |
| İlişkili | 0304, 0415, 0419, 0904, 0606, 0204, 0905, platform/metrics |

---

## 1. Amaç

Bu doküman, GeoLens Platform'un gözlemlenebilirlik altyapısını tanımlar. Kritik metrikler, alarm kuralları, pano tasarımı ve operasyon runbook bağlantılarını kapsar.

---

## 2. Metrik Kataloğu

Metrik adları `platform/metrics/metrics.go` kaynak doğruluk noktasıdır (Prometheus promauto). Etiketler: `method`, `path`, `status`, `engine`, `tenant`, `stream`, `severity`, `gap_type`.

### 2.1 API Metrikleri

| Metrik | Tip | Etiketler | Açıklama |
|--------|:---:|-----------|----------|
| `geolens_http_requests_total` | Counter | method, path, status | Toplam HTTP istek sayısı |
| `geolens_http_request_duration_seconds` | Histogram | method, path | HTTP yanıt süresi dağılımı |
| `geolens_http_requests_in_flight` | Gauge | — | Anlık işlenmekte olan istek sayısı |

### 2.2 Kuyruk Metrikleri

| Metrik | Tip | Etiketler | Açıklama | Kaynak |
|--------|:---:|-----------|----------|:---:|
| `geolens_queue_messages_produced_total` | Counter | stream | Kuyruğa eklenen mesaj sayısı | 0307 |
| `geolens_queue_messages_consumed_total` | Counter | stream | Kuyruktan okunan mesaj sayısı | 0307 |
| `geolens_queue_messages_failed_total` | Counter | stream | İşlenemeyen mesaj sayısı | 0307 |
| `geolens_queue_processing_duration_seconds` | Histogram | stream | Mesaj işleme süresi dağılımı | 0307 |
| `geolens_queue_depth` | Gauge | stream | Kuyruk derinliği (XLEN) | 0307 |
| `geolens_queue_dead_letter_size` | Gauge | stream | DLQ boyutu | 0307 |

### 2.3 Motor Metrikleri

| Metrik | Tip | Etiketler | Açıklama | Kaynak |
|--------|:---:|-----------|----------|:---:|
| `geolens_engine_calls_total` | Counter | engine, tenant | Motor çağrı sayısı | 0308 |
| `geolens_engine_calls_failed_total` | Counter | engine, tenant | Başarısız motor çağrısı | 0308 |
| `geolens_engine_call_duration_seconds` | Histogram | engine | Motor çağrı süresi dağılımı | 0308 |
| `geolens_engine_response_size_bytes` | Histogram | engine | Motor yanıt boyutu | 0308 |

### 2.4 Hesap/İş Metrikleri (periyodik snapshot, 5 dk)

| Metrik | Tip | Etiketler | Açıklama |
|--------|:---:|-----------|----------|
| `geolens_active_users` | Gauge | tenant | Aktif kullanıcı sayısı |
| `geolens_total_brands` | Gauge | tenant | Toplam marka sayısı |
| `geolens_measurements_completed` | Gauge | tenant | Tamamlanan ölçüm sayısı |
| `geolens_audits_completed` | Gauge | tenant | Tamamlanan denetim sayısı |
| `geolens_recommendations_generated` | Gauge | tenant | Üretilen öneri sayısı |
| `geolens_emails_sent` | Gauge | tenant | Gönderilen e-posta sayısı |

### 2.5 AI Analiz Metrikleri (0416-0419)

| Metrik | Tip | Etiketler | Açıklama |
|--------|:---:|-----------|----------|
| `geolens_sentiment_analyses_completed_total` | Counter | tenant | Tamamlanan duygu analizi |
| `geolens_hallucinations_detected_total` | Counter | tenant, severity | Tespit edilen hallüsinasyon |
| `geolens_conversation_snapshots_total` | Counter | tenant | Oluşturulan replay snapshot |
| `geolens_response_archive_entries_total` | Counter | tenant | Arşivlenen yanıt girişi |
| `geolens_technical_geo_analyses_total` | Counter | tenant | Teknik GEO analizi |
| `geolens_content_geo_analyses_total` | Counter | tenant | İçerik GEO analizi |
| `geolens_competitive_gap_analyses_total` | Counter | tenant | Competitive gap analizi |
| `geolens_gap_alerts_triggered_total` | Counter | gap_type | Tetiklenen gap uyarısı |

---

## 3. Kritik Alarmlar

### 3.1 Alarm Seti

| Alarm | Metrik | Eşik | Şiddet | Runbook |
|-------|--------|:----:|:------:|---------|
| İzolasyon Reddi (RLS) | `geolens_http_requests_total{status="500"}` | >0 sürekli | Kritik | Kiracı RLS politikasını / `app.tenant_id` session değişkenini kontrol et |
| Kuyruk Birikimi (worker durdu) | `geolens_queue_depth` | >eşik & tüketim durmuş | Kritik | Worker'ı restart et |
| Motor Hatası | `geolens_engine_calls_failed_total / geolens_engine_calls_total` | >%5 | Yüksek | API anahtarlarını kontrol et |
| Mesaj İşleme Hatası | `geolens_queue_messages_failed_total` | artış | Yüksek | Hata logunu incele |
| DLQ Birikmesi | `geolens_queue_dead_letter_size` | >10 | Uyarı | DLQ'daki işleri manuel incele |
| API Hata Oranı | `geolens_http_requests_total{status="5xx"}` | >%1 | Uyarı | Log/handler kontrolü |

### 3.2 AI Görünürlük Alarmları (0415 ve 0419 ile Entegrasyon)

Platform gözlemlenebilirliğinin yanı sıra, **AI düzeyinde gözlemlenebilirlik** aşağıdaki dokümanlarda tanımlanmıştır:

| Doküman | Kapsam | Alarm Türü |
|:-------:|--------|:----------:|
| **0415 AI Observability** | Görünürlük skoru düşüşü, citation kaybı, rakip artışı, negatif sentiment, hallüsinasyon, yeni kaynak | 6 uyarı türü (FR-D13) |
| **0419 Competitive Gap** | Visibility/Citation/Content/Topic/Prompt gap eşik aşımı | 5 gap türü × alert eşiği |

Bu alarmlar, §3.1'deki platform alarmlarına ek olarak çalışır ve aynı kanallara (Slack, e-posta) iletilir.

### 3.3 Alarm Kanalları

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
| 1.2 | 04.08.2026 | **Metrik senkronu:** §2 metrik kataloğu `platform/metrics/metrics.go` gerçeğiyle yeniden yazıldı (API/kuyruk/motor/hesap/AI analiz alt bölümleri). Kodda olmayan metrikler kaldırıldı: `geolens_measure_jobs_total`, `geolens_engine_latency`, `geolens_queue_consumer_lag`, `geolens_score_*`, `geolens_db_connections`, `geolens_monthly_cost`. AI analiz metrikleri (0416-0419) eklendi. §3.1 alarm seti gerçek metrik adlarıyla güncellendi. |
| 1.1 | 27.07.2026 | 0419 (Competitive Gap) referansı eklendi. AI düzeyinde gözlemlenebilirlik entegrasyonu: 0415 ve 0419 alarm türleri platform kanallarına bağlandı (§3.2). İlişkili ve Kaynaklar güncellendi. |
| 1.0 | 25.07.2026 | İlk yayın: metrik kataloğu, alarm seti, panolar, runbook'lar |
