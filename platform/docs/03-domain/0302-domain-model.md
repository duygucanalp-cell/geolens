# 0302 · Domain Model

| Alan | Değer |
|---|---|
| Doküman ID | 0302 |
| Proje | GeoLens Platform |
| Versiyon | 1.2 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 28 Temmuz 2026 |
| İlişkili | 0301, 0204, 0207, 0303, 0304, 0305, 0306, 0309, 0310, 0312, 0511, 0416, 0417, 0418, 0419 |

---

## 1. Amaç ve Kapsam

Bu doküman GeoLens Platform'un alan modelini sabitler: bağlamlar, varlıklar, ilişkiler, değişmezler ve yaşam döngüleri. 0301 çekirdek kavramlarının yapısal karşılığıdır. 0303 (aggregates), 0304 (domain events), 0305 (bounded contexts) ve 0306 (domain services) bu modelden türetilir.

Kapsam dışı: DDL ve migration'lar, API şekilleri, arayüz modelleri, performans optimizasyonları.

> **Tasarım filtresi bağlantısı:** Bu doküman **F2** (ölçek — model tüm segmentleri tek yapıda kapsar) ve **F5** (moat — değişmezler ve panel versiyonlama rakip taklidini zorlaştırır) filtrelerine kanıt sağlar.

---

## 2. Yöntem ve Adlandırma

- İş dili Türkçedir; kod ve şema adları İngilizcedir ve parantez içinde verilir (örnek: kiracı / tenant).
- Bağlamlar (bounded context) 0305 modül adaylarıyla birebir eşlenir.
- Her bağlam için toplam kökleri (aggregate root) **koyu** ile işaretlenir.
- Değişmezler (invariant) her zaman doğru kalması zorunlu kurallardır (§6).
- Durum makineleri yaşam döngüsü geçişlerini tanımlar (§7).
- Kimlik stratejisi: ULID (26 karakter, zaman-sıralanabilir, metin tipi).

---

## 3. Bağlam Haritası

| Bağlam (0305 modül adayı) | Sorumluluk | Tükettiği / Beslediği |
|---|---|---|
| BC1 · Kimlik ve Kiracılık (identity) | Kiracı, çalışma alanı, kullanıcı, üyelik, rol, paket hakları, davet, SSO yapılandırması (HT1) | Tüm bağlamlara kiracı bağlamı ve hak kararları verir |
| BC2 · Yapılandırma (config) | Marka, site, pazar, prompt seti, şablon kütüphanesi, motor kapsamı, izleme planı, LLM bot takibi (HT1), schema korelasyonu (HT1), rakip yönetimi (HT1) | BC3'e panel tanımı; BC1'den hak sınırları |
| BC3 · Ölçüm ve Hesap (measure) | Ölçüm işi, ham yanıt, alıntı, calculation_run, skor, trend, site denetimi, duygu analizi (HT1), hallüsinasyon tespiti (HT1), per-platform metrikler (HT1), competitive visibility (HT1) | BC2'den panel; BC4-BC5'e skor ve olaylar; BC7-BC9'a ham veri |
| BC4 · İçgörü (insight) | Öneri, öneri-etki (HT1), competitive gap (HT1), content gap (HT1), technical GEO önerileri (HT1), benchmark (HT2) | BC3'ten skor ve bulgular; BC7-BC8'den geçmiş veri |
| BC5 · Bildirim ve Raporlama (delivery) | Uyarı kuralı, uyarı, kanal, özet, rapor, alert history (HT1), webhook çeşitlendirme (HT1) | BC3-BC4'ten olaylar; dış kanallara çıkış |
| BC6 · Denetim ve Kota (governance) | Denetim izi, kullanım kayıtları, kota sayaçları, denetim izi export (HT1) | Tüm bağlamlardan yazma olayları |
| **BC7 · Arşiv (archive)** | **Response archive, S3 versiyonlu saklama, retention policy, toplu dışa aktarım** | **BC3'ten ham yanıt alır; BC5'e export rapor verir** |
| **BC8 · Replay (replay)** | **Conversation replay, snapshot capture, side-by-side karşılaştırma, conversation diff** | **BC3'ten ölçüm sonuçlarını alır** |
| **BC9 · SEO (seo)** | **Google Search Console + GA4 OAuth2 bağlantısı, periyodik veri senkronizasyonu, SEO veri depolama** | **Harici Google API'lerden veri çeker; UI'a veri sağlar** |
| **BC10 · Denetim ve Analiz (audit & analysis)** | **Duygu analizi, hallüsinasyon tespiti, competitive gap analizi, content/topic/prompt gap, denetim izi genişletme (HT1 export)** | **BC3'ten ham yanıt ve skor tüketir; BC4'ü gap sonuçlarıyla besler; BC6 ile birlikte denetim izi genişletmesini yönetir** |

---

## 4. Varlıklar ve İlişkiler

### BC1 · Kimlik ve Kiracılık

| Varlık | Önemli Alanlar | İlişkiler |
|--------|---------------|-----------|
| **Kiracı (Tenant)** | ad, tür (standart/ajans), paket (free/pro/business/enterprise), durum (aktif/pasif/askıda) | 1-N Çalışma Alanı, Üyelik, Kullanım Kaydı |
| **Çalışma Alanı (Workspace)** | ad, durum (aktif/arşiv/devredildi); her kiracıda en az bir varsayılan alan | N-1 Kiracı; 1-N Marka, Prompt Seti, İzleme Planı |
| Kullanıcı (User) | e-posta, kimlik doğrulama profili, ad, soyad | N-N Kiracı (Üyelik ile) |
| Üyelik (Membership) | rol (yönetici/editör/izleyici), çalışma alanı erişim kapsamı | Kullanıcı ↔ Kiracı |
| Paket Hakları (Entitlement) | hak anahtarları: motor seti, frekans tavanı, kota, white-label, koltuk sayısı | 1-1 Kiracı (yapılandırma) |
| Davet (Invitation) | e-posta, rol, süre, durum (bekliyor/kabul/red/süre doldu) | N-1 Kiracı |

### BC2 · Yapılandırma

| Varlık | Önemli Alanlar | İlişkiler |
|--------|---------------|-----------|
| **Marka (Brand)** | ad, tür (kendi/rakip), eş anlamlılar, sektör etiketi | N-1 Çalışma Alanı; 1-N Site |
| Site (Site) | alan adı, doğrulama durumu (doğrulanmamış/bekliyor/doğrulandı) | N-1 Marka; 1-N Denetim Koşusu |
| Pazar (Market) | dil + bölge (TR öncelikli) | Panel bileşeni |
| **Prompt Seti (PromptSet)** | promptlar; etiket: markalı/kategori; kaynak şablon referansı; versiyon | N-1 Çalışma Alanı; Panel bileşeni |
| Şablon Kütüphanesi (PromptTemplate) | sektör etiketi, dil, prompt şablonu; sistem düzeyi (kiracısız) | 1-N Prompt Seti (türetme) |
| Motor Kapsamı (EngineScope) | seçili bağdaştırıcılar; paket hakkı sınırında (FR-B5) | Panel bileşeni |
| İzleme Planı (MonitoringPlan) | frekans (haftalık/günlük), pencere, başlangıç zamanı | N-1 Çalışma Alanı; Zamanlayıcı girdisi |

### BC3 · Ölçüm ve Hesap

| Varlık | Önemli Alanlar | İlişkiler |
|--------|---------------|-----------|
| **Panel Versiyonu (PanelVersion)** | prompt seti anlık görüntüsü + motor kapsamı + pazar; versiyon no; değiştirilemez | 1-N Ölçüm İşi, Skor |
| **Ölçüm İşi (MeasurementJob)** | idempotent anahtar (workspace+panel+zaman aralığı), durum (kuyrukta/çalışıyor/tamam/kısmi/başarısız), deneme sayacı | N-1 Panel Versiyonu; 1-N Ham Yanıt |
| Ham Yanıt (RawResponse) | S3 referansı, içerik karması (SHA-256), kademe etiketi, motor adı, zaman damgası; değiştirilemez | N-1 Ölçüm İşi; 1-N Alıntı |
| Alıntı (Citation) | url, kaynak başlığı, konum (metin içinde), motor adı; FR-D2 zorunlu | N-1 Ham Yanıt |
| **Hesap Koşusu (CalculationRun)** | girdi kümesi karması, faktör anlık görüntüsü, algoritma versiyonu; değiştirilemez | N-1 Ölçüm İşi; 1-N Skor |
| Skor (Score) | değer (0-100), güven aralığı (alt/üst), fidelite etiketi, tazelik damgası, motor kırılımı | N-1 Hesap Koşusu, Panel Versiyonu, Marka |
| Trend Noktası (TrendPoint) | skor referansı, zaman penceresi | Seri: Marka × Panel Versiyonu |
| **Denetim Koşusu (SiteAuditRun)** | durum (çalışıyor/tamam/başarısız), süre, site referansı | N-1 Site; 1-N Bulgu |
| Bulgu (AuditFinding) | kategori (bot izni/SSR/erişilebilirlik/içerik), önem (kritik/yüksek/orta/düşük), düzeltme önerisi | N-1 Denetim Koşusu |

### BC4 · İçgörü

| Varlık | Önemli Alanlar | İlişkiler |
|--------|---------------|-----------|
| **Öneri (Recommendation)** | kanıt derecesi (deneysel/korelasyonel/denenebilir), gerekçe, durum (açık/uygulandı/reddedildi); politika filtresinden geçmiş | N-1 Çalışma Alanı; kaynak: Skor/Bulgu |
| Öneri Etkisi (RecommendationImpact) | işaretli karşılaştırma; öncesi/sonrası skor farkı (HT1) | 1-1 Öneri |
| Benchmark İstatistiği (BenchmarkStat) | anonim küme istatistiği; ortalama, medyan, çeyreklik; ≥5 kiracı eşiği (NFR-13) | Toplulaştırma çıktısı |

### BC5 · Bildirim ve Raporlama

| Varlık | Önemli Alanlar | İlişkiler |
|--------|---------------|-----------|
| Uyarı Kuralı (AlertRule) | eşik (yüzde değişim/mutlak değer), kanal seçimi (e-posta/Slack/webhook), durum (aktif/pasif) | N-1 Çalışma Alanı |
| **Uyarı (Alert)** | tetik bağlamı (skor değişimi), digest grubu (günlük), durum (üretildi/iletildi/geri bildirim alındı) | N-1 Kural; kaynak: Skor değişimi |
| Kanal (NotificationChannel) | tür (e-posta/Slack/webhook), hedef adres/URL, doğrulama durumu | N-1 Çalışma Alanı |
| Özet (Digest) | haftalık içerik, derin bağlantılar (pano URL); zaman damgası | N-1 Çalışma Alanı |
| **Rapor (Report)** | tür (standart/white-label), şablon, marka ayarları (logo/renk), durum (kuyrukta/üretiliyor/hazır/başarısız), S3 referansı, imzalı URL | N-1 Çalışma Alanı; kaynak: Skorlar |

### BC6 · Denetim ve Kota

| Varlık | Önemli Alanlar | İlişkiler |
|--------|---------------|-----------|
| **Denetim Kaydı (AuditLogEntry)** | aktör (kullanıcı kimliği), eylem (oluşturma/güncelleme/silme), kaynak türü/kimliği, özet, zaman damgası; yalnız ekleme; **HT1: export desteği (CSV)** | Tüm yazma yolları |
| **Kullanım Kaydı (UsageRecord)** | sayaç türü (prompt/motor çağrısı/rapor/ölçüm), dönem başlangıç/bitiş, miktar | N-1 Kiracı |
| **Kota Sınırı (QuotaLimit)** | sayaç türü, dönem, limit değer, aşım politikası (beklet/reddet) | N-1 Kiracı |

### BC7 · Arşiv (HT1)

| Varlık | Önemli Alanlar | İlişkiler |
|--------|---------------|-----------|
| **Arşiv Girdisi (ArchiveEntry)** | brand_id, engine_name, prompt_text, response_preview (≤1000 karakter), response_full (tam metin), S3 referansı, versiyon no (otomatik artan), içerik karması (SHA-256), tenant_id; değiştirilemez | N-1 Marka; 1-1 Ölçüm İşi (ölçüm bazlı); S3'te versiyonlu depolama |
| Saklama Politikası (RetentionPolicy) | brand_id (opsiyonel → marka bazlı veya genel), saklama süresi (gün), eylem (otomatik sil/arşivle/uyarı), tenant_id; **— tasarım aşaması, HT2'de implemente edilecek** | N-1 Kiracı; değerlendirilir: Arşiv Girdisi |
| Arşiv Dışa Aktarım (ArchiveExport) | seçili dönem başlangıç/bitiş, filtre (engine, brand), format (json/csv), durum (kuyrukta/hazır/başarısız), S3 referansı, imzalı URL | N-1 Çalışma Alanı; kaynak: Arşiv Girdisi |

**Değişmez (BC7):** Arşiv girdisi yazıldıktan sonra değiştirilemez ve silinemez. İçerik karması bütünlük doğrulaması için kullanılır (I5 genişletmesi).

### BC8 · Replay (HT1)

| Varlık | Önemli Alanlar | İlişkiler |
|--------|---------------|-----------|
| **Konuşma Anlık Görüntüsü (ConversationSnapshot)** | brand_id, prompt_text, engine_name, response_preview (≤500 karakter), response_full, içerik karması (SHA-256), S3 referansı (opsiyonel), tenant_id; yalnız ekle | N-1 Marka, N-1 Engine; 1-N DiffResult |
| **Fark Sonucu (DiffResult)** | snapshot_a_id, snapshot_b_id, has_changed (bool), değişiklik özeti, analiz zamanı | 1-2 ConversationSnapshot |

**Değişmez (BC8):** Her snapshot benzersiz bir brand × engine × prompt × zaman bileşimini temsil eder. Aynı bileşimle ikinci snapshot alınabilir (zaman farklı), ancak var olan snapshot değiştirilemez.

### BC9 · SEO (HT1)

| Varlık | Önemli Alanlar | İlişkiler |
|--------|---------------|-----------|
| **SEO Bağlantısı (SEOConnection)** | platform (search_console / ga4), email (bağlı Google hesabı), access_token (şifreli), refresh_token (şifreli), token_expiry, is_active, last_synced_at, workspace_id | N-1 Çalışma Alanı; Google OAuth2 yönetimi |
| OAuth2 Belirteci (OAuth2Token) | access_token, refresh_token, token_expiry, scope; geçici (5 dk) state token | 1-1 SEO Bağlantısı (yenileme) |
| SC Sorgu Verisi (SearchConsoleQuery) | query (arama sorgusu), clicks, impressions, ctr, avg_position, brand_id (eşleşen marka), measured_at | N-1 SEO Bağlantısı; N-1 Marka |
| GA4 Ölçüm Verisi (GA4Metric) | page_views, sessions, bounce_rate, avg_session_duration, brand_id (eşleşen marka), measured_at | N-1 SEO Bağlantısı; N-1 Marka |

**Değişmez (BC9):** SEO bağlantısı yalnız workspace bazlıdır; token'lar şifreli saklanır, düz metin loga yazılamaz. Her bağlantı için en fazla bir aktif OAuth2 oturumu vardır.

### BC10 · Denetim ve Analiz (HT1)

| Varlık | Önemli Alanlar | İlişkiler |
|--------|---------------|-----------|
| **Duygu Skoru (SentimentScore)** | brand_id, engine_name, overall_sentiment (0-1), positive_score, neutral_score, negative_score, mention_count, analyzed_at | N-1 Marka; N-1 Engine; kaynak: Ham Yanıt |
| **Hallüsinasyon İşareti (HallucinationFlag)** | brand_id, engine_name, hallucination_type (çelişki/yokluk/yanlış atıf/çarpıtma), severity (low/medium/high/critical), description, confidence (0-1), verified (null/bool), created_at | N-1 Marka; N-1 Engine; kaynak: Ham Yanıt |
| **Gap Anlık Görüntüsü (GapSnapshot)** | brand_id, competitor_id, period_start/end, 5 gap türü (visibility/citation/content/topic/prompt), competitive_score (0-100), breakdown (JSON ile gap detayı) | N-1 Marka; N-1 Rakip (Brand); kaynak: Skor + Citation |
| **Gap Detayı (GapDetail)** | gap_value, normalized (0-100), brand_value, competitor_value, direction (brand_ahead/competitor_ahead/equal) | 1-1 Gap Görüntüsü × gap türü (5 tane) |
| **Gap Önerisi (GapRecommendation)** | gap_id, gap_type, priority, description, impact, kanıt_derecesi, related_fr | N-1 GapSnapshot |
| **Bot Erişim Kaydı (BotAccessRecord)** | brand_id, bot_name, erişim_durumu (izinli/engelli/bulunamadı), robots.txt yönergesi, test_tarihi, site_url | N-1 Marka; kaynak: Site Denetimi |
| **Şema Analizi (SchemaAnalysis)** | brand_id, schema_type, kullanım_var_mı (bool), geçerlilik_oranı, önerilen_iyileştirme, analiz_tarihi | N-1 Marka |
| **İçerik Boşluğu (ContentGap)** | brand_id, konu, önem_derecesi, mevcut_durum, öneri, competitor_reference (opsiyonel), tespit_tarihi | N-1 Marka; N-1 Rakip (opsiyonel) |
| **Konu Kümesi (TopicCluster)** | brand_id, konu_adi, alt_konular (JSON), önerilen_içerik_türü, öncelik, durum (önerildi/planlandı/uygulandı) | N-1 Marka |

**Değişmez (BC10):** Duygu analizi ve hallüsinasyon tespiti yalnız mevcut ham yanıtlar üzerinde çalışır; ayrı motor çağrısı yapılmaz (P6). Gap analizi daima en az 2 marka (bir marka + bir rakip) arasında yapılır; tek markalı gap hesaplanamaz. Denetim izi genişletmesi (HT1 export), BC6 ile paylaşılan denetim izi verisine dayanır.

---

## 5. Panel ve Versiyonlama Modeli

Panel; prompt seti içeriği, motor kapsamı ve pazarın birlikte dondurulmuş halidir. Ölçüm dürüstlüğünün temelidir.

**Kurallar:**
1. Skor daima üretildiği panel versiyonuna bağlanır; hangi soruların, hangi motorlarda, hangi pazarda sorulduğu skordan geriye doğru okunabilir.
2. Trend karşılaştırması birincil olarak aynı panel versiyonu içinde yapılır. Versiyon sınırı zaman serisinde görünür işaretle (dikey kesik çizgi + araç ipucu) gösterilir; seriler dikişsiz birleştirilmez.
3. Şablon kütüphanesinden türetilen setlerde kaynak şablon ve sürümü izlenir; kütüphane güncellemesi kiracı setini kendiliğinden değiştirmez.
4. Hesap algoritması ve faktör setinin versiyonu panel versiyonundan bağımsız olarak calculation_run içinde saklanır. İki versiyon ekseni (ne soruldu, nasıl hesaplandı) birbirine karışmaz.

---

## 6. Değişmezler (Invariants)

| ID | Değişmez | Bağ |
|:--:|----------|:---:|
| I1 | Sistem şablonları dışında her kayıt bir çalışma alanına, her çalışma alanı bir kiracıya bağlıdır; kiracısız veri yoktur. | NFR-1, 0310 |
| I2 | Skor, geçerli bir calculation_run olmadan var olamaz; calculation_run girdileri yazıldıktan sonra değiştirilemez. | NFR-7, İ3 |
| I3 | Her skor fidelite etiketi ve güven aralığı taşır; bu alanlar boş bırakılamaz. | İ2, FR-C5, FR-C6 |
| I4 | Panel içeriği değişimi yeni panel versiyonu üretir; skorlar üretildikleri versiyona bağlı kalır. | Ölçüm dürüstlüğü (§5) |
| I5 | Ham yanıt arşivi silinemez ve üzerine yazılamaz; içerik karması bütünlüğü doğrular. | NFR-11 |
| I6 | Denetim izi yalnız eklemelidir; hiçbir alan işlemi mevcut kaydı değiştiremez veya silemez. | NFR-6 |
| I7 | Motor politikalarına aykırı taktik içeren öneri kalıcılaştırılamaz; filtre üretim önkoşuludur. | FR-E2 |
| I8 | Uyarı yalnız anlamlılık kuralını geçen değişimden üretilebilir. | FR-F1 |
| I9 | Kota sayacı aşımında motor çağrısı alan düzeyinde engellenir; iş ertelenir. | NFR-16 |
| I10 | Arşivlenen çalışma alanında yeni ölçüm üretilmez; tarihçe okunur kalır. | FR-G3 |
| I11 | Rapor yalnız yayınlanmış (etiketli) skorlardan üretilir; etiketsiz veri hiçbir çıktı yüzeyine giremez. | İ2, FR-F4 |
| I12 | Arşiv girdisi yazıldıktan sonra değiştirilemez ve silinemez; içerik karması bütünlük doğrulaması sağlar. | FR-D13, NFR-11 |
| I13 | SEO bağlantı token'ları şifreli saklanır; düz metin hiçbir loga veya hata mesajına yazılamaz. | NFR-3, FR-B8 |
| I14 | Duygu analizi ve hallüsinasyon tespiti yalnız mevcut ham yanıtlar üzerinde çalışır; ayrı motor çağrısı yapılmaz. | P6, FR-D7, FR-D8 |
| I15 | Competitive gap analizi daima en az 2 marka arasında yapılır; tek markalı gap hesaplanamaz. | FR-D11, 0419 |
| I16 | Conversation snapshot'ı yalnız yayınlanmış (tamamlanmış) ölçüm işlerinden alınır; devam eden işten snapshot alınamaz. | FR-D12 |

---

## 7. Durum Makineleri (State Machines)

| Varlık | Geçişler | Not |
|--------|----------|-----|
| Ölçüm İşi | kuyrukta → çalışıyor → tamamlandı \| kısmi \| başarısız; başarısız → kuyrukta (sınırlı deneme, max 3) | Kısmi sonuç etiketle yayınlanır veya bekletilir (0309 kuralı) |
| Rapor | kuyrukta → üretiliyor → hazır \| başarısız; başarısız → kuyrukta | Hazır: imzalı URL üretilir; denetim kaydı düşer |
| Öneri | açık → uygulandı \| reddedildi; uygulandı → etki izleniyor (HT1) | İşaretler telemetriye yazılır |
| Uyarı | üretildi → iletildi → geri bildirim: yerinde \| yanlış alarm | Digest grubuna katılabilir; geri bildirim M11 beslemesi |
| Çalışma Alanı | aktif → arşiv; arşiv → aktif (geri alma) \| devredildi (HT1) | I10 uygulanır; devir denetim kaydıyla |
| Kiracı | aktif → pasif → askıda; askıda → aktif | Pasif: yeni ölçüm durur; askıda: tüm erişim kesilir |
| Davet | bekliyor → kabul \| red \| süre doldu | Süre: 7 gün |
| **Konuşma Anlık Görüntüsü** | **oluşturuldu → karşılaştırıldı \| arşivlendi** | **Snapshot oluşturulduktan sonra değiştirilemez; karşılaştırma yeni bir DiffResult üretir** |
| **Arşiv Girdisi** | **kaydedildi → dışa aktarıldı \| bekliyor; dışa aktarıldı → (süre sonu) → silindi** | **Süre RetentionPolicy tarafından belirlenir; silme otomatiktir** |
| **Gap Analizi** | **hesaplanıyor → tamamlandı \| başarısız** | **Ölçüm sonrası otomatik tetiklenir; gap eşik aşımı alert üretir** |
| **SEO Bağlantısı** | **bağlı → token yenileniyor → bağlı; bağlı → kopuk → (manüel) → yeniden bağla** | **Token expiry yaklaştığında otomatik yenileme dener; başarısız olursa kopuk durumuna geçer** |

---

## 8. Sözlük Hizası

Model, 0006 sözlüğündeki yerleşik terimleri aynen kullanır. Aşağıdaki terimler bu dokümanla dile girmiştir ve 0006 v1.1 adayıdır:

| Aday Terim | Kısa Tanım |
|------------|-----------|
| Çalışma alanı (workspace) | Kiracı altındaki izole çalışma birimi; ajans modelinde müşteri karşılığı |
| Panel versiyonu (panel version) | Prompt seti + motor kapsamı + pazarın dondurulmuş hali; skor karşılaştırma birimi |
| Bulgu (finding) | Site erişim denetiminin önem dereceli tekil çıktısı |
| Digest | Aynı gün tetiklerinin birleştirildiği toplu bildirim |
| Calculation Run | Değiştirilemez skorlama kaydı; girdi, faktör ve algoritma versiyonunu saklar |
| **Arşiv girdisi (archive entry)** | **Ham AI yanıtının versiyonlu, değiştirilemez arşiv kaydı; S3'te saklanır** |
| **Konuşma anlık görüntüsü (conversation snapshot)** | **Belirli bir andaki AI yanıtının dondurulmuş hali; replay için kullanılır** |
| **Gap (competitive gap)** | **Marka ile rakip arasındaki görünürlük/alıntı/içerik/konu/prompt farkı** |
| **SEO bağlantısı (SEO connection)** | **Google Search Console veya GA4'e OAuth2 ile kurulan veri bağlantısı** |
| **Gap türü (gap type)** | **5 tür: visibility, citation, content, topic, prompt — 0419'da detaylandırılır** |

---

## 9. GeoLens İçin Çıkarımlar

1. **0303 (Aggregates)** her bağlam için toplam köklerini ve erişim kurallarını bu modelden türetir.
2. **0305 (Bounded Contexts)** bağlam haritasıyla birebir eşlenir; HT1'de BC7-BC10 eklenmiştir; bağlamlar arası erişim yalnız kök varlık arayüzlerinden yapılır.
3. **0306 (API Design)** kaynak adları varlık adlarından türetilir; dış kimlikler opak, iç kimlikler ULID.
4. **0309 (Scoring Engine)** calculation_run alanları ve iki versiyon ekseninin ayrımı (§5 kural 4) hesap motorunun tasarım girdisidir.
5. **0310 (Security)** üyelik-çalışma alanı erişim modeli rol tasarımının ilk sorusudur.
6. **HT1 genişletmesi (v1.2):** 4 yeni bağlam (BC7-BC10) ve 15+ yeni varlık eklenmiştir. Bu genişleme, 0511 (HT1 Sistem Mimarisi) dokümanındaki bounded context genişletmesini yansıtır. Yeni bağlamlar, mevcut BC1-BC6 ile aynı RLS politikası, ULID kimlik stratejisi ve değişmez kurallarına tabidir.
7. **0416-0419 framework bağlantısı:** BC10 varlıkları (SentimentScore, HallucinationFlag, GapSnapshot, ContentGap, TopicCluster) sırasıyla 0416 (Sentiment & Hallucination), 0419 (Competitive Gap Analysis), 0418 (Content GEO) framework dokümanlarındaki metodolojilerle birebir eşlenir.

---

## 10. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | Benchmark varlığının anonimleştirme modeli | ⏳ HT2 öncesi netleşir. AVIP D-60 (≥5 kiracı eşiği) devralındı. |
| O-2 | Öneri-etki takibi varlık modeli (HT1) | ✅ **KAPANDI:** RecommendationImpact varlığı BC4'e eklendi (FR-E4 implementasyonu ile birlikte). |
| O-3 | ULID'nin indeks performans etkisi | ⏳ Pilot öncesi test. AVIP D-35 (ULID kararı) onaylandı. |

### Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|----|-------|--------|
| **D-35** | **ULID kimlik stratejisi:** 26 karakter, zaman-sıralanabilir. TL 21.07.2026. | AVIP 0302 O-2 |
| **D-34** | **Panel versiyon trend sınırı:** Dikey kesik çizgi + hover. TL 21.07.2026. | AVIP 0302 O-4 |

---

## Kaynaklar

- 0301 Core Concepts — çekirdek kavramlar ve tanımlar
- 0204 PRD — FR/NFR bağları, ürün ilkeleri
- 0207 Feature Catalog — özellik-envanter bağları
- 0006 Glossary — sözlük
- archive/avip-v1/0302-domain-model.md — AVIP alan modeli referansı

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: 6 bağlamlı harita, 30+ çekirdek varlık, panel versiyonlama modeli (2 versiyon ekseni), 11 değişmez, 7 durum makinesi, sözlük hizası. 0301'den türetilmiştir. |
| 1.1 | 22.07.2026 | AVIP kapalı kararları taşındı: D-35 (ULID), D-34 (trend sınırı). Devralınan Kararlar eklendi. |
| 1.2 | 28.07.2026 | **HT1 domain model genişletmesi:** 4 yeni bağlam (BC7 Arşiv, BC8 Replay, BC9 SEO, BC10 Analiz) ve 15+ yeni varlık eklendi (ArchiveEntry, RetentionPolicy, ConversationSnapshot, DiffResult, SEOConnection, OAuth2Token, SearchConsoleQuery, GA4Metric, SentimentScore, HallucinationFlag, GapSnapshot, GapDetail, GapRecommendation, BotAccessRecord, SchemaAnalysis, ContentGap, TopicCluster, SSOConfig). 5 yeni değişmez (I12-I16), 4 yeni durum makinesi (snapshot, arşiv, gap, SEO). Sözlüğe 5 yeni terim eklendi. Mevcut BC1-BC6 bağlamları HT1 genişletmelerini yansıtacak şekilde güncellendi. |
