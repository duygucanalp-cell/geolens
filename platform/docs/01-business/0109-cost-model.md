# 0109 · K1 Maliyet Modeli

| Alan | Değer |
|------|-------|
| Doküman ID | 0109 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Review |
| Tarih | 04.08.2026 |

---

## 1. Amaç

Bu doküman, GeoLens Platform'un işletme maliyetini modeller. Pilot öncesi bütçe limitini belirlemek, engine API maliyetlerini hesaplamak ve altyapı maliyetlerini tahmin etmek için kullanılır.

---

## 2. Engine API Maliyetleri

### 2.1 Perplexity Sonar Pro

| Kalem | Değer |
|-------|-------|
| Model | sonar-pro |
| Fiyat | $5.00 / 1K istek |
| İstek başına maliyet | $0.005 |
| n=3 ile ölçüm başına | $0.015 |
| Aylık 1000 ölçüm | $15.00 |

### 2.2 ChatGPT (OpenAI)

| Kalem | Değer |
|-------|-------|
| Model | gpt-4o-search-preview |
| Fiyat (girdi) | $2.50 / 1M token |
| Fiyat (çıktı) | $10.00 / 1M token |
| Tahmini token/istek | 2.000 (girdi) + 500 (çıktı) |
| İstek başına maliyet | ~$0.01 |
| n=3 ile ölçüm başına | ~$0.03 |
| Aylık 1000 ölçüm | ~$30.00 |

### 2.3 Gemini (Google AI)

| Kalem | Değer |
|-------|-------|
| Model | gemini-3.5-pro |
| Fiyat (girdi) | $1.25 / 1M token |
| Fiyat (çıktı) | $5.00 / 1M token |
| Tahmini token/istek | 1.500 (girdi) + 400 (çıktı) |
| İstek başına maliyet | ~$0.004 |
| n=3 ile ölçüm başına | ~$0.012 |
| Aylık 1000 ölçüm | ~$12.00 |

### 2.4 Toplam Engine Maliyeti

| Senaryo | Ölçüm/Ay | Perplexity | ChatGPT | Gemini | Toplam |
|:-------:|:--------:|:----------:|:-------:|:------:|:------:|
| Pilot (tek kiracı) | 100 | $1.50 | $3.00 | $1.20 | **$5.70** |
| Küçük (10 kiracı) | 1.000 | $15.00 | $30.00 | $12.00 | **$57.00** |
| Orta (50 kiracı) | 5.000 | $75.00 | $150.00 | $60.00 | **$285.00** |
| Büyük (200 kiracı) | 20.000 | $300.00 | $600.00 | $240.00 | **$1,140.00** |

---

## 3. Altyapı Maliyetleri

### 3.1 Compute (Docker Host)

| Kaynak | Pilot | Küçük | Orta |
|--------|:-----:|:-----:|:----:|
| CPU | 2 cores | 4 cores | 8 cores |
| RAM | 4 GB | 8 GB | 16 GB |
| Aylık maliyet | ~$20 | ~$40 | ~$80 |

### 3.2 PostgreSQL

| Kaynak | Pilot | Küçük | Orta |
|--------|:-----:|:-----:|:----:|
| Storage | 10 GB | 50 GB | 100 GB |
| Connections | 20 | 50 | 100 |
| Aylık maliyet | ~$15 | ~$30 | ~$60 |

### 3.3 Redis

| Kaynak | Pilot | Küçük | Orta |
|--------|:-----:|:-----:|:----:|
| Memory | 256 MB | 512 MB | 1 GB |
| Aylık maliyet | ~$5 | ~$10 | ~$20 |

### 3.4 S3 (MinIO / Object Storage)

| Kaynak | Pilot | Küçük | Orta |
|--------|:-----:|:-----:|:----:|
| Storage | 5 GB | 50 GB | 200 GB |
| Aylık maliyet | ~$5 | ~$10 | ~$20 |

### 3.5 Monitoring (Prometheus + Grafana)

| Kaynak | Pilot | Küçük | Orta |
|--------|:-----:|:-----:|:----:|
| Storage | 5 GB | 20 GB | 50 GB |
| Aylık maliyet | ~$5 | ~$10 | ~$20 |

### 3.6 Toplam Altyapı Maliyeti

| Senaryo | Compute | DB | Redis | S3 | Monitoring | Toplam |
|:-------:|:-------:|:--:|:-----:|:--:|:----------:|:------:|
| Pilot | $20 | $15 | $5 | $5 | $5 | **$50** |
| Küçük | $40 | $30 | $10 | $10 | $10 | **$100** |
| Orta | $80 | $60 | $20 | $20 | $20 | **$200** |

---

## 4. Pilot Bütçe Hesabı

| Kalem | Aylık | 3 Aylık |
|-------|:-----:|:-------:|
| Engine API (3 motor MVP tabanı, 100 ölçüm/ay) | $5.70 | $17.10 |
| Altyapı (tek node) | $50.00 | $150.00 |
| SendGrid (ücretsiz tier — 100 e-posta/gün) | $0 | $0 |
| **Toplam** | **$55.70** | **$167.10** |

> **Not (v1.1):** Pilot bütçesi 3 motorlu MVP tabanına göredir. Üretimde 8 motor (7 adaptör + AI Mode, 0308 v1.3) vardır; motor başına gerçek maliyet `cost.entries` tablosundan (`032_cost_analytics.sql`, R11) izlenir ve ölçekle orantılı güncellenir.

### 4.1 Pilot Bütçe Limiti

- **Aylık limit:** $100 (güvenlik marjı ile)
- **3 aylık pilot bütçesi:** $300
- **Limit aşımı alarmı:** Aylık maliyet `cost.entries` toplamından hesaplanır (Prometheus gauge'u yoktur); `cost` şeması sorgusu veya 0311 maliyet izleme ile takip edilir

### 4.2 Rate Limit Konfigürasyonu

| Limit | Değer | Aşım Aksiyonu |
|-------|:-----:|:-------------:|
| Ölçüm/kiracı/gün | 50 | 429 Too Many Requests |
| Motor çağrısı/dakika | 30 | 429 + DLQ |
| E-posta/gün | 100 | Sessiz düşüş |

---

## 5. Maliyet Optimizasyonu

| Optimizasyon | Tasarruf | Uygulama |
|:-------------|:--------:|:---------|
| Mock mod (API anahtarı yokken) | %100 engine maliyeti | Zaten implemente |
| Redis cache (GET endpoint'leri) | ~%80 DB yükü | Zaten implemente |
| Ölçüm sıklığı azaltma | Orantılı | Panel schedule_cron ile |
| Batch motor çağrıları | ~%10 | Gelecek |
| S3 lifecycle policy (30 gün sonra sil) | ~%50 S3 | MinIO bucket policy ile |

---

## 6. Panel Modeli Öngörüsü

| Panel Tipi | Ölçüm Sıklığı | Aylık Ölçüm | Marka/Panel | Motor |
|:-----------|:-------------:|:-----------:|:-----------:|:-----:|
| Haftalık takip | Haftada 1 | ~4 | 1-5 | 3 (MVP tabanı) |
| Aylık rapor | Ayda 1 | ~1 | 5-20 | 3 (MVP tabanı) |
| Anlık ölçüm | Elle tetikleme | ~5 | 1 | 3 (MVP tabanı) |

**Pilot öngörüsü:** 2 panel × 3 marka × 3 motor × 4 hafta = **72 ölçüm/ay** (8 motorla üretimde ~192 ölçüm/ay; maliyet motor bazında `cost.entries`'ten izlenir)

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 24.07.2026 | İlk yayın: engine maliyetleri, altyapı maliyetleri, pilot bütçesi, rate limit konfigürasyonu |
| 1.1 | 04.08.2026 | **Kod gerçeği senkronu:** `geolens_monthly_cost` Prometheus metriği kodda olmadığından kaldırıldı; maliyet izleme gerçek `cost.entries` tablosuna (032/R11) bağlandı. Motor sayısı notu eklendi: pilot bütçesi 3 motorlu MVP tabanı, üretimde 8 motor (0308 v1.3). |
