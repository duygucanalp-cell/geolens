# 0206 · Post-MVP Roadmap

| Alan | Değer |
|---|---|
| Doküman ID | 0206 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.2 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 13 Temmuz 2026 |
| Karşıladığı madde | 17 · Post-MVP Roadmap |
| İlişkili | 0205 §5 (tohum), 0003, 0004, 0201 §6-7, 0202; 0301-0311; D-02, D-03, R-04 |

---

## 1. Amaç ve Kapsam

Bu doküman Faz 2'yi kapatır: MVP sonrası ürün evrimini takvim tarihleriyle değil, tetikleyici tabanlı pencerelerle tanımlar. Girdisi 0205 §5 bilinçli açıklar tablosu, 0003 hedef seti ve persona/paket iskeletidir (0201 §6-7). Kapsam dışı: efor tahmini, sprint planı ve takvim (Faz 4 süreçleri); pencere içi kalemlerin teknik tasarımı (Faz 3). Pencere sıralaması sabit değildir; yeniden önceliklendirme mekanizması §9'da tanımlanır ve 0007 karar sürecine bağlanır.

## 2. Yol Haritası İlkeleri

| # | İlke | Anlamı |
|---|---|---|
| Y1 | Tarih değil tetikleyici | Pencereler koşulla açılır (kapı kriteri, karar, veri eşiği); takvim taahhüdü verilmez, dürüst iddia dili yol haritasında da geçerlidir (İ4). |
| Y2 | Öğrenme yeniden sıralar | Pilot ve M4 geri bildirimi pencere içi sırayı değiştirebilir; değişiklik gerekçeli changelog kaydıyla yapılır. |
| Y3 | Güven gevşemez | Sert kurallar (M6, M7, M12, M14), fidelite istisnasızlığı ve NG sınırları hiçbir pencerede pazarlığa açılmaz. |
| Y4 | Tek platform korunur | Her pencere kalemi paket haklarıyla açılır; kod dalı veya müşteriye özel çatal yaratılmaz (İ1). |
| Y5 | Pencere gözetimi | TR fırsat penceresi senaryoları (0105) ve motor erişim seyri (R-04) 0007 kadansında izlenir; sıralama buna duyarlıdır. |

## 3. Pencere Modeli

| Pencere | İçerik özeti | Giriş tetikleyicisi |
|---|---|---|
| HT1 · hızlı takip 1 | Masa bahisi kapanışları, daraltılmış kapsamların genişlemesi, ikinci halka motorlar | Pilot çıkış kapısı geçildi (0205 §7) |
| HT2 · hızlı takip 2 | Ticari açılış tamamlayıcıları, benchmark, yönetim görünürlüğü | Genel açılış + bekleyen politika kararları (ödeme, gizlilik yöntemi) |
| Kurumsal kapı | SSO, SOC 2, genişletilmiş tarihçe, P1 aktif satış | SOC 2 Tip 1 + kesintisiz üretim tarihçesi eşiği [K] + kurumsal pilot sinyali |
| Platform ufku | Öngörü, öğrenen önceliklendirme, yeni yüzeyler, EN açılımı | Veri hacmi ve kanıt eşikleri; ADR kararları |

## 4. Hızlı Takip 1 Penceresi

| Kalem | Değer gerekçesi | Bağımlılık |
|---|---|---|
| Okuma API'si (FR-F6) ve CSV tamamlayıcısı | Masa bahisi kapanışı; ajans BI ihtiyacı (FR-F7 MVP'ye alınmadıysa bu dalganın ilk kalemi) | 0204 O-3 sözleşme ADR'si |
| Zamanlanmış rapor dağıtımı (FR-F5) | Ajans operasyonunun tam otomasyonu; M10 kapsamı genişler | 0307 üzerinde küçük ek |
| Öneri-etki takibi (FR-E4) | Güven halkasını kapatır: öneri, etkisiyle birlikte görünür | MVP'den biriken M4 işaretleri |
| Müşteri arşivleme ve devir (FR-G3) | Ajans ölçeklenmesi; çalışma alanı hijyeni | · |
| Daraltılmış kapsam genişlemeleri (B4, D3, E1, F2, G2) | Denetim kataloğu, derin kıyas, öneri kütüphanesi, kural editörü, grafik panorama | Pilot geri bildirimiyle sıralanır (Y2) |
| İkinci halka motorlar: Claude, Grok | Kapsam genişletme; kırılım değeri (H2) | K1 maliyet payı; Grok kurumsal şartları (0102 O-2) |

## 5. Hızlı Takip 2 Penceresi

| Kalem | Değer gerekçesi | Bağımlılık |
|---|---|---|
| Benchmark bağlamı (FR-D5 + NFR-N13) | P2 çerçeveleme ihtiyacı (0202 §5); kategori farklılaştırıcısı | Gizlilik yöntemi kararı (0204 O-2) + kiracı tabanı eşiği [K] |
| Self-serve ödeme ve tam ticari açılış (FR-A6) | P4/P2 hunisinin sürtünmesiz dönüşümü | O-2 politika kararı; genel açılış |
| Denetim izi görünümü (FR-H1) | Yönetici şeffaflığı; kurumsal kapı ön hazırlığı | Kayıt (N6) zaten tam |
| Bildirim zenginleştirme (webhook çeşitlendirme, digest ayarları) | M11 iyileşmesi; P3 entegrasyon esnekliği | API sözleşmesiyle uyum |
| E-posta özet kişiselleştirmesi | M3 ve e-postadan panoya geçiş oranını büyütür | MVP M3 verisi |

## 6. Kurumsal Kapı Penceresi

Bu pencere P1 aktif satışının açılışıdır (0201 §7 ertelemesinin sonu). Kalemler: SSO/SAML (FR-A4), SOC 2 sürecinin sertifikaya bağlanması (Tip 1, ardından Tip 2; kontrol yolu 0310 ile MVP'den beri işliyor), genişletilmiş tarihçe ve dışa aktarım paketleri, kurumsal onboarding ve güvenlik inceleme paketi (0202 §7 pilot modelinin ürünleşmesi). Giriş tetikleyicisi bileşiktir: SOC 2 Tip 1 raporu alınmış, kesintisiz üretim tarihçesi eşiği [K] aşılmış (W3 borcunun kapanışı) ve kurumsal pilot kiracılarından satın alma sinyali doğrulanmış olmalıdır. Bu pencere açıldığında 0201 P1 kartı saha verisiyle güncellenir ve kurumsal fiyatlama çalışması (set dışı) tetiklenir.

## 7. Platform Ufku

| Kalem | Değer gerekçesi | Kanıt eşiği / tetikleyici |
|---|---|---|
| Tahmine dayalı görünürlük öngörüsü | Trendden öngörüye geçiş; İ4 gereği olasılık dili ve kalibrasyon raporuyla | Yeterli tarihçe hacmi [K]; öngörü isabeti metriği tanımlanmış |
| Öğrenen öneri önceliklendirme | M4 + etki verisiyle önerilerin beklenen etkiye göre sıralanması | FR-E4 verisi olgunlaştı; NG10 filtresi aynen korunur |
| Anomali kök neden yardımı | Uyarıdan açıklamaya: kaynak kırılımı korelasyonlarıyla kanıt dereceli ipuçları | M11 kalibrasyonu oturdu |
| Yerel mobil uygulama | P4/P5 bildirim yüzeyinin derinleşmesi; kategoride hâlâ ayrıştırıcı (0103 mobil boşluğu) | ADR-002 değerlendirmesi; responsive web etkileşim verisi |
| EN pazar açılımı | N15 altyapısının etkinleşmesi; TR çekirdeği kanıtlandıktan sonra | TR pencere durumu (0105) + PO kararı |
| Yeni motor yüzeyleri (asistan/ajan yüzeyleri) | Görünürlüğün yeni katmanları; kademe modeliyle etiketli | R-04 seyri; resmî erişim olgunluğu |

Ufuk sınırı: hiçbir kalem kullanıcı onayı olmadan otomatik site veya içerik değişikliği uygulamaz; öneri üretimi NG sınırları (NG8, NG10) içinde kalır. Ufuk kalemleri 0007'de Tip 2 kararla pencereye çekilebilir.

## 8. Pencere-Metrik Bağları

| Pencere | Başarı sinyali (0004) | Yeni metrik ihtiyacı |
|---|---|---|
| HT1 | M4 artışı, M10/M11 hedef sürdürme, M3 derinleşmesi | Öneri sonrası yeniden ölçüm oranı (0004 v1.1 adayı) |
| HT2 | M1 büyümesi, e-postadan panoya geçiş, paket geçişleri | Dönüşüm ve geçiş oranları (0004 v1.1 adayları) |
| Kurumsal kapı | Kurumsal M2 pilot sinyalleri; W5 kapanışı | Kurumsal değerlendirme döngü süresi |
| Platform ufku | Tarihçe hacmi ve model kalibrasyonu | Öngörü isabeti metriği (0004 v2 adayı) |

## 9. Riskler ve Yeniden Önceliklendirme

| Senaryo | Etki | Sıralama tepkisi |
|---|---|---|
| TR penceresi erken kapanır (0105 senaryoları) | Bilinirlik yarışı sertleşir | Savunulabilirlik öne çekilir: istatistik derinliği, metodoloji yayınları (G9), benchmark |
| Motor erişimi sertleşir (R-04) | Bağdaştırıcı yatırımının riski artar | İkinci halka ertelenir; K2 vekil-korelasyon pilotu (0102 O-1) öne alınır; kademe etiketi iletişimi güçlendirilir |
| Güven yarışı hızlanır (CiteLens sinyali, 0105) | GA ve açıklanabilirlik masa bahisine dönüşür | Bizde MVP'de mevcut; fark iletişimi ve GA görselleştirme derinliği HT1 içinde öne alınır |
| Ajans talebi beklenenden hızlı büyür | G kalemleri darboğaz olur | FR-G3 ve panorama genişlemesi HT1 başına çekilir (Y2) |
| Kaynak kısıtı | Pencere içi kalemler seyrelir | Tek platform ilkesi kaydırma maliyetini düşük tutar; kesinti değil erteleme uygulanır |

Mekanizma: pencere içi sıralama değişiklikleri Tip 2, pencere tanımı değişiklikleri Tip 1 karardır (0007); her ikisi changelog kaydı ister.

## 10. AVIP için Çıkarımlar

1. Faz 2 bu dokümanla tamamlandı: 0201-0206 seti Review durumundadır; Approved geçişleri tanımlı kapılara bağlıdır (0201 §9 saha doğrulaması, pilot çıkış kapısı).
2. Faz 3 açılışı için iki ön koşul işaretli: D-03 kararı (0205 O-1, bloklayıcı) ve v1.1 düzeltme turu zamanlaması (0104, 0105 çapraz referanslar; 0204 sayım ve FR-F7; 0004 metrik adayları; 0005 terim adayları).
3. Sıradaki doküman 0301 System Architecture'dır; bu yol haritasının pencere yapısı mimari esneklik gereksinimi olarak Faz 3'e taşınır (bağdaştırıcı ekleme, paket hakkı aç/kapa, yüzey ekleme maliyetleri düşük olmalı).
4. Kurumsal kapı tetikleyicisi 0310 SOC 2 yol haritasının önceliğini belirler; kontrol yolu MVP'den itibaren işletilir, sertifika süreci kapıya bağlanır.
5. 0201 §6 paket iskeleti pencere kalemlerine göre v1.1'de zenginleştirilir (frekans kademesi, HT kalemlerinin paket eşlemesi).

## 11. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| ~~O-1~~ | ~~Kurumsal kapı tarihçe eşiği (kesintisiz kaç ay) [K]~~ | ✅ **KAPANDI**: 12 ay (başlangıç hipotezi onaylandı). [K] kalibrasyon — pilot verisiyle revize edilebilir. 0007 D-69. |
| O-2 | Yerel mobil değerlendirme kriterleri | ADR-002 kapsamında; responsive etkileşim verisiyle; TL. |
| ~~O-3~~ | ~~EN açılım tetikleyicisinin tanımı~~ | ✅ **KAPANDI**: PMF sinyali bileşik — TR'de M2≥%80 + M1≥%60 + gelen talep/başvuru eşiği. PO kararıyla tetiklenir. 0007 D-70. |
| ~~O-4~~ | ~~v1.1 düzeltme turunun zamanlaması~~ | ~~Öneri: Faz 3 açılışından önce tek geçiş; PO onayı.~~ |
| **✅ O-4 (KAPANDI)** | **Bu oturum bittiğinde — Faz 4 başlamadan önce tek geçiş. Konsolide düzeltmeler (0104/0105 çapraz, 0204 sayım, FR-D4, 0004 adayları) topluca uygulanır.** | **PO kararı (21.07.2026). 0007 D-63.** |

---

## Kaynaklar

- 0205 MVP Scope §5 · bilinçli açıklar (pencere tohum listesi), pilot çıkış kapısı
- 0003 Goals & Non-Goals · hedef seti ve NG sınırları (Y3, ufuk sınırı)
- 0004 Success Metrics · pencere-metrik bağları, v1.1 metrik adayları
- 0201 §6-7 · paket iskeleti ve segment önceliği (kurumsal kapı koşulu)
- 0105 Market Opportunity · TR pencere senaryoları, CiteLens güven yarışı sinyali (§9 girdileri)

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 13.07.2026 | İlk yayın: 5 yol haritası ilkesi, 4 pencereli tetikleyici modeli, HT1/HT2 kalem tabloları, kurumsal kapı bileşik tetikleyicisi, 6 kalemlik platform ufku, pencere-metrik bağları, 5 senaryolu yeniden önceliklendirme mekanizması; Faz 2 kapanış kaydı. |
| 1.1 | 21.07.2026 | O-4 kapandı: v1.1 düzeltme turu Faz 4 öncesi tek geçiş. 0007 D-63. |
| 1.2 | 21.07.2026 | O-1 kapandı: kurumsal kapı tarihçe eşiği 12 ay [K] (hipotez onay). O-3 kapandı: EN açılım tetikleyicisi PMF sinyali bileşik. 0007 D-69, D-70. |
