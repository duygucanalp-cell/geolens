# 0306 · API Design

| Alan | Değer |
|---|---|
| Doküman ID | 0306 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 13 Temmuz 2026 |
| Karşıladığı madde | 22 · API Design |
| İlişkili | 0305, 0302, 0204 (girdi); 0310, 0311, 0403, 0404 (çıktı); FR-F6 (HT1) |

---

## 1. Amaç ve Kapsam

Bu doküman ürün API'sinin sözleşme standartlarını sabitler: kaynak modeli, kimlik ve bağlam çözümü, eşzamansız iş deseni, hata sözlüğü, sayfalama ve e-posta derin bağlantı uçları. Tam OpenAPI dosyası depoda api/openapi.yaml olarak yaşar ve bu standartlara uymak zorundadır; burada temsili uçlar verilir, tam envanter sözleşmededir. Kapsam dışı: SPA rota tasarımı, webhook alıcı uygulamaları, oturum ve token kriptografik ayrıntıları (0310).

## 2. Sözleşme Yaklaşımı

Sözleşme-öncelikli çalışılır: openapi.yaml tek gerçek kaynaktır; Go sunucu tipleri ve TypeScript istemci tipleri bu dosyadan üretilir (0305 çıkarım 1), el yazımı DTO sapması lint ile yakalanır ve sözleşme-kod drift kontrolü CI kapısıdır (0403). Sürümleme URL ana sürümüyle yapılır (/v1); kırıcı değişiklik yeni ana sürüm açar. Genişletme kuralları: yanıtlara yeni alan eklemek kırıcı değildir, istemciler bilinmeyen alanı yok sayar; mevcut alanın tipi veya anlamı değişmez; zorunlu istek alanı eklemek kırıcıdır. Tarihler UTC ISO-8601, kimlikler opak ULID dizeleridir (0302 K2); istemci kimlik biçimine anlam yüklemez.

## 3. Kimlik Doğrulama ve Bağlam

V1 ürün API'si oturum tabanlıdır: httpOnly ve SameSite çerezli oturum (ayrıntı 0310), CSRF koruması durum değiştiren isteklerde zorunludur. Her istek ara katman zincirinden geçer (0305 §5) ve kiracı bağlamı oturumdan çözülür. Aktif çalışma alanı yol üzerinde taşınır: /v1/workspaces/(ws)/... deseni; ajans çok-alan gezinmesini açık kılar, RBAC ve loglarda bağlam görünür olur (O-1 teyidi). Çalışma alanından bağımsız uçlar dar bir kümedir: /v1/auth/* (giriş, çıkış, parola sıfırlama), /v1/me (profil ve üyelikler), /v1/tenant (paket, kullanım özeti FR-H2). SSO uçları kurumsal kapı penceresinde sözleşmeye eklenir (FR-A4 yer tutucu).

## 4. Kaynak Modeli ve URL Sözleşmeleri

Adlandırma: çoğul, kebab-case kaynak adları; iç içe geçme en fazla bir seviye; eylemler alt kaynak olarak modellenir. Temsili envanter:

| Uç (ws öneki: /v1/workspaces/(ws)) | Amaç | Bağ |
|---|---|---|
| (ws)/brands, (ws)/sites | Marka ve alan adı yönetimi | FR-B1 |
| (ws)/prompt-sets; /v1/prompt-templates | Kiracı setleri; sistem şablon kütüphanesi (salt okuma) | FR-B2 |
| (ws)/panels, (ws)/panels/(id)/versions | Panel ve versiyon okuma (I4 izlenebilirliği) | 0302 §5 |
| (ws)/measurements (POST), (ws)/measurements/(id) | Manuel tetik ve iş durumu (§5 deseni) | FR-C1, UC-06 |
| (ws)/scores, (ws)/scores/(id) | Skor listesi ve detay; fidelite, GA, tazelik alanları zorunlu | FR-C5-C7 |
| (ws)/calculation-runs/(id) | Açıklama katmanı: girdi karması, faktör anlık görüntüsü, versiyonlar | UC-07, İ3 |
| (ws)/trends | Zaman serisi; panel versiyon sınır işaretleri yanıtın parçası | FR-D4 |
| (ws)/citations, (ws)/sources | Alıntı listeleri ve kaynak toplulaştırması; url alanı zorunlu | FR-D2, M9 |
| (ws)/site-audits (POST), .../findings | Site erişim denetimi ve bulgular | FR-B4, UC-04 |
| (ws)/recommendations, PATCH .../(id) | Öneri listesi; işaretleme (uygulandı/reddedildi) | FR-E1, E3 |
| (ws)/alerts, .../(id)/feedback; (ws)/alert-rules; (ws)/channels | Uyarılar, geri bildirim (M11), eşik ve kanal ayarları | FR-F1, F2 |
| (ws)/reports (POST), .../(id), .../(id)/download | Rapor üretimi (202), durum, kısa ömürlü imzalı indirme | FR-F4 |
| /v1/tenant/members, /v1/tenant/invitations | Üye ve davet yönetimi (kiracı düzeyi) | FR-A2 |
| /v1/tenant/usage | Kota ve kullanım görünümü | FR-H2 |
| /v1/tenant/audit-log | Denetim izi görünümü (Genişletilmiş; sözleşme yer tutucu) | FR-H1 |

## 5. Eşzamansız İş Deseni

Uzun işler (ölçüm, rapor, site denetimi) tek desenle sunulur: POST tetik 202 Accepted döner, Location başlığı iş kaynağını gösterir; iş kaynağı 0302 §7 durum makinesini birebir yansıtır (queued, running, completed, partial, failed) ve tamamlanınca sonuç bağlantılarını içerir (skor listesi, bulgular veya download alt kaynağı). Rapor indirme her çağrıda taze, kısa ömürlü imzalı URL üretir; URL yanıt gövdesinde döner ve loglanmaz. İstemci tetiklerinde Idempotency-Key başlığı desteklenir; sunucu bunu ölçüm işinin idempotent anahtarıyla (0303) birleştirir, aynı anahtar ikinci kez 200 ile mevcut işi döndürür. Kısmi tamamlanma (partial) yanıtı kısmilik nedenini motor bazında listeler (NFR-N9).

## 6. Hata, Hız Sınırı ve Kota Yüzeyi

Tüm hatalar tek zarfla döner: code (kararlı makine kodu), message (TR kullanıcı metni; i18n kaynağından), details (alan bazlı doğrulama girdileri), correlation_id (request_id; 0311 zinciri). Kod aileleri: AUTH_*, TENANT_*, ENTITLEMENT_*, VALIDATION_*, QUOTA_*, RATE_*, NOT_FOUND, CONFLICT, INTERNAL. İki sözleşme kuralı sertliğiyle korunur: kiracı dışı kaynak istekleri ayrım yapılmadan NOT_FOUND döner, kaynağın varlığı sızdırılmaz (0204 §6 izolasyon kriterinin API yüzü); paket hakkı dışı çağrılar ENTITLEMENT_DENIED koduyla 403 döner ve yükseltme yolunu işaret eder (FR-A5). Hız sınırı yanıtları 429 + Retry-After ve X-RateLimit-* başlıklarıyla gelir; dönemsel kota aşımı da 429 kullanır ancak QUOTA_EXCEEDED koduyla ayrışır ve /v1/tenant/usage bağlantısı taşır (K1 şeffaflığı, FR-H2).

## 7. Sayfalama ve Filtreleme

Listeler imleç tabanlıdır: cursor ve limit parametreleri; yanıt items, next_cursor ve has_more döner. ULID sıralanabilirliği varsayılan imleç kaynağıdır; toplam kayıt sayısı büyük listelerde verilmez (maliyet disiplini), gerektiğinde ayrı özet uçları kullanılır. Filtre ve sıralama parametreleri kaynak bazlı beyaz listedir ve sözleşmede numaralanır; serbest metin arama yalnız tanımlı alanlarda q parametresiyle sunulur. Zaman aralığı from/to (UTC) çiftiyle verilir; trend uçlarında pencere boyutu sunucu tarafından panel frekansına göre normalize edilir.

## 8. Derin Bağlantı ve Webhook Sözleşmeleri

E-posta özetindeki her skor ve içgörü bağlantısı imzalı kısa bağlantı taşır: /l/(token). Çözümleme ucu token'ı doğrular (tek kiracı + tek hedef kapsamı, kısa ömür [K]; kriptografi 0310), oturum yoksa hedefi koruyarak girişe yönlendirir, oturum sonrası kullanıcıyı doğru çalışma alanındaki hedef kaynağa indirir. Her çözümleme tıklama telemetrisi üretir; e-postadan panoya geçiş metriği adayının (0202 §10) veri kaynağı budur ve M1 derinleşme koşulunu besler. Giden webhook'lar (Slack dışı genel uçlar) gövde HMAC imzası ve zaman damgası başlığı taşır; alıcı tarafında yeniden oynatma penceresi reddi önerilir; teslim yeniden denemeleri delivery işçisinin politikasına bağlıdır (0307).

## 9. Dış Okuma API'si (FR-F6; HT1 yer tutucu)

Hızlı takip 1 penceresinde açılacak dış okuma API'si aynı sözleşme ailesinde ayrı yüzeydir: /public/v1 öneki, kiracı başına üretilen API anahtarları (salt okuma kapsam belirteçleriyle), ayrı hız sınırı sınıfı ve yalnız okuma uçları (skorlar, trendler, kaynaklar, raporlar meta). OpenAPI bileşenleri iç API ile paylaşılır; kapsam kararı 0204 O-3'e bağlıdır. Bu bölüm sözleşmede yer tutucu olarak bulunur, V1'de uygulanmaz.

## 10. AVIP için Çıkarımlar

1. 0403 kapıları: openapi.yaml şema doğrulaması, üretilen Go/TS tip senkron kontrolü, örnek tabanlı sözleşme testleri.
2. 0404'e iki sözleşme testi iner: kiracı dışı NOT_FOUND kuralı (varlık sızdırmama) ve ENTITLEMENT_DENIED yolu; her ikisi §6'nın doğrulamasıdır.
3. 0310'a devirler: oturum ve CSRF ayrıntıları, derin bağlantı token kriptografisi ve ömrü (O-2), webhook imza anahtar yönetimi, SSO uçlarının eklenme biçimi.
4. 0311 sözleşmesi: correlation_id her hata zarfında; API metrik adları (uç bazlı gecikme, hata kodu dağılımı) telemetri planına girer.
5. Walking skeleton uç dilimi: auth, me, tenant/usage, brands, prompt-sets, measurements, scores, calculation-runs; geri kalan uçlar dilim ilerledikçe sözleşmeden açılır.

## 11. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| O-1 | Aktif çalışma alanının yolda taşınmasının teyidi | Öneri path (bu doküman); alternatif başlık modeli reddi; TL. |
| O-2 | Derin bağlantı token ömrü ve tek kullanım politikası [K] | 0310; M1 dönüşümü ile güvenlik dengesi; TL. |
| O-3 | Dış API anahtar ve kapsam modeli | HT1; 0204 O-3 ile; TL + PO. |
| O-4 | OpenAPI üreteç araçlarının kesinleştirilmesi | oapi-codegen sınıfı; 0403 zinciriyle; TL. |

---

## Kaynaklar

- 0305 Services & Modules · api.go/DTO hizası, ara katman zinciri (uç davranışının iskeleti)
- 0302 Domain Model · kaynak adları, opak kimlik kuralı, durum makineleri (§5 deseni)
- 0204 PRD · FR-C/D/E/F ailesi (uç envanterinin kaynağı), N1/N2 sözleşme kuralları
- 0303 Database Design · idempotent anahtar birleşimi, imleç kaynağı (ULID)
- 0202 User Journey · derin bağlantı gerekliliği (M1) ve e-posta-pano geçiş metriği adayı

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 13.07.2026 | İlk yayın: sözleşme-öncelikli yaklaşım ve sürümleme kuralları, oturum/bağlam modeli (path tabanlı çalışma alanı), 15 satırlık temsili kaynak envanteri, 202 eşzamansız desen, hata sözlüğü (varlık sızdırmama ve kota ayrımı), imleç sayfalama, imzalı derin bağlantı ve webhook sözleşmeleri, dış okuma API yer tutucusu. |
