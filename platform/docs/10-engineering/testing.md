# Test Stratejisi (Testing)

| Alan | Değer |
|---|---|
| Doküman ID | 10-engineering/testing |
| Proje | GeoLens Platform |
| Versiyon | 1.2 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 10-engineering/*, 09-devops/ci-cd, 0404, 0204 |

---

## 1. Amaç

Bu doküman GeoLens Platform test stratejisini tanımlar. Test piramidi yaklaşımıyla, birim → entegrasyon → E2E test kapsamını belirler.

---

## 2. Test Piramidi

```
         ╱╲
        ╱ E2E ╲
       ╱────────╲
      ╱ Entegrasyon ╲
     ╱────────────────╲
    ╱   Birim Test     ╲
   ╱──────────────────────╲
```

| Katman | Oran | Araç | Süre |
|:------:|:----:|:----:|:----:|
| **Birim** | %70 | go test, Vitest | <1 dk |
| **Entegrasyon** | %20 | testcontainers | <10 dk |
| **E2E** | %10 | Playwright | <15 dk |

---

## 3. Birim Test Kuralları

| Kural | Açıklama |
|:-----:|----------|
| **Kapsam** | Tüm servis katmanı, repository mock ile |
| **Mock** | Test doubles (mockery ile otomatik üretim) |
| **Tablo驱动** | Table-driven testler (Go) |
| **Her PR** | CI'da her push'ta çalışır |

### Kritik Test Edilecek Alanlar

| Alan | Test Odağı |
|:----:|------------|
| ScoringService | Determinizm testi (aynı girdi → aynı skor) |
| FidelityService | Kademe → etiket eşleme |
| AlertEvaluationService | Anlamlılık koşulları |
| PolicyFilterService | NG10 filtre kuralları |
| EntitlementChecker | Paket hakkı denetimi |

---

## 4. Entegrasyon Test Kuralları

| Araç | testcontainers (PG + Redis + S3 mock) |
|:----:|---------------------------------------|
| **Derleme etiketi** | `//go:build integration` |
| **Kapsam** | Repository katmanı, RLS politikaları |
| **İzolasyon** | Kiracılar arası veri sızıntısı negatif testleri |
| **Çalışma** | CI'da her PR (ayrı job) |

---

## 5. E2E Test Kuralları

| Araç | Playwright |
|:----:|------------|
| **Kapsam** | Kritik kullanıcı yolculukları (UC-01, UC-05, UC-06) |
| **Ortam** | Staging (gerçek API) |
| **Sıklık** | Her staging deploy'da |
| **Senaryolar** | Kayıt → marka ekle → ölçüm tetikle → skor gör |

---

## 6. Test Verisi Yönetimi

| Veri | Kaynak |
|:----:|--------|
| **Seed verisi** | migrations/seed (sistem şablonları) |
| **Test verisi** | Test içinde oluşturulur (setup/teardown) |
| **Anonim veri** | Üretim benzeri ama anonim |
| **Temizlik** | Her test kendi verisini temizler |

---

## 7. Kalite Metrikleri

| Metrik | Hedef |
|:------:|:-----:|
| Kritik paket kapsamı | ≥%70 [K] |
| Genel kapsam | ≥%50 [K] |
| Entegrasyon test kapsamı | Tüm kritik yollar |
| E2E test sayısı | 3-5 (MVP kritik yolculuklar) |
| Test süresi (tümü) | <15 dk |
| Flaky test oranı | <%1 |

---

### Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|----|-------|--------|
| **D-20** | **Kapsam eşikleri [K]:** Kritik paketler ≥%70, genel ≥%50. Pilot verisiyle kalibre edilecek. TL 21.07.2026. | AVIP 0404 O-1 |
| **D-21** | **Fixture tazeleme kadansı:** engine_meta sürüm kayması sinyaliyle tetikli + çeyreklik tam tarama. TL+AN 21.07.2026. | AVIP 0404 O-2 |
| **D-22** | **Yük duman testi:** MVP'de yok. Pilot verisi sonrasında yük profili çıkarılıp test edilecek. PO+TL 21.07.2026. | AVIP 0404 O-3 |
| **D-23** | **Mutasyon testi:** HT1'de değerlendirilecek. Pilot sonrası kritik paketlerde uygulama kararı. TL 21.07.2026. | AVIP 0404 O-4 |

## Kaynaklar

- 09-devops/ci-cd — CI pipeline
- 0404 Test Stratejisi — AVIP test referansı
- 0204 PRD — FR/NFR doğrulama gereksinimleri
- archive/avip-v1/0404-test-strategy.md

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: test piramidi, birim/entegrasyon/E2E test kuralları, test verisi yönetimi, kalite metrikleri. |
| 1.1 | 22.07.2026 | AVIP kapalı kararları taşındı: D-20 (coverage eşikleri), D-21 (fixture), D-22 (yük testi), D-23 (mutasyon). Devralınan Kararlar eklendi. |
| 1.2 | 22.07.2026 | D-20 hizalaması: §7 kapsam hedefi >%80 → ≥%70 kritik / ≥%50 genel olarak düzeltildi. |
