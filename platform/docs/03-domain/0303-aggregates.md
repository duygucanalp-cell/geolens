# 0303 · Aggregates (Toplam Kökleri)

| Alan | Değer |
|---|---|
| Doküman ID | 0303 |
| Proje | GeoLens Platform |
| Versiyon | 1.3 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 04 Ağustos 2026 |
| İlişkili | 0302, 0301, 0304, 0305, 0306, 0310, 0511, 0209, 0210, 0416, 0417, 0418, 0419 |

---

## 1. Amaç

Bu doküman, 0302 Domain Model'de tanımlanan varlıkların hangi toplam kökleri (aggregate root) etrafında kümelendiğini, her toplamın değişmezlerini ve erişim kurallarını tanımlar. Amaç, veri tutarlılığı sınırlarını netleştirmek ve transaction kapsamını belirlemektir. HT1 genişletmesi (v1.2) BC7-BC10 köklerini, Faz 4 genişletmesi (v1.3) BC11-BC13 köklerini kapsar (0302 v1.3 ile senkron).

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
| **20** | **Envanter Varlığı (RegistryEntity)** | **BC11 AI Yönetişimi** | **Transactional** |
| **21** | **Kaçak AI Taraması (DiscoveryScan)** | **BC11 AI Yönetişimi** | **Transactional** |
| **22** | **Guardrail Kuralı (GuardrailRule)** | **BC11 AI Yönetişimi** | **Transactional** |
| **23** | **Politika Paketi (PolicyPack)** | **BC11 AI Yönetişimi** | **Transactional** |
| **24** | **Önyargı Testi (BiasTest)** | **BC11 AI Yönetişimi** | **Transactional** |
| **25** | **CI/CD Kapı Denetimi (GateCheck)** | **BC11 AI Yönetişimi** | **Transactional** |
| **26** | **Açıklama Sonucu (ExplainResult)** | **BC11 AI Yönetişimi** | **Transactional** |
| **27** | **Ajan İzi (AgentTrace)** | **BC11 AI Yönetişimi** | **Transactional** |
| **28** | **Kırmızı Takım Senaryosu (RedTeamCase)** | **BC11 AI Yönetişimi** | **Transactional** |
| **29** | **Kırmızı Takım Koşusu (RedTeamRun)** | **BC11 AI Yönetişimi** | **Transactional** |
| **30** | **Prompt Denetimi (PromptAudit)** | **BC12 AI Operasyonları** | **Transactional** |
| **31** | **Model Kıyaslaması (ModelBenchmark)** | **BC12 AI Operasyonları** | **Transactional** |
| **32** | **Maliyet Kaydı (CostEntry)** | **BC12 AI Operasyonları** | **Transactional** |
| **33** | **Kullanım Ölçümü (UsageMetric)** | **BC12 AI Operasyonları** | **Transactional** |
| **34** | **Optimizasyon Önerisi (OptimizationRecommendation)** | **BC12 AI Operasyonları** | **Transactional** |
| **35** | **Versiyon Kaydı (VersionEntry)** | **BC12 AI Operasyonları** | **Transactional** |
| **36** | **Olay Kaydı (IncidentEvent)** | **BC12 AI Operasyonları** | **Transactional** |
| **37** | **Sapma Gözlemi (DriftObservation)** | **BC12 AI Operasyonları** | **Transactional** |
| **38** | **Sapma Uyarısı (DriftAlert)** | **BC12 AI Operasyonları** | **Transactional** |
| **39** | **Fatura (BillingInvoice)** | **BC13 Faturalama** | **Transactional** |
| **40** | **Stripe Müşterisi (StripeCustomer)** | **BC13 Faturalama** | **Transactional** |

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

### 3.20 Envanter Varlığı (RegistryEntity) — BC11 (Faz 4)

| Alan | Değer |
|------|-------|
| **Kök** | Envanter Varlığı |
| **İç varlıklar** | Risk Değerlendirmesi (RiskAssessment) — yalnız ekle geçmiş |
| **Değişmezler** | Risk sınıfı ve yaşam döngüsü enum'larıyla sınırlıdır (düşük/orta/yüksek/kritik; geliştirme→emekli) |
| **Erişim kuralı** | Kiracı yöneticisi/editörü yazabilir; risk değerlendirmeleri yalnız eklemelidir |
| **Transaction sınırı** | Varlık + ilk RiskDeğerlendirmesi tek transaction; sonraki değerlendirmeler ayrı transaction |

### 3.21 Kaçak AI Taraması (DiscoveryScan) — BC11 (Faz 4)

| Alan | Değer |
|------|-------|
| **Kök** | Kaçak AI Taraması |
| **İç varlıklar** | Kaçak AI Bulgusu (DiscoveryFinding) |
| **Değişmezler** | Bulgu yalnız tamamlanmış taramaya bağlanabilir; tarama sonucu (bulunan sayısı) bulgularla tutarlıdır |
| **Erişim kuralı** | Worker (tarama profili) yazar; kullanıcı sorgular ve bulguları envantere aday gösterir |
| **Durum makinesi** | bekliyor → çalışıyor → tamamlandı \| başarısız |

### 3.22 Guardrail Kuralı (GuardrailRule) — BC11 (Faz 4)

| Alan | Değer |
|------|-------|
| **Kök** | Guardrail Kuralı |
| **İç varlıklar** | Guardrail Değerlendirmesi (GuardrailEvaluation) — yalnız ekle geçmiş |
| **Değişmezler** | I17 (aksiyon yalnız eşleşen kuralda); desen regex/anahtar tipindedir, aksiyon block/flag/log |
| **Erişim kuralı** | Yönetici/editör kural CRUD; değerlendirmeleri runtime (worker/scheduler) yazar |
| **Transaction sınırı** | Kural ve değerlendirme ayrı transaction; değerlendirme yalnız ekle |

### 3.23 Politika Paketi (PolicyPack) — BC11 (Faz 4)

| Alan | Değer |
|------|-------|
| **Kök** | Politika Paketi |
| **İç varlıklar** | Politika Kontrolü (PolicyControl) |
| **Değişmezler** | I21 (kiracı × çerçeve tekil); kontrol durumu enum ile sınırlı (bekliyor/geçti/kaldı/uygun değil) |
| **Erişim kuralı** | Yönetici uygular/seed'ler; uyum yüzdesi kontrollerin durumundan türetilir |
| **Durum makinesi** | (kontrol) bekliyor → geçti \| kaldı \| uygun değil |
| **Transaction sınırı** | Paket + N Kontrol tek transaction; framework çakışmasında ret (I21) |

### 3.24 Önyargı Testi (BiasTest) — BC11 (Faz 4)

| Alan | Değer |
|------|-------|
| **Kök** | Önyargı Testi |
| **İç varlıklar** | — (adillik skoru, maksimum fark, öneriler — değer tipleri) |
| **Değişmezler** | Adillik skoru 0-1 aralığında; maksimum fark 4/5 kuralıyla değerlendirilir |
| **Erişim kuralı** | Worker (test profili) yazar; kullanıcı sorgular |

### 3.25 CI/CD Kapı Denetimi (GateCheck) — BC11 (Faz 4)

| Alan | Değer |
|------|-------|
| **Kök** | CI/CD Kapı Denetimi |
| **İç varlıklar** | — (check_details JSON — değer tipi) |
| **Değişmezler** | Karar onaylandı/işaretli/engelli üçlüsünden biridir; geçen denetim sayısı toplamı aşamaz; yalnız ekle |
| **Erişim kuralı** | Yalnız gate servisi (deployment pipeline) yazar; kullanıcı geçmişi sorgular |
| **Transaction sınırı** | Tek GateCheck transaction'ı; append-only |

### 3.26 Açıklama Sonucu (ExplainResult) — BC11 (Faz 4)

| Alan | Değer |
|------|-------|
| **Kök** | Açıklama Sonucu |
| **İç varlıklar** | — (SHAP değerleri, özellik önemleri — değer tipleri) |
| **Değişmezler** | Envanter Varlığı'na yabancı anahtarla bağlanır; yalnız ekle |
| **Erişim kuralı** | Worker yazar; kullanıcı okur |

### 3.27 Ajan İzi (AgentTrace) — BC11 (Faz 4)

| Alan | Değer |
|------|-------|
| **Kök** | Ajan İzi |
| **İç varlıklar** | Ajan Adımı (AgentStep) |
| **Değişmezler** | Tamamlanan adım sayısı ≤ toplam adım; iz durumu adım durumlarıyla tutarlıdır |
| **Erişim kuralı** | Worker/agent runtime yazar; kullanıcı izler |
| **Durum makinesi** | çalışıyor → tamamlandı \| başarısız \| iptal edildi |
| **Transaction sınırı** | İz başlatma tek transaction; adım güncellemeleri ayrı transaction (eventual) |

### 3.28 Kırmızı Takım Senaryosu (RedTeamCase) — BC11 (Faz 4)

| Alan | Değer |
|------|-------|
| **Kök** | Kırmızı Takım Senaryosu |
| **İç varlıklar** | — |
| **Değişmezler** | Kategori 8'li enum (jailbreak/prompt injection/rol yapma/kodlama/PII çıkarma/yanlış bilgi/reddi aşma/özel); payload boş olamaz |
| **Erişim kuralı** | Yönetici/editör CRUD; varsayılan 8 senaryo seed'i yalnız boşken yazılır |

### 3.29 Kırmızı Takım Koşusu (RedTeamRun) — BC11 (Faz 4)

| Alan | Değer |
|------|-------|
| **Kök** | Kırmızı Takım Koşusu |
| **İç varlıklar** | Kırmızı Takım Sonucu (RedTeamResult) |
| **Değişmezler** | I18 (savunma skoru passed/total × 100); toplam = geçen + kalan; sonuçlar Senaryo'ya yabancı anahtarla bağlanır |
| **Erişim kuralı** | Editör çalıştırır; koşu ve sonuçları yalnız okunur |
| **Transaction sınırı** | Koşu + N Sonuç tek transaction; skor sonuçlardan türetilir |

### 3.30 Prompt Denetimi (PromptAudit) — BC12 (Faz 4)

| Alan | Değer |
|------|-------|
| **Kök** | Prompt Denetimi |
| **İç varlıklar** | — (sorunlar JSON — değer tipi) |
| **Değişmezler** | Durum geçti/işaretli/kaldı üçlüsünden biridir; yalnız ekle |
| **Erişim kuralı** | Worker (denetim profili) yazar; kullanıcı okur |

### 3.31 Model Kıyaslaması (ModelBenchmark) — BC12 (Faz 4)

| Alan | Değer |
|------|-------|
| **Kök** | Model Kıyaslaması |
| **İç varlıklar** | — |
| **Değişmezler** | Doğruluk skoru 0-100; alıntı oranı 0-1; yalnız ekle |
| **Erişim kuralı** | Worker (benchmark profili) yazar; kullanıcı okur |

### 3.32 Maliyet Kaydı (CostEntry) — BC12 (Faz 4)

| Alan | Değer |
|------|-------|
| **Kök** | Maliyet Kaydı |
| **İç varlıklar** | — |
| **Değişmezler** | I22 (yalnız ekle); işlem türü enum ile sınırlı; tutar + para birimi zorunlu |
| **Erişim kuralı** | Otomatik (ölçüm/değerlendirme akışı) yazar; kullanıcı analitik okur |

### 3.33 Kullanım Ölçümü (UsageMetric) — BC12 (Faz 4)

| Alan | Değer |
|------|-------|
| **Kök** | Kullanım Ölçümü |
| **İç varlıklar** | — |
| **Değişmezler** | I22 (yalnız ekle); uç nokta/yöntem/durum kodu telemetri alanları zorunlu |
| **Erişim kuralı** | API middleware yazar; yönetici analitik okur |

### 3.34 Optimizasyon Önerisi (OptimizationRecommendation) — BC12 (Faz 4)

| Alan | Değer |
|------|-------|
| **Kök** | Optimizasyon Önerisi |
| **İç varlıklar** | — |
| **Değişmezler** | Etki/çaba yüksek/orta/düşük; durum bekliyor/uygulandı/görmezden gelindi |
| **Erişim kuralı** | Worker üretir; kullanıcı durumu günceller |
| **Durum makinesi** | bekliyor → uygulandı \| görmezden gelindi |

### 3.35 Versiyon Kaydı (VersionEntry) — BC12 (Faz 4)

| Alan | Değer |
|------|-------|
| **Kök** | Versiyon Kaydı |
| **İç varlıklar** | — |
| **Değişmezler** | Eski/yeni sürüm alanları dolu; yalnız ekle denetim kaydı |
| **Erişim kuralı** | Sürüm değişikliği yapan sistem bileşeni yazar; kullanıcı geçmişi okur |

### 3.36 Olay Kaydı (IncidentEvent) — BC12 (Faz 4)

| Alan | Değer |
|------|-------|
| **Kök** | Olay Kaydı |
| **İç varlıklar** | — |
| **Değişmezler** | Durum geçişleri enum ile sınırlı (açık→kapandı); çözüm notu yalnız çözüm/kapanış öncesi yazılır |
| **Erişim kuralı** | Guardrail/denetim/gate kaynaklı otomatik veya manüel; atanan ve durum güncellenebilir |
| **Durum makinesi** | açık → soruşturmada → hafifletildi → çözüldü → kapandı |

### 3.37 Sapma Gözlemi (DriftObservation) — BC12 (Faz 4)

| Alan | Değer |
|------|-------|
| **Kök** | Sapma Gözlemi |
| **İç varlıklar** | — |
| **Değişmezler** | I22 (yalnız ekle); değer ve pencere başlangıcı zorunlu |
| **Erişim kuralı** | Otomatik (ölçüm/metrik akışı) yazar; kullanıcı zaman serisini okur |

### 3.38 Sapma Uyarısı (DriftAlert) — BC12 (Faz 4)

| Alan | Değer |
|------|-------|
| **Kök** | Sapma Uyarısı |
| **İç varlıklar** | — |
| **Değişmezler** | I19 (yalnız eşik aşımında üretilir); sapma skoru 0-100; önem bilgi/uyarı/kritik |
| **Erişim kuralı** | Analiz servisi yazar; kullanıcı okur; Olay Kaydı'na (BC12) kaynak adayı |
| **Durum makinesi** | üretildi → olaya bağlanabilir (BC12 olay akışına katılır) |

### 3.39 Fatura (BillingInvoice) — BC13 (FR-A6 · HT2)

| Alan | Değer |
|------|-------|
| **Kök** | Fatura |
| **İç varlıklar** | — (KDV, GİB durumu, müşteri bilgileri — değer tipleri) |
| **Değişmezler** | I20 (kuruş + izinli KDV {0,1,10,20} + ara toplam = toplam); GİB durumu akışı enum ile sınırlı |
| **Erişim kuralı** | Stripe webhook yazar (senkron); kullanıcı görüntüler/indirir; RLS izolasyonu (ADR-004) |
| **Durum makinesi** | draft → open → paid \| void \| uncollectible; GİB: none → pending → accepted \| rejected |
| **Transaction sınırı** | Webhook olayı başına tek Fatura transaction'ı; e-Fatura gönderimi GİB durum güncellemesiyle ayrı transaction |

### 3.40 Stripe Müşterisi (StripeCustomer) — BC13 (FR-A6 · HT2)

| Alan | Değer |
|------|-------|
| **Kök** | Stripe Müşterisi |
| **İç varlıklar** | — |
| **Değişmezler** | tenant_id ↔ customer_id birebir; yalnız webhook olaylarında kiracı çözümü için kullanılır |
| **Erişim kuralı** | Webhook yazar; RLS izolasyonu (ADR-004) |
| **Transaction sınırı** | Tek satır; webhook olayı başına en fazla bir yazma |

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
| Envanter Varlığı | Kiracı | Yabancı anahtar (tenant_id) |
| Açıklama Sonucu | Envanter Varlığı | Yabancı anahtar (entity_id) |
| Kırmızı Takım Koşusu | Kırmızı Takım Senaryosu | Yabancı anahtar (case_id, sonuç üzerinden) |
| Kaçak AI Taraması | Kiracı | Yabancı anahtar (tenant_id) |
| Guardrail Kuralı | Kiracı | Yabancı anahtar (tenant_id) |
| Politika Paketi | Kiracı | Yabancı anahtar (tenant_id) |
| Önyargı Testi | Kiracı | Yabancı anahtar (tenant_id) |
| CI/CD Kapı Denetimi | Kiracı | Yabancı anahtar (tenant_id) |
| Ajan İzi | Kiracı | Yabancı anahtar (tenant_id) |
| Kırmızı Takım Senaryosu | Kiracı | Yabancı anahtar (tenant_id) |
| Prompt Denetimi | Kiracı | Yabancı anahtar (tenant_id) |
| Model Kıyaslaması | Kiracı | Yabancı anahtar (tenant_id) |
| Maliyet Kaydı | Kiracı | Yabancı anahtar (tenant_id) |
| Kullanım Ölçümü | Kiracı | Yabancı anahtar (tenant_id) |
| Optimizasyon Önerisi | Kiracı | Yabancı anahtar (tenant_id) |
| Versiyon Kaydı | Kiracı | Yabancı anahtar (tenant_id) |
| Olay Kaydı | Kiracı | Yabancı anahtar (tenant_id) |
| Sapma Gözlemi | Kiracı | Yabancı anahtar (tenant_id) |
| Sapma Uyarısı | Kiracı | Yabancı anahtar (tenant_id) |
| Fatura | Kiracı | Yabancı anahtar (tenant_id, RLS) |
| Stripe Müşterisi | Kiracı | Yabancı anahtar (tenant_id, RLS) |

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
| Envanter risk değerlendirmesi | RegistryEntity + RiskAssessment | İlk değerlendirme varlıkla aynı transaction; sonrakiler ayrı (append-only) |
| Politika paketi uygulama | PolicyPack + N PolicyControl | Tek transaction; framework çakışması reddedilir (I21) |
| Guardrail değerlendirmesi | GuardrailEvaluation tek transaction | Runtime akışı; eşleşme + aksiyon kaydı birlikte |
| Kırmızı takım koşusu | RedTeamRun + N RedTeamResult | Tek transaction; savunma skoru sonuçlardan türetilir (I18) |
| Ajan izi kapanışı | AgentTrace + adım güncellemeleri | İz başlatma tek transaction; adımlar ayrı transaction (eventual) |
| Fatura senkronu | BillingInvoice tek transaction | Stripe webhook olayı başına bir yazma; e-Fatura GİB durumu ayrı transaction |
| Gözlem yazma | CostEntry / UsageMetric / DriftObservation tek satır | Telemetri append-only; toplu yazım satır bazında commit edilir |

---

## 6. GeoLens İçin Çıkarımlar

1. **11'den 19 toplam köküne genişleme** (MVP → HT1). Yeni toplamlar BC7-BC10 bağlamlarına dağılmıştır: ArchiveEntry, ArchiveExport, ConversationSnapshot, SEOConnection, SentimentScore, HallucinationFlag, GapSnapshot, TopicCluster. Her biri ait olduğu bağlamın tutarlılık sınırlarını takip eder.
2. **Salt ekleme (append-only) toplamlar** artmıştır: PanelVersion, CalculationRun, ArchiveEntry, ConversationSnapshot, SentimentScore, GapSnapshot — değiştirilemez ve silinemez yapıdadır. Bu, denetlenebilirlik ve veri bütünlüğü ilkelerini güçlendirir.
3. **Ölçüm İşi** en karmaşık yaşam döngüsüne sahip toplamdır: 5 durum, sınırlı yeniden deneme, kısmi sonuç yönetimi.
4. **Toplamlar arası referanslar yalnız ID üzerinden** yapılır. Bu, bağlamlar arası gevşek bağlılığı korur ve 0305 modül sınırlarıyla uyumludur.
5. **0305 (Bounded Contexts)** her toplam kökünü ait olduğu bağlam paketine atar. Transaction sınırları modül sınırlarıyla çakışır.
6. **SEOConnection** diğer toplamlardan farklı olarak şifreli token saklama zorunluluğu (I13) ve OAuth2 yenileme mekanizması nedeniyle özel bir transaction modeline sahiptir.
7. **GapSnapshot**, UPSERT kullanan tek toplamdır. Aynı brand × competitor × period kombinasyonu tekrar hesaplandığında mevcut kayıt güncellenir; bu, gap analizinin periyodik doğasıyla uyumludur.
8. **Faz 4 genişletmesi (v1.3):** toplam kök sayısı 19'dan 40'a çıktı — BC11 (10 kök), BC12 (9 kök), BC13 (2 kök). 0302 v1.3 bağlam haritasıyla birebir senkrondur.
9. **Append-only yoğunluğu:** Yeni köklerin çoğu yalnız eklemelidir (GateCheck, ExplainResult, PromptAudit, ModelBenchmark, CostEntry, UsageMetric, VersionEntry, DriftObservation, RiskAssessment, GuardrailEvaluation, RedTeamRun). Bu, denetlenebilirlik ilkesini (I6/I22) gözlem ve güvenlik alanına taşır.
10. **İzolasyon stratejisi:** BC13 (Fatura, Stripe Müşterisi) RLS ile izole edilir (ADR-004); BC11/BC12 kökleri handler WHERE koşullarıyla kiracı izolasyonu sağlar — 0302 v1.3 §9.9 ile senkron. Her iki strateji de I1 değişmezini sağlar.
11. **RedTeamRun → RedTeamCase bağımlılığı:** Koşu, senaryo toplamına sonuçlar üzerinden bağlanır; savunma skoru koşu içi sonuçlardan türetilir (I18). Senaryo silinmesi ilişkili sonuçları kademeli sildiğinden kapanmış koşu skorlarının tutarlılığı HT2 kısıtlı-silme kararına bağlanmıştır (O-6).

---

## 7. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | Benchmark toplulaştırma modeli | ⏳ HT2'de karara bağlanır. |
| O-2 | Öneri-etki takibi ayrı toplam mı? | ⏳ Mevcut karar: iç varlık (0302 ile uyumlu). |
| O-3 | ULID indeks performansı | ⏳ Pilot öncesi test. AVIP D-35 (ULID) onaylandı. |
| O-4 | Hallüsinasyon İşareti (HallucinationFlag) ayrı toplam mı, SentimentScore altında mı? | ⏳ Mevcut karar: ayrı toplam (bağımsız yaşam döngüsü, doğrulama mekanizması). |
| O-5 | SC Sorgu Verisi ve GA4 Ölçüm Verisi — ayrı toplam olarak kalmalı mı, SEOConnection altında mı? | ⏳ Mevcut karar: ayrı toplam (farklı schema ve yaşam döngüsü). |
| O-6 | RedTeamCase silinmesi, ilişkili RedTeamResult'ları kademeli sildiği için kapanmış RedTeamRun savunma skorlarının tutarlılığını etkiler. Kısıtlı silme (yalnız koşusuz senaryo silinebilir) kuralı uygulanmalı mı? | ⏳ HT2'de karara bağlanır. |

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
| 1.3 | 04.08.2026 | **Faz 4 ve HT2 aggregate genişletmesi:** BC11 (10 kök), BC12 (9 kök), BC13 (2 kök) için 21 yeni toplam kökü eklendi (RegistryEntity, DiscoveryScan, GuardrailRule, PolicyPack, BiasTest, GateCheck, ExplainResult, AgentTrace, RedTeamCase, RedTeamRun, PromptAudit, ModelBenchmark, CostEntry, UsageMetric, OptimizationRecommendation, VersionEntry, IncidentEvent, DriftObservation, DriftAlert, BillingInvoice, StripeCustomer). Toplam kök sayısı 19'dan 40'a çıktı. Her toplam için detaylı tanım eklendi. §4 referans kuralları, §5 transaction kapsamı, §6 çıkarımlar güncellendi. §7 açık sorulara O-6 (kısıtlı senaryo silme) eklendi. 0302 v1.3, 0209 (Faz 4) ve 0210 (rakip kapanışı) ile senkron. |
