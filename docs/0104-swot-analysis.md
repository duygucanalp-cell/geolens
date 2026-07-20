# 0104 · SWOT Analysis

| Alan | Değer |
|---|---|
| Doküman ID | 0104 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.1 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 18 Temmuz 2026 |
| Karşıladığı madde | 9 · SWOT analizi |
| İlişkili | 0101, 0102, 0103 (kanıt tabanı); 0105, 0204, 0205, 0310 (çıktı hedefleri) |

---

## 1. Amaç ve Kapsam

Bu doküman, Faz 1 araştırma bulgularını (0101 disiplin, 0102 motorlar, 0103 rakipler) stratejik bir değerlendirmeye çevirir. Klasik SWOT listesinin ötesine geçer: her madde kanıt referansı taşır, her zayıflık bir hafifletme planına, her tehdit bir erken uyarı sinyaline bağlanır ve §7'deki eşleştirme analizi strateji adaylarını üretir. Çıktılar 0105 (pazar fırsatı), 0204 (ürün ilkeleri; konumlandırma dili) ve 0205 (MVP kapsamı) dokümanlarının girdisidir. Kapsam dışı: finansal projeksiyon, fiyatlandırma ve kadro planlaması.

## 2. Yöntem ve Kanıt Tabanı

İç faktörler (güçlü ve zayıf yanlar) ürünün tasarım kararlarından ve ekibin mevcut durumundan; dış faktörler (fırsat ve tehditler) pazar araştırmasından türetilmiştir. Kural: kanıtı 0101-0103 setinde gösterilemeyen madde bu dokümana giremez. Her tehdit için bir erken uyarı sinyali tanımlanır ve izleme sorumluluğu 0007 kadanslarına bağlanır. Güç-zayıflık ile fırsat-tehdit kombinasyonları §7'de eşleştirilir; üretilen strateji adayları karar dokümanlarında (0204, 0205) sonuçlandırılır. Ürün henüz geliştirme öncesi aşamada olduğundan, güçlü yanların bir bölümü "tasarım taahhüdü" niteliğindedir; bunlar tabloda açıkça işaretlenmiştir.

## 3. Güçlü Yanlar

| ID | Madde | Kanıt / Dayanak | Nasıl kullanılır |
|---|---|---|---|
| S1 | Açıklanabilirlik ve denetlenebilirlik mimarisi: calculation_run_id, faktör anlık görüntüsü, şablon versiyonu (tasarım taahhüdü, G2) | Kategoride eşdeğeri tespit edilmedi; skor üretimi kara kutu (0103 §7) | Kurumsal güven ve tekrarlanabilirlik anlatısının çekirdeği; M6-M7 sert kuralları |
| S2 | Fidelite dürüstlüğü: her skorda görünür yüzey etiketi (ürün kuralı, 0102 §4) | Hiçbir rakip yüzey ayrımını etiketlemiyor (0103 §4 matrisi) | Rakiplerin sakladığı gerilimi ilkeye çevirme; 0204 İ-serisi ilke dili (İ2) |
| S3 | İstatistiksel ölçüm: örnekleme, güven aralığı, oynaklık ayrıştırma (tasarım taahhüdü, H3; M5) | Kategori istatistik beyan etmiyor; araçlar arası tutarsızlık bilinen sorun (0103 §5) | "Ölçümün ölçüm kalitesi" boşluğunu doldurma; Evertune dışında rakipsiz alan |
| S4 | TR pazarı yakınlığı: dil, yerel prompt setleri, motor önceliği bilgisi | TR, ChatGPT trafik payında dünya birincisi; küresel araçlarda TR odağı yok (0102 §6, 0103 §2) | TR-öncelikli giriş kaması; yerel referans müşteriler |
| S5 | Panel-tabanlı öngörülebilir maliyet modeli | Haftalık panel maliyeti düşük tek haneli dolar mertebesinde ölçeklenebilir (0102 §5) | Sağlıklı birim ekonomisi; K1 korumasıyla disiplinli büyüme |
| S6 | Çok kiracılı mimari ve ajans modeli (tasarım taahhüdü, G7) | Çok-hesap yönetimi kategori zayıflığı, lider dahil (0103 §3) | Ajans segmentine erken avantaj; 0201'e "ajans" personası |

## 4. Zayıf Yanlar

| ID | Madde | Etki | Hafifletme |
|---|---|---|---|
| W1 | Geç giriş: kategori 2023-2025'te şekillendi; lider 700+ kurumsal müşteriye ulaştı (0103 §3) | Doğrudan kafa kafaya rekabet kazanılamaz | Cepheden değil kamalardan giriş: TR pazarı, ajans segmenti, güvenilirlik boşluğu (§7 SO stratejileri) |
| W2 | Marka bilinirliği sıfır; ürün adı dahi seçim aşamasında (0006 O-3) | Kurumsal satış döngüsü uzar | Metodoloji şeffaflığıyla içerik stratejisi (set dışı pazarlama çalışması); G9 standart söylemiyle düşünce liderliği; isim kararının hızlandırılması |
| W3 | Tarihçe verisi yok; 12+ ay trend masa bahisi (0103 §7) | Yerleşik araçlardan geçiş bariyeri | Tarihçe borcu yalnız zamanla kapanır: pilot müşterilerde veri biriktirmeye ilk günden başlama; içe aktarma köprüleri değerlendirmesi (0205) |
| W4 | Kapasite: tek ekip, çok motor connector bakımı; motor davranışları hızlı değişiyor (0101 §4) | Bakım yükü ürün geliştirmeyi yavaşlatabilir | Connector standardizasyonu (0308); kademeli motor ekleme (0102 §7); K2-K3 korumalarıyla otomatik izleme |
| W5 | Kurumsal uyum sertifikaları yok; SOC 2 masa bahisi (0103 §7) | Kurumsal satın alma süreçlerinde elenme | Güvenlik-ilk mimari (0310) ve SOC 2 yolunun erken planlanması; zamanlama O-2 |

## 5. Fırsatlar

| ID | Madde | Kanıt | Yakalama stratejisi |
|---|---|---|---|
| F1 | Kategori momentumu: pazar eğitimi maliyetini rakipler ödüyor | 300 milyon doları aşkın yatırım; arama hacmi yıllık ~%1900 artış (0103 §2) | Momentuma binen içerik ve karşılaştırma varlıkları; hızlı MVP |
| F2 | TR ürün boşluğu: hizmet katmanı doğdu, yerli ürün yok | TR ajans söylemi kurulmuş; ürün tespit edilmedi (0103 §2); TR motor tablosu net (0102 §6) | TR-first GTM değerlendirmesi (O-1); ajanslara white-label köprüsü |
| F3 | Metrik standardı kaosu: aynı isimli metrikler farklı paydalarla raporlanıyor | "Alıntı payı" üç farklı paydayla ölçülüyor (0101 §7) | G9: standart tanım seti yayınlama; sözlük (0005) temelli açık metodoloji |
| F4 | Yerleşiklerin sığlığı ve kurumsal liderin ajans zayıflığı | Süit modülleri derinliksiz; çok-hesap kategori zayıflığı (0103 §3-§4) | Orta segment + ajans kaması; S6 ile eşleşme |
| F5 | Ölçüm güvenilirliği krizi: kullanıcılar araçlara güvenmekte zorlanıyor | Platformlar arası tutarsızlık bağımsız değerlendirmelerde (0103 §5) | S1+S3 ile "güvenilir ölçüm" konumlandırması; karşılaştırmalı doğruluk içerikleri |

## 6. Tehditler

| ID | Madde | Erken uyarı sinyali | Yanıt planı |
|---|---|---|---|
| T1 | Kurumsal liderin aşağı segmente inmesi ve pazarı kapatması | Self-serve kademe lansmanı; ajans programı duyurusu | TR ve açıklanabilirlik derinliğinde kalıcı fark; hız |
| T2 | Yerleşik süitlerin AI modüllerini ana pakete gömmesi | Paketleme ve fiyat birleştirme duyuruları | Derinlik ve metodoloji farkının kanıtlanabilir anlatımı (F5 içerikleri) |
| T3 | Motor sağlayıcıların birinci taraf görünürlük analitiği sunması | OpenAI/Google analitik ürün duyuruları | Bağımsız çapraz-motor katman konumu: tek motorun panosu rakip motorları gösteremez; tarafsızlık anlatısı |
| T4 | Erişim koşullarının sertleşmesi: API fiyat artışı, ToS daralması (R-04) | K2 motor politika uyumu izlemesi; fiyat sayfası değişiklikleri | Connector soyutlaması; D-03 çok seçenekli Google stratejisi; panel maliyet tamponu (K1) |
| T5 | Uzun kuyruk çoğalması ve kategori değer erozyonu | Yeni araç sayısındaki artış hızı; karşılaştırma listelerinin şişmesi | Kurumsal güven öğeleriyle (denetim izi, uyum) yukarı segment farklılaşması |

## 7. TOWS Eşleştirmesi

| Tip | Eşleşme | Strateji adayı | Hedef doküman |
|---|---|---|---|
| SO | S4 × F2 | TR-öncelikli giriş: Türkçe ürün, TR prompt kütüphanesi, yerel referanslar; ajanslara white-label | 0205, set dışı (pazarlama) |
| SO | S1+S3 × F5 | "Güvenilir ölçüm" ana konumlandırması: örnekleme + güven aralığı + denetim izi üçlüsü | 0204 |
| SO | S2 × F3 | Fidelite etiketi + standart metrik tanımlarıyla metodoloji liderliği (G9) | 0204, 0309 |
| WO | W2 × F1 | Kategori momentumuna binen şeffaf metodoloji içerikleri; bilinirliği ürün lansmanından önce inşa | Set dışı (pazarlama) |
| WO | W3 × F2 | TR pilot müşterileriyle tarihçe biriktirmeye erken başlama; pilot programının Faz 3 ile paralel kurgusu | 0205 |
| ST | S1 × T3 | Bağımsız çapraz-motor katman anlatısı; motor-tarafsızlık ilkesinin ürünleşmesi | 0204 |
| ST | S5 × T4 | Maliyet tamponu ve connector soyutlamasıyla erişim şoku dayanıklılığı | 0308 |
| WT | W4 × T4 | Kademeli motor genişlemesi; her yeni motor K1-K2 korumalarına karşı test edilerek eklenir | 0205, 0308 |
| WT | W5 × T1 | SOC 2 yolunun ve güvenlik mimarisinin erken planlanması; kurumsal satışa hazırlık | 0310 |

## 8. Stratejik Sonuçlar

1. **Kamalardan giriş, cepheden değil.** W1 gerçeği değişmez; kazanma alanları TR pazarı (S4×F2), ajans segmenti (S6×F4) ve güvenilirlik boşluğudur (S1+S3×F5). MVP ve GTM bu üç kamaya hizalanır.
2. **Dürüstlük stratejik varlıktır.** Fidelite etiketi ve açık metodoloji (S1, S2, S3), pazarın güven krizinde (F5) tek savunulabilir uzun vadeli konumdur; taklidi kolay görünse de kültürel ve mimari taahhüt gerektirir.
3. **Zaman kritik iki borç var:** tarihçe (W3) ve bilinirlik (W2). İkisi de yalnız erken başlangıçla kapanır; pilot program ve içerik stratejisi ürün gelişimini bekleyemez.
4. **Tehdit izleme kurumsallaşır.** T1-T5 sinyalleri 0007 iki haftalık metrik kadansına eklenir; sinyal tetiklenirse yanıt planı gündeme alınır.

## 9. AVIP için Çıkarımlar

1. 0105, F1-F2'yi sayısallaştırır: pazar büyüklüğü sinyalleri, TR fırsat penceresi ve zamanlama.
2. 0204 ürün ilkeleri (İ-serisi) SO stratejilerinden türetildi: güvenilir ölçüm + fidelite + bağımsız katman.
3. 0205 MVP kalibrasyonu: masa bahisleri (0103 §7) + kademeli motor planı (WT) + pilot/tarihçe kurgusu (WO).
4. 0310 güvenlik mimarisi SOC 2 yolunu tasarım hedefi olarak alır (W5).
5. T sinyalleri 0007 risk ve kadans mekanizmasına işlenir; sahiplik AN, eskalasyon PO.
6. Ajans personası 0201 kapsamına alınır (S6, F4).

## 10. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| O-1 | GTM sıralaması: TR-first mi, TR+İngilizce paralel mi? | 0002 O-1 segment kararı ve 0205 ticari açılış çerçevesi; PO. F2 penceresi ile küresel momentum (F1) arasındaki denge. |
| O-2 | SOC 2 hazırlık zamanlaması: MVP ile paralel mi, ilk kurumsal müşteriyle mi? | TL+PO; 0310 tasarımını etkiler. |
| O-3 | Ajans segmentinin öncelik derecesi: birincil persona mı, ikincil mi? | 0201 persona görüşmeleriyle doğrulanır; S6 yatırım derinliğini belirler. |

---

## Kaynaklar

- 0101 GEO Landscape v1.0 · disiplin, motor alıntı davranışları, metrik standardı bulgusu
- 0102 AI Search Landscape v1.0 · erişim kademeleri, fidelite kuralı, TR pazar verileri, maliyet modeli
- 0103 Competitor Analysis v1.0 · segmentler, rakip profilleri, yetenek matrisi, boşluk analizi
- Bu doküman sentezdir; birincil dış kaynaklar ilgili araştırma dokümanlarında listelenmiştir.

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 12.07.2026 | İlk yayın: kanıt bağlantılı SWOT (6 güç, 5 zayıflık, 5 fırsat, 5 tehdit), erken uyarı sinyalli tehdit tablosu, dokuz satırlık TOWS eşleştirmesi, dört stratejik sonuç. |
| 1.1 | 18.07.2026 | Çapraz referans düzeltmeleri: konumlandırma atıfları 0204 ürün ilkelerine, GTM atfı 0002 O-1 + 0205 çerçevesine bağlandı; içerik stratejisi set dışı pazarlama çalışması olarak işaretlendi (v1.1 birleşik turu, 0206 O-4). |
