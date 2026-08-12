# 0422 · Pilot Hazırlık ve Kalibrasyon Runbook'u

| Alan | Değer |
|------|-------|
| Doküman ID | 0422 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Product |
| Tarih | 12 Ağustos 2026 |
| İlişkili | 0420 (İP-06), 0421 (A3/A4), 0404 (intent ağırlıkları), 0309 (§6.2 motor ağırlıkları), NFR-13 (diferansiyel gizlilik) |

---

## 1. Amaç

Bu doküman, pilot döneminde **env üzerinden yapılandırılabilir** hale getirilen
skor/kalibrasyon parametrelerinin nasıl kullanılacağını ve pilot verisiyle
doğrulanacak varsayımları tanımlar. Pilotun çıktısı, üretim (GA) parametrelerinin
kesinleştirilmesi için girdi sağlar.

Pilot öncesi kod/doküman seviyesinde tamamlanan tüm kalibrasyon noktaları:

| Parametre | Env | Varsayılan | Pilot senaryosu |
|-----------|-----|-----------|-----------------|
| Sektör benchmark eşiği (NFR-13) | `BENCHMARK_MIN_TENANTS` | 5 | Pilotta 2-3 kiracı ile demo; üretimde ≥5 |
| Intent ağırlık çarpanları (0421 A3-3) | `INTENT_WEIGHT_SCALE` | Dahili varsayılan tablo (0404 §4) | Pilot prompt'larının intent dağılımına göre çarpanlar güncellenir |
| Motor ağırlıkları (0309 §6.2) | `ENGINE_WEIGHTS` | Perplexity 0.30, ChatGPT 0.30, Gemini 0.25, AI Overview 0.10, diğerleri 0.05 | Pilotta hangi motorların müşteri sektöründe ağırlıklı olduğu görülür |
| Skor algoritması versiyonu | `SCORE_ALGORITHM_VERSION` | 2.0.0 (7 bileşenli VI) | 1.0.0 ile geri dönüş testi (regression karşılaştırması) |
| 7 bileşenli ağırlıklar | `SCORE_WEIGHTS` | %30/20/15/15/10/5/5 (AHP) | AHP çıktısı yerine pilot regresyonu ile yeniden kalibre edilir |
| LLM-as-Judge (0421 A2-5) | `GEOLENS_JUDGE_*` | Kapalı (kural tabanlı fallback) | Pilotta düşük eşikle (threshold=2) açılır, false-positive oranı ölçülür |
| ML serving | `ML_SERVING_URL` | Boş → kural tabanlı fallback | Serving ayağa kalkınca fallback davranışı devre dışı kalır; breaker metrikleri izlenir |

---

## 2. Pilot Kalibrasyon Akışı

### 2.1 Adım 1 — Veri Toplama

1. Pilot tenant'ları oluştur (≥2 tenant, ideal 5).
2. Her tenant için 2-3 marka tanımla ve ölçüm başlat.
3. Ölçümler `measure.calculation_runs`'a `algorithm_version=2.0.0` ile yazılır.
   `component_values` jsonb alanında 7 bileşen ayrı ayrı saklanır — bu, sonraki
   kalibrasyon için **ham bileşen skorlarını** sağlar.
4. `ml/data/export_unlabeled.py` ile gerçek motor cevapları gold etiketleme
   şablonuna export edilir; manuel etiketleme sonrası `validate_labeled.py` ile
   doğrulanıp `gold.jsonl`'a birleştirilir (IAA > %90 şartı — 0421 A1-2).

### 2.2 Adım 2 — Kalibrasyon Kararları

| Karar | Yöntem | Kabul Kriteri |
|-------|--------|---------------|
| Intent çarpanları | Pilot gold üzerinde intent bazlı hata analizi (0404 §4) | MAE düşüşü: v2 ağırlıklar eski 4 bileşenliye göre R² ≥ 0.80 (0421 A3-3 kabulü) |
| Motor ağırlıkları | Pilot sektöründe hangi motorun skoru en iyi tahmin ettiği (0309 §6.2) | weighted_average ile engine breakdown tutarlılığı |
| `BENCHMARK_MIN_TENANTS` | Kiracı sayısı pilot boyunca izlenir | Pilot sonunda ≥5 kiracıya ulaşıldıysa varsayılan korunur; değilse üretim açılışı bekler |
| Judge eşiği | Yüksek şüpheli örneklerde judge doğruluğu ölçülür (0421 A2-5) | Judge doğruluğu > kural tabanlı; false-positive < %10 |

### 2.3 Adım 3 — Regression Testi

Tüm env değişiklikleri sonrası:

```bash
make test                                  # Go birim testleri
cd ml && python -m pytest                  # ML testleri
python -m geolens.eval.main                # gold dataset değerlendirmesi
```

Sektör benchmark eşiği değişimi ayrıca canlı ortamda doğrulanır:

```bash
# BENCHMARK_MIN_TENANTS=2 ile
curl -s http://localhost:8081/metrics | grep benchmark  # aggregator logları
```

---

## 3. Pilot Ortam Kurulumu

```bash
cp .env.example .env

# Pilot kalibrasyonu (örnek)
BENCHMARK_MIN_TENANTS=2
INTENT_WEIGHT_SCALE="presence=1.25,1.00,0.90,0.90,1.10,0.90,0.90;comparison=0.90,1.00,0.90,1.40,0.90,0.90,1.30"
SCORE_ALGORITHM_VERSION=2.0.0
ML_SERVING_URL=http://ml-serving:8900
```

Servisler:

```bash
make dev               # altyapı (postgres, redis, minio, ml-serving)
go run ./cmd/api       # API :8080
go run ./cmd/worker    # worker (ölçüm + skor + ML analizler)
go run ./cmd/scheduler # scheduler :8082/metrics
```

Gözlem:

- `api:8080/metrics`, `worker:8081/metrics`, `scheduler:8082/metrics`
- `geolens_ml_breaker_in_cooldown` / `geolens_ml_breaker_failures_total`
  (serving down alarmı — 60s boyunca 1 ise Prometheus alert tetiklenir)
- Grafana: sektör benchmark paneli (benchmark.industry_stats)

---

## 4. Pilot Çıktı Raporu

Pilot sonunda aşağıdaki kararlar raporlanır:

1. Kesinleşen `SCORE_WEIGHTS` (AHP vs pilot regresyonu farkı)
2. Kesinleşen `INTENT_WEIGHT_SCALE` çarpanları
3. Kesinleşen `ENGINE_WEIGHTS` motor ağırlıkları
4. `BENCHMARK_MIN_TENANTS` üretim değeri (≥5)
5. Judge eşik ve model seçimi
6. Gold dataset büyüme raporu (kaç prompt, IAA skoru, model metriklerindeki değişim)
7. ML serving breaker istatistikleri (failover sayısı, kurtarma süresi)

Bu rapor 0421 A3-3/A3-5 kabul kriterlerinin GA öncesi kapanışı için PO onayına
sunulur.

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 12.08.2026 | İlk yayın: pilot kalibrasyon parametreleri (env'den yapılandırılabilir), kurulum ve çıktı raporu şablonu. |
