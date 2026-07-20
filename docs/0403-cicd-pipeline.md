# 0403 · CI/CD Pipeline

| Alan | Değer |
|---|---|
| Doküman ID | 0403 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 13 Temmuz 2026 |
| Karşıladığı madde | Türetilmiş doküman: 0401 süreç kurallarının otomasyon zorlaması |
| İlişkili | 0401, 0305, 0306, 0303, 0310, 0402 (girdi); 0404, 0406 (çıktı) |

---

## 1. Amaç ve Kapsam

Bu doküman sürekli entegrasyon ve dağıtım hattını sabitler: kapı zinciri, imaj üretimi, staging ve production dağıtım akışı ve ihlal disiplini. 0401'deki süreç kurallarının makine tarafından zorlanmasıdır; bir kural burada kapıya bağlanmamışsa yalnız niyettir. Kapsam dışı: test paketlerinin içerik tasarımı (0404; burada yalnız hangi kapının hangi paketi çağırdığı), yayın treni ve sürüm politikası (0406; burada yalnız teknik terfi mekaniği), CI platformunun marka seçimi (O-1; sınıf düzeyinde tanım).

## 2. Boru Hattı Genel Görünümü

| Hat | Tetik ve içerik | Hedef |
|---|---|---|
| PR hattı | Her PR güncellemesinde §3 kapı zinciri; hızlı geri bildirim için paralel aşamalar | Dakikalar sınıfı toplam süre |
| Main hattı | Birleşme sonrası tam matris + imaj üretimi (§4) | Her main durumu dağıtılabilir imaj seti üretir |
| Dağıtım hattı | Staging otomatik, production elle onaylı terfi (§5) | Bir kez derle, terfiyle taşı |

Tüm hatlar 0402 ile aynı araç ve hizmet sürümlerini kullanır (compose ve testcontainers sürüm birliği); hat tanımları depoda kod olarak yaşar ve PR ile değişir.

## 3. PR Kapı Zinciri (sıralı; tamamı zorunlu, atlama yok)

| # | Kapı | İçerik |
|---|---|---|
| 1 | Biçim ve lint | Biçim denetimi; lint; depguard import kuralları (0305 D1-D7), iç internal ihlali ve döngü tespiti |
| 2 | Birim testleri | Tüm paketler; hesap motoru paketi (measure/calc) her PR'da zorunlu (0401 DoD-1) |
| 3 | Entegrasyon ve izolasyon | Testcontainers ile gerçek PG/Redis; izolasyon negatif paketinin hızlı alt kümesi her PR'da (O-2 kapsamı), tam paket main'de (0310 §5) |
| 4 | Sözleşme senkronu | OpenAPI şema doğrulaması; üretilmiş Go/TS tiplerinin taahhütlü kopyayla birebir eşleşmesi (0306 §2) |
| 5 | Migration kapısı | Tüm migration'lar boş veritabanına baştan uygulanır; şema anlık görüntüsüyle drift karşılaştırması; yalnız-ekleme trigger'larının varlık testi (0303 §6, K3) |
| 6 | Güvenlik taramaları | Sır sızıntı taraması (fark ve tarihçe), Go ve npm bağımlılık taraması (0310 §9 sürekli halka) |
| 7 | Web derlemesi | SPA tip kontrolü ve üretim derlemesi; sözleşme tipleriyle tutarlılık |

Etiket bazlı ekler: migration, sözleşme veya güvenlik etiketli PR'larda ilgili tam paketler PR hattında da koşar (0401 §4 etiketleri kapı tetikleyicisidir).

## 4. Main Hattı ve İmaj Üretimi

Birleşme sonrası main hattı tam test matrisini koşar (izolasyon tam paketi ve sözleşme örnek testleri dahil), ardından üç imajı üretir: app, renderer, web (0402 §4). İmajlar git özeti ile etiketlenir; sürüm etiketleri 0406 treninde eklenir. İmaj taraması bu hattadır (temel imaj ve bağımlılık zafiyetleri); bağımlılık envanteri imajla birlikte üretilip saklanır. Üretilen imaj değiştirilemez referanstır: staging ve production aynı imajı çalıştırır, ortam farkı yalnız yapılandırmadır (0402 §3 eşitlik ilkesi). İmaj imzalama sınıfı doğrulama V1 adayıdır (O-3); karar depo tarafında uygulanır.

## 5. Dağıtım Akışı

Staging: main hattı yeşil bittiğinde otomatik dağıtım yapılır; sıralama sabittir: yedek/geri dönüş noktası işaretlenir, migration'lar uygulanır (genişlet-daralt kuralı sayesinde eski kod yeni şemayla güvenle çalışır), yeni imaj devreye alınır, smoke doğrulama koşar (0404 sınıfı: sağlık uçları, korelasyon zinciri izlemesi, kritik akış örnekleri). Smoke başarısızlığı dağıtımı otomatik geri alır ve alarm üretir. Production: elle onaylı terfidir; staging'de doğrulanmış imaj ve migration seti tek adımla terfi eder, yeniden derleme yoktur. Geri alma politikası: kod için imaj geri terfisi dakikalar içinde; şema için geri migration yoktur, ileri-düzeltme uygulanır (0303 §6); bu asimetri genişlet-daralt disiplininin varlık sebebidir. Zamanlayıcı ve işçilerin sürüm geçişi 0307 §8 güvencesiyle kesintisizdir.

## 6. Kapı İhlalleri ve Disiplin

Kırmızı main protokolü: main hattı kırmızıysa yeni özellik birleşmez; düzeltme veya geri alma en yüksek önceliktir ve kırmızı süresi metriktir (§7). Kapı atlama mekanizması yoktur; acil durumda bile hotfix yolu güvenlik taramaları, izolasyon hızlı alt kümesi ve migration kapısını korur, yalnız tam matris birleşme sonrasına ertelenir (0401 O-4 sınırlarıyla uyumlu). Kararsız (flaky) test sessizce yeniden denenmez: karantina etiketiyle işaretlenir, kayıt açılır ve karantina listesi haftalık ritimde (0401 §8) eritilir; karantinadaki test kapı sonucunu etkilemez ama görünür borçtur.

## 7. Boru Hattı Gözlemlenebilirliği

Hat kendi telemetrisini üretir: hat ve kapı süreleri, kapı başarısızlık dağılımı, kırmızı main süresi, karantina (flaky) sayısı ve yaşı, dağıtım sıklığı ile geri alma sayısı. Bu küçük küme 0311 metrik kataloğuna ek olarak işlenir ve aylık kapasite/kalite gözden geçirmesinde okunur; hedef, sürecin kendisinin de trend tabanlı yönetilmesidir (yavaşlayan hat, artan flaky ve uzayan kırmızı süreleri erken uyarıdır).

## 8. AVIP için Çıkarımlar

1. 0404 sözleşmesi net: kapıların çağırdığı paketlerin (birim, izolasyon hızlı/tam, sözleşme, smoke) içerik tanımı orada yapılır; kapı-paket eşlemesi bu dokümandan referans alınır.
2. 0406 entegrasyon noktaları hazır: sürüm etiketleme main hattına, terfi onayı dağıtım hattına bağlanır.
3. 0401 DoD otomasyon eşlemesi tamam: DoD 1-3 ve 6 maddeleri kapı zincirinde, 4-5 ve 7-8 maddeleri PR şablon denetimindedir; süreç ile otomasyon arasında boşluk kalmadı.
4. Hattın kurulumu dilim 1 işidir: walking skeleton'ın ilk PR'ı bu kapılardan geçer; kapılar sonradan eklenmez, iskeletle doğar.

## 9. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| O-1 | CI platform sınıfının seçimi | Depo barındırmayla birlikte; TL. |
| O-2 | İzolasyon hızlı alt kümesinin kapsamı | PR süresi ile güvence dengesi; 0404 ile; TL. |
| O-3 | İmaj imzalama ve doğrulamanın V1'e alınması | Tedarik zinciri güvencesi; TL. |
| O-4 | Staging yedek/geri dönüş noktası mekaniği | Sağlayıcı yeteneğine bağlı; 0402 O-1 ile; TL. |

---

## Kaynaklar

- 0401 Development Process · DoD ve PR etiketleri (kapı tetikleyicileri), hotfix sınırları
- 0305 / 0306 / 0303 · depguard kuralları, sözleşme senkronu, migration kapı kuralları
- 0310 §9 · sürekli halka taramaları ve izolasyon negatif paketi
- 0402 Environments & Docker · imaj seti, ortam eşitliği, sürüm birliği
- 0307 §8 · sürüm geçişi güvencesi (dağıtım kesintisizliği)

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 13.07.2026 | İlk yayın: üç hatlı boru modeli, yedi kapılı PR zinciri (etiket bazlı ekler), main imaj üretimi ve değiştirilemez referans kuralı, staging otomatik / production elle terfili dağıtım (bir kez derle), kırmızı main ve karantina disiplini, hat telemetrisi ve DoD otomasyon eşlemesi. |
