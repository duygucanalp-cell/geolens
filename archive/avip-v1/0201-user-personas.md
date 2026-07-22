# 0201 · User Personas

| Alan | Değer |
|---|---|
| Doküman ID | 0201 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.4 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
| Karşıladığı madde | Türetilmiş doküman; 0002 O-1 (segment önceliği) kararının taşıyıcısı, 0202-0203 (madde 13-14) girdisi |
| İlişkili | 0002, 0103, 0104, 0105 (kanıt); 0202, 0203, 0204, 0205, 0310 |

---

## 1. Amaç ve Kapsam

Bu doküman Faz 2'yi açar: ürünün kimin için kurulduğunu persona düzeyinde sabitler, 0002 O-1 açık sorusunu (hedef segment önceliği) karar önerisine bağlar ve 0202 (User Journey) ile 0203 (Use Cases) dokümanlarının aktör setini tanımlar. Persona seti hem B2B (kurumsal, KOBİ, ajans) hem B2C (bağımsız profesyonel, içerik üreticisi) tarafını ilk günden kapsar; bu, ürünün çok kiracılı tek platform ilkesinin (G7) doğrudan sonucudur. Fiyat bilgisi kapsam dışıdır; paket eşlemeleri niteldir.

## 2. Yöntem: Proto-Persona Yaklaşımı

Buradaki personalar saha görüşmesinden değil, Faz 1 kanıt tabanından türetilmiş proto-personalardır: 0002 §4 aktör-ağrı haritası, 0103 segment ve rakip bulguları, 0105 benimseme ve uygulama açığı verileri. Kişiler kurgusaldır; her kartın kanıt satırı hangi bulguya dayandığını gösterir. Proto-persona statüsü bilinçli bir dürüstlük işaretidir: kartlar §9'daki doğrulama planıyla saha görüşmelerinde test edilir, doğrulanan veya yanlışlanan varsayımlar v1.1'e işlenir. Bir varsayım yanlışlanırsa bağlı ürün kararları (0204, 0205) gözden geçirilir.

## 3. Segment Haritası ve Tek Platform İlkesi

| Taraf | Segment | Pazar durumu (kanıt) | Persona |
|---|---|---|---|
| B2B | Kurumsal (enterprise) | Kategori girişimleri başlamış; rekabet yoğun, satın alma kurumsal uyum ister (0105 §6, 0103 §3) | P1 |
| B2B | KOBİ ve orta segment | Büyük ölçüde başlamamış; en geniş bakir alan (0105 §6) | P2 |
| B2B | Ajanslar | Kategori zayıflığı çok-hesap yönetimi; TR'de hizmet söylemi kurulmuş (0103 §3, 0102 §6) | P3 |
| B2C | Bağımsız profesyonel / danışman | Self-serve giriş kademesi kategoride kanıtlı model (0103 §3) | P4 |
| B2C | İçerik üreticisi / kişisel marka | AI arama, kullanıcıların yüzde 44'ü için birincil keşif kanalı; kişisel görünürlük yeni ihtiyaç (0105 §3) | P5 |

> **Tek platform ilkesi:** beş persona tek kod tabanında, tek çok kiracılı platformda yaşar. Segment farkları paketleme ve paket haklarıyla (entitlement) ifade edilir; hiçbir persona için ayrı ürün, ayrı kod tabanı veya tek müşteriye özel tasarım yapılmaz. Fidelite etiketi ve açıklanabilirlik her pakette istisnasız bulunur; güven öğeleri paket farkı değil ürün kimliğidir (0102 §4, G2).

## 4. Persona Kartları (kişiler kurgusaldır; kanıt satırları Faz 1'e bağlanır)

### P1 · Deniz — Kurumsal Pazarlama Direktörü

| Alan | İçerik |
|---|---|
| Bağlam | TR merkezli büyük marka; SEO, içerik ve PR ekiplerini yönetiyor; yönetim kuruluna raporluyor. |
| Hedefler | "AI bizi öneriyor mu" sorusuna kanıtlı yanıt; rakip kıyası; marka hakkında yanlış bilgi riskini erken görmek. |
| Ağrılar | Anekdottan öte veri yok (0002 §4); mevcut araçlara güven düşük, araçlar arası tutarsızlık biliniyor (0103 §5); satın almada SOC 2 ve veri işleme şartları elemesi (0103 §7). |
| Karar kriterleri | Metodoloji şeffaflığı, denetim izi, kurumsal uyum, tarihçe derinliği, SSO. |
| Kanal hipotezi | Haftalık e-posta yönetici özeti + ekip için pano; kritik değişimde anlık uyarı. |
| AVIP eşleşmesi | Denetim izi ve tekrarlanabilirlik (S1), fidelite etiketi (S2), güven aralıklı skor (S3), rakip kıyası (G5). |
| Paket eğilimi | Enterprise. |

### P2 · Elif — KOBİ Pazarlama Yöneticisi

| Alan | İçerik |
|---|---|
| Bağlam | E-ticaret ağırlıklı KOBİ; pazarlamayı küçük ekiple, kısıtlı bütçeyle yürütüyor; SEO'yu kısmen ajansa yaptırıyor. |
| Hedefler | AI yanıtlarında görünür olmak; nereden başlayacağını bilmek; harcadığı içerik bütçesinin karşılığını görmek. |
| Ağrılar | Kategori bilgisi sınırlı (pazarın yüzde 92 plan / yüzde 40.6 uygulama makası tam bu segmentte, 0105 §6); araç karmaşıklığı ve yabancı dil bariyeri; zaman yok. |
| Karar kriterleri | Türkçe arayüz ve rapor, hızlı kurulum, net aksiyon önerisi, erişilebilir kademe. |
| Kanal hipotezi | Haftalık e-posta özeti; panoya seyrek giriş. |
| AVIP eşleşmesi | TR-öncelikli prompt kütüphanesi (S4), kanıt dereceli öneriler (G4), site erişim denetimi hızlı kazanımı (0101 çıkarım 6). |
| Paket eğilimi | Pro veya Business. |

### P3 · Mert — Ajans SEO/GEO Direktörü

| Alan | İçerik |
|---|---|
| Bağlam | Dijital pazarlama ajansında çok müşterili portföy; müşterilerine yeni AIO/GEO hizmeti satmak istiyor (TR ekosistem sinyali, 0102 §6). |
| Hedefler | Müşteri başına görünürlük raporu üretmek; hizmeti ölçeklenebilir ve markalı sunmak; yeni gelir hattı açmak. |
| Ağrılar | Elle derlenen ekran görüntüsü raporları (0002 §4); kategori liderinde dahi çok-hesap yönetimi zayıf (0103 §3); rapor üretimi zaman yiyor. |
| Karar kriterleri | Çok müşterili çalışma alanı, white-label rapor, koltuk politikası, BI/API entegrasyonu, müşteri başına maliyet öngörülebilirliği. |
| Kanal hipotezi | Pano yoğun kullanım + Slack uyarıları; müşteriye giden zamanlanmış PDF/BI raporu. |
| AVIP eşleşmesi | Çok kiracılı ajans modeli (S6), white-label raporlama, panel-tabanlı maliyet modeli (S5). |
| Paket eğilimi | Business (ajans çalışma alanı). |

### P4 · Selin — Bağımsız SEO/GEO Danışmanı

| Alan | İçerik |
|---|---|
| Bağlam | Tek kişilik danışmanlık; birkaç müşteri, dar araç bütçesi; uzmanlığını yeni kategoriye taşıyarak fark yaratmak istiyor. |
| Hedefler | Müşterilerine AI görünürlüğü teşhisi ve iyileştirme sunmak; kendini kategoride erken uzman konumlandırmak. |
| Ağrılar | Kurumsal araçlar pahalı ve satış-temaslı; manuel prompt denemesi tekrarlanamaz (H1); metodolojisini müşteriye savunacak kanıt dili yok. |
| Karar kriterleri | Self-serve kayıt, düşük giriş kademesi, metodolojinin açıklanabilirliği (müşteriye anlatılabilirlik), dışa aktarılabilir rapor. |
| Kanal hipotezi | E-posta + mobil bildirim; hafif, hızlı pano oturumları. |
| AVIP eşleşmesi | Açıklanabilir skor ve fidelite dili (S1-S2) danışmanın satış aracına dönüşür; self-serve giriş. |
| Paket eğilimi | Free ile deneme, Pro'ya geçiş. |

### P5 · Kaan — İçerik Üreticisi / Kişisel Marka

| Alan | İçerik |
|---|---|
| Bağlam | Uzmanlık içeriği üreten yaratıcı (bülten, video, blog); geliri görünürlüğüne bağlı; AI araçlarını yoğun kullanıyor. |
| Hedefler | AI motorları kendi alanında onu kaynak olarak gösteriyor mu, rakip üreticiler mi öne çıkıyor görmek; içerik konularını buna göre seçmek. |
| Ağrılar | Keşif, AI yanıtlarına kayıyor (birincil keşif kanalı verisi, 0105 §3) ama üretici tarafında ölçüm aracı kurumsal odaklı; bütçe düşük. |
| Karar kriterleri | Ücretsiz başlangıç, tek isim/marka izleme, basit skor, paylaşılabilir sonuç. |
| Kanal hipotezi | Mobil bildirim + e-posta; pano kullanımı hafif. |
| AVIP eşleşmesi | Kişisel marka izleme, konu bazlı görünürlük sinyali; Free kademesi büyüme ve topluluk motoru. |
| Paket eğilimi | Free; sınırlı Pro dönüşümü. |

## 5. Persona-İhtiyaç Matrisi

| Yetenek alanı | P1 | P2 | P3 | P4 | P5 |
|---|---|---|---|---|---|
| Çok motorlu izleme ve skor (G1) | Kritik | Kritik | Kritik | Kritik | Temel |
| Alıntı / kaynak analizi (G3) | Kritik | Orta | Kritik | Kritik | Düşük |
| Kanıt dereceli öneriler (G4) | Orta | Kritik | Kritik | Kritik | Orta |
| Rakip kıyası (G5) | Kritik | Orta | Kritik | Orta | Orta (rakip üretici) |
| Trend ve uyarılar (G6) | Kritik | Orta | Kritik | Orta | Orta |
| White-label / dışa aktarım | Orta (BI) | Düşük | Kritik | Kritik (PDF) | Düşük |
| Kurumsal uyum: SSO, denetim izi (G7) | Kritik | Düşük | Orta | Düşük | Düşük |
| API / BI entegrasyonu | Kritik | Düşük | Kritik | Düşük | Düşük |
| TR dil ve prompt kütüphanesi | Orta | Kritik | Kritik | Kritik | Kritik |
| Fidelite etiketi ve açıklanabilirlik | Tüm personalarda istisnasız (ürün kimliği, §3) | | | | |

Matris 0204 (PRD) gereksinim önceliklendirmesinin ve 0205 (MVP Scope) kesitinin girdisidir; "Kritik" hücreler MVP adayı yetenekleri işaret eder ancak MVP kararı 0205'te verilir.

## 6. Paketleme ve Yetkilendirme Girdileri

Nitel eşleme; fiyat yok, kesin kota yok. Amaç, 0205 ve 0310 tasarımına persona-paket iskeleti vermektir:

| Paket | Birincil persona | Ayırt edici paket hakları (aday) |
|---|---|---|
| Free | P5, P4 (deneme) | Tek marka/isim, dar prompt kotası, temel skor, fidelite etiketi dahil; topluluk ve dönüşüm hunisi işlevi. |
| Pro | P4, P2 | Çekirdek motor seti, haftalık zamanlanmış izleme, öneriler, PDF dışa aktarım. |
| Business | P3, P2 (büyüyen) | Çok müşterili çalışma alanı, white-label rapor, API/BI, ekip koltukları, uyarı kanalları. |
| Enterprise | P1 | SSO/SAML, denetim izi dışa aktarımı, genişletilmiş tarihçe, sözleşmesel destek; SOC 2 yoluyla hizalı (0310). |

İki tasarım kuralı: paket hakları yapılandırmadır, kod dalı değildir (tek platform ilkesi); güven öğeleri (fidelite, açıklanabilirlik, izolasyon) hiçbir pakette kısıtlanmaz.

> **Frekans kademesi (0205 v1.4, §3 kararı):** Free ve Pro haftalık ölçüm; Business ve Enterprise günlük ölçüm. Frekans paket hakkı olarak uygulanır ve pilotta kalibre edilir.

## 7. Segment Önceliği Önerisi (0002 O-1; karar PO'da)

> ✅ **KARAR (21.07.2026, PO onayı):** V1 ticari odağı P3 (ajans) ve P2 (KOBİ) ikilisidir; P4 self-serve huniyle eş zamanlı açılır, P5 Free kademesinin büyüme motorudur. P1 (kurumsal) tasarım hedefi olarak korunur (mimari kurumsal-hazır kurulur, G7) ancak aktif satış odağı SOC 2 yolu ve tarihçe birikimi olgunlaşana kadar ertelenir. Gerekçe: 0104 stratejik sonucu "kamalardan giriş"; 0105 §6 KOBİ/orta bakir alanı; 0103 ajans zayıflığı; W5 kurumsal uyum eksiği. Bu kararla 0002 O-1 kapanmış, 0007 karar kaydına D-04 olarak işlenmiştir.

## 8. Anti-Personalar

| Profil | Neden kapsam dışı |
|---|---|
| Garanti arayan alıcı | "Bizi ChatGPT'de 1 numara yapın" beklentisi NG8 ile çelişir; olasılıksal ölçüm satılır, garanti satılmaz. |
| Manipülasyon talep eden | Motor politikalarına aykırı taktik isteyenler NG10 sınırıdır; öneri motoru bu taktikleri üretmez. |
| Tek seferlik denetim isteyen | Ürün sürekli izleme platformudur; tek seferlik teşhis ihtiyacı ajans kanalına (P3) yönlendirilir. |
| Kazıma verisi talep eden | Kademe 3 yüzeylerin arayüz kazımasını isteyen alıcı NG9 ile çelişir; fidelite ilkesi pazarlıksızdır. |

## 9. Doğrulama Planı

| Hipotez | Doğrulama yöntemi | Bağ |
|---|---|---|
| Kart varsayımları (hedef, ağrı, karar kriterleri) | Segment başına en az 5 yarı yapılandırılmış görüşme; P3 ve P2 öncelikli | H1, H5; 0002 §8 |
| Bildirim kanalı tercihleri | Görüşme + erken pilotta kanal etkileşim ölçümü | 0103 O-3; D-02 uygulaması |
| Ajans önceliği ve white-label ihtiyacı | TR ajanslarıyla görüşme; iş modeli soruları (0104 §11'de 5 soruluk kılavuz) | 0104 O-3, S6 |
| TR pencere varsayımı (12-18 ay) | Alıcı olgunluğu soruları; kategori farkındalık ölçümü | 0105 O-3 |
| Free-Pro dönüşüm varsayımı (P4/P5) | Bekleme listesi + açılış deneyi; nitel ön test | M1 paydası; 0004 O-1 |

Tamamlanma kriteri: P2 ve P3 kartları görüşme verisiyle güncellenmeden 0204 (PRD) Approved durumuna geçmez; diğer kartlar paralel doğrulanır.

### P3 Görüşme Aday Listesi (0104 §11'den devralınmıştır)

P3 (Mert — Ajans SEO/GEO Direktörü) personasını doğrulamak için hedeflenen ajanslar:

| Öncelik | Ajans | Görüşülecek Kişi Profili | Görüşme Odağı |
|---|---|---|---|
| 🔴 1 | **Sheltron** | SEO/GEO direktörü veya kurucu | Predictive SEO yaklaşımı, AI görünürlük denetim süreci, müşteri raporlama iş akışı |
| 🔴 2 | **Cremicro** | Haydar Özkömürcü (kurucu) veya GEO ekibi | Çok dilli GEO, cross-border müşteri yönetimi, ajans araç ihtiyaçları |
| 🔴 3 | **Seobaz** | GEO/AI Visibility ekibi | AI analiz motoru, "ölçülebilir GEO" yaklaşımı, müşteri başına maliyet modeli |
| 🔴 4 | **Webtures** | Kaan Gülten (kurucu) veya strateji ekibi | Agentic Web Optimization, ajans-white-label ihtiyacı |
| 🟡 5 | **Zeo Agency** | Strateji direktörü | Veri odaklı GEO, büyük müşteri portföyü yönetimi, BI/API entegrasyon ihtiyacı |
| 🟡 6 | **Mobitek** | E-ticaret SEO ekibi | Büyük katalog yönetimi, e-ticaret müşterilerinin AI görünürlük farkındalığı |
| 🟡 7 | **Aora Digital** | GEO/AEO uzmanı | Ankara perspektifi — KOBİ ve orta ölçekli müşteri profili |
| 🟡 8 | **Digipeak** | Uluslararası müşteri ekibi | TR+EN paralel ajans operasyonu, çok kanallı yaklaşım |

> **Görüşme protokolü:** 0104 §11'deki 5 soruluk kılavuz kullanılır. Görüşmelerde satış değil, keşif ve ortaklık dili kullanılır. Her görüşme 30-45 dk, online. AN yürütür, bulgular 0007 haftalık senkronunda raporlanır.

## 10. AVIP için Çıkarımlar

1. 0202 (User Journey) birincil yolculukları P3 ve P2 için çizer; P4 self-serve yolculuğu ayrı akıştır (kayıt-değere-ulaşma süresi kritik).
2. 0204 (PRD) gereksinim önceliklendirmesi §5 matrisini temel alır; "Kritik" satırlar aday zorunlu gereksinimlerdir.
3. 0205 (MVP Scope) paket iskeletini §6'dan alır; masa bahisleri (0103 §7) ile persona kritikleri kesiştirilir.
4. 0310 güvenlik tasarımı P1 gereksinimlerini (SSO, denetim izi dışa aktarımı) ilk günden mimariye koyar; satışı beklemez (kurumsal-hazır ilke).
5. Çapraz referans düzeltmesi: 0104 v1.1 ve 0105 v1.1'deki hatalı atıflar ("0202 konumlandırma" → 0204 ürün ilkeleri, "0203 GTM" → 0002 O-1 + 0205 çerçevesi, "0405 içerik stratejisi" → set dışı pazarlama çalışması) düzeltildi (v1.1 birleşik turu).
6. Kanal hipotezleri (kart satırları) D-02 uygulamasının (responsive web + uyarı kanalları) tasarım girdisidir; doğrulama sonrası 0204'e gereksinim olarak iner.

## 11. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| ~~O-1~~ | ~~Segment önceliği önerisinin onayı (§7)~~ | ~~PO kararı; kabulle 0002 O-1 kapanır, 0007'ye işlenir.~~ |
| ✅ O-1 | Segment önceliği önerisinin onayı (§7) | **KAPANDI** (21.07.2026, PO onayı): P3 (ajans) + P2 (KOBİ) odağı kabul edildi. 0002 O-1 böylece kapanmıştır. 0007 karar kaydına işlendi. |
| ~~O-2~~ | ~~P4/P5 self-serve hunisinin V1'de mi, hızlı-takip sürümde mi açılacağı~~ | ~~0205 kapsam kararı; destek yükü ve ödeme altyapısı bağımlılığı.~~ |
| ✅ O-2 | P4/P5 self-serve hunisi | **KAPANDI** (21.07.2026): self-serve kayıt V1 MVP'de teknik olarak açık. Pilot döneminde davetli + self-serve birlikte, paket atamaları arka ofisten. Genel açılış pilot çıkış kapısı sonrası. Ödeme bilgisi istenmez — sürtünmesiz kayıt (e-posta + şifre). |
| ~~O-3~~ | ~~Görüşme erişimi: P2/P3 adaylarına ulaşım kanalı ve takvim~~ | ~~AN yürütür; TR ajans ekosistemi (0102 §6) ilk havuz.~~ |
| ✅ O-3 | Görüşme erişimi: P2/P3 adaylarına ulaşım kanalı ve takvim | **KAPANDI** (21.07.2026): TR ajans ekosistemi üzerinden, AN yürütür. Pilot öncesi tamamlanmalı. |

---

## Kaynaklar

- 0002 Problem Statement §4 · aktör-ağrı haritası (persona tohumları)
- 0103 Competitor Analysis · segment yapısı, ajans zayıflığı, self-serve modeller, masa bahisleri
- 0104 SWOT · S4/S5/S6 güçleri, "kamalardan giriş" stratejik sonucu, O-3 ajans önceliği sorusu
- 0105 Market Opportunity · benimseme verileri, uygulama açığı, KOBİ bakir alanı, keşif kanalı verisi
- 0102 AI Search Landscape §6 · TR motor önceliği ve ajans ekosistemi sinyali

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 13.07.2026 | İlk yayın: beş proto-persona (B2B: P1-P3, B2C: P4-P5), ihtiyaç matrisi, dört paketli yetkilendirme iskeleti, segment önceliği önerisi (0002 O-1), anti-personalar, doğrulama planı; 0104/0105 çapraz referans düzeltme notu. |
| 1.1 | 21.07.2026 | Segment önceliği PO onayıyla karara bağlandı: P3+P2 odağı. §7 öneriden karara dönüştü; O-1 kapandı (0002 O-1 böylece kapanmıştır). 0007 karar kaydına D-04 olarak işlendi. |
| 1.2 | 21.07.2026 | O-2 kapandı: self-serve V1'de açık, sürtünmesiz kayıt (ödeme bilgisi istenmez). 0007 D-07. |
| 1.3 | 21.07.2026 | O-3 kapandı: P2/P3 görüşmeleri TR ajans ekosistemi üzerinden, pilot öncesi. AN yürütür. 0007 D-25. |
| 1.4 | 22.07.2026 | §9 Doğrulama Planı güncellendi: P3 görüşme aday listesi eklendi (8 ajans, öncelik sırasıyla). Görüşme protokolü ve 0104 §11 referansı eklendi. 0007 D-78 kapsamı. |
