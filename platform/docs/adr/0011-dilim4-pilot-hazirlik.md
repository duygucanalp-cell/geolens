# ADR-011 · Pilot Hazırlık Kararları (Dilim 4)

| Alan | Değer |
|---|---|
| ADR ID | ADR-011 |
| Durum | Kabul |
| Tarih | 24.07.2026 |
| Karar veren | TL |
| İlişkili | ADR-012, project-plan §7, K1 Maliyet Modeli, Pilot Onboarding, 0206 |

---

## Bağlam

Dilim 4 (H13–H16) kapsamında pilot çıkış kapısı için operasyonel hazırlıklar yapılması gerekmiştir. ADR-012'de tanımlı 7 kriterden Kriter 2 (Performans), Kriter 4 (Maliyet Modeli) ve Kriter 6 (Pilot Referans) için aşağıdaki eksikler kapatılmıştır:

- Maliyet modeli dokümanı ve bütçe limiti
- API benchmark script'i ve performans baseline
- Pilot onboarding dokümanı ve süreci
- CI/CD pipeline sertleştirmesi (GitHub Actions)

---

## Kararlar

### K1: Maliyet Modeli ve Bütçe Limiti

| Öngörü | Gerçekleşen |
|--------|-------------|
| Maliyet modeli tanımlı değil | `0109-cost-model.md` — engine API maliyetleri + altyapı maliyet tahmini |

**Gerekçe:** Pilot öncesi bütçe limitini belirlemek için her engine'in istek başına maliyeti (Perplexity $0.005, OpenAI $0.003, Gemini $0.002) ve aylık altyapı maliyeti ($150-$250) modellenmiştir. `governance.rate_limit_buckets` ile aylık kota konfigüre edilebilir.

### K2: API Benchmark ve Performans Baseline

| Öngörü | Gerçekleşen |
|--------|-------------|
| Performans hedefleri tanımlı ama ölçülmemiş | `hey` benchmark script'i + Prometheus metrikleri + Grafana dashboard |

**Gerekçe:** NFR-9 (performans hedefleri) için p50/p95 yanıt süreleri benchmark ile kaydedilmiştir. Redis cache (30s TTL) ve ETag middleware ile pano <5s hedefi karşılanmıştır.

### K3: Pilot Onboarding Süreci

| Öngörü | Gerçekleşen |
|--------|-------------|
| Pilot onboarding tanımlı değil | Pilot onboarding dokümanı + kullanıcı kılavuzu + geri bildirim anketi |

**Gerekçe:** Pilot kiracıların (P2 dijital ajans, P3 marka yöneticisi) sistematik şekilde onboard edilmesi için adım adım süreç tanımlanmıştır. 4 hafta minimum, 8 hafta hedef süre belirlenmiştir.

### K4: CI/CD Pipeline Sertleştirmesi

| Öngörü | Gerçekleşen |
|--------|-------------|
| GitHub Actions temel workflow | Güvenlik tarama adımları (trivy, gosec) + SOPS decrypt + integration test aşaması eklendi |

**Gerekçe:** Pilot öncesi CI/CD pipeline'ına güvenlik tarama ve sır yönetimi adımları eklenerek üretim kalitesine yükseltilmiştir.

---

## Sonuçlar

- Maliyet modeli ile pilot bütçesi öngörülebilir hale gelmiştir
- Performans baseline ile NFR-9 doğrulanabilir durumdadır
- Pilot onboarding süreci tanımlanmıştır (P2/P3 kiracıları için)
- CI/CD pipeline'ı üretim standardına yükseltilmiştir

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 24.07.2026 | İlk yayın: Dilim 4 pilot hazırlık kararları |
