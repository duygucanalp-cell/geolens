# 0204 · PRD: Requirements

| Alan | Değer |
|---|---|
| Doküman ID | 0204 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.1 |
| Durum | Review (Approved geçişi 0201 §9 doğrulama kapısına bağlı) |
| Sahip | U2 AI Studio · Product |
| Tarih | 18 Temmuz 2026 |
| Karşıladığı madde | 12 + 15 · İşlevsel ve işlevsel olmayan gereksinimler |
| İlişkili | 0201, 0202, 0203 (girdi); 0205, 0301-0311, 0404, 0310 (çıktı); D-02, D-03 |

---

## 1. Amaç ve Kapsam

Bu doküman, V1 ürününün ne yapması gerektiğini doğrulanabilir gereksinim cümleleriyle sabitler. Girdileri 0201 (aktörler ve ihtiyaç matrisi), 0202 (yolculuklar) ve 0203 (senaryo envanteri); çıktıları 0205 (MVP kesiti), Faz 3 mimari dokümanları ve 0404 (test stratejisi) besler. Kapsam dışı: görsel arayüz tasarımı, fiyatlandırma sayıları (nitel paket adları kullanılır), teknoloji ve çerçeve seçimi (ADR süreci, 0304) ve motor bazlı uygulama detayları (0308). Gereksinimler yüzeyden bağımsız yazılmıştır; kanal kararı D-02/ADR-002 kapsamındadır.

## 2. Gereksinim Formatı ve Yönetimi

İşlevsel gereksinimler FR-, işlevsel olmayanlar NFR- önekiyle numaralanır ve alan gruplarına ayrılır. Her gereksinim tek cümledir, "-melidir" kipiyle doğrulanabilir biçimde yazılır ve 0203 senaryo bağını taşır; çift yönlü izlenebilirlik §7'de gösterilir. Öncelik etiketleri 0203'ten devralınır: Çekirdek (MVP adayı) ve Genişletilmiş (hızlı takip); nihai kesit 0205'te verilir. Değişiklikler 0007 süreciyle yönetilir: gereksinim ekleme, değiştirme ve MVP kesiti değişiklikleri changelog'a gerekçeli işlenir. Bu doküman, 0201 §9 kapısı gereği P2 ve P3 persona kartları saha görüşmeleriyle doğrulanana kadar Review durumunda kalır.

## 3. Ürün İlkeleri (tüm gereksinimlere üstten uygulanan kısıtlar)

| # | İlke | Anlamı |
|---|---|---|
| İ1 | Tek platform, paket hakları | Segment farkları yapılandırmayla açılır; kod dalı, ayrı ürün veya tek müşteriye özel tasarım yoktur (0201 §3). |
| İ2 | Fidelite istisnasızlığı | Skor gösteren her yüzey fidelite etiketi taşır; etiketsiz skor hiçbir pakette, hiçbir kanalda yayınlanmaz (0102 §4). |
| İ3 | Açıklanabilirlik | Her skor calculation_run kaydına iner: girdiler, faktör anlık görüntüsü, şablon versiyonu; yeniden hesap aynı sonucu verir (M6-M7). |
| İ4 | Dürüst iddia dili | Ürün metinleri sıralama garantisi ima etmez; olasılıksal ölçüm dili kullanılır (NG8). |
| İ5 | TR-öncelik | Arayüz, rapor ve şablon kütüphanesi Türkçe-öncelikli tasarlanır; ikinci dil altyapısı hazırdır (D-01). |

## 4. İşlevsel Gereksinimler

### A · Kimlik, kiracı ve erişim

| ID | Gereksinim | Bağ | Öncelik |
|---|---|---|---|
| FR-A1 | E-posta doğrulamalı self-serve kayıt ve kiracı provizyonu sağlanmalıdır. | UC-01 | Çekirdek |
| FR-A2 | Kiracı yöneticisi üye davet edebilmeli ve rol atayabilmelidir (en az yönetici/üye). | UC-25 | Çekirdek |
| FR-A3 | Güvenli oturum yönetimi ve parola sıfırlama akışı sunulmalıdır. | UC-01 | Çekirdek |
| FR-A4 | SSO/SAML ile oturum açma desteklenmelidir. | UC-28 | Genişletilmiş |
| FR-A5 | Paket hakları sunucu tarafında uygulanmalı; hak kapsamı dışındaki işlev çağrıları reddedilmelidir. | İ1 | Çekirdek |
| FR-A6 | Self-serve paket yükseltme akışı sunulmalıdır. | UC-26 | Genişletilmiş |

### B · Yapılandırma ve kurulum

| ID | Gereksinim | Bağ | Öncelik |
|---|---|---|---|
| FR-B1 | Marka, rakip ve pazar (TR/EN) tanımı yapılabilmelidir. | UC-02 | Çekirdek |
| FR-B2 | TR-öncelikli sektör şablon kütüphanesinden prompt seti kurulabilmeli; promptlar markalı/kategori olarak etiketlenmelidir. | UC-03 | Çekirdek |
| FR-B3 | Kurulum sihirbazı adım ilerlemesini göstermeli; yarım kalan kurulum kaldığı yerden sürdürülebilmelidir. | 0202 §3 | Çekirdek |
| FR-B4 | Site erişim denetimi; bot izinleri, SSR ve temel erişilebilirlik bulgularını önem sırası ve düzeltme önerisiyle raporlamalıdır. | UC-04 | Çekirdek |
| FR-B5 | Motor kapsamı paket haklarına göre seçilebilmeli; seçilen kapsam skor etiketine yansımalıdır. | UC-02 | Çekirdek |

### C · Ölçüm ve skor

| ID | Gereksinim | Bağ | Öncelik |
|---|---|---|---|
| FR-C1 | Kullanıcı manuel ölçüm tetikleyebilmelidir. | UC-05 | Çekirdek |
| FR-C2 | Zamanlanmış ölçümler kiracı izleme planına göre otomatik yürütülmelidir. | UC-06 | Çekirdek |
| FR-C3 | Ölçümler örneklemeli çok tekrarla koşulmalı; ham motor yanıtları arşivlenmelidir. | UC-06 | Çekirdek |
| FR-C4 | Skorlar deterministik hesap katmanında üretilmeli; calculation_run_id, girdi ve faktör anlık görüntüsü ile şablon versiyonu saklanmalıdır. | UC-07 | Çekirdek |
| FR-C5 | Her skor nesnesi fidelite etiketi taşımalıdır; etiketsiz skor hiçbir yüzeyde yayınlanmamalıdır. | UC-07, İ2 | Çekirdek |
| FR-C6 | Her skor güven aralığıyla birlikte hesaplanmalı ve gösterilmelidir. | UC-07 | Çekirdek |
| FR-C7 | Skorlar tazelik damgası taşımalı; bayatlık eşiği aşıldığında kullanıcı uyarılmalıdır. | UC-07, K3 | Çekirdek |

### D · Analiz

| ID | Gereksinim | Bağ | Öncelik |
|---|---|---|---|
| FR-D1 | Skorlar motor bazında kırılımla görüntülenebilmelidir. | UC-08 | Çekirdek |
| FR-D2 | Alıntı ve kaynak analizi sunulmalı; alıntılar kaynak sayfaya tıklanabilir bağlantı içermelidir. | UC-09 | Çekirdek |
| FR-D3 | Tanımlı rakiplerle kıyas görünümü sunulmalıdır. | UC-10 | Çekirdek |
| FR-D4 | Skor ve görünürlük zaman serisi görüntülenebilmelidir; panel versiyon sınırları seride görünür işaretle gösterilir (0302 §5). | UC-11 | Çekirdek |
| FR-D5 | Anonim toplulaştırılmış benchmark bağlamı sunulmalıdır. | UC-12 | Genişletilmiş |

### E · Öneri motoru

| ID | Gereksinim | Bağ | Öncelik |
|---|---|---|---|
| FR-E1 | Kanıt derecesi etiketli öneriler üretilmelidir (deneysel / korelasyonel / denenebilir). | UC-13 | Çekirdek |
| FR-E2 | Motor politikalarına aykırı taktikler öneri üretim hattında filtrelenmeli; kullanıcıya hiç gösterilmemelidir. | UC-13, NG10 | Çekirdek |
| FR-E3 | Öneriler uygulandı/reddedildi olarak işaretlenebilmeli; işaretler telemetriye yazılmalıdır. | UC-14, M4 | Çekirdek |
| FR-E4 | Uygulanan önerinin sonraki ölçümlerdeki etkisi işaretli karşılaştırmayla izlenebilmelidir. | UC-15 | Genişletilmiş |

### F · Bildirim ve raporlama

| ID | Gereksinim | Bağ | Öncelik |
|---|---|---|---|
| FR-F1 | İstatistiksel anlamlılık kuralından geçen değişimlerde uyarı üretilmeli; aynı gün tetikleri birleştirilmeli; yanlış alarm geri bildirimi alınmalıdır. | UC-16, M11 | Çekirdek |
| FR-F2 | Uyarı eşikleri ve kanalları kullanıcı tarafından yapılandırılabilmelidir. | UC-17 | Çekirdek |
| FR-F3 | Haftalık e-posta özeti üretilip dağıtılmalı; özet panoya derin bağlantılar içermelidir. | UC-18, M1 | Çekirdek |
| FR-F4 | White-label PDF raporlar sunucu tarafında eşzamansız üretilmelidir; istemci tarafında PDF üretimi yapılmamalıdır. | UC-19 | Çekirdek |
| FR-F5 | Raporlar zamanlanmış olarak üretilip dağıtılabilmelidir. | UC-20 | Genişletilmiş |
| FR-F6 | Okuma amaçlı API erişimi sunulmalıdır. | UC-21 | Genişletilmiş |
| FR-F7 | Skor ve alıntı verileri temel CSV biçiminde dışa aktarılabilmelidir. | UC-21 | Çekirdek |

### G · Ajans operasyonları

| ID | Gereksinim | Bağ | Öncelik |
|---|---|---|---|
| FR-G1 | Ajans kiracısı müşteri başına ayrık çalışma alanı açabilmelidir. | UC-22 | Çekirdek |
| FR-G2 | Çok müşteri panoraması tek görünümde sunulmalıdır. | UC-23 | Çekirdek |
| FR-G3 | Müşteri çalışma alanı arşivlenebilmeli ve devredilebilmelidir. | UC-24 | Genişletilmiş |

### H · Yönetim görünürlüğü

| ID | Gereksinim | Bağ | Öncelik |
|---|---|---|---|
| FR-H1 | Denetim izi kiracı yöneticisine görüntülenebilir ve dışa aktarılabilir olmalıdır. | UC-27 | Genişletilmiş |
| FR-H2 | Kiracı kullanım ve kota durumu kullanıcıya görünür olmalıdır. | K1 | Çekirdek |

## 5. İşlevsel Olmayan Gereksinimler

### Güvenlik ve izolasyon

| ID | Gereksinim | Bağ | Öncelik |
|---|---|---|---|
| NFR-N1 | Tüm veri erişimi kiracı bağlamında yürütülmeli; kiracılar arası erişim mimari olarak engellenmeli ve otomatik negatif testlerle sürekli doğrulanmalıdır. | M12 | Çekirdek |
| NFR-N2 | Her uç nokta rol tabanlı yetkilendirme denetiminden geçmelidir. | UC-25 | Çekirdek |
| NFR-N3 | Tüm girdiler sunucu tarafında doğrulanmalı; dosya yüklemelerinde tip, boyut ve karma denetimi ile zararlı içerik tarama kancası bulunmalıdır. | FR-F4 | Çekirdek |
| NFR-N4 | Sırlar koda gömülmemeli; ortam değişkeni veya sır kasası üzerinden yönetilmelidir. | 0310 | Çekirdek |
| NFR-N5 | Veriler aktarımda ve beklemede şifrelenmelidir. | 0310 | Çekirdek |

### Denetlenebilirlik

| ID | Gereksinim | Bağ | Öncelik |
|---|---|---|---|
| NFR-N6 | Tüm yazma işlemleri değiştirilemez denetim izine kaydedilmelidir. | M14 | Çekirdek |
| NFR-N7 | Aynı calculation_run girdileriyle yeniden hesap birebir aynı sonucu üretmelidir. | M7, İ3 | Çekirdek |

### Güvenilirlik ve performans

| ID | Gereksinim | Bağ | Öncelik |
|---|---|---|---|
| NFR-N8 | Zamanlanmış işler planlanan pencerede tamamlanmalıdır (hedef M10). | M10 | Çekirdek |
| NFR-N9 | Motor çağrı hataları sınırlı yeniden denemeyle yönetilmeli; kısmi sonuç etiketlenmeli; hata oranı motor bazında izlenmelidir. | M8 | Çekirdek |
| NFR-N10 | Pano ve API için temel yanıt süresi hedefleri tanımlanmalı ve izlenmelidir (nicel eşik [K]). | 0404 | Çekirdek |

### Veri

| ID | Gereksinim | Bağ | Öncelik |
|---|---|---|---|
| NFR-N11 | Ölçüm ve skor tarihçesi kayıpsız saklanmalıdır; tarihçe ilk günden birikir. | W3 | Çekirdek |
| NFR-N12 | Kiracı verisi talep üzerine dışa aktarılabilmeli ve silinebilmelidir (KVKK/GDPR uyum yüzü). | 0310 | Çekirdek |
| NFR-N13 | Benchmark toplulaştırması gizlilik eşiği altındaki kümelerde sonuç döndürmemelidir. | FR-D5 | Genişletilmiş |

### Maliyet, dil, erişilebilirlik

| ID | Gereksinim | Bağ | Öncelik |
|---|---|---|---|
| NFR-N14 | Kiracı ve platform düzeyinde kota, hız sınırı ve bütçe tavanı uygulanmalıdır. | K1 | Çekirdek |
| NFR-N15 | Arayüz ve raporlar TR-öncelikli sunulmalı; ikinci dil için altyapı hazır olmalıdır. | İ5, D-01 | Çekirdek |
| NFR-N16 | Temel web erişilebilirlik uyumu sağlanmalıdır. | 0404 | Çekirdek |

## 6. Temsili Kabul Kriterleri (kritik gereksinimler; tam set 0404'te)

| Gereksinim | Verilen | Eylem | Beklenen |
|---|---|---|---|
| FR-C4, C5, NFR-N7 | Tamamlanmış bir ölçüm ve skor | Skor detayına inilir; aynı calculation_run yeniden hesaplanır | Fidelite etiketi ve güven aralığı görünür; girdi/faktör anlık görüntüsü listelenir; yeniden hesap birebir aynı skoru döndürür |
| NFR-N1 | Kiracı A kimliğiyle geçerli oturum | Kiracı B'ye ait kaynak kimliğiyle istek yapılır | İstek yetki hatasıyla reddedilir; kaynak varlığı sızdırılmaz; deneme denetim izine düşer |
| FR-F1 | Güven aralığı içinde kalan skor değişimi | Yeni ölçüm sonucu işlenir | Uyarı üretilmez; aralık dışı değişimde tek digest içinde bağlamlı uyarı üretilir |
| FR-F4 | Ajans marka ayarları tanımlı | Rapor üretimi istenir | İş kuyruğa alınır ve kabul yanıtı döner; PDF sunucu tarafında üretilir; hazır olunca indirme bağlantısı bildirilir |
| FR-B4 | robots.txt dosyasında arama botu engeli olan alan adı | Site erişim denetimi çalıştırılır | Engel kritik bulgu olarak, etkilenen bot adı ve düzeltme önerisiyle saniyeler içinde raporlanır |
| FR-F3 | Haftalık özet e-postası alan kullanıcı | Özetteki skor bağlantısına tıklanır | Oturum sonrası doğru kiracının ilgili skor detayına inilir (M1 derinleşme koşulu) |
| FR-E2 | Politika-aykırı taktik üretmeye zorlayan test girdi seti | Öneri üretimi çalıştırılır | Çıktıda aykırı taktik bulunmaz; filtre olayı telemetriye düşer |
| FR-D2 | Alıntı içeren ölçüm sonucu | Kaynak analizi açılır | Her alıntı kartı kaynak sayfaya tıklanabilir bağlantı taşır |

## 7. İzlenebilirlik Matrisi (UC → gereksinim; 0203 türetme kuralının kanıtı)

| UC | Gereksinim(ler) | UC | Gereksinim(ler) |
|---|---|---|---|
| UC-01 | FR-A1, FR-A3 | UC-16 | FR-F1 |
| UC-02 | FR-B1, FR-B5 | UC-17 | FR-F2 |
| UC-03 | FR-B2 | UC-18 | FR-F3 |
| UC-04 | FR-B4 | UC-19 | FR-F4, NFR-N3 |
| UC-05 | FR-C1 | UC-20 | FR-F5 |
| UC-06 | FR-C2, FR-C3, NFR-N8, NFR-N9 | UC-21 | FR-F6, FR-F7 |
| UC-07 | FR-C4, FR-C5, FR-C6, FR-C7 | UC-22 | FR-G1 |
| UC-08 | FR-D1 | UC-23 | FR-G2 |
| UC-09 | FR-D2 | UC-24 | FR-G3 |
| UC-10 | FR-D3 | UC-25 | FR-A2, NFR-N2 |
| UC-11 | FR-D4 | UC-26 | FR-A6 |
| UC-12 | FR-D5, NFR-N13 | UC-27 | FR-H1 (kayıt: NFR-N6) |
| UC-13 | FR-E1, FR-E2 | UC-28 | FR-A4 |
| UC-14 | FR-E3 | · | İlke/kota kaynaklı: FR-A5, FR-B3, FR-H2 |
| UC-15 | FR-E4 | · | Yatay NFR: N1, N4-N7, N10-N12, N14-N16 |

Kontrol: 0203'teki 19 Çekirdek senaryonun tamamı en az bir Çekirdek gereksinimle karşılanmıştır; hiçbir gereksinim senaryosuz (sahipsiz) değildir. Yatay NFR'ler tanımları gereği tüm senaryolara uygulanır.

## 8. Definition of Done (V1 özellik kabul listesi)

Bir özellik ancak aşağıdakilerin tamamı sağlandığında "bitti" sayılır:

1. Gereksinim bağı kayıtlı: özellik en az bir FR/NFR kimliğine, o da UC izine bağlanmış.
2. Kabul testleri yeşil: ilgili §6 kriterleri dahil, 0404 test planındaki senaryolar geçiyor.
3. İzolasyon ve yetkilendirme negatif testleri geçiyor (NFR-N1, NFR-N2 kapsamı).
4. Skor gösteren yüzeylerde fidelite etiketi ve güven aralığı görsel olarak doğrulanmış (İ2).
5. Yazma yolları denetim izinde doğrulanmış (NFR-N6).
6. TR dil tamlığı sağlanmış; terimler 0005 sözlüğüyle tutarlı.
7. Telemetri olayları ilgili 0004 metriklerine akıyor.
8. Changelog ve etkilenen doküman güncellemeleri 0007 süreciyle işlenmiş.

## 9. Varsayımlar ve Bağımlılıklar

| Kalem | Etki | Durum |
|---|---|---|
| D-02 kanal kararı | Gereksinimler yüzeyden bağımsız; responsive web önerisi değişirse yalnız sunum katmanı etkilenir | ADR-002, TL (PENDING) |
| D-03 Google yüzeyi | FR-B5 motor kapsamını ve FR-D1 kırılım setini etkiler | PO+TL, 0205 ile (PENDING) |
| Motor API sözleşmeleri | FR-C2/C3 kademe yapısına (0102) dayanır; erişim sertleşmesi R-04 riskini taşır | 0007 izleme kadansı |
| Ödeme altyapısı | FR-A6 self-serve yükseltme bu karara bağlı | O-2 (0202), PO |
| Persona doğrulaması | PRD'nin Approved geçişi P2/P3 görüşme kapısına bağlı | 0201 §9, AN |
| Benchmark gizlilik yöntemi | FR-D5 ve NFR-N13 yöntem kararını bekler | 0309/0310 (O-2) |

## 10. AVIP için Çıkarımlar

1. 0205 kesit kuralı işler durumda: Çekirdek etiketli 31 FR ve 15 NFR, masa bahisleri (0103 §7), K1 korumaları ve D-03 kararıyla kesiştirilerek MVP sınırına indirilir.
2. NFR-N1, N6 ve N7 mimari yükümlülüktür; Faz 3 tasarımları (0303 veri modeli, 0310 güvenlik) bu üçünü sonradan eklenemez kabul edip temel katmana koyar.
3. §6 kriterleri ve DoD listesi 0404 test stratejisinin çekirdek girdisidir; izolasyon negatif testi sürekli entegrasyonda zorunlu kapıdır (0403).
4. FR-F4 ve NFR-N3 birlikte dosya işleme hattını tanımlar: yükleme doğrulaması, eşzamansız üretim, imzalı indirme; 0304 ve 0307 tasarım girdisi.
5. FR-D2 tıklanabilir alıntı şartı motor bağdaştırıcı sözleşmesine iner: alıntı meta verisi (url_citation ve karşılıkları) 0308'de zorunlu alan olur.
6. ADR ihtiyaç listesi güncellendi: ADR-002 (kanal), API sözleşme yaklaşımı (FR-F6), arşiv ve rapor saklama yaşam döngüsü (O-4).

## 11. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| O-1 | Nicel performans ve zamanındalık eşikleri ([K] işaretli hedefler) | 0404 + pilot verisiyle kalibrasyon; TL. |
| O-2 | Benchmark gizlilik eşiği yöntemi (asgari kiracı sayısı, toplulaştırma kuralı) | 0309 + 0310; TL + PY. |
| O-3 | Okuma API'sinin kaynak kapsamı ve sözleşme yaklaşımı | 0304 ADR; TL. |
| O-4 | Ham yanıt arşivi ve rapor dosyaları saklama süreleri | Maliyet ve uyum dengesi; 0310 + 0007; TL + PO. |

---

## Kaynaklar

- 0203 Use Cases · senaryo envanteri ve türetme kuralı (§7 matrisinin temeli)
- 0202 User Journey · gereksinim adayları (çıkarım 2), kanal mimarisi, sürtünme karşı tasarımları
- 0201 User Personas · ihtiyaç matrisi, paket iskeleti, doğrulama kapısı (§9)
- 0102 AI Search Landscape · fidelite kuralı, tıklanabilir alıntı uyum şartı, kademe modeli
- 0004 Success Metrics · M1, M4, M6-M8, M10-M12, M14 bağları; K1/K3 korumaları

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 13.07.2026 | İlk yayın: 5 ürün ilkesi, 38 işlevsel + 16 işlevsel olmayan gereksinim, 8 temsili kabul kriteri, çift yönlü izlenebilirlik matrisi, 8 maddelik Definition of Done, varsayım/bağımlılık tablosu. |
| 1.1 | 18.07.2026 | Çekirdek sayım düzeltmesi (31 FR / 15 NFR; önceki metin 29/14); FR-F7 eklendi: temel CSV dışa aktarımı (Çekirdek; 0205 önerisinin kabulü); FR-D4'e panel versiyon işareti notu (0302 §5, 0309 §9); skor ölçeği teyidi 0-100 numeric(5,2) (0303 K7). Toplam 39 FR / 16 NFR. |
