# 0303 · Database Design

| Alan | Değer |
|---|---|
| Doküman ID | 0303 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 13 Temmuz 2026 |
| Karşıladığı madde | 19 · Database Design (şema, migration, cache veri modeli) |
| İlişkili | 0302, 0301 (girdi); 0304, 0305, 0307, 0309, 0310, 0311 (çıktı); ADR-004, ADR-005 |

---

## 1. Amaç ve Kapsam

Bu doküman veri katmanının tasarım sözleşmesini sabitler: PostgreSQL şeması (tablolar, kısıtlar, indeksler), migration stratejisi, Redis anahtar modeli ve S3 nesne şeması. Tam DDL dosyaları depoda migrations/ dizininde yaşar ve bu sözleşmeye uymak zorundadır; burada kolon düzeyi tanım yalnız çekirdek tablolar için verilir. Kapsam dışı: sorgu optimizasyon ayarları ve kapasite planlaması (0311), ORM ve sürücü seçimi (0304), yedekleme ve felaket kurtarma prosedürleri (0311).

## 2. Tasarım Kuralları (tüm tablolara üstten uygulanır)

| # | Kural |
|---|---|
| K1 | Kiracı kapsamı: sistem tabloları dışında her tabloda tenant_id ve (uygulanabilirse) workspace_id NOT NULL; bileşik indeksler bu kolonlarla başlar (I1). |
| K2 | Kimlik: birincil anahtarlar ULID (26 karakterlik metin; zaman-sıralanabilir); dış yüzeylere opak kimlik olarak taşınır (0302 O-4 uygulaması). |
| K3 | Yalnız-ekleme tabloları (calculation_runs, audit_log, raw_responses): UPDATE ve DELETE yetkisi uygulama rolünden alınır, trigger ile ikinci kez engellenir. |
| K4 | RLS: kiracı kapsamlı tüm tablolarda etkin; oturum değişkeni (app.tenant_id) üzerinden tek tip politika şablonu; süper kullanıcı yolu yalnız operasyon prosedürlerinde (ADR-004). |
| K5 | Zaman: tüm zaman kolonları timestamptz (UTC); created_at zorunlu; updated_at yalnız değişebilir tablolarda ve trigger ile güncellenir. |
| K6 | Soft delete yok: yaşam döngüsü durum kolonuyla yönetilir (I10); kalıcı silme yalnız KVKK silme prosedürüyle yapılır (N12; §9 ve O-3). |
| K7 | Skor hassasiyeti: skor ve güven aralığı numeric(5,2), 0-100 ölçeği; CHECK ile aralık ve sıra korunur (ölçek kesinleştirme 0309, O-4). |
| K8 | JSONB disiplini: esnek meta (faktör anlık görüntüsü, panel içeriği, olay yükleri) JSONB + schema_version alanıyla saklanır; sık sorgulanan alan kolona terfi eder. |
| K9 | Yabancı anahtarlar ON DELETE RESTRICT varsayılandır (tarihçe korunur); CASCADE yalnız saf detay tablolarında (örnek: citations → raw_responses). |
| K10 | Adlandırma: snake_case İngilizce; tablo adları çoğul (tenants, scores); enum yerine kısıtlı metin + CHECK (migration kolaylığı). |

## 3. Şema Genel Görünümü (25 tablo; bağlam gruplu envanter)

### BC1 · identity

| Tablo | Amaç | RLS | Yalnız ekleme | Hacim sınıfı |
|---|---|---|---|---|
| tenants | Kiracı kaydı, tür, paket, durum | · | · | Küçük |
| workspaces | Çalışma alanları; ajans müşteri karşılığı | Evet | · | Küçük |
| users, memberships, invitations | Kullanıcı, rol, davet | Evet* | · | Küçük |
| entitlements | Paket hakları yapılandırması | Evet | · | Küçük |

### BC2 · configuration

| Tablo | Amaç | RLS | Yalnız ekleme | Hacim sınıfı |
|---|---|---|---|---|
| brands, sites, markets | Marka, alan adı, pazar tanımları | Evet | · | Küçük |
| prompt_sets, prompts | Kiracı prompt setleri ve satırları | Evet | · | Orta |
| prompt_templates | Sistem şablon kütüphanesi (kiracısız) | · | · | Küçük |
| monitoring_plans | Frekans ve pencere planı | Evet | · | Küçük |

### BC3 · measurement

| Tablo | Amaç | RLS | Yalnız ekleme | Hacim sınıfı |
|---|---|---|---|---|
| panel_versions | Dondurulmuş panel anlık görüntüsü (§4) | Evet | · | Orta |
| measurement_jobs | İdempotent ölçüm işleri, durum makinesi | Evet | · | Büyük |
| raw_responses | Ham yanıt meta verisi; S3 bağı (§4) | Evet | Evet | Büyük |
| citations | Alıntı meta verisi (FR-D2) | Evet | Evet | Büyük |
| calculation_runs | Deterministik hesap kayıtları (§4) | Evet | Evet | Büyük |
| scores | Skor + GA + fidelite (§4) | Evet | · | Büyük |
| site_audit_runs, audit_findings | Site denetimi koşuları ve bulguları | Evet | · | Orta |

### BC4 · insight

| Tablo | Amaç | RLS | Yalnız ekleme | Hacim sınıfı |
|---|---|---|---|---|
| recommendations | Kanıt dereceli öneriler, durum | Evet | · | Orta |

### BC5 · delivery

| Tablo | Amaç | RLS | Yalnız ekleme | Hacim sınıfı |
|---|---|---|---|---|
| alert_rules, alerts | Eşik ayarları ve uyarılar | Evet | · | Orta |
| notification_channels | E-posta/Slack/webhook hedefleri | Evet | · | Küçük |
| reports | Rapor işleri, S3 referansı, durum | Evet | · | Orta |

### BC6 · governance

| Tablo | Amaç | RLS | Yalnız ekleme | Hacim sınıfı |
|---|---|---|---|---|
| audit_log | Denetim izi; bütünlük zinciri kolonları (§4) | Evet | Evet | Büyük |
| usage_records | Kota ve kullanım sayaçları (K1 kaynağı) | Evet | · | Orta |
| event_outbox | PG işleminden kuyruklara güvenilir olay aktarımı (outbox deseni) | Evet | · | Büyük/geçici |

\* users tablosu kiracılar arası paylaşımlıdır; RLS üyelik üzerinden uygulanır. Sistem tabloları (prompt_templates) kiracı kolonlu değildir ve salt okunur yayın kanalıyla beslenir.

## 4. Çekirdek Tablolar (kolon sözleşmeleri; ortak kolonlar id, tenant_id, workspace_id, created_at tekrar edilmez)

### panel_versions

| Kolon | Tip | Not |
|---|---|---|
| version_no | integer | Çalışma alanı + panel içinde artan; UNIQUE (workspace_id, panel_key, version_no) |
| panel_key | text | Mantıksal panel kimliği (marka seti bağı) |
| content | jsonb | Prompt seti içeriği + motor kapsamı + pazar anlık görüntüsü; schema_version alanıyla (K8) |
| content_hash | text | İçerik karması; aynı içerik yeni versiyon üretmez (idempotent versiyonlama) |

### measurement_jobs

| Kolon | Tip | Not |
|---|---|---|
| panel_version_id | ulid FK | → panel_versions; RESTRICT |
| window_start / window_end | timestamptz | Ölçüm penceresi; M10 takibi |
| idempotency_key | text | UNIQUE; (workspace, panel_version, window) türevi; çift üretim engeli |
| status | text CHECK | queued / running / completed / partial / failed (0302 §7 makinesi) |
| attempt | smallint | Sınırlı yeniden deneme sayacı (NFR-N9) |

### raw_responses (yalnız ekleme)

| Kolon | Tip | Not |
|---|---|---|
| job_id | ulid FK | → measurement_jobs |
| engine | text | Bağdaştırıcı kimliği (chatgpt / gemini / perplexity ...) |
| tier_label | text CHECK | Kademe etiketi: direct / official_proxy / directional (0102; fidelite kaynağı) |
| s3_key | text | Arşiv nesnesi anahtarı (§8 deseni) |
| content_hash | text | Nesne bütünlük karması (I5) |
| measured_at | timestamptz | Motor yanıt zamanı; K3 tazelik kaynağı |

### calculation_runs (yalnız ekleme)

| Kolon | Tip | Not |
|---|---|---|
| job_id | ulid FK | → measurement_jobs |
| input_set_hash | text | Girdi kümesinin (ham yanıt kimlikleri + parametreler) karması; N7 yeniden üretim anahtarı |
| factor_snapshot | jsonb | Faktör anlık görüntüsü; schema_version ile (K8) |
| algo_version | text | Hesap algoritması sürümü (0302 §5 kural 4: panel ekseninden bağımsız) |
| template_version | text | Skor şablonu sürümü |

### scores

| Kolon | Tip | Not |
|---|---|---|
| calculation_run_id | ulid FK NOT NULL | → calculation_runs; skor koşusuz var olamaz (I2) |
| panel_version_id | ulid FK NOT NULL | → panel_versions (I4) |
| brand_id | ulid FK | → brands |
| engine | text NULL | NULL = birleşik skor; dolu = motor kırılımı (FR-D1) |
| value / ci_low / ci_high | numeric(5,2) | CHECK: 0-100 ve ci_low ≤ value ≤ ci_high (I3'ün aralık yarısı) |
| fidelity_label | text NOT NULL CHECK | Kademe türevi etiket; boş bırakılamaz (I3; İ2'nin şema karşılığı) |
| freshness_at | timestamptz NOT NULL | Tazelik damgası (K3) |

### audit_log (yalnız ekleme)

| Kolon | Tip | Not |
|---|---|---|
| actor_type / actor_id | text / ulid | user / system / job ayrımı |
| action / resource_type / resource_id | text | Eylem ve hedef kaynak |
| summary | jsonb | Önce/sonra özeti; hassas alanlar maskelenir (0310 kuralı) |
| prev_hash / entry_hash | text | Bütünlük zinciri kolonları; zincir doldurma stratejisi 0310'da |
| at | timestamptz | BRIN indeks adayı (§5) |

### event_outbox

| Kolon | Tip | Not |
|---|---|---|
| event_type / payload | text / jsonb | Alan olayı (skor yayınlandı, uyarı tetiklendi, rapor hazır) |
| status | text CHECK | pending / dispatched / dead; dispatcher Redis kuyruklarına taşır |
| dispatched_at | timestamptz | Aktarım zamanı; dispatched kayıtları periyodik temizlenir (tek silme istisnası, K6 notu) |

Outbox deseni, PG işlemi ile kuyruk yazımı arasındaki çift-yazma riskini kaldırır: olay, iş verisiyle aynı işlemde outbox'a yazılır; ayrı bir dağıtıcı Redis'e taşır (0301 H3, 0307).

## 5. İndeks ve Erişim Desenleri

| Erişim deseni | İndeks stratejisi |
|---|---|
| Pano trend sorgusu (marka × pencere) | scores (workspace_id, brand_id, panel_version_id, freshness_at DESC); engine kırılımı için kısmi indeks (engine IS NOT NULL) |
| Skor açıklama katmanı (UC-07) | scores (calculation_run_id); calculation_runs (job_id) |
| Kuyruk taraması (zamanlayıcı/işçi) | measurement_jobs kısmi indeks WHERE status = 'queued' (window_start); event_outbox kısmi indeks WHERE status = 'pending' |
| Uyarı listesi ve digest gruplama | alerts (workspace_id, created_at DESC); digest grubu kolonu üzerinde ikincil |
| Denetim izi taraması | audit_log (tenant_id, at DESC); at kolonu için BRIN (büyük yalnız-ekleme tablosu, düşük maliyetli aralık taraması) |
| Kullanım/kota okuma (FR-H2) | usage_records UNIQUE (tenant_id, period, counter_type) |
| Alıntı kaynak analizi (FR-D2, M9) | citations (workspace_id, raw_response_id); alan adı türetilmiş kolonu üzerinde ikincil indeks |

Kural: indeksler bu desen tablosundan türetilir ve migration'da desen referansıyla yorumlanır; kullanılmayan indeks tespiti 0311 operasyon rutinine bağlanır. Büyük tablolarda (raw_responses, citations, audit_log) bölümleme (partitioning) V1'de uygulanmaz; hacim eşiği izlenir ve aylık bölümlemeye geçiş kriteri 0311'de tanımlanır [K].

## 6. Migration ve Evrim Stratejisi

Migration'lar sıralı, tek amaçlı SQL dosyalarıdır ve depoda migrations/ altında yaşar (araç seçimi 0304; golang-migrate sınıfı). Kurallar: (1) Üretimde yalnız ileri gidilir; geri alma yerine ileri-düzeltme (forward-fix) uygulanır. (2) Kırıcı değişiklikler genişlet-daralt (expand-contract) deseniyle yapılır: yeni kolon/tablo eklenir, kod çift yazar, geri doldurma biter, eski yapı ayrı migration ile kaldırılır. (3) RLS politikaları, yalnız-ekleme trigger'ları ve CHECK kısıtları migration'ın parçasıdır; şema ile güvenlik ayrışmaz. (4) CI kapısı (0403): her migration boş veritabanına baştan uygulanır, şema anlık görüntüsüyle sapma (drift) kontrolü yapılır, K3 korumaları otomatik testle doğrulanır. (5) Sistem verisi (prompt_templates) ayrı seed kanalıyla, migration'dan bağımsız sürümlenir. (6) Her migration dosyası başlıkta ilgili değişmez ve gereksinim kimliklerini yorum olarak taşır (izlenebilirlik).

## 7. Redis Veri Modeli

| Anahtar deseni | Amaç | Tür / ömür |
|---|---|---|
| q:measure, q:report, q:notify | İş kuyrukları; outbox dağıtıcısı besler | Stream veya liste (ADR-005); işlenince biter |
| rl:(tenant):(pencere) | Hız sınırı sayaçları (NFR-N14) | Sayaç + TTL |
| quota:(tenant):(dönem) | Kota ön kontrolü önbelleği; gerçek kaynak usage_records | Sayaç + TTL |
| cache:dash:(workspace):(anahtar) | Pano okuma önbelleği (kısa TTL) | Değer + TTL |
| lock:(iş anahtarı) | Tekil çalıştırma kilitleri (zamanlayıcı yarışları) | Kısa TTL kilit |

İki kural: Redis hiçbir verinin gerçek kaynağı değildir; tam kayıpta kuyruklar outbox'tan, sayaçlar usage_records'tan yeniden inşa edilir. Anahtarlar kiracı kimliği taşır ve işçiler yük içindeki kiracı bağlamını ayrıca doğrular (0301 §5 kuyruk katmanı).

## 8. S3 Anahtar Şeması ve Yaşam Döngüsü

| Desen | İçerik ve kurallar |
|---|---|
| raw/(tenant)/(workspace)/(yyyy)/(mm)/(response_id).json.gz | Ham yanıt arşivi; sıkıştırılmış; content_hash ile doğrulanır; nesne kilidi (yazım sonrası değiştirilemezlik) 0310 stratejisi (I5) |
| reports/(tenant)/(workspace)/(report_id).pdf | Üretilmiş raporlar; erişim yalnız kısa ömürlü imzalı URL |
| assets/(tenant)/brand/(dosya) | White-label logo ve marka varlıkları; NFR-N3 doğrulamasından geçmiş nesneler |

Yaşam döngüsü: ham arşivde depolama sınıfı geçişi (sık erişimden seyrek erişime) ve saklama süreleri 0204 O-4 kararına bağlıdır [K]; silme yalnız KVKK prosedürüyle (§9, O-3). Kova versiyonlama açıktır; anahtarlar kiracı önekli olduğundan erişim politikaları önek bazlı yazılır (0301 §5 depolama katmanı).

## 9. Veri Bütünlüğü ve Değişmez Eşlemesi (0302 §6 → mekanizma)

| Değişmez | Veritabanı mekanizması |
|---|---|
| I1 | tenant_id/workspace_id NOT NULL + FK zinciri + RLS (K1, K4) |
| I2 | scores.calculation_run_id NOT NULL; calculation_runs yalnız ekleme (K3) |
| I3 | fidelity_label NOT NULL + CHECK; ci kolonları NOT NULL + sıra CHECK |
| I4 | scores.panel_version_id NOT NULL FK; panel_versions içerik karması ile idempotent versiyon |
| I5 | raw_responses yalnız ekleme + content_hash + S3 nesne kilidi (0310) |
| I6 | audit_log yalnız ekleme + prev_hash/entry_hash zincir kolonları (0310 doldurur) |
| I7 | recommendations.policy_checked_at NOT NULL (filtre kanıtı); filtre mantığı uygulama katmanında (0309) |
| I8 | alerts.source_score_id FK + anlamlılık kanıt alanı; kural mantığı 0309 |
| I9 | usage_records UNIQUE sayaç + uygulama kapısı; Redis yalnız ön kontrol (§7) |
| I10 | workspaces.status CHECK; iş üretimi uygulama katmanında durum koşullu |
| I11 | Rapor üretim sorgusu yalnız scores üzerinden (fidelity_label NOT NULL zinciri); etiketsiz veri yapısal olarak yok |

## 10. AVIP için Çıkarımlar

1. 0307 tasarım girdisi netleşti: kuyruklar outbox'tan beslenir; measurement_jobs durum makinesi ve kısmi indeksler zamanlayıcı-işçi sözleşmesinin veri tarafıdır.
2. 0309'a iki sözleşme iner: calculation_runs kolon seti (girdi karması, faktör anlık görüntüsü, iki versiyon ekseni) ve scores CHECK kuralları; hesap motoru bu şemaya yazarak determinizmi kanıtlar.
3. 0310'a üç açık devredildi: audit_log zincir doldurma stratejisi, S3 nesne kilidi modu ve KVKK silme prosedürünün yalnız-ekleme arşivle uzlaşımı (O-3; kripto-silme ve anonimleştirme adayları).
4. 0304 kararlarına somut kıyas zemini: migration aracı, ULID üretim kütüphanesi, ADR-005 kuyruk türü (Stream ve liste karşılaştırması §7 desenleri üzerinden yapılır).
5. 0311'e izleme kancaları: büyük tablo hacim eşikleri ve bölümlemeye geçiş kriteri [K], kullanılmayan indeks tespiti, outbox birikme alarmı.
6. Tam DDL ve ilk migration seti (0001_init benzeri) depo tarafında bu sözleşmeye göre yazılır; doküman-kod sapması 0403 drift kontrolüyle yakalanır.

## 11. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| O-1 | Kuyruk türü: Redis Stream mi liste mi | ADR-005; teslim garantileri ve tüketici grubu ihtiyacına göre; 0304; TL. |
| O-2 | Saklama süreleri ve S3 sınıf geçiş eşikleri [K] | 0204 O-4 ile birlikte; maliyet-uyum dengesi; TL + PO. |
| O-3 | KVKK silme ↔ yalnız-ekleme arşiv uzlaşımı | Kripto-silme (kiracı anahtarını imha) ve anonimleştirme adayları; 0310; PY + TL. |
| O-4 | Skor ölçeği ve hassasiyetin kesinleştirilmesi | 0309 hesap tasarımıyla; K7 varsayılanı 0-100, numeric(5,2). |

---

## Kaynaklar

- 0302 Domain Model · varlıklar, değişmezler (I1-I11), türetme kuralları (çıkarım 1)
- 0301 System Architecture · izolasyon katmanları, ölçüm hattı, ADR-004/005 önerileri
- 0204 PRD · NFR-N1/N6/N7/N11/N12/N14 (şema karşılıkları), FR-D2 alıntı meta zorunluluğu
- 0102 AI Search Landscape · kademe etiketi değer kümesi (tier_label)
- 0004 Success Metrics · K1/K3 korumalarının veri kaynakları (usage_records, freshness_at)

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 13.07.2026 | İlk yayın: 10 tasarım kuralı, 25 tabloluk envanter, 7 çekirdek tablo kolon sözleşmesi (outbox deseni dahil), erişim deseni bazlı indeks stratejisi, genişlet-daralt migration kuralları, Redis ve S3 modelleri, I1-I11 mekanizma eşlemesi. |
