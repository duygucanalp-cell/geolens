# 0303 · Aggregates (Toplam Kökleri)

| Alan | Değer |
|---|---|
| Doküman ID | 0303 |
| Proje | GeoLens Platform |
| Versiyon | 1.2 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 28 Temmuz 2026 |
| İlişkili | 0302, 0301, 0304, 0305, 0306, 0310, 0511, 0416, 0417, 0418, 0419 |

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
| **12** | **Arşiv Girdisi (ArchiveEntry)** | **BC7 Arşiv** | **Transactional** |
| **13** | **Arşiv Dışa Aktarım (ArchiveExport)** | **BC7 Arşiv** | **Eventual** |
| **14** | **Konuşma Anlık Görüntüsü (ConversationSnapshot)** | **BC8 Replay** | **Transactional** |
| **15** | **SEO Bağlantısı (SEOConnection)** | **BC9 SEO** | **Transactional** |
| **16** | **Duygu Skoru (SentimentScore)** | **BC10 Denetim ve Analiz** | **Transactional** |
| **17** | **Hallüsinasyon İşareti (HallucinationFlag)** | **BC10 Denetim ve Analiz** | **Transactional** |
| **18** | **Gap Anlık Görüntüsü (GapSnapshot)** | **BC10 Denetim ve Analiz** | **Transactional** |
| **19** | **Konu Kümesi (TopicCluster)** | **BC10 Denetim ve Analiz** | **Transactional** |

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

### 3.12 Arşiv Girdisi (ArchiveEntry) — BC7 (HT1)

| Alan | Değer |
|------|-------|
| **Kök** | Arşiv Girdisi |
| **İç varlıklar** | — (S3 referansı, versiyon no, içerik karması — tümü değer tipi) |
| **Değişmezler** | I12 (yazıldıktan sonra değiştirilemez ve silinemez); her girdi benzersiz brand × engine × versiyon bileşimine sahiptir |
| **Erişim kuralı** | Worker (measure sonrası) yazabilir; kullanıcı sorgulayabilir ve dışa aktarabilir; silme yasaktır |
| **Durum makinesi** | kaydedildi → dışa aktarıldı \| bekliyor; dışa aktarıldı → (süre sonu) → silindi |
| **Transaction sınırı** | Arşiv Girdisi tek transaction'da kaydedilir; S3 yazma işlemi PG transaction'ından önce veya sonra olabilir (eventual consistency) |

### 3.13 Arşiv Dışa Aktarım (ArchiveExport) — BC7 (HT1)

| Alan | Değer |
|------|-------|
| **Kök** | Arşiv Dışa Aktarım |
| **İç varlıklar** | — (filtreler, format, S3 referansı — değer tipleri) |
| **Değişmezler** | Export başlangıç tarihi bitiş tarihinden önce olmalıdır |
| **Erişim kuralı** | Kullanıcı talep eder; worker üretir; hazır olduğunda imzalı URL ile indirilir |
| **Transaction sınırı** | Eventual — worker asenkron üretir; kullanıcı durumu poll eder |

### 3.14 Konuşma Anlık Görüntüsü (ConversationSnapshot) — BC8 (HT1)

| Alan | Değer |
|------|-------|
| **Kök** | Konuşma Anlık Görüntüsü |
| **İç varlıklar** | — (içerik karması, S3 referansı — değer tipleri) |
| **Değişmezler** | I16 (yalnız tamamlanmış ölçüm işlerinden alınır); oluşturulduktan sonra değiştirilemez |
| **Erişim kuralı** | Worker (ölçüm sonrası) yazabilir; kullanıcı görüntüleyebilir ve karşılaştırabilir |
| **Durum makinesi** | oluşturuldu → karşılaştırıldı \| arşivlendi |
| **Alt toplam** | DiffResult (değer tipi — snapshot_a + snapshot_b + has_changed + changes) |

### 3.15 SEO Bağlantısı (SEOConnection) — BC9 (HT1)

| Alan | Değer |
|------|-------|
| **Kök** | SEO Bağlantısı |
| **İç varlıklar** | OAuth2Token (değer tipi — access_token, refresh_token, expiry) |
| **Değişmezler** | I13 (token'lar şifreli saklanır, düz metin loga yazılamaz); workspace başına her platform için en fazla bir aktif bağlantı |
| **Erişim kuralı** | Çalışma alanı yöneticisi bağlayabilir/koparabilir; worker token'ı kullanır; token bilgisi hiçbir API yanıtında düz metin dönmez |
| **Alt toplamlar** | SC Sorgu Verisi (SearchConsoleQuery), GA4 Ölçüm Verisi (GA4Metric) — bunlar ayrı toplamlardır, SEO Bağlantısı'na yabancı anahtarla bağlanır |
| **Durum makinesi** | bağlı → token yenileniyor → bağlı; bağlı → kopuk → (manüel) → yeniden bağla |
| **Transaction sınırı** | Bağlantı oluşturma: SEOConnection + OAuth2Token tek transaction'da |

### 3.16 Duygu Skoru (SentimentScore) — BC10 (HT1)

| Alan | Değer |
|------|-------|
| **Kök** | Duygu Skoru |
| **İç varlıklar** | — (overall_sentiment, positive/neutral/negative_score, mention_count — tümü değer tipi) |
| **Değişmezler** | I14 (yalnız mevcut ham yanıtlar üzerinde çalışır, ayrı motor çağrısı yapılmaz); duygu skoru [0,1] aralığındadır |
| **Erişim kuralı** | Worker (sentiment profili) yazabilir; kullanıcı sorgulayabilir |
| **Alt toplam** | Hallüsinasyon İşareti (HallucinationFlag) — ayrı bir toplam, SentimentScore'dan bağımsız ancak aynı worker'da üretilir |

### 3.17 Hallüsinasyon İşareti (HallucinationFlag) — BC10 (HT1)

| Alan | Değer |
|------|-------|
| **Kök** | Hallüsinasyon İşareti |
| **İç varlıklar** | — (hallucination_type, severity, description, confidence, verified — tümü değer tipi) |
| **Değişmezler** | I14 (yalnız mevcut ham yanıtlar üzerinde çalışır); verified alanı null başlar, kullanıcı tarafından true/false olarak işaretlenebilir |
| **Erişim kuralı** | Worker (sentiment profili) yazabilir; kullanıcı verify edebilir (true = doğrulandı, false = yanlış pozitif) |
| **Durum makinesi** | tespit edildi → doğrulandı \| yanlış pozitif |
| **Transaction sınırı** | Tek HallucinationFlag transaction'ı; toplu tespitte her flag ayrı kaydedilir |

### 3.18 Gap Anlık Görüntüsü (GapSnapshot) — BC10 (HT1)

| Alan | Değer |
|------|-------|
| **Kök** | Gap Anlık Görüntüsü |
| **İç varlıklar** | Gap Detayı (GapDetail) — 5 ayrı değer tipi (visibility/citation/content/topic/prompt), Gap Önerisi (GapRecommendation), **İçerik Boşluğu (ContentGap)** — gap analizinin bir parçası olarak hesaplanan içerik boşlukları |
| **Değişmezler** | I15 (en az 2 marka arasında yapılır); aynı brand_id × competitor_id × period_start × period_end bileşimi için en fazla bir aktif snapshot |
| **Erişim kuralı** | Worker (gap profili) yazabilir; kullanıcı sorgulayabilir; eşik aşımı alert tetikler |
| **Durum makinesi** | hesaplanıyor → tamamlandı \| başarısız |
| **Transaction sınırı** | GapSnapshot + 5 GapDetail + N GapRecommendation + ContentGap (iç varlık) tek transaction'da kaydedilir. Aynı kombinasyon varsa UPSERT (ON CONFLICT DO UPDATE). |

### 3.19 Konu Kümesi (TopicCluster) — BC10 (HT1)

| Alan | Değer |
|------|-------|
| **Kök** | Konu Kümesi |
| **İç varlıklar** | — (alt_konular JSON, önerilen_içerik_türü, öncelik, durum — tümü değer tipi) |
| **Değişmezler** | Her topic cluster bir brand'e aittir; durum alanı önerildi/planlandı/uygulandı değerlerinden birini alır |
| **Erişim kuralı** | Worker (content GEO analizi) üretebilir; kullanıcı okuyabilir ve durumunu güncelleyebilir (planlandı/uygulandı) |
| **Durum makinesi** | önerildi → planlandı → uygulandı |
| **Transaction sınırı** | Tek TopicCluster transaction'ı; kullanıcı güncellemesi ayrı transaction |

> **Alt varlık notu:** `BotAccessRecord` ve `SchemaAnalysis`, BC10 (Bot erişim kaydı ve şema analizi) entity'leri olarak 0302'de tanımlanmıştır; bunlar BC3'teki Denetim Koşusu (SiteAuditRun) toplamının iç varlıklarıdır (kaynak: Site Denetimi). Tek başlarına aggregate root değildirler.

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
| **Arşiv Girdisi** | **Marka** | **Yabancı anahtar (brand_id)** |
| **Arşiv Dışa Aktarım** | **Çalışma Alanı** | **Yabancı anahtar (workspace_id)** |
| **Konuşma Anlık Görüntüsü** | **Marka** | **Yabancı anahtar (brand_id)** |
| **SEO Bağlantısı** | **Çalışma Alanı** | **Yabancı anahtar (workspace_id)** |
| **SC Sorgu Verisi** | **SEO Bağlantısı, Marka** | **Yabancı anahtar (seo_conn_id, brand_id)** |
| **GA4 Ölçüm Verisi** | **SEO Bağlantısı, Marka** | **Yabancı anahtar (seo_conn_id, brand_id)** |
| **Duygu Skoru** | **Marka** | **Yabancı anahtar (brand_id)** |
| **Gap Anlık Görüntüsü** | **Marka, Rakip** | **Yabancı anahtar (brand_id, competitor_id)** |
| **Hallüsinasyon İşareti** | **Marka** | **Yabancı anahtar (brand_id)** |

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
| **Arşiv girdisi kaydetme** | **ArchiveEntry (PG) + Ham yanıt (S3)** | **S3 önce (imzalı URL), PG sonra (meta); eventual consistency kabul edilebilir** |
| **Arşiv dışa aktarma** | **ArchiveExport + S3 export dosyası** | **Worker asenkron üretir; kullanıcı durumu poll eder (eventual)** |
| **Snapshot capture** | **ConversationSnapshot tek transaction** | **Yalnız tamamlanmış ölçüm işlerinden tetiklenir** |
| **SEO bağlantısı** | **SEOConnection + OAuth2Token (şifreli)** | **Tek transaction; token yenileme ayrı transaction (yalnız token alanları)** |
| **Duygu analizi** | **SentimentScore (1-N mention) tek transaction** | **Worker (sentiment profili) toplu yazma; hata durumunda kısmi sonuç kabul edilmez** |
| **Gap analizi** | **GapSnapshot + 5 GapDetail + N GapRecommendation** | **Tek transaction UPSERT; aynı kombinasyon varsa güncelle** |

---

## 6. GeoLens İçin Çıkarımlar

1. **11'den 19 toplam köküne genişleme** (MVP → HT1). Yeni toplamlar BC7-BC10 bağlamlarına dağılmıştır: ArchiveEntry, ArchiveExport, ConversationSnapshot, SEOConnection, SentimentScore, HallucinationFlag, GapSnapshot, TopicCluster. Her biri ait olduğu bağlamın tutarlılık sınırlarını takip eder.
2. **Salt ekleme (append-only) toplamlar** artmıştır: PanelVersion, CalculationRun, ArchiveEntry, ConversationSnapshot, SentimentScore, GapSnapshot — değiştirilemez ve silinemez yapıdadır. Bu, denetlenebilirlik ve veri bütünlüğü ilkelerini güçlendirir.
3. **Ölçüm İşi** en karmaşık yaşam döngüsüne sahip toplamdır: 5 durum, sınırlı yeniden deneme, kısmi sonuç yönetimi.
4. **Toplamlar arası referanslar yalnız ID üzerinden** yapılır. Bu, bağlamlar arası gevşek bağlılığı korur ve 0305 modül sınırlarıyla uyumludur.
5. **0305 (Bounded Contexts)** her toplam kökünü ait olduğu bağlam paketine atar. Transaction sınırları modül sınırlarıyla çakışır.
6. **SEOConnection** diğer toplamlardan farklı olarak şifreli token saklama zorunluluğu (I13) ve OAuth2 yenileme mekanizması nedeniyle özel bir transaction modeline sahiptir.
7. **GapSnapshot**, UPSERT kullanan tek toplamdır. Aynı brand × competitor × period kombinasyonu tekrar hesaplandığında mevcut kayıt güncellenir; bu, gap analizinin periyodik doğasıyla uyumludur.

---

## 7. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | Benchmark toplulaştırma modeli | ⏳ HT2'de karara bağlanır. |
| O-2 | Öneri-etki takibi ayrı toplam mı? | ⏳ Mevcut karar: iç varlık (0302 ile uyumlu). |
| O-3 | ULID indeks performansı | ⏳ Pilot öncesi test. AVIP D-35 (ULID) onaylandı. |
| O-4 | Hallüsinasyon İşareti (HallucinationFlag) ayrı toplam mı, SentimentScore altında mı? | ⏳ Mevcut karar: ayrı toplam (bağımsız yaşam döngüsü, doğrulama mekanizması). |
| O-5 | SC Sorgu Verisi ve GA4 Ölçüm Verisi — ayrı toplam olarak kalmalı mı, SEOConnection altında mı? | ⏳ Mevcut karar: ayrı toplam (farklı schema ve yaşam döngüsü). |

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
| 1.2 | 28.07.2026 | **HT1 aggregate genişletmesi:** BC7-BC10 için 8 yeni toplam kökü eklendi (ArchiveEntry, ArchiveExport, ConversationSnapshot, SEOConnection, SentimentScore, HallucinationFlag, GapSnapshot, TopicCluster). Toplam kök sayısı 11'den 19'a çıktı. Her yeni toplam için detaylı tanım (iç varlıklar, değişmezler, erişim kuralları, durum makineleri, transaction sınırları). BotAccessRecord ve SchemaAnalysis'in SiteAuditRun iç varlığı olduğu notu eklendi. ContentGap'in GapSnapshot iç varlığı olduğu notu eklendi. §4 referans kuralları BC7-BC10 toplamlarıyla genişletildi. §5 transaction kapsamına HT1 işlemleri eklendi. §6 çıkarımlar güncellendi. §7 açık sorulara O-4 (HallucinationFlag) ve O-5 (SC/GA4 veri modeli) eklendi. |
