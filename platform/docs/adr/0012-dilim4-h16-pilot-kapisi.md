# ADR-012 · Dilim 4 (H16) — Pilot Çıkış Kapısı Planı

| Alan | Değer |
|------|-------|
| ADR ID | ADR-012 |
| Durum | Approved |
| Tarih | 24.07.2026 |
| Karar veren | TL |
| İlişkili | project-plan §7, 0205-mvp §8, ADR-009, ADR-010, ADR-011 |

---

## Bağlam

Dilim 4'ün son hipotezi (H16) — Pilot çıkış kapısı. Proje planına göre (project-plan.md §7) pilot çıkış kapısı, 0205-mvp.md §8'de tanımlı **7 kriterin tamamının yeşil** olmasını gerektirir.

Bu doküman, her kriterin mevcut durumunu değerlendirir, eksikleri analiz eder ve kapatma planını tanımlar.

---

## 7 Kriter Detaylı Analizi

### Kriter 1: Sert kural ihlali sıfır — NFR-1, NFR-6, NFR-7

| NFR | Açıklama | Mevcut Durum | Eksik |
|:---:|----------|:------------:|:-----:|
| **NFR-1** | Kiracı izolasyonu — tüm veri erişimi kiracı bağlamında | ✅ Tüm tablolarda RLS, `identity.get_tenant_id()` ile tenant context middleware | Yok |
| **NFR-6** | Değişmez denetim izi — tüm yazma işlemleri denetim kaydına | ✅ `governance.audit_log` tablosu, `INSERT ONLY` policy | Denetim izi görünümü (FR-H2) MVP dışı |
| **NFR-7** | Deterministik yeniden hesap — aynı girdi → aynı skor | ✅ `calculation_run` tablosu, `PanelVersion`, `AlgorithmVersion`, `temp=0`, n=3 örnekleme | Partial yayın durumunda determinizm testi |

**Değerlendirme:** 🟢 Büyük ölçüde tamam. NFR-7 için partial yayın durumunda determinizm doğrulama testi eklenebilir.

**Kapatma için gerekenler:**
- [x] RLS tüm tablolarda aktif
- [x] Tenant context middleware tüm route'larda
- [x] calculation_run kaydı (algorithm_version "1.0.0")
- [x] Temperature=0 tüm engine adapter'larında
- [x] Sample_count yapılandırılabilir (env var, default 3)
- [ ] Partial yayın determinizm testi (H15'te kısmen yapıldı)

---

### Kriter 2: Kalibre edilen performans hedefleri (NFR-9)

| Hedef | Mevcut Durum | Eksik |
|-------|:------------:|:-----:|
| Pano <5s (p50) | ✅ Redis cache middleware (30s TTL) | Benchmark yok |
| API <1s (p50) | ❌ Ölçülmemiş | Benchmark testi gerekli |
| Ölçüm <60s | ❌ Ölçülmemiş (mock engine <1s, gerçek API değişken) | Benchmark testi gerekli |

**Değerlendirme:** 🟡 Kısmen tamam. Cache stratejisi var (Redis + ETag). Ancak hiçbir performans benchmark'ı yok.

**Kapatma için gerekenler:**
- [x] Redis cache middleware (GET endpoint'lerinde)
- [x] Prometheus metrikleri (`geolens_http_request_duration_seconds`)
- [ ] API benchmark script'i (ör: `hey` veya `wrk` ile)
- [ ] Pano yanıt süresi ölçümü (Grafana dashboard ile)
- [ ] Ölçüm süresi izleme (worker metrikleri ile)

---

### Kriter 3: P2 ve P3 persona kartları saha verisiyle doğrulandı

| Persona | Açıklama | Mevcut Durum | Eksik |
|:-------:|----------|:------------:|:-----:|
| **P2** | Dijital ajans yöneticisi | ❌ Ajans görüşmeleri yapılmamış | Pilot onboarding + geri bildirim |
| **P3** | Kurum içi marka yöneticisi | ❌ Görüşme yapılmamış | Pilot onboarding + geri bildirim |

**Değerlendirme:** 🔴 Tamamlanmamış. Pilot kiracı bulunmadığı için saha verisi toplanamamış. Bu kriter pilot başladıktan **sonra** doğrulanabilir.

**Kapatma için gerekenler:**
- [ ] En az 1 P2 ve 1 P3 pilot kiracısı bul
- [ ] Onboarding dokümanı hazırla
- [ ] Kullanıcı kılavuzu hazırla
- [ ] İlk geri bildirim turu tamamla

---

### Kriter 4: K1 maliyet gerçekleşmesi panel modeli öngörüsüyle uyumlu

| Alan | Mevcut Durum | Eksik |
|------|:------------:|:-----:|
| Maliyet modeli tanımı | ❌ Tanımlanmamış | Maliyet modeli dokümanı gerekli |
| Panel modeli öngörüsü | ❌ Tanımlanmamış | Panel modeli dokümanı gerekli |
| Gerçek maliyet verisi | ❌ Pilot kiracı yok | Pilot sonrası toplanabilir |

**Değerlendirme:** 🔴 Pilot öncesi tanımlanması gereken bir model. Maliyet hesaplaması için engine API maliyetleri (Perplexity, OpenAI, Gemini) ve altyapı maliyetleri (PostgreSQL, Redis, S3, Compute) modellenmeli.

**Kapatma için gerekenler:**
- [ ] Maliyet modeli dokümanı oluştur (engine maliyeti / ölçüm)
- [ ] Altyapı maliyet tahmini (aylık)
- [ ] Pilot bütçe limiti belirle
- [ ] Kota ve hız sınırı konfigürasyonu (`governance.rate_limit_buckets`)

---

### Kriter 5: Motor kapsamı üretimde karara uygun çalışıyor

| Motor | Durum | Detay |
|-------|:-----:|-------|
| **Perplexity** | ✅ Implemente | Sonar API, mock mod, temperature=0 |
| **ChatGPT (OpenAI)** | ✅ Implemente | Responses API, mock mod, temperature=0 |
| **Gemini** | ✅ Implemente | Gemini API, Google Search grounding, temperature=0 |

**Değerlendirme:** 🟢 3 motor da implemente. Hepsi mock modda çalışıyor. Gerçek API anahtarlarıyla canlı test yapılmamış (beklenen — pilot aşaması).

**Kapatma için gerekenler:**
- [x] Perplexity adapter (mock + gerçek)
- [x] ChatGPT adapter (mock + gerçek)
- [x] Gemini adapter (mock + gerçek)
- [x] Temperature=0 (deterministik çıktı)
- [ ] Canlı API testi (pilot onboarding ile)
- [ ] Engine hata yönetimi testi (NFR-10)

---

### Kriter 6: Pilot kiracılarından referans sinyali

| Sinyal | Açıklama | Mevcut Durum |
|:------:|----------|:------------:|
| P3 referansı | En az 1 P3 kiracıdan olumlu referans | ❌ Pilot henüz başlamadı |
| P2 referansı | En az 1 P2 kiracıdan olumlu referans | ❌ Pilot henüz başlamadı |

**Değerlendirme:** 🔴 Pilot başlamadan sağlanamaz. Pilot onboarding planı hazırlanmalı.

**Kapatma için gerekenler:**
- [ ] Pilot kiracı listesi oluştur
- [ ] Onboarding süreci tanımla
- [ ] Referans toplama anketi hazırla
- [ ] İlk pilot haftası sonunda referansları topla

---

### Kriter 7: Güvenlik kapanışı

| Alan | Mevcut Durum | Eksik |
|------|:------------:|:-----:|
| RBAC matrisi | ✅ 3 rol (admin/editor/viewer) | Yok |
| RLS politikaları | ✅ Tüm tablolarda | Yok |
| Girdi validasyonu | ✅ Content-Type, MaxBodySize, ValidateContentType | Yok |
| Sır yönetimi | 🔴 SOPS+Age seçildi ama implemente edilmedi | Kasa entegrasyonu |
| KVKK veri silme | ❌ Implemente edilmedi | FR-D4, NFR-12 |
| Veri şifreleme (beklemede) | 🔴 TLS var, AES-256 yok | NFR-5 |
| Güvenlik header'ları | ✅ SecureHeaders middleware | Yok |
| Kripto-silme | ❌ H13'te planlandı ama implemente edilmedi | S3 şifreleme |
| Güvenlik testleri | ❌ Hiçbiri yapılmadı | RBAC testi, izolasyon testi, sızma testi |

**Değerlendirme:** 🟡 Kısmen tamam. Temel güvenlik önlemleri var (RLS, RBAC, TLS, header'lar). Ancak H13'te planlanan kripto-silme, sır yönetimi ve güvenlik testleri yapılmamış.

**Kapatma için gerekenler:**
- [x] RLS politikaları (tüm tablolar)
- [x] RBAC (3 rol)
- [x] SecureHeaders middleware
- [x] Girdi validasyonu
- [x] TLS (varsayılan)
- [ ] SOPS+Age sır yönetimi entegrasyonu
- [ ] S3 şifreleme (kripto-silme altyapısı)
- [ ] KVKK veri silme endpoint'i
- [ ] RBAC matrisi testleri
- [ ] İzolasyon negatif testleri
- [ ] Sızma testi (ilk tur)

---

## Genel Durum Özeti

| # | Kriter | Durum | Çaba Tahmini |
|:-:|--------|:-----:|:------------:|
| 1 | NFR-1, NFR-6, NFR-7 (sert kural ihlali) | 🟢 Büyük ölçüde tamam | 1 gün |
| 2 | Performans hedefleri (NFR-9) | 🟡 Kısmen tamam | 2 gün |
| 3 | P2/P3 persona doğrulama | 🔴 Pilot sonrası | Pilot başlangıcı |
| 4 | K1 maliyet modeli | 🔴 Tanımlanmamış | 2 gün |
| 5 | Motor kapsamı | 🟢 Tamam | 0 gün |
| 6 | Pilot referans sinyali | 🔴 Pilot sonrası | Pilot başlangıcı |
| 7 | Güvenlik kapanışı | 🟡 Kısmen tamam | 5 gün |

### Öncelikli Eylemler (Pilot Öncesi)

| # | Eylem | İlgili Kriter | Tahmini Çaba |
|:-:|-------|:-------------:|:------------:|
| 1 | SOPS+Age sır yönetimi entegrasyonu | K7 (güvenlik) | 2 gün |
| 2 | KVKK veri silme endpoint'i | K7 (güvenlik) | 1 gün |
| 3 | Maliyet modeli dokümanı | K4 | 1 gün |
| 4 | RBAC + izolasyon negatif testleri | K7 (güvenlik) | 2 gün |
| 5 | API benchmark script'i | K2 (performans) | 1 gün |
| 6 | Performance baseline (Grafana) | K2 (performans) | 1 gün |
| 7 | Pilot onboarding dokümanı | K3, K6 | 2 gün |

### Pilot Başlangıcı İçin Asgari Kriterler (MVP Kapısı)

H16 çıkış kapısı için **aşağıdaki kriterlerin yeşil olması yeterlidir** (kalan kriterler pilot sırasında doğrulanır):

1. ✅ NFR-1, NFR-6, NFR-7 sağlanıyor (kısmi determinizm testi ile)
2. 🟡 Redis cache aktif, performans baseline alınmış
3. ✅ 3 motor da implemente ve mock modda çalışıyor
4. 🟡 Güvenlik: RLS + RBAC + SecureHeaders tamam, SOPS+Age + KVKK silme eklenmeli
5. ❌ Maliyet modeli tanımlanmış

**Pilot başlangıcı için bloklayıcı olmayanlar** (pilot sırasında tamamlanabilir):
- P2/P3 referans sinyalleri (pilot sırasında toplanır)
- Performans hedeflerinin ardışık 2 hafta doğrulanması (pilot sırasında ölçülür)
- Saha verisiyle persona doğrulama (pilot sırasında yapılır)

---

## Kapatma Planı

### Hafta 1: Güvenlik + Sır Yönetimi (5 gün)
| Gün | İş |
|:---:|----|
| 1 | SOPS+Age kurulumu + `.env` → şifreli dosya geçişi |
| 2 | Docker Compose'a decrypt adımı + CI/CD güvenlik kapısı |
| 3 | KVKK veri silme endpoint'i + frontend arayüzü |
| 4 | RBAC matrisi testleri + izolasyon negatif testleri |
| 5 | Maliyet modeli dokümanı + bütçe limiti konfigürasyonu |

### Hafta 2: Performans + Pilot Hazırlık (5 gün)
| Gün | İş |
|:---:|----|
| 1 | API benchmark script'i (`hey` ile) + baseline kaydı |
| 2 | Prometheus metrikleri + Grafana performance dashboard |
| 3 | Pilot onboarding dokümanı + kullanıcı kılavuzu |
| 4 | Pilot kiracı listesi + onboarding süreci |
| 5 | Tüm dokümanların Review → Approved geçişi + PO onayı |

---

## Çıkış Kapısı Kontrol Listesi

| # | Kriter | Durum | Sorumlu |
|:-:|--------|:-----:|:-------:|
| 1 | NFR-1: RLS tüm tablolarda | ✅ | TL |
| 2 | NFR-6: Denetim izi (audit_log) | ✅ | TL |
| 3 | NFR-7: Determinizm (temp=0, n=3, calc_run) | ✅ | TL |
| 4 | NFR-9: Performans baseline alınmış | ⏳ | TL |
| 5 | Cache middleware aktif (Redis + ETag) | ✅ | TL |
| 6 | 3 motor implemente | ✅ | TL |
| 7 | Temperature=0 tüm adapter'larda | ✅ | TL |
| 8 | Sample count yapılandırılabilir | ✅ | TL |
| 9 | Prometheus metrikleri akıyor | ✅ | TL |
| 10 | Grafana ayakta | ✅ | TL |
| 11 | SOPS+Age sır yönetimi | ✅ | TL |
| 12 | KVKK veri silme endpoint'i | ✅ | TL |
| 13 | RBAC testleri | ✅ | TL |
| 14 | İzolasyon negatif testleri | ✅ | TL |
| 15 | Maliyet modeli dokümanı | ✅ | TL |
| 16 | Pilot onboarding dokümanı | ✅ | TL |
| 17 | Kripto-silme altyapısı (S3 AES-256-GCM) | ✅ | TL |
| 18 | API benchmark script'i | ✅ | TL |
| 19 | CI/CD pipeline (GitHub Actions) | ✅ | TL |
| 20 | PO onayı | ⏳ | PO |

---

## Açık Öğeler (H16'dan Pilota Devreden)

1. P2/P3 referans sinyalleri — pilot başladıktan sonra
2. Performans hedeflerinin ardışık 2 hafta doğrulanması
3. Saha verisiyle persona doğrulama
4. Maliyet gerçekleşmesi panel modeli karşılaştırması
5. Kripto-silme altyapısı (S3 şifreleme)

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 24.07.2026 | İlk yayın: H16 pilot çıkış kapısı planı — 7 kriter analizi, eksik değerlendirmesi, kapatma takvimi |
| 2.0 | 24.07.2026 | H16 tüm eksikler kapatıldı: KVKK veri silme, maliyet modeli, API benchmark, kripto-silme (AES-256-GCM), pilot onboarding, CI/CD (GitHub Actions), RBAC+izolasyon negatif testleri, SOPS+Age. Pilot çıkış kapısına hazır. |
