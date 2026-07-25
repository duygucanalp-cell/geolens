# 0306 · Alan Hizmetleri (Domain Services)

| Alan | Değer |
|---|---|
| Doküman ID | 0306 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0302, 0303, 0305, 0309, 0310, 0204 |

---

## 1. Amaç

Bu doküman, GeoLens Platform'daki alan hizmetlerini (domain services) tanımlar. Alan hizmetleri, bir toplam köküne veya varlığa ait olmayan, birden çok toplam veya bağlam arasında koordinasyon gerektiren iş mantığını kapsar. Her hizmet durumsuzdur (stateless) ve yan etkilerini alan olayları (0304) aracılığıyla bildirir.

> **Tasarım filtresi bağlantısı:** Bu doküman **F5** (moat — skorlama ve öneri hizmetleri rakip taklidini zorlaştıran algoritmik farklılaştırıcılardır) filtresine kanıt sağlar.

---

## 2. Hizmet Sınıflandırması

| Sınıf | Açıklama | Örnek |
|-------|----------|-------|
| **Skorlama hizmetleri** | Ham veriyi bileşik skora dönüştürür | ScoringService, FidelityService |
| **Koordinasyon hizmetleri** | Birden çok toplam/bağlam arası iş akışını yönetir | MeasurementOrchestrator, PanelVersionService |
| **Üretim hizmetleri** | Veriden yeni varlıklar türetir | RecommendationService, AlertEvaluationService |
| **Doğrulama hizmetleri** | İş kurallarını ve kısıtları denetler | PolicyFilterService, QuotaEnforcementService |
| **Dönüşüm hizmetleri** | Veriyi bir formattan diğerine dönüştürür | ReportGenerationService, BenchmarkAggregationService |

---

## 3. Hizmet Kataloğu

### 3.1 Skorlama Hizmetleri

#### ScoringService (BC3 · Measure)

| Alan | Değer |
|------|-------|
| **Sorumluluk** | Ham AI yanıtlarından 4 bileşenli görünürlük skoru hesaplar |
| **Girdi** | Ham yanıtlar, alıntılar, panel versiyonu, faktör yapılandırması |
| **Çıktı** | Skor (0-100), güven aralığı, fidelite etiketi, motor kırılımı |
| **Bağımlılık** | PanelVersionRepository, RawResponseRepository |
| **Kullanım** | MeasurementOrchestrator tarafından çağrılır |
| **Not** | Deterministik: aynı girdi → aynı skor (NFR-7). Detaylı algoritma: 0309 |

#### FidelityService (BC3 · Measure)

| Alan | Değer |
|------|-------|
| **Sorumluluk** | Her skor için fidelite etiketi üretir; kademe (1/2/3) ve güven düzeyini belirler |
| **Girdi** | Motor adı, erişim yöntemi, örnekleme sayısı (n) |
| **Çıktı** | Fidelite etiketi (kademe + motor + n + güven aralığı) |
| **Kullanım** | ScoringService tarafından çağrılır |
| **Not** | Etiketsiz skor yayınlanamaz (FR-C5, İ2) |

#### TrendService (BC3 · Measure)

| Alan | Değer |
|------|-------|
| **Sorumluluk** | Zaman serisi trend noktalarını yönetir; panel versiyonu sınırında işaret koyar |
| **Girdi** | Skor, marka, panel versiyonu, zaman |
| **Çıktı** | Trend noktası (zaman serisine eklenir) |
| **Kullanım** | ScoreCalculated olayıyla tetiklenir |
| **Not** | Panel versiyonu değişiminde seri birleştirilmez; görünür işaret eklenir |

### 3.2 Koordinasyon Hizmetleri

#### MeasurementOrchestrator (BC3 · Measure)

| Alan | Değer |
|------|-------|
| **Sorumluluk** | Ölçüm işinin tüm yaşam döngüsünü koordine eder: motor çağrısı, ham yanıt saklama, skorlama |
| **Girdi** | MeasurementJob |
| **Çıktı** | CalculationRun, MeasurementJobCompleted/Partial/Failed olayı |
| **Kullandığı hizmetler** | EngineDispatcher, ScoringService, FidelityService, RawResponseStore |
| **İş akışı** | Motor çağrısı → ham yanıt → skorlama → olay üretimi |
| **Not** | MVP'de sıralı (3 motor); HT1'de paralel motor çağrısı opsiyonu |

#### EngineDispatcher (BC3 · Measure — internal/engines)

| Alan | Değer |
|------|-------|
| **Sorumluluk** | Kayıtlı motor bağdaştırıcılarına çağrı yapar; hata yönetimi ve yeniden deneme sağlar |
| **Girdi** | Prompt seti, motor listesi, panel yapılandırması |
| **Çıktı** | Ham AI yanıtları (her motor için) |
| **Kullandığı hizmetler** | EngineRegistry (kayıt defteri), bağdaştırıcılar |
| **Not** | NFR-10 (hata yönetimi): sınırlı yeniden deneme, kısmi sonuç etiketleme |

#### PanelVersionService (BC2 · Config + BC3 · Measure)

| Alan | Değer |
|------|-------|
| **Sorumluluk** | Prompt seti, motor kapsamı veya pazar değiştiğinde yeni panel versiyonu üretir |
| **Girdi** | Değişiklik olayı (PromptSetChanged, EngineScopeChanged) |
| **Çıktı** | Yeni PanelVersion; PanelVersionCreated olayı |
| **Not** | Değişmez: panel versiyonu oluştuktan sonra değiştirilemez (I4) |

### 3.3 Üretim Hizmetleri

#### RecommendationService (BC4 · Insight)

| Alan | Değer |
|------|-------|
| **Sorumluluk** | Skor ve bulgulardan kanıt derecesi etiketli öneriler üretir |
| **Girdi** | Skor değişimi, site denetimi bulguları, kural kütüphanesi |
| **Çıktı** | Öneri (Recommendation); PolicyFilterService'ten geçmiş |
| **Kullandığı hizmetler** | PolicyFilterService, RecommendationRuleLibrary |
| **Not** | MVP'de kural tabanlı; HT1'de etki takibi ile genişler (FR-E4) |

#### AlertEvaluationService (BC5 · Delivery)

| Alan | Değer |
|------|-------|
| **Sorumluluk** | Skor değişimlerini uyarı kurallarıyla eşleştirir; anlamlılık testini uygular |
| **Girdi** | ScoreSignificantChange olayı, AlertRule |
| **Çıktı** | Uyarı (Alert) — yalnız anlamlılık kuralını geçen değişimden (I8) |
| **Not** | FR-F1: istatistiksel anlamlılık, aynı gün tetik birleştirme, yanlış alarm geri bildirimi |

#### DigestService (BC5 · Delivery)

| Alan | Değer |
|------|-------|
| **Sorumluluk** | Haftalık e-posta özetini üretir; derin bağlantılarla panoya yönlendirir |
| **Girdi** | Haftalık skor özeti, son uyarılar, açık öneriler |
| **Çıktı** | Digest (e-posta içeriği + derin bağlantılar) |
| **Not** | FR-F3: haftalık periyot, panoya derin bağlantılar |

#### ReportGenerationService (BC5 · Delivery)

| Alan | Değer |
|------|-------|
| **Sorumluluk** | PDF rapor üretir; white-label şablon uygular (ajans logosu/renkleri) |
| **Girdi** | Skorlar, marka ayarları, rapor şablonu |
| **Çıktı** | PDF (S3'e yazılır), imzalı URL |
| **Kullandığı hizmetler** | S3 storage, PDF render motoru |
| **Not** | Yalnız etiketli skorlardan üretilir (I11). FR-F4, FR-G2 |

### 3.4 Doğrulama Hizmetleri

#### PolicyFilterService (BC4 · Insight)

| Alan | Değer |
|------|-------|
| **Sorumluluk** | Önerilerin motor politikalarına aykırı olup olmadığını denetler |
| **Girdi** | Öneri adayı |
| **Çıktı** | Geçti/kaldı; kalan öneri gerekçesiyle işaretlenir |
| **Not** | İ7: aykırı öneri kalıcılaştırılamaz; üretim önkoşuludur |

#### QuotaEnforcementService (BC6 · Governance)

| Alan | Değer |
|------|-------|
| **Sorumluluk** | Motor çağrısı, ölçüm, rapor gibi işlemlerde kota kontrolü yapar |
| **Girdi** | İşlem türü, kiracı, sayaç durumu |
| **Çıktı** | İzin ver / reddet; aşımda QuotaExceeded olayı |
| **Not** | NFR-16: kota, hız sınırı, bütçe tavanı. I9: aşımda motor çağrısı engellenir |

#### SiteAuditService (BC3 · Measure)

| Alan | Değer |
|------|-------|
| **Sorumluluk** | Site erişilebilirlik, bot izinleri ve SSR denetimini yürütür |
| **Girdi** | Site URL |
| **Çıktı** | Bulgu listesi (kategori + önem + düzeltme önerisi) |
| **Not** | FR-B4: süre hedefi <30 saniye |

### 3.5 Dönüşüm Hizmetleri

#### ExportService (BC5 · Delivery)

| Alan | Değer |
|------|-------|
| **Sorumluluk** | Skor ve alıntı verilerini CSV/PDF biçiminde dışa aktarır |
| **Girdi** | Sorgu parametreleri (marka, tarih aralığı, motor) |
| **Çıktı** | CSV veya PDF dosyası |
| **Not** | FR-F7: temel dışa aktarım |

#### BenchmarkAggregationService (BC4 · Insight — HT2)

| Alan | Değer |
|------|-------|
| **Sorumluluk** | Anonim toplulaştırılmış benchmark istatistiği üretir (≥5 kiracı) |
| **Girdi** | Skor havuzu |
| **Çıktı** | Benchmark istatistiği (ortalama, medyan, çeyreklik) |
| **Not** | NFR-13: ≥5 kiracı eşiği. HT2'de devreye girer |

---

## 4. Hizmet-Bağlam Eşlemesi

| Hizmet | Bağlam | Tip | MVP |
|--------|:------:|:---:|:---:|
| ScoringService | BC3 Measure | Skorlama | ✅ |
| FidelityService | BC3 Measure | Skorlama | ✅ |
| TrendService | BC3 Measure | Skorlama | ✅ |
| MeasurementOrchestrator | BC3 Measure | Koordinasyon | ✅ |
| EngineDispatcher | BC3 Measure | Koordinasyon | ✅ |
| PanelVersionService | BC2/BC3 | Koordinasyon | ✅ |
| RecommendationService | BC4 Insight | Üretim | 🟡 (kural tabanlı) |
| AlertEvaluationService | BC5 Delivery | Üretim | ✅ |
| DigestService | BC5 Delivery | Üretim | ✅ |
| ReportGenerationService | BC5 Delivery | Üretim | ✅ |
| PolicyFilterService | BC4 Insight | Doğrulama | ✅ |
| QuotaEnforcementService | BC6 Governance | Doğrulama | ✅ |
| SiteAuditService | BC3 Measure | Doğrulama | 🟡 (daraltılmış) |
| ExportService | BC5 Delivery | Dönüşüm | ✅ |
| BenchmarkAggregationService | BC4 Insight | Dönüşüm | 🔴 (HT2) |

---

## 5. Hizmet Bağımlılık Grafiği

```
MeasurementOrchestrator
  ├── EngineDispatcher
  │     └── EngineRegistry (kayıt defteri)
  ├── RawResponseStore
  ├── ScoringService
  │     ├── FidelityService
  │     └── PanelVersionRepository
  └── EventPublisher (outbox)

RecommendationService
  ├── PolicyFilterService
  ├── SkorRepository
  └── RecommendationRuleLibrary

AlertEvaluationService
  ├── AlertRuleRepository
  └── StatisticalSignificanceTest
```

> **Kural:** Hizmetler durumsuzdur (stateless). Tüm durum, bağlı oldukları repository'lerde saklanır.

---

## 6. GeoLens İçin Çıkarımlar

1. **15 alan hizmeti** tanımlanmıştır. 12'si MVP'de tam, 2'si daraltılmış, 1'i HT2'de devreye girer.
2. **ScoringService ve FidelityService** GeoLens'in en kritik algoritmik farklılaştırıcılarıdır. Detaylı tasarım 0309'da (Scoring Engine) yapılır.
3. **MeasurementOrchestrator** en karmaşık koordinasyon hizmetidir: üç motor çağrısı, hata yönetimi, kısmi sonuç, skorlama zinciri.
4. **PolicyFilterService** motor politikalarına uyumu garanti eder. Bu hizmet olmadan hiçbir öneri kullanıcıya gösterilmez (I7, FR-E2).
5. **Hizmetler durumsuzdur.** Ölçekleme için worker replika sayısı artırılabilir; hizmetler arasında paylaşılan durum yoktur.
6. **0309 (Scoring Engine)** ScoringService ve FidelityService'in algoritmik detayını verir.

---

## 7. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | BenchmarkAggregationService anonimleştirme yöntemi | ⏳ HT2'de karara bağlanır; NFR-13. |
| O-2 | StatisticalSignificanceTest eşik değerleri | ⏳ 0309 ile kalibre edilecek. AVIP D-31 (anlamlılık eşikleri) devralındı. |
| O-3 | Öneri-etki takibi hizmet modeli | ⏳ Ön hipotez: RecommendationService'e ek (HT1). |

### Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|----|-------|--------|
| **D-36** | **Çalışma alanı path modeli:** /v1/workspaces/(ws)/... TL 21.07.2026. | AVIP 0306 O-1 |
| **D-83** | **Derin bağlantı token ömrü:** 7 gün + tek kullanım. TL 21.07.2026. | AVIP 0306 O-2 |
| **D-37** | **OpenAPI üreteç:** oapi-codegen. TL 21.07.2026. | AVIP 0306 O-4 |
| **D-31** | **Anlamlılık eşikleri:** Mutlak fark ≥5 puan, %95 GA. TL 21.07.2026. | AVIP 0309 O-1 |

---

## Kaynaklar

- 0302 Domain Model — varlıklar, bağlamlar
- 0303 Aggregates — toplam kökleri, transaction sınırları
- 0304 Domain Events — olay tetikleyicileri
- 0305 Bounded Contexts — bağlam-paket eşlemesi
- 0309 Scoring Engine — skorlama ve hesaplama detayı
- 0204 PRD — FR/NFR bağları
- archive/avip-v1/README.md — AVIP arşiv indeksi

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: 15 alan hizmeti, 5 sınıf (skorlama/koordinasyon/üretim/doğrulama/dönüşüm), hizmet kataloğu, bağlam eşlemesi, bağımlılık grafiği. 0302-0305'ten türetilmiştir. |
