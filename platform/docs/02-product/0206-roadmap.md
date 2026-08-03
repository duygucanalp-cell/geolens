# 0206 · Post-MVP Yol Haritası

| Alan | Değer |
|---|---|
| Doküman ID | 0206 |
| Proje | GeoLens Platform |
| Versiyon | 1.4 |
| Durum | Approved |
| Sahip | U2 AI Studio · Product |
| Tarih | 28 Temmuz 2026 |
| İlişkili | 0205, 0201, 0204, 0003, 0004, specification/docs/00-overview/0005-version-sync-plan, 0416, 0417, 0418, 0419 |

---

## 1. Amaç

Bu doküman Faz 2'yi kapatır. MVP sonrası GeoLens ürün evrimini takvim tarihleriyle değil, **tetikleyici tabanlı pencerelerle** tanımlar.

Girdisi: 0205 §4.3'teki bilinçli açıklar, daraltılmış kapsamlar, Turkcell RFP sonrası genişletilmiş gereksinimler ve persona/paket iskeleti.

---

## 2. Yol Haritası İlkeleri

| # | İlke | Anlamı |
|---|------|--------|
| **Y1** | Tarih değil tetikleyici | Pencereler koşulla açılır (kapı kriteri, karar, veri eşiği). Takvim taahhüdü verilmez. |
| **Y2** | Öğrenme yeniden sıralar | Pilot ve M4 geri bildirimi pencere içi sırayı değiştirebilir. |
| **Y3** | Güven gevşemez | Sert kurallar (fidelite, açıklanabilirlik, izolasyon) hiçbir pencerede pazarlığa açılmaz. |
| **Y4** | Tek platform korunur | Her pencere kalemi paket haklarıyla açılır; kod dalı yaratılmaz (İ1). |
| **Y5** | Pencere gözetimi | TR fırsat penceresi (0101 §8) ve motor erişim seyri (0102) düzenli izlenir; sıralama buna duyarlıdır. |

---

## 3. Pencere Modeli

| Pencere | İçerik | Giriş Tetikleyicisi |
|---------|--------|---------------------|
| **HT1** · Hızlı Takip 1 | Masa bahisi kapanışları, daraltılmış kapsam genişlemeleri, Turkcell RFP kapsam derinleştirme, ikinci/üçüncü halka motorlar | Pilot çıkış kapısı geçildi (0205 §7) |
| **HT2** · Hızlı Takip 2 | Kalan PRD özellikleri (benchmark, self-serve ödeme, Google AI Mode), GA4/SC worker sertleştirme, globalleşme ve yerelleşme, ticari olgunlaştırma, platform sertleştirme | Pilot çıkış kapısı geçildi + HT1 kalemleri üretimde ≥2 hafta kararlı |
| **Kurumsal Kapı** | SSO, SOC 2, genişletilmiş tarihçe, P1 aktif satış | SOC 2 Tip 1 + 12 ay üretim tarihçesi + kurumsal pilot sinyali |
| **Platform Ufku** | Öngörü, anomali tespiti, mobil, EN açılımı | Veri hacmi ve kanıt eşikleri |

---

## 4. Hızlı Takip 1 — HT1

Pilot çıkış kapısından hemen sonra açılır. MVP'nin bilinçli açıklarını kapatır, daraltılmış kapsamları genişletir ve Turkcell RFP ile eklenen yeni yetenekleri derinleştirir.

> **✅ HT1 tamamlandı (28.07.2026).** Aşağıdaki tüm kalemler kod seviyesinde mevcuttur. Detaylı kapanış durumu için bkz. 0205 §4.2–4.3.

### 4.1 Masa Bahisi Kapanışları

| Kalem | Değer Gerekçesi | Bağımlılık |
|-------|-----------------|------------|
| **FR-F6** — REST API (public/v1) | Masa bahisi kapanışı. Ajans BI ihtiyacı. Read-only skor+trend+alıntı+rapor meta. | API sözleşme tasarımı (ADR), API anahtarı yönetimi |
| **FR-E4** — Öneri-etki takibi | Güven halkasını kapatır: öneri, etkisiyle görünür. MVP'den biriken M4 işaretleriyle beslenir. | MVP'den biriken M4 işaretleri |
| **FR-G3** — Müşteri arşivleme | Ajans ölçeklenmesi. Çalışma alanı hijyeni ve kota yönetimi. | — |

### 4.2 MVP Daraltılmış Kapsam Genişlemeleri

| Kalem | MVP Daraltması | HT1 Genişletme |
|-------|----------------|----------------|
| **FR-B4** — Site denetimi | Bot izinleri, SSR, temel erişilebilirlik | Bulgu kataloğu genişletme, öneri detaylandırma, zaman serisi takibi |
| **FR-B7** — Schema korelasyonu | Temel Schema.org analizi (Product/FAQ/Organization) | Genişletilmiş schema kataloğu: Article, BreadcrumbList, HowTo, LocalBusiness, VideoObject, Event. Otomatik schema validasyonu ve TR lokasyon şemaları. Bkz. 0417 §4. |
| **FR-B8** — SEO entegrasyonları | Placeholder kart (ReportsPanel) | **Google Search Console** bağlantısı: tıklama/gösterim verisi import, AI görünürlük korelasyonu. **GA4** bağlantısı: trafik metrikleri. OAuth2 akışı. Ahrefs/Semrush/Screaming Frog roadmap'e alınır. |
| **FR-D3** — Rakip kıyası | Tanımlı rakip setiyle temel skor karşılaştırması | Segment/konu bazlı derin kıyas. Rakip motor kırılımı, rakip trend overlay, rakip citation karşılaştırması. |
| **FR-D11** — Competitive Gap | Temel rakip fark raporu (Visibility/Citation gap) | Detaylı **Topic Gap** (konu bazlı örtüşme/boşluk), **Prompt Gap** (bankanın hangi promptlarda eksik kaldığı), **Content Gap** derinleştirme. Normalizasyon ve alert eşikleri. Bkz. 0419. |
| **FR-E1** — Öneri kütüphanesi | Kural tabanı + kanıt derecesi etiketi | TR sektör şablonlarıyla derinleştirme. Ajans geri bildirimine göre yeni kural setleri. |
| **FR-E7** — Teknik GEO | Structured Data önerileri | **Entity optimizasyonu**: Knowledge Graph entity tespiti ve geliştirme önerileri. **Schema öneri motoru**: eksik schema tiplerini tespit edip otomatik JSON-LD çıktısı üretme. LLM bot davranış analizi derinleştirme. Bkz. 0417 §5. |
| **FR-F2** — Uyarı ayarları | Varsayılan eşikler + kanal (Slack/e-posta) | Eşik editörü, özel kural tanımlama, webhook desteği, sessiz saat ayarları. |
| **FR-F5** — Zamanlanmış rapor | Manuel rapor + zamanlama küçük ek | Tam zamanlama katmanı: günlük/haftalık/aylık, takvim görünümü, tekrarlama, hedef kanal seçimi. |

### 4.3 Turkcell RFP Derinleştirme (Yeni Yetenekler)

| Kalem | MVP Durumu | HT1 Genişletme |
|-------|:----------:|----------------|
| **FR-B6** — LLM Bot izleme | MVP Tam (7 bot: GPTBot, Google-Extended, PerplexityBot, Claude-Web, CCBot, Amazonbot, Applebot-Extended) | Bot davranış zaman serisi, bot bazlı tarama frekansı analizi, robots.txt değişim izleme, bot engeli → visibility korelasyon raporu. Bkz. 0417 §3. |
| **FR-D7** — Sentiment analizi | MVP Tam (Temel TF-IDF + sözlük tabanlı) | **Transformer tabanlı** sentiment modeli (BERTurk fine-tune). TR sektörel duygu sözlüğü genişletme. Aspect-based sentiment (marka/ürün/hizmet ayrımı). Zaman serisi trend overlay. Bkz. 0416 §3. |
| **FR-D8** — Hallüsinasyon tespiti | MVP Tam (Doğruluk kontrolü + tutarsızlık skoru) | **Kaynak çapraz doğrulama**: AI yanıtındaki iddiaları gerçek kaynaklarla eşleştirme. **Tutarlılık zaman serisi**: aynı prompt'un farklı zamanlardaki yanıtlarını karşılaştırma. Otomatik alarm eşiği. Bkz. 0416 §4. |
| **FR-D9** — Per-platform metrikler | MVP Tam (EngineComparison grafiği) | Platform bazlı trend grafikleri, platform karşılaştırma radar görünümü, platform-filtered öneriler. |
| **FR-D10** — Competitive Visibility | MVP Tam (Temel skor + prompt coverage) | Prompt Coverage Score derinleştirme: kategori bazlı kırılım, rakip benchmark overlay, visibility growth trendi. |
| **FR-D12** — Conversation Replay | MVP Tam (Snapshot capture + replay viewer) | **Side-by-side karşılaştırma**: aynı prompt'un farklı motorlarda, farklı zamanlardaki yanıtlarını yan yana gösterme. Conversation diff: sürümler arası fark vurgulama. Paylaşılabilir replay bağlantısı. |
| **FR-D13** — Response Archive | MVP Tam (S3 versiyonlu saklama + API) | **Toplu dışa aktarım**: seçili dönem yanıtlarını JSON/CSV export. Archive search: içerik bazlı arama. Retention policy yönetim UI'ı. |
| **FR-E5** — Content Gap | MVP Tam (Temel içerik boşluk tespiti) | **Topic Cluster öneri motoru**: eksik konu alanlarını AI yanıt analizinden türetme. İçerik takvimi entegrasyonu. Bkz. 0418 §3. |
| **FR-E6** — GEO içerik önerileri | MVP Tam (Topic Cluster, FAQ, Entity) | **Entity geliştirme otomasyonu**: Knowledge Graph entity önerileri. **Semantik ağ haritası**: LSI terimlerle içerik ilişki grafiği. Rakip içerik stratejisi benchmark. Bkz. 0418 §4. |
| **FR-F8** — Executive Dashboard | MVP Tam (Tek ekran KPI) | Dashboard widget'ları: kullanıcı tarafından özelleştirilebilir layout. PDF snapshot export. |
| **FR-F9** — Operasyonel Dashboard | MVP Tam (SEO ekibi detay görünümü) | Gelişmiş filtreler: tarih aralığı, motor, marka, prompt kategorisi. Dashboard paylaşımı. |
| **FR-F10** — Otomatik raporlama | MVP Tam (Scheduler + PDF) | Rapor şablonu özelleştirme, markalı rapor (ajans logosu), çoklu kanal dağıtımı. |
| **FR-F11** — Export formatları | MVP Tam (PDF + CSV + TSV/Excel) | **Gerçek XLSX** desteği (TSV yerine). **API export**: REST üzerinden ham veri indirme. |
| **FR-F12** — Alerting | MVP Tam (Alert engine + NotificationSettings) | **Kural motoru**: kullanıcı tanımlı alert kuralları (IF visibility_drop > X% AND engine = "chatgpt" THEN notify). **Alert history** görünümü. **Alert dashboard**: tüm aktif uyarılar tek ekranda. Webhook desteği genişletme (Slack, Teams, Discord, e-posta). |

### 4.4 Yeni Motor Yüzeyleri

| Kalem | Kademe | MVP Durumu | HT1 Planı |
|-------|:------:|:----------:|-----------|
| **Google AI Mode** | Kademe 3 (directional) | MVP dışı (bilinçli açık) | Gemini proxy adapter'ında AI Mode endpoint desteği. Kademe 3 etiketiyle. Maliyet/kararlılık riski değerlendirmesi. |
| **Mistral** | Kademe 2 (official proxy) | MVP dışı (bilinçli açık) | AB pazarı ve KVKK/GDPR uyumu için stratejik. Engine adapter implementasyonu. Resmi API entegrasyonu. Le Chat yüzeyi (opsiyonel). |
| **İkinci halka (Claude, Grok, Copilot)** | Kademe 2-3 | MVP dışı (bilinçli açık) | Mevcut adapter'lar (engine/claude/, engine/grok/, engine/copilot/) üretime hazırlama. K1 maliyet payı doğrulaması. |

---

## 5. Hızlı Takip 2 — HT2

Genel açılış sonrası. HT1'de kod seviyesine çıkan yeteneklerin üretim olgunluğuna eriştirilmesi, kalan PRD özelliklerinin kapatılması ve global pazara hazırlık.

**Giriş tetikleyicisi:** Pilot çıkış kapısı geçildi + HT1 kalemleri üretimde ≥2 hafta kararlı çalışıyor.

---

### 5.1 Kalan PRD Özellikleri

HT1'de kod seviyesine çıkamamış son 3 FR'nin kapatılması.

| Kalem | FR | Değer Gerekçesi | Bağımlılık |
|-------|:--:|-----------------|------------|
| **Benchmark bağlamı** | FR-D5 | P2 çerçeveleme ihtiyacı. Kategori farklılaştırıcısı. Anonim toplulaştırılmış benchmark ile müşterilere sektör ortalaması görünürlük sağlanır. | ≥5 kiracı eşiği + differential privacy yöntemi + NFR-13 gizlilik kuralı |
| **Self-serve ödeme UI** | FR-A6 | P4/P2 hunisinin sürtünmesiz dönüşümü. Stripe entegrasyonu tam; self-serve UI (paket yükseltme, fatura görüntüleme, kredi kartı yönetimi) eksik. | Ödeme sağlayıcı kararı (Stripe mevcut) + vergi/fatura altyapısı (TR özel) |
| **Google AI Mode** | FR-B6 (genişletme) | Kademe 3 (directional). Gemini proxy üzerinden AI Mode endpoint desteği. Maliyet/kararlılık değerlendirmesi HT1 verisiyle yapılır. | Gemini adapter mevcut; AI Mode endpoint ayrı API çağrısı gerektirir |

#### Detay: Benchmark Bağlamı (FR-D5)

| Boyut | Açıklama |
|-------|----------|
| **Veri kaynağı** | Tüm kiracıların anonimleştirilmiş visibility/skor verileri |
| **Gizlilik yöntemi** | Differential privacy (ε=1.0) + ≥5 kiracı eşiği (NFR-13). Hiçbir tek kiracı verisi ifşa edilmez. |
| **Kırılımlar** | Sektör bazlı (telekom, finans, perakende, vb.), ölçek bazlı (KOBİ/Kurumsal), motor bazlı |
| **Çıktılar** | Sektör ortalaması, yüzdelik dilim (25./50./75.), trend karşılaştırma |
| **UI** | Dashboard widget'ı: "Sektörünüze Göre Konumunuz" kartı. Executive Dashboard'a eklenir. |
| **Masa bahisi** | Benchmark gizlilik eşiği NFR-13 ile sabitlenmiştir. Eşik altı kalır → benchmark gösterilmez, kullanıcıya bilgi verilir. |

#### Detay: Self-Serve Ödeme UI (FR-A6)

| Boyut | Açıklama |
|-------|----------|
| **Kapsam** | Kredi kartı ile ödeme, otomatik fatura, paket yükseltme/düşürme, iptal |
| **Entegrasyon** | Stripe (mevcut backend entegrasyonu üzerine UI katmanı) |
| **TR özel** | e-Fatura/e-Arşiv entegrasyonu, KDV/KV hesaplama, Türkçe fatura şablonu |
| **UI** | Workspace ayarları > Fatura > Paket Yönetimi sayfası |
| **Test** | Stripe test modu + sandbox ortamı. Pilot kiracılardan biriyle beta. |

---

### 5.2 GA4/SC Worker Sertleştirme

HT1'de implemente edilen Google Search Console ve GA4 data sync worker'larının üretim olgunluğuna çıkarılması.

| Kalem | Açıklama | Öncelik |
|-------|----------|:-------:|
| **OAuth token auto-refresh** | Token expiry handling, refresh token rotasyonu, kopuk bağlantı tespiti ve e-posta bildirimi | Kritik |
| **Exponential backoff & retry** | API rate limit ve geçici hatalar için smart retry (3 deneme, üstel bekleme, jitter). Kalıcı hatalarda alert. | Yüksek |
| **Worker telemetrisi** | Her sync işlemi için: başarı/başarısızlık sayısı, latency, veri hacmi, hata kodu dağılımı. Prometheus metrikleri + Grafana paneli. | Yüksek |
| **Veri validasyonu** | Schema uyum kontrolü, outlier tespiti, deduplication (aynı gün verisi iki kez yazılmaz), eksik gün tespiti | Yüksek |
| **Tenant izolasyonu** | Her tenant'ın OAuth token'ı ve API çağrı kotası ayrı yönetilir. Bir tenant'ın rate limit'i diğerini etkilemez. | Yüksek |
| **Backfill mekanizması** | Bağlantı ilk kurulduğunda geçmiş veriyi (son 90 gün SC, son 30 gün GA4) toplu çekme. Worker kesintisi sonrası otomatik backfill. | Orta |
| **Error notification** | Worker hatalarında tenant yöneticisine e-posta/Slack bildirimi. Kritik hatalarda (auth expired) on-call alert. | Orta |
| **Batch optimizasyonu** | Dinamik batch boyutu ayarlama, paralel tenant işleme, bellek kullanım profili iyileştirme | Orta |
| **Data freshness SLA** | SC verisi için ≤4 saat gecikme, GA4 için ≤6 saat. SLA ihlalinde otomatik uyarı. | Orta |
| **Manual refresh override** | Kullanıcının talep ettiği anlık refresh'in worker kuyruğunda önceliklendirilmesi | Düşük |

#### SLA Hedefleri

| Worker | Beklenen Frekans | Maksimum Gecikme | Veri Tarihçesi |
|:------:|:----------------:|:----------------:|:--------------:|
| Search Console | 6 saatte bir | 4 saat | Son 90 gün |
| GA4 | 6 saatte bir | 6 saat | Son 30 gün |
| Backfill (ilk) | Tek sefer | 30 dakika | SC: 90 gün, GA4: 30 gün |

---

### 5.3 Globalleşme ve Yerelleşme

TR pazarında kanıtlanmış ürünün uluslararası pazarlara hazırlanması.

| Kalem | Değer Gerekçesi | Bağımlılık |
|-------|-----------------|------------|
| **EN arayüz** | İkinci dil desteği tam UI katmanında. i18n altyapısı hazır (react-intl). Tüm etiket, mesaj ve hata metinleri çevrilir. | i18n transform pipeline mevcut (bkz. web/scripts/i18n-transform.mjs) |
| **EN prompt şablonları** | TR sektör şablonlarının İngilizce versiyonları. Uluslararası markalar için hazır prompt setleri. | içerik ekibi tarafından hazırlanır |
| **Multi-currency fiyatlama** | USD/EUR bazlı paket fiyatları. Bölgeye göre otomatik döviz dönüşümü. | Stripe multi-currency desteği |
| **GDPR veri yönetimi** | Veri saklama politikaları (sağ unutulma, veri taşınabilirliği), GDPR uyumlu silme akışı, veri sorumlusu kaydı | KVKK altyapısı mevcut, GDPR üzerine genişletilir |
| **Bölgesel motor kapsamı** | AB odaklı: Mistral (Kademe 2 — mevcut), AB merkezli veri merkezi seçeneği. ABD odaklı: Claude/Grok derinleştirme. | Motor adapter'ları mevcut (HT1) |
| **Bölgesel SEO entegrasyonu** | Search Console bölgesel property desteği, GA4 multi-property, bölgesel schema.org varyasyonları | SC/GA4 worker altyapısı mevcut |
| **Dil bazlı sentiment** | İngilizce sentiment modeli (BERT base uncased fine-tune). TR model mevcut (BERTurk) → EN model eklenir. | 0416 sentiment altyapısı mevcut |
| **Pazar lokasyonu yönetimi** | Kullanıcının workspace'inde hedef pazar(lar) tanımlaması. Ölçüm ve skorlama pazara göre filtrelenir. | — |

#### Globalleşme Kapı Kriterleri

| # | Kriter |
|:-:|--------|
| 1 | TR'de PMF sinyali doğrulanmış: M2 ≥ %80 + M1 ≥ %60 + talep eşiği (AVIP D-70) |
| 2 | EN arayüz test edilmiş ve en az 2 beta kullanıcıdan geri bildirim alınmış |
| 3 | GDPR veri yönetimi denetlenmiş ve uyum raporu hazır |
| 4 | Bölgesel motor kapsamı (Mistral + veri merkezi) üretimde doğrulanmış |
| 5 | TR pazarından haftalık aktif kullanıcı sayısı ≥50 |

---

### 5.4 Ticari Olgunlaştırma

Self-serve dönüşüm, ajans beyaz etiket ve raporlama zenginleştirme.

| Kalem | Değer Gerekçesi | Bağımlılık |
|-------|-----------------|------------|
| **Dashboard widget özelleştirme** | Executive/Operational dashboard'ların kullanıcı tarafından düzenlenebilir layout'u. Sürükle-bırak widget sıralama, göster/gizle, boyutlandırma. | FR-F8/F9 dashboard altyapısı mevcut |
| **White-label rapor** | Ajans müşterileri için kendi logosu ve renkleriyle markalanabilir PDF/HTML raporlar. Rapor şablonu özelleştirme (kapak, renk, yazı tipi, footer). | FR-F10 otomatik raporlama mevcut + PDF engine mevcut |
| **Webhook çeşitlendirme** | Mevcut Slack/e-posta kanallarına ek olarak: Microsoft Teams, Discord, PagerDuty, custom webhook URL. | FR-F12 alerting altyapısı mevcut |
| **API key yönetim UI** | Public API (FR-F6) için API anahtarı oluşturma, devre dışı bırakma, rotasyon, izin ve kullanım istatistiği görünümü. | FR-F6 Public API mevcut |
| **Looker Studio connector** | Public API üzerinden Looker Studio / Google Data Studio Community Connector. Kullanıcılar kendi dashboard'larını oluşturabilir. | FR-F6 API mevcut; connector geliştirme gerektirir |
| **Tableau connector** | Tableau Web Data Connector (WDC). Looker Studio'dan sonra, talep bazlı. | API altyapısı aynı; WDC spesifik geliştirme |
| **E-posta kişiselleştirme** | Rapor e-postalarında müşteri adı, marka bazlı içerik, kişiselleştirilmiş öneri özeti. MVP M3 verisiyle beslenir. | M3 verisi MVP'den itibaren birikir |
| **Haftalık özet zenginleştirme** | Mevcut haftalık özete ek: trend yorumu, önerilen aksiyonlar, öne çıkan değişiklikler, rakip hamle uyarıları. | FR-F3 haftalık özet mevcut |

---

### 5.5 Platform Sertleştirme

Altyapı ve güvenlik iyileştirmeleri.

| Kalem | Açıklama | Öncelik |
|-------|----------|:-------:|
| **Performans profili çıkarma** | Tüm API endpoint'leri için p50/p95/p99 latency hedefleri belirleme. Yavaş sorguların tespiti ve optimizasyonu. | Yüksek |
| **Ölçek testi** | 100+ eşzamanlı kiracı senaryosunda yük testi. Veritabanı bağlantı havuzu, Redis bellek, worker kuyruk derinliği sınırlarını doğrulama. | Yüksek |
| **Güvenlik taraması** | Bağımlılık güncellemesi (go mod, npm audit), OWASP Top 10 taraması, statik analiz (Semgrep/CodeQL). | Yüksek |
| **CI/CD iyileştirme** | Integration test suite'ini GitHub Actions'a taşıma. Pipeline süresi hedefi: < 10 dakika. | Orta |
| **Dokümantasyon güncellemesi** | API referansı (OpenAPI/Swagger), deployment kılavuzu, runbook, incident response prosedürü. | Orta |
| **Disaster recovery** | Veritabanı PITR (point-in-time recovery) prosedürü, çapraz bölge yedekleme planı, RTO/RPO hedefleri. | Düşük |

---

## 6. Kurumsal Kapı

Bu pencere P1 aktif satışının açılışıdır (0201 §6 ertelemesinin sonu).

**Giriş tetikleyicisi (bileşik):**
1. SOC 2 Tip 1 raporu alınmış
2. Kesintisiz ≥12 ay üretim tarihçesi birikmiş
3. Kurumsal pilot kiracılarından satın alma sinyali doğrulanmış

**Kalemler:**
- **FR-A4** — SSO/SAML oturum açma
- **SOC 2** sertifikasyonu (Tip 1 → Tip 2; kontrol yolu MVP'den beri işliyor)
- Genişletilmiş tarihçe ve dışa aktarım paketleri
- Kurumsal onboarding ve güvenlik inceleme paketi

---

## 7. Platform Ufku

| Kalem | Değer Gerekçesi | Tetikleyici |
|-------|-----------------|-------------|
| **Tahmine dayalı görünürlük** | Trendden öngörüye geçiş. Olasılık diliyle. | Yeterli tarihçe hacmi |
| **Öğrenen öneri sıralaması** | M4 + etki verisiyle önerilerin beklenen etkiye göre sıralanması. | FR-E4 verisi olgunlaştı |
| **Anomali kök neden** | Uyarıdan açıklamaya: kaynak kırılımı korelasyonları. | M11 kalibrasyonu oturdu |
| **Yerel mobil uygulama** | P4/P5 bildirim yüzeyinin derinleşmesi. Kategoride hâlâ boşluk. | Responsive web etkileşim verisi |
| **EN pazar açılımı** | TR çekirdeği kanıtlandıktan sonra İngilizce pazar. | PMF sinyali (TR'de M2≥%80 + M1≥%60) |
| **Yeni motor yüzeyleri** | Asistan/ajan yüzeyleri. Kademe modeliyle etiketli. | Resmî erişim olgunluğu |

> **Ufuk sınırı:** Hiçbir kalem kullanıcı onayı olmadan otomatik site/içerik değişikliği uygulamaz. Öneri üretimi NG sınırları içinde kalır.

---

## 8. Riskler ve Yeniden Önceliklendirme

| Senaryo | Etki | Sıralama Tepkisi |
|---------|------|------------------|
| TR penceresi erken kapanır | Bilinirlik yarışı sertleşir | Savunulabilirlik öne çekilir: istatistik derinliği, metodoloji yayınları (GAVF), benchmark |
| Motor erişimi sertleşir | Bağdaştırıcı yatırımı riski artar | İkinci halka ertelenir; K2 vekil-korelasyon pilotu öne alınır |
| Ajans talebi beklenenden hızlı büyür | Ajans kalemleri darboğaz olur | FR-G3 ve panorama genişlemesi HT1 başına çekilir |
| Pilot çıkış kapısı gecikir/geçilemez | TR penceresi (0101 §8) daralır | HT1 girişi ertelenir. Pilot süresi uzatılır, kriterler revize edilir. |
| Kaynak kısıtı | Pencere içi kalemler seyrelir | Tek platform ilkesi kaydırma maliyetini düşük tutar |
| Turkcell RFP sonrası rekabet baskısı | Kurumsal beklenti hızlanır | Sentiment, conversation replay, content gap derinleştirme öne çekilir |

---

## 9. Pencere-Metrik Bağları

| Pencere | Başarı Sinyali | Yeni Metrik İhtiyacı |
|---------|----------------|----------------------|
| **HT1** | M4 artışı, M10/M11 hedef sürdürme, RFP yeteneklerinde tamamlanma oranı ≥%80 | Öneri sonrası yeniden ölçüm oranı; Turkcell RFP uyum skoru |
| **HT2** | M1 büyümesi, e-posta→pano geçişi, paket geçişleri, benchmark kullanım oranı | Dönüşüm ve geçiş oranları; benchmark gösterim sayısı; worker SLA uyum yüzdesi |
| **HT2 (global)** | EN kullanıcı sayısı, GDPR uyum tamlığı, uluslararası motor kapsam oranı | Bölgesel pazar giriş metrikleri; dil bazlı kullanıcı memnuniyeti |
| **Kurumsal** | Kurumsal pilot sinyalleri | Kurumsal değerlendirme döngü süresi |
| **Ufuk** | Tarihçe hacmi ve model kalibrasyonu | Öngörü isabeti metriği |

---

## 10. GeoLens İçin Çıkarımlar

1. **Faz 2 bu dokümanla tamamlanmıştır.** 0201-0206 seti Draft durumundadır. Approved geçişleri tanımlı kapılara bağlıdır (0201 saha doğrulaması, pilot çıkış kapısı).
2. **Specification bağlantısı:** Platform ufkundaki metodoloji yayınları ve GAVF güncellemeleri, specification reposunda ayrı bir yol haritasıyla yönetilir.
3. **Turkcell RFP genişletmesi (v1.3–v1.4):** RFP sonrası eklenen yeteneklerin tamamı HT1'de kod seviyesine çıkmıştır. HT2'de bu yetenekler üretim olgunluğuna eriştirilecek (GA4/SC worker hardening, benchmark, self-serve ödeme, white-label rapor).
4. **HT1 tamamlandı (v1.4):** 8 daraltılmış giriş ve 9 bilinçli açığın tamamı kod seviyesinde mevcuttur. 50 FR'den 47'si (%94) tamamlanmıştır. Kalan 3 FR HT2'ye tarihlenmiştir. Detay için bkz. 0205 §4.2–4.4.
5. **HT2 kapsamı:** Kalan PRD özellikleri (FR-D5 benchmark, FR-A6 self-serve ödeme, Google AI Mode), GA4/SC worker sertleştirme, globalleşme ve yerelleşme, ticari olgunlaştırma (white-label, özelleştirilebilir dashboard, webhook çeşitlendirme), platform sertleştirme (performans, ölçek testi, güvenlik).
6. **Faz 3 açılışı:** Sıradaki doküman 0301 System Architecture'dır. Bu yol haritasının pencere yapısı, mimari esneklik gereksinimi olarak Faz 3'e taşınır.
7. **Kurumsal kapı tetikleyicisi** SOC 2 yol haritasının önceliğini belirler. Kontrol yolu MVP'den itibaren işletilir.
8. **TR penceresi gözetimi** (0101 §8'deki 12-18 ay varsayımı) 0007 kadansında izlenir. Erken kapanma sinyali gelirse §8'deki sıralama tepkisi devreye girer.
9. **Yeni framework dokümanları:** 0416 (Sentiment & Hallucination), 0417 (Technical GEO), 0418 (Content GEO), 0419 (Competitive Gap Analysis) — bu dokümanlardaki metodoloji, algoritma ve genişletme planları HT1 kalemlerinin teknik girdisini oluşturur. HT2'de globalleşme kapsamında EN sentiment modeli (0416) ve uluslararası schema/entity analizi (0417) devreye alınır.

---

## 12. GAVF Specification Versiyon Senkronizasyonu

Platform sürümleri ile GAVF Specification versiyonları arasındaki eşleme, `specification/docs/00-overview/0005-version-sync-plan.md` dokümanında ayrıntılı olarak tanımlanmıştır. Aşağıdaki tablo, platform pencere modelini GAVF versiyonlarına bağlar.

| Platform Penceresi | Sürüm Etiketi | GAVF Versiyonu | Değişiklik Türü | Kilit Karar |
|:------------------:|:-------------:|:---------------:|:----------------:|:-----------:|
| **MVP** | `v1.0.0` | `1.0.0` | İlk eşzamanlı yayın | GAVF Temel + İleri seviye uyumluluğu sağlanır |
| **HT1** | `v1.1.0` | `1.0.x` (patch) veya değişmez | GAVF değişikliği yok | Sentiment/hallüsinasyon metodolojisi spec'e minor adayı |
| **HT2** | `v1.2.0` | `1.1.0` (minor adayı) | GAVF minor — yeni metodoloji | Benchmark (FR-D5) → spec'e benchmark standardı eklenir; globalleşme → GAVF çoklu dil/metrik standardı; GA4/SC worker → veri kaynağı standardı (0106) |
| **Kurumsal** | `v2.x.0` | `1.x.0` veya `2.0.0` | Karara bağlı | SOC 2 ile birlikte GAVF sertifikasyon süreci başlar |
| **Ufuk** | `v2.y.z` | `1.x.0`+ | Yeni katmanlar | Tahmin, anomali gibi yeni bileşenler spec'e eklenir |

### Senkronizasyon Kuralları (Özet)

| # | Kural |
|:-:|------|
| SK-1 | Platform ve Specification bağımsız versiyonlanır; yalnızca GAVF etkileyen değişiklikler senkronizasyon gerektirir |
| SK-2 | Skor algoritması değişikliği → GAVF major, yeni skor bileşeni → GAVF minor, düzeltme → GAVF patch |
| SK-3 | Aynı anda yalnızca bir tarafta major versiyon değişikliği yapılır |
| SK-4 | Specification yayını öncesi platformun GAVF uyumluluk testlerini (spec/0304) geçtiği doğrulanır |
| SK-5 | Her platform release notu, hangi GAVF versiyonuyla uyumlu olduğunu belirtir |

### Release Notu Formatı

Her platform sürüm notu şu bilgiyi içerir:

```
## GAVF Uyumluluk
- GAVF Versiyonu: 1.0.0
- Uyumluluk Seviyesi: Temel + İleri
- Değişiklik: Major/Minor/Patch
- Specification Tag: gavf-1.0.0
```

### İlgili Doküman

Tüm detaylar (olay-matrisi, CI/CD entegrasyonu, geçiş senaryoları) için:
[specification/docs/00-overview/0005-version-sync-plan.md](../../specification/docs/00-overview/0005-version-sync-plan.md)

---

## 13. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | Yerel mobil uygulama değerlendirme kriterleri | ✅ **KAPANDI** (AVIP D-38): Mobil talep ≥%20 veya P1 kurumsal kapısı açıldığında yeniden değerlendirilir. |
| O-2 | EN pazar açılımı tetikleyici eşikleri | ✅ **KAPANDI** (AVIP D-70): PMF sinyali bileşik — TR'de M2≥%80 + M1≥%60 + talep eşiği. |
| O-3 | HT1 öncelik sırası — Turkcell RFP yetenekleri vs masa bahisi kapanışları | ✅ **KAPANDI (AVIP D-95):** Tüm HT1 kalemleri eşzamanlı implemente edilmiştir. Önceliklendirme gerekmemiştir. |
| O-4 | HT2 GA4/SC worker kapasite planlaması — kaç tenant'a kadar mevcut worker mimarisi yeterlidir? | ⏳ HT1 üretim verisi toplandıktan sonra yanıtlanacak. |
| O-5 | Globalleşme öncelik sırası — EN arayüz mü, GDPR uyumu mu, yoksa bölgesel motor kapsamı mı önce gelir? | ⏳ TR PMF sinyali ve pilot geri bildirimine göre kararlaştırılacak. |

### Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|----|-------|--------|
| **D-69** | **Kurumsal kapı tarihçe eşiği:** 12 ay (hipotez). Pilotta kalibre. PO 21.07.2026. | AVIP 0206 O-1 |
| **D-70** | **EN açılım tetikleyicisi:** PMF sinyali bileşik. PO 21.07.2026. | AVIP 0206 O-3 |
| **D-38** | **Flutter reeval tetikleyicisi:** Mobil talep ≥%20 veya P1 kurumsal kapısı. TL 21.07.2026. | AVIP 0304 O-4 |
| **D-63** | **v1.1 düzeltme turu:** Faz 4 öncesi tek geçiş. PO 21.07.2026. | AVIP 0206 O-4 |

---

## Kaynaklar

- 0205 MVP Scope — bilinçli açıklar, daraltılmış kapsamlar, pilot çıkış kapısı, Turkcell RFP genişletmesi
- 0201 User Personas — paket iskeleti, segment önceliği, kurumsal kapı koşulu
- 0204 PRD — FR/NFR öncelikleri, Turkcell RFP gereksinimleri
- 0101 Pazar Analizi — TR pencere varsayımı
- 0102 Rekabet Analizi — motor kademe modeli
- 0416 Sentiment & Hallucination — duygu analizi ve hallüsinasyon tespiti metodolojisi
- 0417 Technical GEO — LLM bot izleme, schema analizi, teknik GEO önerileri
- 0418 Content GEO — içerik boşluğu analizi, GEO içerik önerileri
- 0419 Competitive Gap Analysis — gap metodolojisi, 5 gap türü algoritması

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: GeoLens Platform post-MVP yol haritası. 5 yol haritası ilkesi, 4 pencereli tetikleyici modeli, HT1/HT2/kurumsal/ufuk kalemleri, risk senaryoları. Faz 2 kapanışı. |
| 1.1 | 22.07.2026 | §12 GAVF Specification versiyon senkronizasyonu eklendi: her platform penceresi için GAVF versiyon eşlemesi, 5 senkronizasyon kuralı (SK-1–SK-5), release notu formatı. |
| 1.2 | 22.07.2026 | AVIP kapalı kararları taşındı. Devralınan Kararlar eklendi. |
| 1.3 | 27.07.2026 | **Turkcell RFP genişletmesi:** HT1 bölümü 4 alt bölüme ayrıldı (masa bahisi kapanışları, daraltılmış genişlemeler, RFP derinleştirme, yeni motor yüzeyleri). 13 yeni HT1 kalemi eklendi: FR-B6 (LLM Bot), FR-B7 (Schema), FR-B8 (SEO), FR-D11 (Gap), FR-D12/D13 (Replay/Archive), FR-D7/D8 (Sentiment/Hallucination), FR-F12 (Alerting), FR-E5/E6 (Content/GEO). Google AI Mode ve Mistral eklendi. Risk senaryolarına Turkcell maddesi eklendi. Yeni doküman referansları (0416-0419) ilişkili alanına ve GeoLens çıkarımlarına eklendi. Açık sorular güncellendi. |
| 1.4 | 28.07.2026 | **HT2 kapsamı genişletme:** §5 HT2 tamamen yeniden yazıldı. 5 alt bölüm: kalan PRD özellikleri (FR-D5 benchmark, FR-A6 self-serve ödeme, Google AI Mode), GA4/SC worker sertleştirme (10 kalem + SLA hedefleri), globalleşme ve yerelleşme (EN arayüz, GDPR, multi-currency, bölgesel motor), ticari olgunlaştırma (white-label, dashboard özelleştirme, webhook çeşitlendirme, API key UI, Looker/Tableau connector), platform sertleştirme (performans, ölçek, güvenlik). §4 HT1 tamamlandı bildirimi eklendi. §9 Pencere-Metrik bağları HT2/global satırları eklendi. §10 GeoLens çıkarımları HT2 kapsamıyla güncellendi. §12 GAVF HT2 mapping güncellendi. |
