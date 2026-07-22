# 0202 · User Journey

| Alan | Değer |
|---|---|
| Doküman ID | 0202 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.2 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 13 Temmuz 2026 |
| Karşıladığı madde | 13 · User Journey |
| İlişkili | 0201 (aktörler), 0203, 0204, 0205, 0307; 0004 (M1, M3, M4, M10, M11), D-02 |

---

## 1. Amaç ve Kapsam

Bu doküman, 0201'deki persona setinin ürünle yaşam döngüsünü aşama düzeyinde haritalar: keşiften kayda, ilk değerden haftalık kullanım ritmine ve genişlemeye. Çıktıları üç dokümanı besler: 0203 (Use Cases) aktör-adım envanterini, 0204 (PRD) gereksinim adaylarını, 0205 (MVP Scope) kesit kararını buradan alır. Ekran düzeyi UX tasarımı ve görsel akışlar kapsam dışıdır; burada akış mantığı, sürtünme noktaları ve ölçüm bağları tanımlanır.

## 2. Yöntem ve Yolculuk Çerçevesi

Yolculuklar altı aşamalı ortak çerçeveyle yazılır: Keşif, Kurulum, İlk Değer, Haftalık Ritim, Genişleme, Savunuculuk. Her aşamada dört soru yanıtlanır: kullanıcı ne başarmak istiyor, ürün hangi yüzeyle karşılıyor, sürtünme riski ne, hangi metrikle izlenir. İki tasarım ilkesi çerçeveye gömülüdür. Birincisi, güven anları: fidelite etiketi, güven aralığı ve kanıt derecesi kullanıcıya pazarlama sayfasında değil, ürünün içinde ilk skorla birlikte öğretilir (0102 §4 kuralının deneyime inişi). İkincisi, haftalık ritim hedefi: North Star (M1, WAT%) "raporu inceleyen ve detaya inen" kiracıyı saydığı için tüm yolculuklar kullanıcıyı haftalık karar ritmine taşıyacak biçimde kurgulanır. Bu yolculuklar 0201 §2 ile aynı statüdedir: proto-yolculuktur, 0201 §9 doğrulama planıyla saha verisine karşı test edilir.

## 3. Ortak Omurga: Kayıttan İlk Değere

Tüm personalar aynı çekirdek akıştan geçer; farklar paket hakları ve varsayılanlarla açılır (tek platform ilkesi, 0201 §3). Motor ölçümleri eşzamansız çalıştığı için ilk değer tek bir an değil, aşamalı bir seridir:

| # | Adım | Tasarım | Not |
|---|---|---|---|
| 1 | Kayıt | Düşük sürtünmeli kayıt; kiracı oluşturma; rol ataması (RBAC temeli) | Ödeme bilgisi politikası O-2 |
| 2 | Marka ve alan tanımı | İzlenecek marka adları, alan adları, rakipler; TR/EN pazar seçimi | Ölçüm bölgesi tanımı (0102 §4) |
| 3 | Prompt seti kurulumu | Sektöre göre TR-öncelikli şablon kütüphanesinden öneri; markalı ve kategori promptları ayrı etiketlenir (0101 çıkarım 4) | Boş sayfa sendromuna karşı şablon zorunlu varsayılan |
| 4 | Anında değer: site erişim denetimi | Bot izinleri, SSR ve temel erişilebilirlik kontrolü; motor API'si gerektirmez, saniyeler içinde sonuç (0101 çıkarım 6) | İlk oturumda somut kazanım; kazara GPTBot engeli yaygın hızlı düzeltme |
| 5 | İlk ölçüm çalıştırma | Çekirdek motorlarda örneklemli ilk ölçüm kuyruğa girer; ilerleme görünür | 0307 zamanlayıcı; K1 panel modeli |
| 6 | İlk skor ve güven anı | Skor, fidelite etiketi ve güven aralığıyla birlikte sunulur; kısa açıklama katmanı ("bu skor nasıl hesaplandı" bağlantısı calculation_run detayına iner) | S1-S3'ün deneyimdeki karşılığı |
| 7 | İlk öneri ve döngü başlangıcı | Kanıt dereceli ilk öneri listesi; "uyguladım/reddettim" işaretleme (M4 döngüsü) | G4; aksiyon boşluğunun kapanışı |

İlk değere ulaşma süresi (time-to-first-value) tasarım hedefi iki kademelidir: adım 4 tek oturumda saniyeler mertebesinde, adım 6 aynı gün içinde. Sayısal eşikler 0004 v1.1'e metrik adayı olarak önerilir (§10); burada taahhüt değil tasarım hedefi statüsündedir.

## 4. P3 · Ajans Yolculuğu (birincil ticari odak, 0201 §7)

| Aşama | Akış | Sürtünme riski | İzleme |
|---|---|---|---|
| Keşif | TR ekosistem içerikleri, metodoloji yayınları (G9 söylemi), meslektaş tavsiyesi; "müşterime AI görünürlük raporu satmak istiyorum" niyeti | Kategori araçlarına güvensizlik (0103 §5) | Kanal atıf verisi |
| Kurulum | Ajans çalışma alanı; ortak omurga ilk iki müşteri için tekrarlanır; müşteri başına marka/prompt seti; ekip koltukları | Müşteri başına kurulum yükü | Kurulum tamamlama oranı |
| İlk Değer | İlk müşteri raporu: white-label PDF ve paylaşılabilir özet; müşteriye sunulabilir metodoloji sayfası (fidelite dili satış aracı olur) | Rapor kişiselleştirme ihtiyacı | İlk rapor üretim süresi |
| Haftalık Ritim | Zamanlanmış müşteri raporları (M10); Slack uyarıları; pano üzerinden çok müşteri panoraması | Uyarı yorgunluğu (M11) | M1, M3, M10 |
| Genişleme | Müşteri ekleme; koltuk artışı; BI/API ile ajans iç raporlamasına bağlama | Müşteri başına maliyet endişesi; panel modeli şeffaflığıyla karşılanır (S5) | Kiracı içi büyüme |
| Savunuculuk | Vaka çalışması ve referans; ajans ağında yayılım | Rakip white-label teklifleri (0103 §3) | Referans dönüşümü |

## 5. P2 · KOBİ Yolculuğu (birincil ticari odak, 0201 §7)

| Aşama | Akış | Sürtünme riski | İzleme |
|---|---|---|---|
| Keşif | Ajans tavsiyesi (P3 kanalı çift işlevli), arama ve kategori içerikleri; "AI'da görünmüyoruz" endişesi | Kategori farkındalığı düşük (0105 §6 makası) | Kanal atıf verisi |
| Kurulum | Ortak omurga, Türkçe sihirbaz; sektör şablonundan otomatik prompt önerisi; sözlük destekli arayüz (0005 terimleri, açıklama katmanı) | Terminoloji ve dil bariyeri; zaman kısıtı | Kurulum tamamlama, adım terk oranı |
| İlk Değer | Site erişim denetimi hızlı kazanımı; ilk skor benchmark bağlamıyla sunulur ("sektöründe tipik aralık") ki düşük skor terke değil aksiyona yönlendirsin | İlk skor moral bozar; çerçeveleme kritik (§9) | Aktivasyon oranı (aday) |
| Haftalık Ritim | Ana yüzey haftalık e-posta özeti; özetten panoya "detaya in" bağlantıları (M1 tanımı gereği derinleşme e-posta içinden tetiklenir) | E-posta okunmazlığı | M1, M3; e-posta-pano geçişi (aday) |
| Genişleme | Öneri-uygula-yeniden ölç döngüsü alışkanlığı (M4); rakip ekleme; Pro'dan Business'a geçiş tetikleyicileri | Aksiyonların etkisi görünmezse döngü kopar | M4; yeniden ölçüm oranı |
| Savunuculuk | Sektör içi tavsiye; vaka verisiyle içerik katkısı | · | Tavsiye kaynağı |

## 6. P4/P5 · Self-Serve Yolculuğu

P4 (bağımsız danışman) ve P5 (içerik üreticisi) aynı self-serve kapıdan girer; ayrım Free sonrası niyette ortaya çıkar:

| Aşama | Ortak akış | P4/P5 ayrımı |
|---|---|---|
| Keşif | İçerik, topluluk, sosyal paylaşımlar; paylaşılabilir skor kartları organik keşif motoru | P4 profesyonel içerikten, P5 üretici topluluklarından gelir |
| Kurulum | Free kayıt; tek marka/isim; ortak omurganın hafif sürümü | P5 kişisel isim izler; P4 kendi markasıyla dener |
| İlk Değer | Basit skor + fidelite etiketi; paylaşılabilir sonuç | P4 metodolojiyi inceler (müşteriye anlatacak); P5 sonucu paylaşır |
| Ritim | E-posta ve mobil bildirim; hafif pano oturumları (D-02 responsive) | P5 için bildirim ana yüzeydir |
| Genişleme | Pro dönüşüm tetikleyicileri: ikinci marka ihtiyacı, PDF dışa aktarım, zamanlanmış izleme | P4 müşteri kazanınca Pro'ya geçer; P5 Free'de kalabilir, dönüşüm baskısı uygulanmaz (büyüme ve topluluk motoru, 0201 §6) |
| Savunuculuk | Paylaşım ve topluluk katkısı; W2 bilinirlik borcunu kapatan organik kanal | P4 vaka anlatır; P5 skor kartı paylaşır |

## 7. P1 · Kurumsal Yolculuk (tasarım hedefi; aktif satış odağı ertelenmiş, 0201 §7)

P1 yolculuğu V1'de pazarlama hunisiyle değil, seçili pilot davetiyle işler: güvenlik inceleme paketi (0310 çıktıları) ile ön yeterlilik, SSO ile kurulum, denetim izi ve tarihçe dışa aktarımıyla değerlendirme. Mimari kurumsal-hazır kurulduğu için (G7) bu yolculuk kod değişikliği değil satış olgunluğu bekler; SOC 2 yolu ve 12+ ay tarihçe biriktiğinde (W3, W5) huni açılır. Pilot P1 kiracıları, kurumsal gereksinim doğrulaması için erken sinyal kaynağıdır (0004 O-1 ile birlikte).

## 8. Kanal ve Ritim Mimarisi (D-02 önerisiyle uyumlu; nihai karar ADR-002)

| Yüzey | Rol | Birincil personalar |
|---|---|---|
| Web pano (responsive) | Derin analiz, kaynak detayı, yapılandırma; M1'in "detaya inme" koşulunun gerçekleştiği yer | P3 yoğun; P1, P2 haftalık; P4/P5 hafif |
| E-posta özetleri | Haftalık ritmin taşıyıcısı; panoya derin bağlantılar; yönetici özeti biçimi | P2 ana yüzey; P1 yönetici özeti; P4/P5 |
| Slack / webhook uyarıları | Anlamlı değişim bildirimi; eşikler 0309 oynaklık modeline bağlı | P3; P1 ekipleri |
| Zamanlanmış PDF / BI | Müşteriye giden white-label rapor; BI beslemesi | P3; P1 (BI) |
| Mobil bildirim | Hafif sinyal katmanı; responsive web'e köprü (yerel uygulama 0206 adayı) | P4/P5 |

Uyarı tasarım ilkeleri: uyarı ancak istatistiksel olarak anlamlı değişimde tetiklenir (güven aralığı dışına çıkış; 0309), kullanıcı eşik ve kanal ayarı yapabilir, her uyarı "yanlış alarm" geri bildirimi taşır (M11 beslemesi). Uyarı yorgunluğu, haftalık ritmi öldüren birincil düşman olarak ele alınır.

## 9. Sürtünme ve Terk Riskleri

| Aşama | Risk | Belirti | Karşı tasarım |
|---|---|---|---|
| Kurulum | Prompt seti boş kalır, kullanıcı ne soracağını bilemez | Adım 3 terk oranı | Sektör şablonları varsayılan; boş başlangıç yok |
| İlk Değer | İlk skor düşük gelir, kullanıcı ürünü bırakır | İlk oturum sonrası dönüşsüzlük | Benchmark bağlamı + ilk öneriyle birlikte sunum; site denetimi hızlı kazanımı önce |
| İlk Değer | Oynaklık kafa karıştırır ("dün 62, bugün 55") | Destek soruları; güven kaybı | Güven aralığı görselleştirmesi; fidelite ve örnekleme eğitim katmanı (güven anı) |
| Haftalık Ritim | E-posta özetleri okunmadan silinir | M3 düşüşü; e-posta-pano geçişi sıfırlanır | Özet tek içgörü + tek aksiyon formatı; kişiselleştirilmiş konu satırı |
| Haftalık Ritim | Uyarı yorgunluğu | M11 kötüleşir; kanal kapatma | Anlamlılık eşiği; günlük birleştirme (digest); kanal ayarları |
| Genişleme | Öneriler uygulanır ama etki görünmez; döngü kopar | M4 işaretleme sonrası yeniden ölçüm yok | Öneri-etki takibi: uygulanan önerinin sonraki ölçümde işaretli karşılaştırması |
| Genişleme (P3) | Ajansın müşterisi ayrılır; çalışma alanı kirlenir | Pasif müşteri alanları | Arşivleme ve devir akışı; kota iadesi kuralı 0205'te |

## 10. Yolculuk-Metrik Eşlemesi

| Aşama | Mevcut metrik (0004) | Yeni metrik adayı (0004 v1.1 önerisi) |
|---|---|---|
| Kurulum | · | Kurulum tamamlama oranı; adım bazlı terk |
| İlk Değer | · | İlk değere ulaşma süresi (iki kademeli); aktivasyon oranı |
| Haftalık Ritim | M1 (WAT%), M3 (içgörü tüketimi), M10 (zamanındalık), M11 (uyarı isabeti) | E-postadan panoya geçiş oranı |
| Genişleme | M4 (öneri etkileşimi) | Öneri sonrası yeniden ölçüm oranı; paket geçiş oranı |
| Savunuculuk | · | Tavsiye kaynaklı kayıt payı |

Adaylar formülsüz listelenmiştir; formül, eşik ve sahip ataması 0004 v1.1 revizyonunda 0007 süreciyle yapılır. Pilot tanımı (M2) 8 haftalık planını bu yolculuğun İlk Değer ve Haftalık Ritim aşamaları üzerinden kurgular.

## 11. AVIP için Çıkarımlar

1. 0203 (Use Cases) envanteri bu dokümanın adımlarından türetilir; her tablo satırı bir kullanım senaryosu adayıdır (aktör + hedef + akış).
2. 0204 (PRD) gereksinim adayları: kurulum sihirbazı ve şablon kütüphanesi, site erişim denetimi modülü, aşamalı ölçüm ilerleme görünümü, açıklama katmanı (calculation_run detayı), e-posta özet motoru, uyarı eşik/kanal ayarları, white-label rapor şablonu, ajans çalışma alanı ve arşivleme.
3. 0205 (MVP Scope) kesiti ortak omurgayı çekirdek alır; site denetimi düşük maliyet/yüksek değer MVP adayıdır (0101 çıkarım 6 ile tutarlı).
4. Zamanlanmış rapor ve uyarı akışları 0307 (zamanlayıcı/kuyruk) tasarımının işlevsel gereksinimleridir; M10 buradan ölçülür.
5. Benchmark bağlamı ("sektöründe tipik aralık") yeni bir veri ihtiyacı doğurur: anonim toplulaştırılmış kıyas. Gizlilik sınırları ve yöntem 0309 ile 0310'da tanımlanmalıdır (O-3).
6. "İlk değer" ve "aktivasyon" terimleri 0005 sözlüğüne v1.1 adayıdır (sözlük disiplini).

## 12. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| ~~O-1~~ | ~~İlk değere ulaşma süresi ve aktivasyon için sayısal eşikler~~ | ~~0004 v1.1; TL+PO; pilot verisiyle kalibre edilir.~~ |
| ✅ O-1 | İlk değere ulaşma süresi ve aktivasyon için sayısal eşikler | **KAPANDI** (21.07.2026): adım 4 (site denetimi) < 30 saniye, adım 6 (ilk skor) < 24 saat. Tasarım hedefi; pilot verisiyle kalibre edilir. |
| ~~O-2~~ | ~~Free kayıtta ödeme bilgisi politikası~~ | ~~PO; sürtünme-dönüşüm dengesi; P4/P5 hunisini doğrudan etkiler (0201 O-2 ile birlikte).~~ |
| ✅ O-2 | Free kayıtta ödeme bilgisi politikası | **KAPANDI** (21.07.2026): sürtünmesiz kayıt — e-posta + şifre yeterli, ödeme bilgisi istenmez. 0201 O-2 ile birlikte karara bağlandı. 0007 D-07. |
| O-3 | Benchmark kıyas verisinin gizlilik ve yöntem sınırları | Anonim toplulaştırma kuralları; 0309 + 0310; kiracı verisi asla çapraz sızmaz (M12 ilkesi). |
| ~~O-4~~ | ~~Uyarı eşik varsayılanları~~ | ~~0309 oynaklık pilotu sonrası; M11 hedefiyle kalibre.~~ |
| ✅ O-4 | Uyarı eşik varsayılanları | **KAPANDI** (21.07.2026): pilot verisiyle kalibre edilecek. MVP'de manuel eşik ayarı yeterli (FR-F2 daraltılmış). 0007 D-12. |

---

## Kaynaklar

- 0201 User Personas · aktör seti, kanal hipotezleri, paket iskeleti, segment önceliği önerisi
- 0004 Success Metrics · M1 North Star tanımı, M3/M4/M10/M11; pilot çerçevesi
- 0102 AI Search Landscape §4 · fidelite kuralının deneyime inişi
- 0101 GEO Landscape · site erişim denetimi fırsatı (çıkarım 6), markalı/kategori prompt ayrımı
- 0103 Competitor Analysis · ajans zayıflığı, white-label modelleri; D-02 önerisi (§6)

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 13.07.2026 | İlk yayın: altı aşamalı çerçeve, yedi adımlı ortak omurga ve aşamalı ilk değer tasarımı, dört persona yolculuğu, beş yüzeyli kanal mimarisi, yedi satırlık sürtünme haritası, metrik eşlemesi ve beş yeni metrik adayı. |
| 1.1 | 21.07.2026 | O-2 kapandı: sürtünmesiz free kayıt (ödeme bilgisi istenmez). 0201 O-2 ve 0007 D-07 ile birlikte. |
| 1.2 | 21.07.2026 | O-1 kapandı: ilk değer eşikleri (adım 4 < 30sn, adım 6 < 24sa). O-4 kapandı: uyarı eşikleri pilotta kalibre edilecek. 0007 D-11, D-12. |
