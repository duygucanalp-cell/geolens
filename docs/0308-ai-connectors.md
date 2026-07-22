# 0308 · AI Connectors

| Alan | Değer |
|---|---|
| Doküman ID | 0308 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.5 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 18 Temmuz 2026 |
| Karşıladığı madde | 24 · AI Connectors (model soyutlama, maliyet/kota yönetimi) |
| İlişkili | 0102, 0205, 0307 (girdi); 0309, 0310, 0311, 0402 (çıktı); D-03 (karar), R-04, K1, K2 |

---

## 1. Amaç ve Kapsam

Bu doküman motor soyutlama katmanını sabitler: bağdaştırıcı arayüz sözleşmesi, üç çekirdek motorun tasarımı, kayıt defteri modeli, hız/maliyet politikaları ve erişim sertleşmesine karşı dayanıklılık. Motor-dışı site denetim bileşeni de bu katmanın parçasıdır (§8). Kapsam dışı: örnekleme planı ve skor hesabı (0309), kuyruk ve deneme mekaniği (0307; yalnız hata sınıfı eşlemesi burada), sağlayıcı hesap açılışı ve faturalama operasyonu (0402).

## 2. D-03 Kararı ve Motor Kapsamı

> **D-03 karara bağlandı (PO onayı, bu doküman turu):** Google yüzeyi MVP'de (b) seçeneğiyle temsil edilir: Gemini resmî API'si üzerinden, official_proxy kademe etiketiyle ve kullanıcıya açık vekil beyanıyla. (c) seçeneği (lisanslı üçüncü taraf Google verisi) hukuki incelemede kalır; sonuç olumluysa ayrı bağdaştırıcı olarak eklenir ve bu bir kayıt defteri değişikliğidir, mimari değişiklik değildir (0301 §9). Karar 0007 defterine Tip 1 olarak işlenir.

| Motor | Kaynak | tier_label | Not |
|---|---|---|---|
| ChatGPT | OpenAI Responses API + web araması | official_proxy | Tüketici yüzeyinin resmî vekili; alıntı meta zorunlu (FR-D2) |
| Gemini | Gemini API + Google Search grounding | official_proxy | D-03 (b); Google yüzey temsili bu bağdaştırıcıdadır |
| Perplexity | Sonar API | direct | Ürünün kendisi API ile aynı motor; en yüksek fidelite |
| Claude, Grok | İkinci halka (HT1) | · | Yer tutucu; Grok kurumsal şartları O-1'de izlenir |

## 3. Bağdaştırıcı Sözleşmesi

Arayüz measure bağlamında tanımlıdır, uygulamalar internal/engines altındadır (0305 D4). Bağdaştırıcı durumsuz saf çağrı katmanıdır; örnekleme, tekrar ve skor kararları taşımaz.

| Sözleşme öğesi | İçerik |
|---|---|
| Capabilities() | Desteklenen pazar/dil seti, alıntı desteği, önerilen eşzamanlılık tavanı, maliyet sınıfı, kademe etiketi. Kayıt defteri ve K1 hesabı bu bildirimi okur. |
| Execute(ctx, ProbeRequest) | Girdi: prompt, pazar, zaman aşımı bütçesi, örnekleme parametreleri (0309'dan geçirilir). Tek istek, tek yanıt; iç kısa deneme sınıfı hariç durum yok. |
| ProbeResult | Ham içerik (S3'e yazılacak gövde), citations[] (url zorunlu; başlık, konum), tier_label, engine_meta (model/sürüm, motorun bildirdiği kadarıyla), usage sinyali (istek/token maliyet göstergesi), arama-yapılmadı bayrağı. |
| Hata sınıfları | transient (yeniden denenebilir), permanent (denenmez), upstream_quota (motor kotası; erteleme sınıfı), policy (içerik/şart engeli). 0307 §6 deneme kararı bu sınıfa göre verilir; M8 sayaçları sınıf etiketlidir. |
| Değişmezler | tier_label boş dönemez (I3 zinciri); citations öğelerinde url boş olamaz; ham içerik döndürülmeden başarı raporlanamaz (I5 arşiv bütünlüğü). |

## 4. Çekirdek Bağdaştırıcılar

| Motor | Alıntı çıkarımı | Pazar/dil davranışı | Bilinen kısıtlar ve eşleme |
|---|---|---|---|
| ChatGPT | Yanıt açıklamalarındaki url_citation kayıtları; konum bilgisiyle | TR promptları TR yerelleştirme bağlamıyla; pazar parametresi istek düzeyinde | Arama aracı her yanıtta tetiklenmeyebilir: arama-yapılmadı bayrağı işaretlenir, 0309 örnekleme kuralı değerlendirir; upstream 429 → upstream_quota |
| Gemini | groundingMetadata kaynak listesi; yönlendirme URI'leri saklanır, nihai alan adı çözümü en-iyi-çaba (O-2) | Arama tabanı Google; TR sonuç yerelliği grounding üzerinden | Grounding kapsamı yanıt bazında değişken; kaynak listesi boşsa arama-yapılmadı bayrağı kullanılır |
| Perplexity | Yanıtın kaynak listesi doğrudan; sıra bilgisi korunur | TR sorgularında yerel kaynak ağırlığı doğal | Model ailesi seçimi yapılandırmada sabitlenir; sürüm değişimi engine_meta ile izlenir |

Ortak kural: bağdaştırıcı, motorun döndürmediği bilgiyi tahmin etmez; eksik meta eksik olarak işaretlenir ve 0309 buna göre davranır. Fidelite dürüstlüğü bağdaştırıcıda başlar.

## 5. Kayıt Defteri ve Yapılandırma

Bağdaştırıcılar derleme zamanında kayıt defterine eklenir; çalışma zamanında hangi motorun hangi kiracıya açık olduğu iki kesişimle belirlenir: platform yapılandırması (motor küresel açık/kapalı, D-03 gibi kararların uygulanma yeri) ve kiracı paket hakkı (FR-B5; entitlement anahtarı). Motor ekleme süreci dört adımdır: bağdaştırıcı uygulaması, kayıt girişi, entitlement anahtarı tanımı, K1 maliyet profili girişi; süreç Tip 2 karardır ve changelog ister. İkinci halka yer tutucuları (claude, grok) kayıtta pasif durur; aktifleştirme HT1 penceresi ve O-1 şart takibine bağlıdır.

## 6. Hız, Maliyet ve Eşzamanlılık

| Politika | Kural |
|---|---|
| Eşzamanlılık sınırı | Motor bazlı küresel tavan Redis sayaçla (0307 §8); Capabilities önerisi başlangıç değeridir, yapılandırma ezebilir [K] |
| İstek hızı ve zaman aşımı | Motor bazlı hız tavanı ve çağrı bütçesi; bütçe aşımı transient sınıfına düşer |
| Maliyet sinyali | Her ProbeResult usage sinyali üretir; governance usage_records'a yazar (K1 gerçek kaynağı); panel maliyet raporu buradan beslenir |
| Upstream kota | upstream_quota hatası iş ertelemesine çevrilir (0307 §6); bizim kota kapımızdan ayrı sayaçla izlenir |
| Bütçe tavanı | Platform günlük harcama tavanına yaklaşımda üretim kısılır ve alarm üretilir (K1 sert koruması; eşik [K], 0311) |

## 7. Dayanıklılık ve R-04 Tepkisi

Dört mekanizma birlikte çalışır. Canary probe: motor başına günlük düşük hacimli sağlık ölçümü; M8 erken uyarısı ve sözleşme değişikliği tespiti. Devre kesici: ardışık hata eşiğinde [K] motor geçici devre dışı kalır, işler kısmilik etiketiyle ilerler veya bekletilir (0309 kuralı), toparlanma yarı-açık denemeyle. Kademe düşürme: resmî vekil erişimi daralırsa (R-04) bağdaştırıcı etiketi directional'a düşürülür, kullanıcı iletişimi fidelite dili üzerinden yapılır ve K2 vekil-korelasyon pilotu (0102 O-1) devreye alınır; skor yayın kuralları (İ2) değişmez. Şart izleme: sağlayıcı API şart ve sürüm değişiklikleri 0007 kadansında gözden geçirilir; engine_meta sürüm kayması telemetride görünür. Bu mekanizmaların tamamı bağdaştırıcı seviyesindedir; hat (0307) ve hesap (0309) etkilenmeden çalışır.

## 8. Site Denetim Bileşeni (motor-dışı bağdaştırıcı ailesi; UC-04, FR-B4)

Denetim bileşeni motor API'si kullanmaz; hedef siteye karşı çalışır. MVP kataloğu (0205 daraltması): bot izinleri denetimi (robots.txt ve yapılandırılabilir bot listesi: GPTBot, OAI-SearchBot, ClaudeBot, PerplexityBot, Google-Extended ve eklenebilir girişler), SSR ve temel erişilebilirlik sinyalleri (getirilen sayfada işaret kontrolü). Bulgular önem dereceli üretilir ve düzeltme önerisi bağı taşır. Ölçüm nezaketi kuralları: kısa zaman aşımı, düşük istek hacmi, robots kurallarına saygı, tanımlayıcı user-agent. Güvenlik (SSRF) korumaları: yalnız http/https şemaları ve genel internet adresleri getirilir; özel ve iç IP aralıkları ile bağlantı-yerel adresler reddedilir; yönlendirmeler sınırlı sayıda izlenir ve her adımda aynı kurallar uygulanır; getirme trafiği kontrollü çıkış noktasından geçer (0402 §6; 0405 A10). Katalog genişlemesi HT1'dedir; bot listesi bakımı 0007 kadansına bağlanır (O-3).

## 9. Güvenlik ve Anahtar Yönetimi

Sağlayıcı API anahtarları platform sırrıdır: ortam/kasa üzerinden yüklenir (N4), koda ve loglara girmez; rotasyon prosedürü 0310'dadır. MVP'de kiracıya ait sağlayıcı anahtarı yoktur; panel modeli platform anahtarlarıyla çalışır (K1 disiplini bunu gerektirir). Giden isteklerde prompt içeriği telemetriye yazılmaz; loglar yalnız meta taşır (motor, süre, hata sınıfı, korelasyon kimlikleri). Kiracı promptları müşteri verisidir: sağlayıcıların veri işleme şartları 0102 kayıtlarıyla izlenir ve KVKK değerlendirmesi 0310 kapsamındadır. Ham yanıtlar S3 arşivinde şifreli saklanır (N5) ve erişim imzalı yollarla sınırlıdır.

## 10. AVIP için Çıkarımlar

1. FR-D2 zinciri uçtan uca kapandı: citations.url bağdaştırıcı değişmezi → 0303 şeması → 0306 kaynak uçları → arayüz tıklanabilir alıntısı.
2. 0309 girdileri hazır: ProbeResult alanları, arama-yapılmadı bayrağı ve tier→fidelite eşlemesi örnekleme ve skor kurallarının ham maddesidir.
3. 0307 bağları sabitlendi: hata sınıfı → deneme kararı; upstream_quota → erteleme; eşzamanlılık sayaçları ortak.
4. 0311'e devirler: canary probe planı, devre kesici eşikleri [K], bütçe tavanı alarmı, engine_meta sürüm kayması izlemesi.
5. 0402'ye devirler: sağlayıcı hesap ve anahtar provizyonu, ortam bazlı anahtar ayrımı.
6. D-03 kapanışıyla Faz 3 kritik yolundaki son karar bloğu kalktı; bekleyen tek program açığı v1.1 düzeltme turu zamanlaması (0206 O-4).

## 11. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| O-1 | Grok kurumsal API şartlarının ikinci halka önkoşulu olarak takibi | 0102 O-2 devamı; HT1 aktivasyon koşulu; TL + PY. |
| ~~O-2~~ | ~~Gemini yönlendirme URI'lerinin alan adı çözüm derinliği~~ | ~~Kaynak analizi (M9) doğruluğu; uygulamada kalibre; TL.~~ |
| **✅ O-2 (KAPANDI)** | **Tam çözüm: tüm yönlendirme zinciri son hedef alan adına kadar takip edilir. En doğru kaynak analizi (M9).** | **TL kararı (21.07.2026). 0007 D-39.** |
| ~~O-3~~ | ~~Bot listesi ve denetim kataloğu bakım süreci~~ | ~~0007 kadansına madde; ekosistem değişimine duyarlı; AN + TL.~~ |
| **✅ O-3 (KAPANDI)** | **0007 haftalık senkron gündemine eklendi. AN her hafta bot listesi taraması yapar, TL onaylar. Değişiklik varsa 0308 changelog'una işlenir.** | **AN+TL kararı (21.07.2026). 0007 D-51.** |
| O-4 | Lisanslı Google verisi (c) hukuki inceleme sonucu | Olumluysa yeni bağdaştırıcı kararı (Tip 2). PY incelemesi MVP ile paralel başladı (21.07.2026); **sonuç bekleniyor — açık soru olarak takipte**. |

---

## Kaynaklar

- 0102 AI Search Landscape · kademe modeli, motor erişim kayıtları, K2 korelasyon pilotu, R-04
- 0205 MVP Scope §3 · motor kapsamı ve D-03 çerçevesi (bu dokümanda karara bağlandı)
- 0307 Background Jobs · hata sınıfı-deneme eşlemesi, eşzamanlılık sınırı, kota kapısı
- 0303 Database Design · raw_responses/citations sözleşmesi, usage_records (K1 kaynağı)
- 0301 System Architecture §9 · bağdaştırıcı soyutlaması ve değişime dayanıklılık ilkesi

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 13.07.2026 | İlk yayın: D-03 kararının kaydı (b + c incelemesi), beş öğeli bağdaştırıcı sözleşmesi (sınıflı hatalar ve değişmezler), üç çekirdek bağdaştırıcı kartı, kayıt defteri ve dört adımlı motor ekleme süreci, hız/maliyet/eşzamanlılık politikaları, dört mekanizmalı dayanıklılık (R-04 tepkisi), site denetim bileşeni ve anahtar yönetimi. |
| 1.1 | 18.07.2026 | Site denetim bileşenine SSRF koruma maddeleri eklendi (0405 A10 denetim yakalaması; 0405 O-4 kapanışı). |
| 1.2 | 21.07.2026 | D-03 kararı §2'de kayıtlıydı, O-4 PY inceleme durumu güncellendi (paralel başladı). 0205 O-1 kapanışı ile Faz 3 kritik yolundaki son blokaj kalktı. |
| 1.3 | 21.07.2026 | O-2 kapandı: Gemini URI tam çözüm (son hedefe kadar). 0007 D-39. |
| 1.4 | 21.07.2026 | O-3 kapandı: bot listesi bakımı 0007 haftalık senkron gündeminde. 0007 D-51. |
| 1.5 | 21.07.2026 | O-4 durum güncellemesi: PY incelemesi devam ediyor. O-1 (Grok) PY takibinde, HT1 öncesi değerlendirilecek. |
