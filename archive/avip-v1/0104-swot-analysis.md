# 0104 · SWOT Analysis

| Alan | Değer |
|---|---|
| Doküman ID | 0104 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.4 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
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
5. **Ajans birincil segment hipotezi (O-3).** Mevcut kanıt sentezi ajans segmentinin birincil, KOBİ'nin paralel ikincil olması yönündedir — gerekçe, kanıt tablosu ve doğrulama planı §10-11'de. Bu hipotez saha görüşmeleriyle test edilip doğrulandıktan sonra stratejik sonuca dönüşür.

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
| ~~O-1~~ | ~~GTM sıralaması: TR-first mi, TR+İngilizce paralel mi?~~ | ~~0002 O-1 segment kararı ve 0205 ticari açılış çerçevesi; PO. F2 penceresi ile küresel momentum (F1) arasındaki denge.~~ |
| **✅ O-1 (KAPANDI)** | **TR+İngilizce paralel. Ürün baştan iki dilde yayınlanacak.** | **PO kararı (21.07.2026). 0007 D-44.** |
| ~~O-2~~ | ~~SOC 2 hazırlık zamanlaması: MVP ile paralel mi, ilk kurumsal müşteriyle mi?~~ | ~~TL+PO; 0310 tasarımını etkiler.~~ |
| **✅ O-2 (KAPANDI)** | **İlk kurumsal müşteriyle başlar. MVP döneminde güvenlik-ilk mimari yeterli. SOC 2 çalışması P1 satışıyla tetiklenir.** | **PO kararı (21.07.2026). 0007 D-45.** |
| O-3 | Ajans segmentinin öncelik derecesi: birincil persona mı, ikincil mi? | **Bloklayıcı — görüşmelerle kapanacak.** Pilot öncesi netleşmeli. AN yürütür. 0007 D-78. **Ön hipotez ve doğrulama planı aşağıda (§11).** |

### O-3 Ön Hipotez: Ajans = Birincil, KOBİ = Paralel İkincil

Mevcut doküman kanıtı, aşağıdaki sentez doğrultusunda **ajansın birincil segment olması** yönünde güçlü sinyal vermektedir. Bu hipotez §11'deki görüşmelerle test edilecek; doğrulanması halinde karar PO'ya sunulacaktır.

#### Ajansın Birincil Olması Lehinde Kanıt

| # | Kanıt | Kaynak | Güç |
|---|---|---|---|
| K1 | Rakip liderin (Profound) çok-hesap yönetimi zayıf — bu doğrudan S6 avantajımızın hedefidir | 0103 §3 | 🟢 Kesin |
| K2 | TR ajans ekosistemi AIO/GEO söylemini kurmuş durumda; pazar eğitimi maliyeti yok | 0102 §6 | 🟢 Kesin |
| K3 | P3 (ajans personası) 18 kullanım senaryosuyla en geniş ürün yüzeyine sahip — en çok ihtiyaç duyan segment | 0203 §6 | 🟢 Kesin |
| K4 | S6 (çok kiracılı mimari) tasarım taahhüdüdür; ajans özellikleri ek mimari yük değil, mevcut yapının konfigürasyonudur | 0104 §3 | 🟢 Kesin |
| K5 | B2B2B çarpan etkisi: 1 ajans müşterisi → onlarca markaya white-label rapor → dolaylı KOBİ erişimi | 0201 §4 | 🟡 Güçlü |
| K6 | Pilot profili (D-79) ajans ağırlıklı: 6-8 kiracının 3'ü ajans, 2-3'ü KOBİ | 0205 O-3 | 🟢 Kesin |
| K7 | TR'de AI görünürlük hizmeti var, ürün yok; ajanslar elle rapor hazırlıyor — acil ürün boşluğu | 0002 §4 | 🟢 Kesin |
| K8 | White-label rapor ajansın faturalandırabildiği çıktıdır; değer zincirinin kalbi | 0201 §4 | 🟡 Güçlü |

#### KOBİ'nin Birincil Olması Lehinde Kanıt

| # | Kanıt | Kaynak | Güç |
|---|---|---|---|
| K9 | KOBİ en geniş bakir alan: pazarın %92'si planlıyor, yalnız %40,6'sı uyguluyor; PLG için ideal | 0105 §6 | 🟢 Kesin |
| K10 | KOBİ ihtiyacı daha basit: hızlı kurulum, Türkçe arayüz, net aksiyon — MVP'yi karmaşıklaştırmaz | 0201 §4 | 🟢 Kesin |
| K11 | Ajans araçları rekabetçi: Peec AI (sınırsız koltuk) ve Otterly (white-label, MCP) ajans segmentinde güçlü | 0103 §3 | 🟡 Güçlü |

#### Sentez ve Ön Hipotez

```
K1+K2+K7 (rekabet avantajı)
  + K3+K4 (ürün uyumu)
  + K5+K6 (çarpan etkisi + pilot)
  + K8 (değer zinciri)
  = AJANS BİRİNCİL

K9+K10 (pazar büyüklüğü + basitlik)
  ≠ K11'e rağmen (ajans rekabeti var)
  = KOBİ PARALEL İKİNCİL
```

**Ön hipotez:** Ajans (P3) birincil segmenttir; KOBİ (P2) paralel ikincildir. Ajans özellikleri MVP'de farklılaştırıcı, KOBİ özellikleri hacim odağıdır. Bu hipotez §11 görüşmeleriyle test edilecektir. Kritik doğrulama noktası: **satın alma gücü ajansın kendisinde mi, yoksa müşteriye fatura ediyor mu?** (Görüşme sorusu #5)

#### Hipotezin Etkileri

| Alan | Ajans birincil ise | KOBİ birincil ise |
|---|---|---|
| MVP kapsamı (0205) | FR-G1 (çalışma alanı), FR-G2 (panorama) tam kapsam; white-label kritik yol | FR-G1, G2 daraltılabilir; ajans özellikleri hızlı takibe |
| S6 yatırımı | Çok kiracılı mimari erken sertleştirme | Yeterli düzey, ajans özelleştirmeleri sonra |
| Pilot ağırlığı | 3 ajans + 2 KOBİ + 1-2 P4 (mevcut D-79 ile uyumlu) | 2 ajans + 4 KOBİ + 1-2 P4 |
| GTM mesajı | "Ajanslar için AI görünürlük platformu" → KOBİ'ye genişleme | "KOBİ'ler için AI görünürlüğü" → ajans özellikleri sonra |
| Rekabet avantajı | Ajans zayıflığına doğrudan — Profound/Peec farkı | KOBİ boşluğuna — daha geniş TAM, daha çok rakip |

---

## 11. O-3 Doğrulama Planı: Ajans Görüşme Kılavuzu

### Amaç

Yukarıdaki ön hipotezi (ajans birincil) TR ajans ekosisteminde yarı yapılandırılmış görüşmelerle test etmek. Görüşmeler AN tarafından yürütülür; hedef: pilot öncesi en az 5 ajans görüşmesi tamamlanmış olsun. Görüşme bulguları O-3'ü kapatır, 0201 persona kartını (P3) günceller ve 0205 MVP kesitini etkiler.

### Görüşme Soruları

| # | Soru | Ölçtüğü | Hipotez testi                                                                                                                           |
|---|---|---|-----------------------------------------------------------------------------------------------------------------------------------------|
| 1 | Şu anda AI görünürlüğünü müşterilerinize nasıl raporluyorsunuz? (Elle mi, araçla mı? Hangi araç? Ne kadar zaman alıyor?) | Mevcut iş akışı ve ağrı seviyesi | Ağrı yüksekse → ajans talebi güçlü (K7 doğrulaması)                                                                                     |
| 2 | Müşteri başına haftalık AI görünürlük raporu üretebilseydiniz, bunu ayrı bir hizmet kalemi olarak faturalandırabilir misiniz? | White-label'in parasal karşılığı | Evet oranı yüksekse → K5 (B2B2B çarpanı) doğrulanır                                                                                     |
| 3 | Şu an kullandığınız araçlarda en çok eksikliğini hissettiğiniz ajans özelliği nedir? (White-label? Çok müşteri? API? Koltuk?) | S6 öncelik sırası | Hangi özellik MVP'de kritik? 0205 kesitine girdi                                                                                        |
| 4 | KOBİ müşterileriniz AI görünürlüğü hakkında ne kadar bilinçli? Onlar mı talep ediyor, siz mi öneriyorsunuz? | Pazar olgunluğu — KOBİ'nin ajansa bağımlılığı | Ajans talep yaratıcı ise → ajans birincil hipotezi güçlenir (K2 doğrulaması)                                                            |
| 5 | AI görünürlük izleme aracını seçerken karar verici siz misiniz, yoksa müşteriniz mi? Satın alma gücü kimde? | **Karar verici haritası — kritik** | Ajans karar verici ise → satış döngüsü kısa, ajans birincil; müşteri karar verici ise → fiyatlandırma ve satış stratejisi farklı olmalı |

### Ajans Aday Listesi (Canlı Web Araştırması — 22.07.2026)

TR ajans ekosisteminde GEO/AIO hizmeti verdiği tespit edilen 11 ajans, öncelik sırasına göre:

#### 🔴 Dalga 1 · GEO'yu Operasyonel Hale Getirmiş Ajanslar (ilk temas)

| # | Ajans | Merkez | GEO Odağı | Temas Kanıtı |
|---|---|---|---|---|
| 1 | **Sheltron** | İstanbul | Predictive SEO + AI görünürlük değerlendirmesi; ücretsiz AI görünürlük denetimi sunuyor | Dokümanlarda referans (0102 §6, 0103 §2). AVIP'in kendi değer önerisiyle doğrudan örtüşüyor |
| 2 | **Cremicro** | Gaziantep/İstanbul | Çok dilli GEO, cross-border SEO. Google Partner + Semrush partner. Kurucusu Haydar Özkömürcü | Dokümanlarda referans (0102 §6, 0103 §2). Clutch'da somut ciro artışı referansları |
| 3 | **Seobaz** | İstanbul | AI Visibility danışmanlığı. Semrush Türkiye LLM optimization sıralamasında. Kendi AI analiz motoru var | GEO'yu "ölçülebilir operasyon" olarak tanımlıyor — AVIP metodolojisiyle en uyumlu ajans |
| 4 | **Webtures** | İstanbul | Agentic Web Optimization. ISO 9001 belgili ilk SEO ajansı. Kurucusu Kaan Gülten sektör kitaplarıyla referans | "Agentic Web" kavramsal çerçevesi AVIP'in AI-ajan optimizasyonu anlatısıyla örtüşüyor |

#### 🟡 Dalga 2 · GEO Portföyünde Kanıtlı Ajanslar (sonraki temas)

| # | Ajans | Merkez | GEO Odağı | Temas Kanıtı |
|---|---|---|---|---|
| 5 | **Zeo Agency** | İstanbul/Ankara/Londra | Veri bilimi + GEO. Hepsiburada, BMW, Pegasus referansları. 50+ danışman | Kendi aracı (seo.do), Digitalzone konferansı. En büyük ekip — kurumsal perspektif |
| 6 | **Mobitek** | İstanbul | Büyük katalog + GEO adaptasyonu. 20+ yıl, e-ticaret odaklı | Semrush Türkiye listesinde; büyük ölçekli site deneyimi |
| 7 | **Aora Digital** | Ankara/İstanbul | GEO + AEO uyumlu stratejiler. 18 yıllık firma | Semrush Türkiye LLM optimization listesinde. Ankara merkezli — İç Anadolu perspektifi |
| 8 | **Digipeak** | İstanbul/Londra/New York | Çok kanallı GEO (organik+ücretli). Turizm/perakende | TR+EN paralel GTM stratejimizle uyumlu; uluslararası deneyim |

#### 🟢 Dalga 3 · Niş / Takip Edilecek Ajanslar

| # | Ajans | GEO Odağı | Ne Zaman Görüşülmeli |
|---|---|---|---|
| 9 | **Fascinatid** | B2B, SaaS, endüstriyel GEO | P1 kurumsal satış başlayınca |
| 10 | **Adverpeak** | Sağlık turizmi, otomotiv, çok dilli | Pilot sonrası dikey genişleme |
| 11 | **ADWEBX** | GEO danışmanlığı | Yeni oyuncu — ekosistem takibi için |

### Görüşme Takvimi

| Aşama | Süre | Çıktı |
|---|---|---|
| Dalga 1 temas: Sheltron + Cremicro + Seobaz + Webtures'e e-posta/LinkedIn daveti | 3 iş günü | En az 4 davet gönderilmiş |
| Dalga 2 temas: Zeo + Mobitek + Aora + Digipeak'e davet (Dalga 1 yanıt oranı düşükse) | +5 iş günü | En az 4 davet daha |
| Görüşmeleri yürütme (her biri 30-45 dk, online) | 10 iş günü | 5+ tamamlanmış görüşme notu |
| Sentez ve doküman güncelleme | 3 iş günü | O-3 kapanışı, 0201 güncellemesi, 0205 etki analizi |

### Görüşme Dili ve Yaklaşımı

> **Kritik:** Bu ajanslar AVIP'in yalnızca potansiyel müşterisi değil, aynı zamanda potansiyel **iş ortağıdır**. AVIP onların araç setini tamamlayabilir (white-label rapor + çok motorlu ölçüm). Görüşmelerde satış değil, **keşif ve ortaklık dili** kullanılmalıdır.

Önerilen açılış:
> "Türkiye'de GEO hizmeti veren bir ajans olarak süreçlerinizi anlamak istiyoruz. AI görünürlük ölçümünü nasıl yapıyorsunuz, hangi araçları kullanıyorsunuz, en büyük operasyonel zorluklarınız neler? Karşılıklı değer üretebileceğimiz alanları keşfetmek isteriz."

### Başarı Kriteri

- En az 5 ajans görüşmesi tamamlanmış (Dalga 1'den en az 3, Dalga 2'den en az 2)
- Her soru için cevap dağılımı özetlenmiş
- Ön hipotez (ajans birincil) doğrulanmış, reddedilmiş veya koşullu kabul edilmiş
- O-3 kapanmış ve 0007 D-78'e işlenmiş
- 0201 P3 persona kartı güncellenmiş (proto-persona → doğrulanmış persona)
- 0205 MVP kesiti etkileniyorsa değişiklik önerisi hazır
- Ajans listesi canlı araştırmayla doğrulanmış ve kaynak referansı verilmiş (Teknobird + ajans web siteleri)

---

## Kaynaklar

- 0101 GEO Landscape v1.0 · disiplin, motor alıntı davranışları, metrik standardı bulgusu
- 0102 AI Search Landscape v1.0 · erişim kademeleri, fidelite kuralı, TR pazar verileri, maliyet modeli
- 0103 Competitor Analysis v1.0 · segmentler, rakip profilleri, yetenek matrisi, boşluk analizi
- Bu doküman sentezdir; birincil dış kaynaklar ilgili araştırma dokümanlarında listelenmiştir.
- teknobird.com · 2026'da Çalışmanız Gereken En İyi 10 SEO ve GEO Ajansı (14 Nisan 2026) · TR ajans ekosistemi taraması (§11 ajans listesi kaynağı)
- adwebx.com.tr · GEO Yapay Zeka Görünürlük Hizmeti · ADWEBX ajans profili doğrulaması

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 12.07.2026 | İlk yayın: kanıt bağlantılı SWOT (6 güç, 5 zayıflık, 5 fırsat, 5 tehdit), erken uyarı sinyalli tehdit tablosu, dokuz satırlık TOWS eşleştirmesi, dört stratejik sonuç. |
| 1.1 | 18.07.2026 | Çapraz referans düzeltmeleri: konumlandırma atıfları 0204 ürün ilkelerine, GTM atfı 0002 O-1 + 0205 çerçevesine bağlandı; içerik stratejisi set dışı pazarlama çalışması olarak işaretlendi (v1.1 birleşik turu, 0206 O-4). |
| 1.2 | 21.07.2026 | O-1 kapandı: TR+İngilizce paralel GTM. O-2 kapandı: SOC 2 ilk kurumsal müşteriyle. 0007 D-44, D-45. |
| 1.3 | 21.07.2026 | O-3 eylem planı eklendi: bloklayıcı AN sorusu — 0201 görüşmelerinde öncelikli. 0007 D-78. |
| 1.5 | 22.07.2026 | §11 güncellendi: 11 ajanslı aday listesi eklendi (Sheltron, Cremicro, Seobaz, Webtures, Zeo, Mobitek, Aora, Digipeak, Fascinatid, Adverpeak, ADWEBX). 2 dalgalı temas stratejisi ve görüşme dili eklendi. Kaynaklar canlı web araştırmasıyla güncellendi (Teknobird, ADWEBX). 0007 D-78 kapsamı. | |
