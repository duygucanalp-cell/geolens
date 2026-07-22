# 0303 · Aggregates (Toplam Kökleri)

| Alan | Değer |
|---|---|
| Doküman ID | 0303 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0302, 0301, 0304, 0305, 0306, 0310 |

---

## 1. Amaç

Bu doküman, 0302 Domain Model'de tanımlanan varlıkların hangi toplam kökleri (aggregate root) etrafında kümelendiğini, her toplamın değişmezlerini ve erişim kurallarını tanımlar. Amaç, veri tutarlılığı sınırlarını netleştirmek ve transaction kapsamını belirlemektir.

> **Kural:** Dış dünya bir bağlama yalnız toplam kökü üzerinden yazar. Toplam içi varlıklara doğrudan dışarıdan erişilmez.

---

## 2. Toplam Kökleri Haritası

| # | Toplam Kökü | Bağlam | Tutarlılık Sınırı |
|:-:|------------|--------|:-----------------:|
| 1 | Kiracı (Tenant) | BC1 Kimlik ve Kiracılık | Transactional |
| 2 | Çalışma Alanı (Workspace) | BC1 Kimlik ve Kiracılık | Transactional |
| 3 | Marka (Brand) | BC2 Yapılandırma | Transactional |
| 4 | Prompt Seti (PromptSet) | BC2 Yapılandırma | Transactional |
| 5 | Panel Versiyonu (PanelVersion) | BC3 Ölçüm ve Hesap | Transactional |
| 6 | Ölçüm İşi (MeasurementJob) | BC3 Ölçüm ve Hesap | Transactional |
| 7 | Hesap Koşusu (CalculationRun) | BC3 Ölçüm ve Hesap | Transactional |
| 8 | Denetim Koşusu (SiteAuditRun) | BC3 Ölçüm ve Hesap | Transactional |
| 9 | Öneri (Recommendation) | BC4 İçgörü | Transactional |
| 10 | Uyarı (Alert) | BC5 Bildirim ve Raporlama | Transactional |
| 11 | Rapor (Report) | BC5 Bildirim ve Raporlama | Transactional |

---

## 3. Toplam Detayları

### 3.1 Kiracı (Tenant) — BC1

| Alan | Değer |
|------|-------|
| **Kök** | Kiracı |
| **İç varlıklar** | Paket Hakları (Entitlement), Kullanım Kaydı (UsageRecord), Kota Sınırı (QuotaLimit) |
| **Alt toplamlar** | Çalışma Alanı (1-N), Üyelik (N-N), Davet (1-N) |
| **Değişmezler** | I1 (kiracısız veri yok), I9 (kota sınırı) |
| **Erişim kuralı** | Yalnız sistem yöneticisi veya kiracı yöneticisi yazabilir; kullanıcılar yalnız kendi kiracılarını görebilir |
| **Transaction sınırı** | Kiracı oluşturma: Tenant + Entitlement + varsayılan Workspace tek transaction'da |

### 3.2 Çalışma Alanı (Workspace) — BC1

| Alan | Değer |
|------|-------|
| **Kök** | Çalışma Alanı |
| **İç varlıklar** | — (kendi başına toplam) |
| **Alt toplamlar** | Marka (1-N), Prompt Seti (1-1), İzleme Planı (1-1) |
| **Değişmezler** | I10 (arşivde yeni ölçüm üretilmez) |
| **Erişim kuralı** | Kiracı üyeleri yetki kapsamındaki çalışma alanlarına erişebilir; ajans müşteri izolasyonu (0310) |
| **Durum makinesi** | aktif → arşiv → aktif \| devredildi (§7) |

### 3.3 Marka (Brand) — BC2

| Alan | Değer |
|------|-------|
| **Kök** | Marka |
| **İç varlıklar** | Site (Site) |
| **Değişmezler** | Aynı çalışma alanında aynı marka adı birden fazla kez tanımlanamaz |
| **Erişim kuralı** | Çalışma alanı yöneticisi/editörü yazabilir; izleyici okuyabilir |

### 3.4 Prompt Seti (PromptSet) — BC2

| Alan | Değer |
|------|-------|
| **Kök** | Prompt Seti |
| **İç varlıklar** | Promptlar (değer tipi koleksiyonu) |
| **Değişmezler** | En az 1 prompt; her prompt etiketli olmalıdır (markalı/kategori) |
| **Erişim kuralı** | Çalışma alanı yöneticisi/editörü; şablon kütüphanesi sistem düzeyi |

### 3.5 Panel Versiyonu (PanelVersion) — BC3

| Alan | Değer |
|------|-------|
| **Kök** | Panel Versiyonu |
| **İç varlıklar** | — (değer tipleri: prompt seti anlık görüntüsü, motor kapsamı, pazar) |
| **Değişmezler** | I4 (değişim yeni versiyon üretir); oluşturulduktan sonra değiştirilemez |
| **Erişim kuralı** | Sadece sistem üretir (yapılandırma değişikliğinde otomatik); kullanıcı doğrudan yazamaz |

### 3.6 Ölçüm İşi (MeasurementJob) — BC3

| Alan | Değer |
|------|-------|
| **Kök** | Ölçüm İşi |
| **İç varlıklar** | Ham Yanıt (RawResponse), Alıntı (Citation) |
| **Değişmezler** | I5 (ham yanıt silinemez/üzerine yazılamaz); idempotent anahtar tekil |
| **Erişim kuralı** | Sadece scheduler ve worker üretir/yazar; kullanıcı durum sorgulayabilir |
| **Durum makinesi** | kuyrukta → çalışıyor → tamamlandı \| kısmi \| başarısız; başarısız → kuyrukta (max 3 deneme) |

### 3.7 Hesap Koşusu (CalculationRun) — BC3

| Alan | Değer |
|------|-------|
| **Kök** | Hesap Koşusu |
| **İç varlıklar** | Skor (Score), Trend Noktası (TrendPoint) |
| **Değişmezler** | I2 (değiştirilemez); I3 (skor fidelite etiketi ve güven aralığı taşır) |
| **Erişim kuralı** | Sadece worker üretir; kullanıcı yalnız okur; silme yasak |

### 3.8 Denetim Koşusu (SiteAuditRun) — BC3

| Alan | Değer |
|------|-------|
| **Kök** | Denetim Koşusu |
| **İç varlıklar** | Bulgu (AuditFinding) |
| **Değişmezler** | Her bulgu önem kategorisi taşır; süre hedefi <30 saniye |
| **Erişim kuralı** | Worker üretir; kullanıcı okuyabilir |

### 3.9 Öneri (Recommendation) — BC4

| Alan | Değer |
|------|-------|
| **Kök** | Öneri |
| **İç varlıklar** | Öneri Etkisi (RecommendationImpact) — HT1 |
| **Değişmezler** | I7 (politika filtresinden geçmemiş öneri kalıcılaştırılamaz) |
| **Erişim kuralı** | Worker üretir; kullanıcı işaretleyebilir (uygulandı/reddedildi) |
| **Durum makinesi** | açık → uygulandı \| reddedildi; uygulandı → etki izleniyor (HT1) |

### 3.10 Uyarı (Alert) — BC5

| Alan | Değer |
|------|-------|
| **Kök** | Uyarı |
| **İç varlıklar** | — |
| **Değişmezler** | I8 (yalnız anlamlılık kuralını geçen değişimden üretilir); M11 geri bildirim |
| **Erişim kuralı** | Worker üretir; kullanıcı görüntüleyebilir ve geri bildirim verebilir |

### 3.11 Rapor (Report) — BC5

| Alan | Değer |
|------|-------|
| **Kök** | Rapor |
| **İç varlıklar** | — (S3 referansı + meta veri) |
| **Değişmezler** | I11 (yalnız etiketli skorlardan üretilir) |
| **Erişim kuralı** | Worker üretir; kullanıcı talep eder ve indirir; Business paketi white-label (FR-F4) |
| **Durum makinesi** | kuyrukta → üretiliyor → hazır \| başarısız; başarısız → kuyrukta |

---

## 4. Toplamlar Arası Referans Kuralları

| Kaynak Toplam | Hedef Toplam | Referans Tipi |
|--------------|-------------|:-------------:|
| Çalışma Alanı | Kiracı | Yabancı anahtar (tenant_id) |
| Marka | Çalışma Alanı | Yabancı anahtar (workspace_id) |
| Prompt Seti | Çalışma Alanı | Yabancı anahtar (workspace_id) |
| Panel Versiyonu | Çalışma Alanı | Yabancı anahtar (workspace_id) |
| Ölçüm İşi | Panel Versiyonu | Yabancı anahtar (panel_version_id) |
| Hesap Koşusu | Ölçüm İşi | Yabancı anahtar (measurement_job_id) |
| Skor | Hesap Koşusu, Panel Versiyonu, Marka | Yabancı anahtar (3 yön) |
| Öneri | Çalışma Alanı | Yabancı anahtar (workspace_id) |
| Uyarı | Çalışma Alanı, Uyarı Kuralı | Yabancı anahtar (2 yön) |
| Rapor | Çalışma Alanı | Yabancı anahtar (workspace_id) |

> **Kural:** Toplamlar arası referans yalnız yabancı anahtar (ID) üzerinden yapılır. Başka bir toplamın iç varlıklarına doğrudan erişilmez.

---

## 5. Transaction Kapsamı

| İşlem | Kapsam | Not |
|-------|--------|-----|
| Kiracı oluşturma | Kiracı + Entitlement + Varsayılan Workspace | Tek transaction; başarısız olursa geri al |
| Ölçüm işi üretme | MeasurementJob + EventOutbox | Aynı PG transaction'ında |
| Hesap koşusu oluşturma | CalculationRun + Score (1-N) | Tek transaction; yazma hatasında tüm koşu iptal |
| Rapor üretme | Report + S3 nesnesi + AuditLogEntry | S3 önce, PG sonra (eventual consistency) |
| Kullanıcı işaretleme | Recommendation + telemetri olayı | Recommendation transaction'ı + outbox |

---

## 6. GeoLens İçin Çıkarımlar

1. **11 toplam kökü** tanımlanmıştır. Her toplam kendi tutarlılık sınırını çizer; transaction kapsamı toplam bazlıdır.
2. **PanelVersiyon ve CalculationRun** salt ekleme (append-only) toplamlardır. Silinemez ve değiştirilemezler. Bu, denetlenebilirlik ve deterministik hesap ilkelerinin veri tabanı zorlamasıdır.
3. **Ölçüm İşi** en karmaşık yaşam döngüsüne sahip toplamdır: 5 durum, sınırlı yeniden deneme, kısmi sonuç yönetimi.
4. **Toplamlar arası referanslar yalnız ID üzerinden** yapılır. Bu, bağlamlar arası gevşek bağlılığı korur ve 0305 modül sınırlarıyla uyumludur.
5. **0305 (Bounded Contexts)** her toplam kökünü ait olduğu bağlam paketine atar. Transaction sınırları modül sınırlarıyla çakışır.

---

## 7. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | Benchmark toplulaştırma modeli | ⏳ HT2'de karara bağlanır. |
| O-2 | Öneri-etki takibi ayrı toplam mı? | ⏳ Mevcut karar: iç varlık (0302 ile uyumlu). |
| O-3 | ULID indeks performansı | ⏳ Pilot öncesi test. AVIP D-35 (ULID) onaylandı. |

### Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|----|-------|--------|
| **D-74** | **Redis Streams + tüketici grupları.** Liste elendi. TL 21.07.2026, ADR-005. | AVIP 0303 O-1 |
| **D-54** | **S3 saklama:** 30gün STANDARD → 90gün GLACIER → sil. PO+TL 21.07.2026. | AVIP 0303 O-2 |
| **D-58** | **KVKK silme:** Kripto-silme + anonimleştirme. PY+TL 21.07.2026. | AVIP 0303 O-3 |

---

## Kaynaklar

- 0302 Domain Model — varlıklar, ilişkiler, değişmezler, durum makineleri
- 0301 Core Concepts — çekirdek kavramlar
- 0207 Feature Catalog — özellik-toplam bağları
- 0310 Security — erişim kuralları ve RBAC
- archive/avip-v1/0302-domain-model.md — AVIP referansı

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: 11 toplam kökü haritası, her toplam için detaylı tanım (iç varlıklar, değişmezler, erişim kuralları, durum makineleri), toplamlar arası referans kuralları, transaction kapsamı. 0302'den türetilmiştir. |
| 1.1 | 22.07.2026 | AVIP kapalı kararları taşındı: D-74 (Redis Streams), D-54 (saklama), D-58 (KVKK silme). Devralınan Kararlar eklendi. |
