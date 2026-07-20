# 0404 · Test Strategy

| Alan | Değer |
|---|---|
| Doküman ID | 0404 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 13 Temmuz 2026 |
| Karşıladığı madde | Türetilmiş doküman: 0403 kapılarının çağırdığı test paketlerinin içerik tanımı |
| İlişkili | 0403, 0309, 0310, 0306, 0307, 0402 (girdi); 0405 (çıktı); 0401 DoD-1 |

---

## 1. Amaç ve Kapsam

Bu doküman test stratejisini ve 0403 kapılarının çağırdığı paketlerin içeriğini sabitler: katman yapısı, paket odakları, izolasyon matrisi, motor bağımlılığının fixture yaklaşımı ve kalite göstergeleri. Kapsam dışı: kapı sıralaması ve tetikleyicileri (0403), test kodunun kendisi (depoda), kapsamlı yük/performans programı (pilot sonrası; burada yalnız duman düzeyi), manuel keşif testinin oturum planları (haftalık ritim içinde hafif yürütülür).

## 2. Piramit ve İlkeler

Katmanlar tabandan uca: birim testleri (çoğunluk; saf mantık, milisaniyeler), entegrasyon testleri (testcontainers ile gerçek PG ve Redis; RLS ve trigger'lar gerçek), sözleşme testleri (üretilen tiplerle örnek tabanlı), uçtan uca smoke (dar küme; staging). Dört ilke: (1) Gerçekçilik taklitten üstündür: izolasyon, RLS ve yalnız-ekleme korumaları asla taklit edilmez, gerçek veritabanında doğrulanır. (2) Testler deterministiktir: sabit saat ve tohum enjekte edilir; zamana ve sıraya bağımlı test kabul edilmez. (3) Hız bütçelidir: PR alt kümeleri 0403 süre hedefine sığar; yavaşlayan paket bölünür. (4) Test spesifikasyondur: her test izlenebilirlik kimliği taşır (FR/NFR/UC/I referansı test adında veya etiketinde), kaynağı olmayan davranış test edilmez, test edilmeyen sözleşme iddia sayılmaz.

## 3. Birim Test Paketleri

| Paket | Zorunlu odaklar |
|---|---|
| measure/calc | 0309 seti: determinizm (aynı girdi → aynı input_set_hash ve skorlar), GA sınır durumları (küçük örneklem, bayraklı oran eşiği), tier→fidelite eşlemesi, üç koşullu anlamlılık matrisi, partial kapsam eşiği kararları, bileşen hesapları ve ağırlık uygulaması (factor_snapshot okuması) |
| engines | Motor başına kayıtlı yanıt fixture'larından ayrıştırma: citations/url çıkarımı, tier_label ataması, arama-yapılmadı bayrağı, hata sınıfı eşlemesi (§6 fixture kuralları) |
| insight | NG10 filtre kapısı (aykırı taktik kalıcılaşamaz; policy_checked_at), tekilleştirme, kanıt derecesi ataması, iddia dili şablon denetimi (garanti ifadesi yok) |
| delivery | Digest gruplama ve gün sonu kapanışı, hafta anahtarı idempotensi, derin bağlantı token üretimi ve kapsam kodlaması, webhook imza üretimi |
| governance | Kota kapısı kararları (aşım/erteleme), kullanım sayaç dönemleri, denetim zinciri karma hesabı (entry_hash türetimi) |
| config / identity | Panel içerik doğrulama ve content_hash idempotent versiyonlama; erişim listesi kararları (boş liste = tüm alanlar; dolu liste kısıtı), RBAC karar birimi |

## 4. Entegrasyon ve İzolasyon Matrisi

Testcontainers ile gerçek PG/Redis üzerinde koşan zorunlu matris (0310 §5'in test karşılığı): çapraz kiracı okuma, yazma ve listeleme girişimlerinin sıfır satır davranışı; yalnız-ekleme trigger'larının UPDATE/DELETE reddi; K3 korumalarının varlığı; kuyruk yükünde kiracı uyuşmazlık reddi ve alarm olayı; outbox dağıtıcısının yaz-dağıt-tüket zinciri; koşullu durum geçişleriyle çift teslim zararsızlığı (0307 idempotens); imzalı URL üretiminin çapraz kiracı önek reddi. Hızlı alt küme tanımı (0403 O-2 kapanışı): çapraz kiracı okuma/yazma çekirdeği + yalnız-ekleme reddi + kuyruk uyuşmazlık reddi; hedef süre PR bütçesine sığar, tam matris main hattında koşar. Matris satırları değişmez kimlikleriyle etiketlenir (I1, I2, I5, I6, I9) ve yeni değişmez matris satırı olmadan kapanmış sayılmaz.

## 5. Sözleşme ve API Testleri

Üretilen tiplerle örnek tabanlı istek/yanıt doğrulaması yapılır; örnekler openapi.yaml içindeki şemalardan türetilir ve sözleşme değişince örnek seti de değişir (0403 kapı 4 ile birlikte). Zorunlu senaryolar: kiracı dışı kimlikle isteklerin ayrımsız NOT_FOUND davranışı (varlık sızdırmama; hata gövdesi denetimi dahil), paket hakkı dışı çağrının ENTITLEMENT_DENIED yolu ve yükseltme işareti, hata zarfının şekli ve correlation_id varlığı, imleç sayfalama sözleşmesi (next_cursor/has_more tutarlılığı), Idempotency-Key ile ikinci tetikte mevcut işin 200 dönüşü, 202 + Location deseni ve iş kaynağı durum alanları. Bu paket 0306'nın yaşayan doğrulamasıdır; sözleşmeden sapan uygulama burada kırılır.

## 6. Motor Bağımlılığı: Kayıt ve Oynatma

Birim ve entegrasyon katmanlarında gerçek motor çağrısı yapılmaz; engines paketleri temizlenmiş gerçek yanıt kayıtlarından oluşan fixture deposuyla beslenir (motor başına temsili senaryolar: alıntılı yanıt, alıntısız yanıt, arama-yapılmadı, hata gövdeleri). Fixture bakım kuralı: kayıtlar canary veya kontrollü kayıt oturumlarından güncellenir; 0308 engine_meta sürüm kayması sinyali fixture tazeleme işini tetikler (O-2 kadans ve sahiplik). Staging'de düşük kotalı gerçek çağrı yalnız smoke içindeki tek canary probunda kullanılır (0402 §3); bu denge maliyeti (K1) ve test determinizmini birlikte korur. Fixture'lar kişisel veri ve gerçek müşteri promptu içermez; sentetik panelden üretilir.

## 7. Smoke ve Dağıtım Doğrulaması

Staging smoke seti dar ve keskindir: sağlık uçları; giriş → ölçüm tetiği (sentetik panel, fixture modlu motor veya tek canary) → iş durumu → etiketli skor okuma zinciri; korelasyon kimliklerinin loglarda uçtan uca izlenmesi (0311 §2 sözleşmesinin doğrulaması); e-posta yakalayıcıda özetin oluşması ve derin bağlantının hedefe çözülmesi. Başarısız smoke dağıtımı otomatik geri alır (0403 §5). Production sonrası doğrulama pasiftir: sağlık uçları ve yazma içermeyen sentetik okuma probu; üretimde test verisi üretilmez.

## 8. Kapsam Hedefleri ve Kalite Göstergeleri

Kapsam yüzdesi amaç değil göstergedir: kritik paketlerde (measure/calc, governance, platform/httpmw) yüksek eşik, genel tabanda makul eşik uygulanır; sayılar [K] işaretlidir (O-1) ve kapı eşiği olarak 0403'e yazılır. Mutasyon testi V1 kapsamı dışıdır, hızlı takip değerlendirmesidir (O-4). Kalite göstergeleri: karantina sayısı ve yaşı (0403), paket süre trendleri, kaçan hata kaydı. Kaçak kuralı serttir: üretimde veya pilotta yakalanan her hata, eksik test sınıfı analiziyle kaydedilir ve o hatayı yakalayacak test yazılmadan kayıt kapanmaz; strateji, kaçaklardan öğrenerek büyür.

## 9. AVIP için Çıkarımlar

1. 0403 O-2 kapandı: izolasyon hızlı alt kümesi §4'te tanımlandı; kapı-paket eşlemesi artık iki yönlü tam.
2. 0401 DoD-1 ayrıntılandı: hesap motoru ve izolasyon değişikliklerinde zorunlu paketler §3-4 listeleriyle net.
3. 0405 bağlantısı hazır: güvenlik test sınıfları (RBAC matrisi, dosya yükleme zinciri, sızıntı denetimleri) OWASP kontrol listesiyle eşlenecek; içerikleri bu dokümanın uzantısıdır.
4. Pilot öncesi yük duman testi tanımlanacak iş olarak kaydedildi (O-3): temel eşzamanlı ölçüm senaryosu ve rapor üretimi altında sistem davranışı.
5. Fixture deposu ürün varlığıdır: bakım sahipliği ve tazeleme kadansı (O-2) pilot öncesi atanır; motor davranış değişimlerinin erken göstergesi olarak da değer taşır.

## 10. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| O-1 | Kapsam eşiklerinin sayısal değerleri [K] | Kritik ve genel taban ayrımıyla; TL. |
| O-2 | Fixture tazeleme kadansı ve sahipliği | engine_meta sinyaliyle tetikli; TL + AN. |
| O-3 | Yük duman testinin kapsamı ve pilot kapısına bağlanması | 0205 §8 ile; PO + TL. |
| O-4 | Mutasyon testinin hızlı takip değerlendirmesi | Kritik paketlerde pilot sonrası; TL. |

---

## Kaynaklar

- 0403 CI/CD Pipeline · kapı-paket eşlemesi ve süre bütçeleri (çerçeve)
- 0309 §10 · hesap motoru birim odakları (measure/calc setinin kaynağı)
- 0310 §5/§9 · izolasyon doğrulama tablosu ve güvenlik test sınıfları
- 0306 / 0307 · sözleşme senaryoları, idempotens ve outbox davranışları
- 0402 §3 · staging motor çağrı politikası (fixture/canary dengesi)

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 13.07.2026 | İlk yayın: dört katmanlı piramit ve dört ilke (test = spesifikasyon), altı birim paketi odak listesi, değişmez etiketli izolasyon matrisi + hızlı alt küme tanımı (0403 O-2 kapanışı), sözleşme senaryoları, motor fixture kayıt-oynatma yaklaşımı, smoke setleri, kapsam göstergeleri ve kaçak kuralı. |
