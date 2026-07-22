# 0305 · Services & Modules

| Alan | Değer |
|---|---|
| Doküman ID | 0305 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.1 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 13 Temmuz 2026 |
| Karşıladığı madde | 21 · Services & Modules |
| İlişkili | 0301, 0302, 0304 (girdi); 0306, 0307, 0308, 0309, 0403 (çıktı); ADR-003 |

---

## 1. Amaç ve Kapsam

Bu doküman ADR-003 kararını (modüler monolit + işçi havuzu) paket düzeyine indirir: depo iskeleti, bağlam-paket eşlemesi, bağımlılık yönleri ve bunların derleyici ile lint tarafından nasıl zorlanacağı. Amaç, monolitin zamanla çamurlaşmasını önleyen sınırları ilk günden kurmaktır; 0304 §9'daki servis ayırma dönüş yolu ancak bu sınırlar korunursa ucuz kalır. Kapsam dışı: fonksiyon düzeyi tasarım, API sözleşmesi (0306), iş algoritmaları (0309), dağıtım düzeni (0402).

## 2. Depo ve Dizin İskeleti

| Yol | Amaç |
|---|---|
| cmd/api, cmd/scheduler, cmd/worker | Üç giriş noktası; aynı Go modülünden derlenen ayrı süreçler (ADR-003) |
| internal/identity, internal/config, internal/measure, internal/insight, internal/delivery, internal/governance | Altı bağlam paketi (0302 §3 haritası; §3 eşlemesi) |
| internal/engines | Motor bağdaştırıcıları ve kayıt defteri (0308); measure'ın tanımladığı arayüzü uygular |
| internal/platform | Çapraz kesen teknik paketler: db, queue, storage, telemetry, i18n, httpmw (§7) |
| internal/app | Kablolama: yapılandırma yükleme, bağımlılık kurulumu, süreç yaşam döngüsü (§5) |
| web/ | React + TypeScript SPA (ADR-002); ayrı derleme hattı, aynı depo (monorepo önerisi, O-3) |
| migrations/, docs/adr/, deploy/ | 0303 migration seti; ADR kayıtları; dağıtım tanımları (0402) |

## 3. Bağlam-Paket Eşlemesi

| Bağlam (0302) | Paket | Dışa açık yüzey (api.go) | Sakladığı iç |
|---|---|---|---|
| BC1 Kimlik ve Kiracılık | internal/identity | Kiracı/alan sorguları, üyelik ve rol kararları, paket hakkı sorgusu (Entitlements arayüzü) | Parola/oturum ayrıntısı, davet akış içi |
| BC2 Yapılandırma | internal/config | Marka/site/prompt seti yönetimi, panel tanımı okuma, şablon kütüphanesi | Panel içerik doğrulama kuralları |
| BC3 Ölçüm ve Hesap | internal/measure (+ calc alt paketi) | Ölçüm tetikleme, iş durumu, skor/trend okuma, bağdaştırıcı arayüz tanımı | calculation_run üretimi, örnekleme planı (0309) |
| BC4 İçgörü | internal/insight | Öneri listesi/işaretleme, (HT1) etki takibi | Üretim kuralları ve NG10 filtresi (0309 ile) |
| BC5 Bildirim ve Raporlama | internal/delivery | Kanal yönetimi, uyarı/özet/rapor uçları | Şablon işleme, digest birleştirme, PDF sürücüsü |
| BC6 Denetim ve Kota | internal/governance | Denetim yazıcısı (tek kapı), kullanım sayaç API'si, kota kararı | Zincir kolon doldurma (0310), sayaç dönemleri |

Sınır zorlaması derleyici desteklidir: her bağlam paketinin iç tipleri internal/\<bc\>/internal/ altında yaşar; Go bu dizinleri paket dışından import edilemez kılar. Dışa açık yüzey yalnız api.go arayüzleri ve DTO'larıdır.

## 4. Bağımlılık Kuralları

| # | Kural |
|---|---|
| D1 | cmd/* yalnız internal/app'i çağırır; iş mantığına doğrudan dokunmaz. |
| D2 | Bağlam paketleri birbirini yalnız dışa açık arayüz ve DTO üzerinden kullanır; iç tip importu derleyici (iç internal dizini) ve lint ile yasaktır. |
| D3 | Yön bağlam → platform'dur; internal/platform hiçbir bağlam paketini import edemez. |
| D4 | Bağımlılık tersine çevirme: bağdaştırıcı arayüzü measure tanımlar, internal/engines uygular; measure motorları kayıt defterinden tanır, engines'ı import etmez. |
| D5 | governance yalnız çağrılan taraftır (fan-in): herkes denetim yazıcısını ve kota kararını çağırır, governance kimseyi çağırmaz. |
| D6 | delivery, measure ve insight'ı olaylar üzerinden tüketir (outbox → kuyruk); derleme bağımlılığı DTO düzeyinde tutulur. |
| D7 | Döngüsel import yasaktır; import kuralları lint yapılandırmasıyla (depguard sınıfı) CI kapısına bağlanır (0403). |

## 5. Giriş Noktaları ve Kablolama

| Süreç | Sorumluluk ve iskelet |
|---|---|
| cmd/api | HTTP sunucusu; ara katman zinciri sabittir: panik kurtarma → request_id → kimlik doğrulama → kiracı bağlamı → RBAC → paket hakkı → işleyici. Zincir platform/httpmw'de tanımlı, sırası değiştirilemez (0301 §7). |
| cmd/scheduler | Tek etkin örnek (Redis kilidiyle seçim); izleme planlarını tarar, idempotent işleri outbox'a yazar; M10 pencere kayıtlarını açar (0307). |
| cmd/worker | Profil bayrağıyla başlar: measure, report, notify; ilgili Streams tüketici grubuna bağlanır; yük içindeki kiracı bağlamını yeniden doğrular (0301 §5). |
| internal/app | Elle bağımlılık kurulumu (çerçevesiz kablolama): yapılandırma (ortamdan; N4), havuzlar, depo/servis kurulumları, kapanış sıralaması. Tek kablolama noktası test edilebilirliği korur. |

## 6. Modül İçi Standart Yapı

Her bağlam paketi aynı dosya düzenini izler: api.go (dışa açık arayüzler ve DTO'lar), service.go (uygulama mantığı), repo.go (sqlc sarmalayıcıları; RLS oturumu platform/db üzerinden), events.go (outbox olay tanımları), errors.go (hata sözlüğü kodları; kullanıcı metni i18n'den), internal/ (saf iç tipler ve yardımcılar). Testler aynı pakette yaşar; entegrasyon testleri testcontainers ile gerçek PG/Redis üzerinde koşar ve integration derleme etiketiyle ayrılır. Standart düzen, gözden geçirme yükünü düşürür ve bağlamlar arası geçişte zihinsel haritayı korur.

## 7. Çapraz Kesen Uygulama Paketleri

| Paket | Sorumluluk |
|---|---|
| platform/httpmw | Ara katman zinciri (§5); kimlik, kiracı bağlamı, RBAC, paket hakkı, hız sınırı |
| platform/db | Havuz yönetimi; her işlemde SET LOCAL app.tenant_id (K4); sqlc bağlayıcıları |
| platform/queue | Streams üretici/tüketici sarmalayıcıları; outbox dağıtıcısı; yeniden teslim ve ölü kuyruk |
| platform/storage | S3 istemcisi; anahtar şeması (0303 §8); imzalı URL üretimi; NFR-N3 doğrulama kancası |
| platform/telemetry | OTel kurulum; korelasyon zinciri (request_id → job_id → calculation_run_id); metrik adları 0311 |
| platform/i18n | TR-öncelikli metin kaynakları; hata mesajı çözümleme (NFR-N15) |

Not: denetim yazımı çapraz kesen olmasına rağmen platformda değil governance bağlamındadır; tek yazıcı kapısı iş anlamı taşır (N6) ve fan-in kuralıyla (D5) korunur.

## 8. Kod Sahipliği ve Boyut Disiplini

CODEOWNERS bağlam bazlı tutulur; her bağlamın bir birincil sahibi vardır (W4 ölçeğinde kişi başına birden çok bağlam normaldir, sahiplik gözden geçirme yönlendirmesi içindir). Boyut sinyalleri nitel izlenir: bir bağlam paketi tek başına gözden geçirilemez büyüklüğe ulaşırsa önce iç alt paketlere bölünür (measure/calc örneği); servis ayırma yalnız 0304 §9 yanlışlanma sinyaliyle gündeme gelir. Yeni üst düzey paket açmak Tip 2 karardır ve bu dokümanın changelog'una işlenir; iskeletin sessizce genişlemesi engellenir.

## 9. AVIP için Çıkarımlar

1. 0306, api.go DTO'larını OpenAPI sözleşmesinden üretilen tiplere hizalar; el yazımı DTO ile üretilmiş tip çakışması lint ile yakalanır.
2. 0307, cmd/scheduler kilidi ve worker profillerinin parametrelerini (tüketici grubu adları, yeniden teslim süreleri) bu iskelet üzerinde tanımlar.
3. 0308, engines kayıt defteri sözleşmesini yazar: bağdaştırıcı arayüzü measure/api.go'da, uygulamalar internal/engines altında, kayıt app kablolamasında (D4).
4. 0403 lint kapıları buradan türetilir: depguard import kuralları (D1-D7), döngü tespiti, iç internal ihlal denetimi.
5. Walking skeleton dilimi (0301 çıkarım 2) şu paketlerle açılır: platform (db, httpmw, telemetry), identity (kayıt/oturum), config (marka+panel asgari), measure (+tek bağdaştırıcı), governance (denetim yazıcısı + kota iskeleti); insight ve delivery ikinci dilimde devreye girer.

## 10. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| ~~O-1~~ | ~~Hesap motorunun yeri: measure/calc alt paketi mi, ayrı bağlam mı~~ | ~~Öneri alt paket; 0309 tasarımı sırasında teyit; TL.~~ |
| **✅ O-1 (KAPANDI)** | **measure/calc alt paketinde kalır. Ayrı bağlam değil.** | **TL kararı (21.07.2026). 0007 D-64.** |
| ~~O-2~~ | ~~engines için ayrı Go modülü gerekip gerekmediği~~ | ~~Öneri tek modül; bağımlılık şişmesi sinyalinde yeniden değerlendirme; TL.~~ |
| **✅ O-2 (KAPANDI)** | **Tek modül — internal/engines. Bağımlılık şişerse ayrı modüle geçiş sinyali 0304 §9 yanlışlanma yolu olarak kalır.** | **TL kararı (21.07.2026). 0007 D-65.** |
| ~~O-3~~ | ~~web/ SPA'nın monorepo'da kalması~~ | ~~Öneri monorepo (tek PR akışı, sözleşme senkronu); 0403 boru hattı ayrımıyla; TL.~~ |
| **✅ O-3 (KAPANDI)** | **Monorepo — web/ aynı depoda. Ayrı derleme hattı, aynı PR akışı.** | **TL kararı (21.07.2026). 0007 D-66.** |
| ~~O-4~~ | ~~Lint kural setinin kesinleştirilmesi (depguard yapılandırması)~~ | ~~0403 ile; D1-D7 birebir kurala çevrilir; TL.~~ |
| **✅ O-4 (KAPANDI)** | **D1-D7 tüm kurallar depguard lint kuralı olarak 0403 CI kapısına bağlanır. İstisnasız uygulanır.** | **TL kararı (21.07.2026). 0007 D-67.** |

---

## Kaynaklar

- 0304 Technology Selection · ADR-003 kararı, sqlc ve araç seçimleri (iskeletin dayanağı)
- 0302 Domain Model §3 · bağlam haritası (paket eşlemesinin kaynağı)
- 0301 System Architecture · §5 izolasyon katmanları, §7 çapraz kesenler, walking skeleton önerisi
- 0303 Database Design · K4 oturum modeli, outbox (platform/db ve queue sözleşmeleri)
- 0007 Governance · Tip 2 karar süreci (§8 iskelet değişiklik kuralı)

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 13.07.2026 | İlk yayın: depo iskeleti, altı bağlam paketi eşlemesi (derleyici destekli iç sınırlar), 7 bağımlılık kuralı, üç giriş noktası ve kablolama, standart modül düzeni, çapraz kesen paket seti, sahiplik disiplini ve walking skeleton paket dilimi. |
| 1.1 | 21.07.2026 | O-1 kapandı: measure/calc onay. O-2 kapandı: tek modül onay. O-3 kapandı: monorepo onay. O-4 kapandı: D1-D7 lint kuralları. 0007 D-64..D-67. |
