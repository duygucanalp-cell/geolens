# 0301 · System Architecture

| Alan | Değer |
|---|---|
| Doküman ID | 0301 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.2 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 13 Temmuz 2026 |
| Karşıladığı madde | 18 · System Architecture (konteyner görünümü, izolasyon, hat, karar seti) |
| İlişkili | 0204, 0005 (girdi); 0302, 0303, 0304, 0305, 0306, 0307, 0308, 0309, 0310, 0311 (çıktı); ADR-001–ADR-005 |

---

## 1. Amaç ve Kapsam

Bu doküman AVIP'in sistem mimarisini konteyner düzeyinde sabitler: bileşenlerin sorumlulukları, izolasyon katmanları, uçtan uca ölçüm hattı, ara katman zinciri, Önerildi statüsünde mimari karar seti ve bağdaştırıcı soyutlama ilkesi. Mimari fazın (Faz 3) giriş dokümanıdır; 0302–0311 seti buradaki sözleşmeleri detaylandırır. Kapsam dışı: fonksiyon düzeyi tasarım (her bağlam kendi dokümanında), dağıtım ortamı ve sağlayıcı seçimi (0402), CI/CD araç zinciri (0403), kapasite boyutlandırma (0311).

## 2. Tasarım İlkeleri

| # | İlke | Açıklama |
|---|---|---|
| P1 | Tek dağıtım, tek kod tabanı | Modüler monolit (ADR-003); işçiler aynı koddan ayrı süreç olarak çalışır |
| P2 | Bağlam sınırları paket düzeyinde | Go internal dizinleri derleyici tarafından zorlanır; iç import yasak |
| P3 | Kiracı bağlamı her katta | Veri katmanından iş mantığına ve kuyruğa kadar kiracı bağlamı taşınır |
| P4 | Deterministik hesap | Aynı girdilerle aynı sonuç; versiyonlanmış algoritmalar (N7) |
| P5 | Değişime dayanıklılık | Bağdaştırıcı ekleme/kaldırma kayıt defteri düzeyinde yapılır; mimari değişmez |

## 3. Konteyner Görünümü

Sistem üç ana süreçten ve destekleyici altyapı bileşenlerinden oluşur:

| Süreç | Sorumluluk | Ana bileşenler |
|---|---|---|
| **cmd/api** | HTTP API sunucusu; istek yönetimi, kimlik doğrulama, kiracı bağlamı, RBAC, paket hakkı kontrolü | platform/httpmw (ara katman zinciri), tüm bağlam paketlerinin api.go yüzeyleri |
| **cmd/scheduler** | Zamanlayıcı; izleme planlarını tarar, pencere kayıtları açar, idempotent ölçüm işleri üretir, outbox'a yazar | platform/queue (outbox dağıtıcısı), internal/config (izleme planları) |
| **cmd/worker** | İşçi süreçleri; profil bayrağıyla (measure, report, notify) çalışır, kuyruktan iş alır, sonuçları kalıcılaştırır | internal/measure, internal/delivery, platform/queue (Streams tüketici grupları) |

### Altyapı Bileşenleri

| Bileşen | Rol | Not |
|---|---|---|
| PostgreSQL 16+ | Birincil veri kaynağı; tek şema + RLS ile çok kiracılı izolasyon (ADR-004) | 0303 şema sözleşmesi |
| Redis 7+ | İş kuyrukları (Streams), hız sınırları, kilitler, önbellek | Kaynak veri değil; tam kayıpta outbox'tan yeniden inşa (0303 §7) |
| S3-uyumlu depo | Ham yanıt arşivi, raporlar, marka varlıkları | Kiracı önekli anahtar şeması (0303 §8) |

## 4. Konteyner Sorumlulukları

### cmd/api

- HTTP isteklerini alır ve işler
- Ara katman zinciri sıralaması sabittir (§7)
- Tüm bağlam paketlerinin dışa açık yüzeylerini (api.go) sunar
- Hata yanıtlarını standart biçimde üretir (correlation_id taşır)

### cmd/scheduler

- Tek etkin örnek (Redis kilidi ile seçim)
- İzleme planlarını okur, UTC pencere hesabını yapar
- Idempotent ölçüm işleri üretir (measurement_jobs + event_outbox aynı PG işleminde)
- M10 pencere kayıtlarını açar
- Kesinti sonrası sınırlı derinlikte telafi üretir

### cmd/worker

- Üç profil: measure, report, notify
- İlgili Streams tüketici grubuna bağlanır
- Yük içindeki kiracı bağlamını yeniden doğrular
- Zarif kapanış: SIGTERM sonrası mevcut işleri tamamlar

## 5. Beş Katmanlı İzolasyon Modeli (ADR-004 uygulaması)

Kiracılık izolasyonu beş bağımsız katmandan oluşur; katmanlardan birinin ihlali diğerlerini etkilemez:

| Katman | Mekanizma | Doğrulama |
|---|---|---|
| **1. Kuyruk Katmanı** | Redis anahtarları kiracı önekli; iş yükünde kiracı kimliği taşınır; işçi yükleme sonrası bağlamı doğrular | Yük kiracı uyuşmazlığı testi; kuyruk taraması testleri |
| **2. Depolama Katmanı** | S3 anahtar şeması `raw/(tenant)/(workspace)/...` formatındadır; imzalı URL'ler kapsam sınırlı | Çapraz kiracı önekine imzalı URL üretilemezlik testi |
| **3. Süreç/Bağlam Katmanı** | İşçi her işi yürütmeden önce kiracı bağlamını yükten okur ve doğrular; bağlamsız yol yalnız kimlik doğrulamada | İşçi負荷 testi: uyuşmazlıkta iş reddi ve alarm |
| **4. Veri/Şema Katmanı** | Tek şema + RLS; `SET LOCAL app.tenant_id` her işlem başında; politika şablonu 0303 K4 | Gerçek PG üzerinde negatif testler: A kiracısı B verisine erişemez |
| **5. Test Kapısı** | İzolasyon negatif testleri CI'da zorunlu kapıdır; her sprintte çalıştırılır | 0404 test paketi; 0403 CI kapısı |

## 6. Uçtan Uca Ölçüm Hattı

Ölçüm işinin tetiklenmesinden skorun yayınlanmasına kadar olan tam veri akışı:

| Adım | Sorumlu | Açıklama |
|---|---|---|
| 1. Tetikleme | cmd/scheduler | İzleme planı penceresi açıldığında zamanlayıcı tetiklenir |
| 2. İş üretimi | cmd/scheduler | measurement_jobs kaydı + event_outbox aynı PG işleminde yazılır; idempotency_key |
| 3. Outbox dağıtımı | Outbox dağıtıcısı | pending kayıtlar kilitli okunur (SKIP LOCKED), Streams kuyruğuna eklenir, dispatched işaretlenir |
| 4. Kuyruktan okuma | cmd/worker | XREADGROUP ile iş alınır; kiracı bağlamı yükten doğrulanır |
| 5. Bağdaştırıcı çağrısı | internal/engines | Seçili motor bağdaştırıcısı (ChatGPT, Gemini, Perplexity) Execute() ile çağrılır |
| 6. Ham yanıt saklama | internal/measure | Yanıt S3'e yazılır, meta verisi raw_responses tablosuna kaydedilir |
| 7. Hesaplama | internal/measure/calc | Hesap koşusu (calculation_run) üretilir; faktör anlık görüntüsü ve algoritma versiyonu saklanır |
| 8. Skor üretimi | internal/measure/calc | Skorlar hesaplanır; panel versiyonuna, calculation_run'a ve markaya bağlanır |
| 9. Korelasyon zinciri | Tüm katmanlar | request_id → job_id → calculation_run_id zinciri korunur; her hata zarfı correlation_id döndürür |

**Not:** 0307 bu hattın 1-3 ve 8-9 adımlarının sözleşme düzeyinde ayrıntısını verir; 0308 5. adımın bağdaştırıcı içini, 0309 7-8. adımların algoritmasını tanımlar.

## 7. Ara Katman Zinciri (Middleware Stack)

HTTP isteklerinin işlenme sırası sabittir ve değiştirilemez:

| Sıra | Ara katman | Sorumluluk |
|---|---|---|
| 1 | Panik kurtarma | Beklenmedik hatalarda düzgün yanıt üretir; log yazar |
| 2 | Request ID | Her isteğe benzersiz kimlik atar; korelasyon zincirinin başı |
| 3 | Kimlik doğrulama | Oturum/Token doğrulaması; kullanıcı kimliğini bağlama yazar |
| 4 | Kiracı bağlamı | Kullanıcının üyelik olduğu kiracıyı belirler; `app.tenant_id` oturum değişkenini kurar |
| 5 | RBAC | Kullanıcının rolüne göre eylem izni kontrol eder (0310 §4) |
| 6 | Paket hakkı | Kiracının paketinin ilgili özelliği kapsamayıp kapsamadığını kontrol eder (entitlement) |
| 7 | İşleyici | Asıl iş mantığını çalıştırır |

Zincir platform/httpmw paketinde tanımlıdır; sırası uygulama tarafından değiştirilemez. Her ara katman başarısız olursa isteği sonlandırır ve uygun hata kodunu döndürür.

## 8. Önerildi Statüsünde Mimari Kararlar

0301'de Önerildi olarak kaydedilen kararlar, 0304'te alternatif kıyaslarıyla karara bağlanır ve Kabul statüsüne geçirilir:

| Karar | Konu | Öneri | Durum |
|---|---|---|---|
| ADR-001 | Çekirdek Yığın | Go + PostgreSQL 16+ + Redis 7+ + S3-uyumlu | Önerildi → 0304'te Kabul |
| ADR-003 | Uygulama Topolojisi | Modüler monolit + işçi havuzu | Önerildi → 0304'te Kabul |
| ADR-004 | İzolasyon Mekanizması | Tek şema + RLS + uygulama sözleşmesi | Önerildi → 0304'te Kabul |
| ADR-005 | İş Kuyruğu | Redis Streams + tüketici grupları | Önerildi → 0304'te Kabul |
| ADR-002 | İstemci Yığını | React + TypeScript SPA (Flutter mobil pencereye rezerve) | Önerildi → 0304'te Kabul |

Statü sözlüğü: Önerildi (bu dokümanda) → Kabul (0304 onayıyla) → PENDING (doküman dışı karar).

## 9. Bağdaştırıcı Soyutlaması ve Değişime Dayanıklılık

Motor bağdaştırıcıları measure bağlamında tanımlanan arayüzü uygular ve engines paketinde yaşar (0305 D4). Temel ilke: **yeni bir bağdaştırıcı eklemek mimari değişiklik değil, kayıt defteri değişikliğidir.**

Bağdaştırıcı ekleme süreci dört adımdır:
1. Bağdaştırıcı uygulaması (arayüz implementasyonu)
2. Kayıt defterine giriş (derleme zamanında)
3. Entitlement anahtarı tanımı (kiracı paket hakkına ekleme)
4. K1 maliyet profili girişi

Bu süreç Tip 2 karardır ve 0007 değişiklik süreciyle yönetilir. Mevcut sistemde bu değişiklik ne API yüzeyini ne de veri şemasını etkiler; yalnız yapılandırma ve paket hakkı düzeyinde güncelleme gerektirir.

Mimari esneklik gereksinimi (0206'da ifade edilen): bağısurucu ekleme/kaldırma maliyetinin düşük olması, platformun yeni motorlara hızlı uyum sağlamasını mümkün kılar.

## 10. Çıkarımlar

Bu dokümandan türeyen temel çıkarımlar:

1. **Konteyner sorumlulukları** → 0302 alan modelinin bağlam haritasını ve dilini kurar; 0305 modül-paket eşlemesinin temelidir.
2. **Walking skeleton dilimi** → İlk açılış sırası: platform (db, httpmw, telemetry) → identity (kayıt/oturum) → config (marka+panel asgari) → measure (+tek bağdaştırıcı) → governance (denetim yazıcısı + kota iskeleti); insight ve delivery ikinci dilimde.
3. **Beş katmanlı izolasyon** → 0303 RLS/K4 kurallarının, 0310 negatif test stratejisinin ve 0305 işçi bağlam doğrulamasının kaynağıdır.
4. **Ölçüm hattı** → 0307 iş kuyruğu tasarımının, 0308 bağdaştırıcı sözleşmesinin ve 0309 hesaplama tasarımının girdisidir.
5. **Ara katman zinciri** → 0305 httpmw paketinin, 0306 API tasarımının ve 0310 oturum/RBAC modelinin zeminini oluşturur.
6. **Karar seti** → 0304'ün kıyas edeceği alternatifler ve kriterler bu dokümanda sabitlenir.
7. **Bağdaştırıcı soyutlaması** → 0308 motor tasarımının, 0206 yol haritasının esneklik gereksiniminin karşılığıdır.

## 11. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| ~~O-1~~ | ~~Walking skeleton dilimlerinin zamanlama ve sıralama detayı~~ | ~~Faz 4 planlamasında netleşir; TL.~~ |
| **✅ O-1 (KAPANDI)** | **0401 §7'deki 4 dilimli plan onaylandı: D1: platform+identity+config+measure+gov → D2: kalan motorlar+pano+denetim → D3: delivery+insight → D4: sertleştirme.** | **PO+TL kararı (21.07.2026). 0007 D-53.** |
| ~~O-2~~ | ~~Worker profillerinin (measure, report, notify) bağımsız ölçekleme ihtiyacı~~ | ✅ **KAPANDI**: V1'de tek replika seti (tüm profiller aynı grupta). Pilot verisiyle yük gözlenir, ihtiyaç halinde HT1'de ayrıştırılır. 0007 D-72. |
| ~~O-3~~ | ~~Tek etkin örnek zamanlayıcı için Redis kilidi kaybı senaryosu~~ | ✅ **KAPANDI**: Anında pasif (mevcut tasarım onay). Kilit kaybında üretim durur, yeni lider seçilene kadar beklenir. Tüketim etkilenmez. 0007 D-73. |

---

## Kaynaklar

- 0204 PRD · Fonksiyonel gereksinimler (konteyner sorumluluklarının kaynağı)
- 0005 Glossary · Terminoloji hizası
- 0304 Technology Selection · ADR kararlarının kıyaslanması (bu dokümanın çıktısı)
- 0305 Services & Modules · Paket yapısı ve bağımlılık kuralları (bu dokümanın çıktısı)
- 0307 Background Jobs · İş kuyruğu ve zamanlayıcı tasarımı (ölçüm hattının çıktısı)
- 0308 AI Connectors · Bağdaştırıcı sözleşmesi ve kayıt defteri (bağdaştırıcı soyutlamasının çıktısı)
- 0310 Security · İzolasyon test stratejisi (beş katmanın doğrulaması)

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 13.07.2026 | İlk yayın: 5 tasarım ilkesi, 3 konteyner sorumluluğu, 5 katmanlı izolasyon modeli, 9 adımlık uçtan uca ölçüm hattı, 7 katmanlı ara katman zinciri, 5 Önerildi statüsünde mimari karar, bağdaştırıcı soyutlaması ve değişime dayanıklılık ilkesi, walking skeleton önerisi, 7 temel çıkarım. |
| 1.1 | 21.07.2026 | O-1 kapandı: 4 dilimli walking skeleton planı onayı. 0007 D-53. |
| 1.2 | 21.07.2026 | O-2 kapandı: V1'de tek replika seti (HT1'de ayrıştırma adayı). O-3 kapandı: Redis kilit kaybı anında pasif. 0007 D-72, D-73. |
