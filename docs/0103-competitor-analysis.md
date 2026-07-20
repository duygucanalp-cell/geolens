# 0103 · Competitor Analysis

| Alan | Değer |
|---|---|
| Doküman ID | 0103 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 12 Temmuz 2026 (araştırma tarihi) |
| Karşıladığı madde | 8 · Rakiplerde mobil, 10 · Rakip analizleri (madde 7 bölüm 3/3) |
| İlişkili | 0101, 0102, 0104, 0105, 0205; ADR-002 (D-02) |

---

## 1. Amaç, Kapsam ve Yöntem

Bu doküman AI görünürlük araçları pazarını sistematik olarak değerlendirir: segmentler, öne çıkan oyuncular, yetenek karşılaştırması, ölçüm yöntemi felsefeleri ve mobil kanal durumu. Çıktısı üç karar girdisidir: farklılaşma tezi (0104, 0105), MVP kapsam kalibrasyonu (0205) ve frontend platform kararı (ADR-002, D-02). Fiyat bilgisi bilinçli olarak nitel verilir: Giriş, Orta, Kurumsal kademe; sayısal fiyat bu doküman setinin kapsamı dışındadır.

Yöntem notu: bulgular 12 Temmuz 2026 tarihli canlı web araştırmasına dayanır. Kategori içeriğinin önemli bölümü rakiplerin kendisi veya bağlı ajanslarca yazılmıştır; her iddia mümkün olduğunca birden çok bağımsız kaynakla çaprazlanmış, tek kaynaklı iddialar "raporlanan" ifadesiyle işaretlenmiştir. Kategori hızla değiştiği için üç aylık tazeleme geçerlidir (R-05).

## 2. Pazar Haritası ve Segmentler

| Segment | Tanım ve örnekler | Not |
|---|---|---|
| A · AI-native izleme platformları | Kategori için sıfırdan kurulmuş ürünler: Profound, Peec AI, Otterly.AI, Scrunch, Evertune, Bluefish, AthenaHQ, Goodie, LLMrefs, ZipTie, Rankscale, Knowatoa, Sanbi, Trysight, Lantern, MaxAEO ve büyüyen bir uzun kuyruk. | Rekabetin merkezi; hızlı çoğalma, düşük giriş bariyeri sinyali |
| B · Yerleşik SEO süit modülleri | Semrush AI Toolkit, Ahrefs Brand Radar, SE Ranking (SE Visible), Conductor, BrightEdge, seoClarity, HubSpot AEO Grader. | En geniş kullanıcı tabanı; derinlik sınırlı, mevcut sözleşmeye eklemlenme avantajı |
| C · Dikey ve niş | Mobil uygulama görünürlüğü (AppTweak AI Visibility), alışveriş asistanı görünürlüğü (Amazon Rufus modülleri). | Problem uzayının komşu dilimleri; doğrudan rakip değil |
| D · Hizmet katmanı | GEO/AIO ajansları; Türkiye'de Cremicro, Sheltron gibi oyuncular söylemi kurmuş durumda (0102 §6). | TR'de ürün boşluğu: hizmet var, yerli ürün tespit edilmedi |

Pazar momentumu güçlü: kategori 2025 ortası ile 2026 baharı arasında 300 milyon doları aşkın yatırım çekti; "AI visibility tools" arama hacmi Temmuz 2026 itibarıyla yıllık yaklaşık yüzde 1900 arttı. Bu, H5'in (talep aciliyeti) pazar tarafı kanıtıdır ve 0105'te derinleştirilir.

## 3. Öne Çıkan Rakip Profilleri

| Rakip | Konumlanma | Güçlü yanlar | Zayıf yanlar | Kademe |
|---|---|---|---|---|
| **Profound** | Kurumsal kategori lideri; 700+ kurumsal müşteri, Fortune 500'ün ~onda biri (raporlanan) | 10 motora kadar kapsam (Rufus dahil); gerçek kullanıcı prompt hacmi verisi; ön-yüz yakalama; Cloudflare/Akamai/Fastly entegrasyonları; SOC 2, SSO/SAML | Analist-yoğun; çok-hesap (ajans) yönetimi zayıf raporlanıyor; raporlama aksiyondan önde | Kurumsal |
| **Peec AI** | Orta segment ve ajansların hızlı büyüyen tercihi | Sınırsız koltuk; bölge/dil segmentasyonu; Looker Studio; Pitch Workspaces | Motor kapsamı eklenti arkasında; aksiyon katmanı ve trend derinliği sınırlı; crawler görünürlüğü yok | Orta |
| **Otterly.AI** | En erişilebilir giriş noktası; ajans dostu | Anahtar kelimeden prompt üretimi; GEO denetimi ve içerik önerileri; white-label; MCP ve API; güçlü G2 görünürlüğü | Düşük kademede dar prompt kotası; bazı motorlar ek ücretli | Giriş-Orta |
| **Scrunch** | Orta segment; rekabet istihbaratı ve bot davranışı | Persona ve huni modelleme; gerçek zamanlı bot izleme; Agent Experience katmanı; SOC 2 Type II | Coğrafi segmentasyon sığ; teknik denetim ayrı araç istiyor | Orta-Kurumsal |
| **Evertune** | "İstatistiksel titizlik" iddialı kurumsal oyuncu | Metodoloji vurgusu; kurumsal odak | Kapsam ve şeffaflık detayı kamuya sınırlı; derin inceleme gerekli (O-2) | Kurumsal |
| **Yerleşik süitler** | Semrush, Ahrefs, SE Ranking modülleri | Mevcut sözleşme ve iş akışına eklemlenme; geniş taban | Kategori derinliği sığ; ölçüm metodolojisi ikincil | Mevcut aboneliğe bağlı |

## 4. Yetenek Karşılaştırma Matrisi

| Yetenek | Profound | Peec AI | Otterly | Scrunch | Yerleşik süitler |
|---|---|---|---|---|---|
| Motor kapsamı genişliği | Çok geniş | Orta (eklentili) | Orta (eklentili) | Orta | Dar-Orta |
| Ölçüm yöntemi | Ön-yüz yakalama ağırlıklı | Belirsiz | Belirsiz | Belirsiz | Belirsiz |
| Örnekleme / istatistik şeffaflığı | Kısmi | Yok | Yok | Kısmi | Yok |
| Fidelite etiketi (yüzey ayrımı) | Yok | Yok | Yok | Yok | Yok |
| Alıntı / kaynak analizi | Derin | Var | Var | Var | Kısmi |
| Aksiyon / öneri katmanı | Kısmi (CDN entegrasyonlu) | Zayıf | Var (denetim + öneri) | Var | Zayıf |
| Kurumsal: SOC 2, SSO, tarihçe | Var | Kısmi | Kısmi | Var (SOC 2 T2) | Var (süit düzeyi) |
| Ajans / çok-hesap | Zayıf (raporlanan) | Güçlü | Güçlü (white-label) | Partner programı | Var |
| Entegrasyon: Looker, API, MCP | API | Looker + API (beta) | Looker + API + MCP | API/Zapier | Süit içi |
| Mobil istemci uygulaması | Yok* | Yok* | Yok* | Yok* | Kısmi (süit uygulamaları) |
| TR pazarı / Türkçe odak | Yok | Dil desteği geniş | 50+ ülke | Yok | Kısmi |

\* 12.07.2026 taramasında tespit edilemedi; §6'daki araştırma sınırı notuyla birlikte okunmalı. "Belirsiz": kamuya açık metodoloji beyanı bulunamadı.

## 5. Ölçüm Yöntemi Ayrışması

Kategoride iki ölçüm felsefesi ayrışıyor. Birincisi ön-yüz yakalama: gerçek kullanıcı davranışını taklit ederek tüketici arayüzünden yanıt toplama (Profound ve ZipTie bu yöntemle anılıyor). Yüzey sadakati yüksektir; karşılığında kırılganlık (arayüz değişince kırılma) ve motor kullanım şartları açısından gri alan taşır. İkincisi resmî API ölçümü: sürdürülebilir ve izinlidir; karşılığında tüketici yüzeyinin vekilidir (0102 §4). Kategorinin geneli hangi yöntemi kullandığını kamuya net beyan etmiyor.

İkinci yapısal bulgu güvenilirlikle ilgili: bağımsız değerlendirmeler, aynı prompt setinde platformlar arası kapsam ve doğruluğun "pazarlamanın ima ettiğinden çok daha fazla" değiştiğini, farklılaşmanın çoğunlukla arayüz ve kapsamda olup ölçüm kalitesinde olmadığını not ediyor; motorların doğal değişkenliği de kabul edilen bir belirsizlik. İstatistiksel titizliği açık konumlanma yapan tek oyuncu Evertune'dur. Sonuç: H3'ün hedeflediği "ölçümün ölçüm kalitesi" alanı kategoride büyük ölçüde boştur; fidelite etiketi ise hiçbir oyuncuda yoktur.

## 6. Mobil Kanal Analizi (madde 8; ADR-002 girdisi)

Bulgu: incelenen hiçbir AI-native izleme platformunda tüketiciye sunulan bir iOS/Android istemci uygulaması tespit edilmedi; kategori bütünüyle web panosu üzerinden çalışıyor. Pazarda "mobil" başlığı altında var olan tek belirgin oyuncu farklı bir problemi çözüyor: AppTweak AI Visibility, mobil uygulamaların AI yanıtlarındaki görünürlüğünü ölçüyor (uygulama mağazası istihbaratından türetilmiş metodolojiyle); yani mobil, ürün kanalı değil ölçüm konusu. Araştırma sınırı: yokluk kanıtı kesin değildir; uygulama mağazası taraması yapılmamıştır ve tazeleme döngüsünde teyit edilecektir.

Kullanım deseni de bulguyu destekler: ürün kategorisi analist ve pazarlama ekiplerinin haftalık masaüstü iş akışına gömülüdür; mobil ihtiyaç, panoyu cepte çalıştırmak değil, kritik değişimde haberdar olmak ve özeti hızla görmektir.

> D-02 önerisi (karar ADR-002'de): V1 frontend'i web-first pano olarak kurgulanır; mobil ihtiyaç V1'de duyarlı (responsive) web + e-posta/Slack uyarılarıyla karşılanır. Yerel mobil uygulama ("yönetici cep özeti" senaryosu) 0206 yol haritası adayıdır. Teknoloji seçimi (Flutter Web dahil alternatifler) ADR-002'de gerekçelendirilir; kategori normali tek başına belirleyici değildir, veri-yoğun tablo/grafik panosunun gereksinimleri esas alınır.

## 7. Boşluk Analizi ve Farklılaşma

| Boşluk | Kategori durumu | AVIP karşılığı |
|---|---|---|
| Ölçüm güvenilirliği ve istatistik | Örnekleme ve güven aralığı beyanı yaygın değil; platformlar arası tutarsızlık bilinen sorun | Örneklemli ölçüm, güven aralığı, M5-M6 sert kuralları (H3) |
| Fidelite şeffaflığı | Hiçbir oyuncu yüzey ayrımını (UI vekili mi, doğrudan mı) kullanıcıya etiketlemiyor | Kademe etiketi zorunlu (0102 kuralı); dürüstlük konumlanması |
| Açıklanabilirlik ve denetlenebilirlik | Skorun nasıl hesaplandığı kara kutu; calculation_run eşdeğeri tespit edilmedi | calculation_run_id, faktör anlık görüntüsü, şablon versiyonu (G2) |
| Aksiyon katmanı | Kategorinin evrensel eleştirisi: "hepsi pano; ölçer, düzeltmez" | Kanıt dereceli öneri motoru + geri besleme döngüsü (G4, M4); vaat disiplini NG8-NG10 |
| TR pazarı | Küresel araçlarda TR odağı yok; TR'de hizmet var, yerli ürün tespit edilmedi | TR-öncelikli prompt setleri, Türkçe arayüz ve rapor; 0102 §6 motor önceliği |

Masa bahisleri (farklılaşma değil, giriş şartı): çok-motor kapsam, günlük zamanlanmış izleme, görünürlük payı kıyası, Looker/BI ve API entegrasyonu, ajans çok-hesap modeli, SOC 2 yolu ve en az 12 aylık tarihçe hedefi. Bunlar 0205 kapsam kalibrasyonuna girer.

## 8. SWOT Girdileri (0104 köprüsü)

Güçlü yan tohumları: açıklanabilirlik mimarisi, fidelite dürüstlüğü, TR pazarı yakınlığı, düşük maliyetli panel modeli (0102 §5). Zayıf yan tohumları: geç giriş, marka bilinirliği sıfır, tarihçe verisi yok, tek ekip kapasitesi. Fırsat tohumları: kategori momentumu, TR ürün boşluğu, metrik standardı boşluğu (G9), yerleşiklerin sığlığı. Tehdit tohumları: Profound'un ölçeklenmesi, yerleşik süitlerin paketleme gücü, motorların birinci taraf analitik sunma ihtimali, erişim koşullarının değişmesi (R-04). Değerlendirme 0104'te yapılır.

## 9. AVIP için Çıkarımlar

1. Farklılaşma tezi netleşti: "güvenilir ve açıklanabilir ölçüm + dürüst fidelite + kanıt dereceli aksiyon", kategorinin üç kör noktasına birden oturur (0104, 0105, 0202 konumlandırma girdisi).
2. Masa bahisleri listesi 0205 MVP kalibrasyonuna alınır; eksik masa bahisi farklılaşmayı geçersizleştirir.
3. Ölçüm yöntemi kararımız (resmî API + etiket) rakip normuna karşı bilinçli bir tercihtir; pazarlama dili bunu kusur değil ilke olarak anlatır (0202).
4. Web-first frontend önerisi ADR-002'ye taşınır; mobil V1'de responsive + uyarı kanallarıyla çözülür (D-02).
5. Ajans/çok-hesap modeli kategori zayıflığıdır (lider dahil); çok kiracılı mimarimiz (G7) bunu erken avantaja çevirebilir; 0201 personalarına "ajans" adayı eklenir.
6. Entegrasyon asgarisi: rapor dışa aktarımı, BI bağlantısı ve API; MCP arayüzü değerlendirmesi 0308'e not edilir.

## 10. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| O-1 | Ön-yüz yakalama yönteminin hukuki ve platform-politikası seyri | PY takibi; rakip riski bizim konumumuzu güçlendirir, pazar normalleşirse baskı yaratır (R-04). |
| O-2 | Evertune metodolojisinin derin incelemesi | H3 alanındaki tek doğrudan rakip iddiası; demo/dokümantasyon üzerinden AN incelemesi. |
| O-3 | Mobil bildirim kanalı önceliği (e-posta, Slack, push) | 0201 persona görüşmelerinde doğrulanacak; D-02 uygulamasını etkiler. |

---

## Kaynaklar

- bushnote.com · 2026 araç değerlendirmesi: Profound finansman/müşteri verileri, motor kapsamı, çapraz-araç tutarsızlık gözlemi
- surmado.com · Kategori finansman özeti ($300M+), segment kısa listesi, aksiyon katmanı eleştirisi
- justinmckelvey.com · Temmuz 2026 doğrulamalı karşılaştırma; arama hacmi +%1900 YoY; "hepsi pano" tespiti
- discoveredlabs.com · Profound/Peec/Otterly ve SaaS değerlendirmeleri; çok-hesap ve koltuk politikaları (raporlanan)
- blog.timsoulo.com · 14 alternatif karşılaştırması: motor eklenti politikaları, ajans programları
- sanbi.ai · Dört kademeli pazar tipolojisi; Profound/Peec/Scrunch sınırlılıkları
- zapier.com · Ön-yüz yakalama yöntemi notu (Profound, ZipTie); değişkenlik uyarısı; araç profilleri
- maxaeo.ai · 9 Temmuz 2026 tarihli vendor sayfası taraması: Profound ön-yüz motor listesi, Otterly MCP/API
- thepuffer.fish · Kategori iş tipolojisi; Profound Series C ve müşteri örnekleri
- stackmatix.com · Kurumsal uyum karşılaştırması: SOC 2, 12+ ay tarihçe
- ailedgrowth.com · Scrunch SOC 2 Type II ve finansman; Otterly kademe yapısı; AI Overviews %13 görünüm ve CTR etkisi
- seoptimer.com · Profound kurumsal plan kapsamı (SSO/SAML, SOC2); Rankscale kredi modeli
- averi.ai · "Farklılaşma çoğunlukla arayüz ve kapsamda, ölçüm kalitesinde değil" tespiti
- apptweak.com · Mobil açının gerçek durumu: uygulama görünürlüğü ölçümü; web-tabanlı araçların yapısal sınırı
- otterly.ai · Ürün sayfası beyanları (kapsam, kullanıcı tabanı)

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 12.07.2026 | İlk yayın: dört segmentli pazar haritası, altı rakip profili, on bir satırlık yetenek matrisi, ölçüm yöntemi ayrışması, mobil kanal bulgusu ve D-02 önerisi, beş boşluklu farklılaşma tezi, SWOT tohumları. |
