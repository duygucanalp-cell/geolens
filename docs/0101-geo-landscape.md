# 0101 · GEO Landscape

| Alan | Değer |
|---|---|
| Doküman ID | 0101 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.1 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 12 Temmuz 2026 (araştırma tarihi) |
| Karşıladığı madde | 7 · Market search (bölüm 1/3) |
| İlişkili | 0002 (H2–H5), 0102, 0103, 0309 |

---

## 1. Amaç ve Kapsam

Bu doküman GEO (Generative Engine Optimization) ekosisteminin bugünkü haritasını çıkarır: disiplinin kökeni ve olgunluğu, kullanıcı davranışındaki değişimin ölçeği, motorların alıntı davranışları, bilinen görünürlük faktörleri ve erişim standartları. Amaç ürün kararlarına kanıt tabanı sağlamaktır; rakip ürünlerin değerlendirmesi 0103'te, motor bazlı derin teknik analiz 0102'dedir. Alan hızla değiştiği için doküman üç ayda bir tazelenir (0007 §6, R-05).

## 2. Yöntem ve Kaynak Notu

Bulgular 12 Temmuz 2026 tarihli canlı web araştırmasına dayanır. Kaynak öncelik sırası: akademik yayın, büyük ölçekli platform çalışmaları (milyonlarca alıntı üzerinden), resmî motor dokümantasyonu, sektör analizleri. Üç temkin notu geçerlidir. Birincisi, alan gençtir ve çalışmaların metodolojileri farklıdır; sayılar nokta değil aralık olarak okunmalıdır. İkincisi, GEO içeriğinin önemli bölümü GEO hizmeti satan taraflarca üretilmektedir; çıkar beyanı gözetilmiştir. Üçüncüsü, aynı kavram üç farklı paydayla raporlanmaktadır: toplam alıntı payı, ilk-10 kaynak içi pay ve yanıt-başına görülme oranı. Bu doküman her sayıyı paydasıyla birlikte verir.

> Yapısal bulgu: sektör, "alıntı payı" gibi temel bir kavramı bile ortak paydayla ölçemiyor. Standart metrik yokluğu 0002 P1'i doğrular ve G9 (kategori referansı olma) hedefinin fırsat alanını tanımlar.

## 3. Disiplinin Doğuşu ve Terim Ailesi

GEO terimi akademik kökenlidir: Aggarwal ve arkadaşlarının KDD 2024'te yayımlanan "GEO: Generative Engine Optimization" makalesi, içerik müdahalelerinin üretken motor yanıtlarındaki kaynak görünürlüğünü yüzde 40'a varan oranda artırabildiğini deneysel olarak göstermiştir. Terim ailesi pratikte iç içe kullanılır: GEO, AEO (Answer Engine Optimization) ve LLM görünürlüğü. Google'ın resmî pozisyonu ise ayrışmayı reddeder: 2026 Search Central dokümantasyonuna göre üretken AI araması için optimizasyon "hâlâ SEO"dur.

Olgunlaşma sinyalleri belirgindir: GEO ve AI görünürlüğü geçen iş ilanlarında 2025-2026'da yüksek oranlı artış, ajansların GEO hizmet paketleri açması, eğitim kurumlarının müfredat eklemesi ve izleme araçlarından oluşan yeni bir ürün kategorisinin doğması (0103'ün konusu). Disiplin, "10 mavi bağlantıda sıralanmak" yerine "yanıt başına tipik olarak alıntılanan 2-7 alan adından biri olmak" problemine odaklanır.

## 4. Davranış Değişimi: Ölçek ve Karşı Denge

| Bulgu | Kaynak | Yorum |
|---|---|---|
| Geleneksel arama hacminde 2026'ya kadar yüzde 25 düşüş projeksiyonu; AI asistanlarının bu yıl aramaların yaklaşık dörtte birini, 2028'de yarısından fazlasını karşılayacağı öngörüsü. | Gartner (sektör kaynaklarınca aktarım) | Projeksiyon; gerçekleşme izlenmeli |
| ABD'de Google aramalarının yaklaşık yüzde 58-60'ı tıklamasız sonuçlanıyor; AI Overviews varlığında organik tıklama oranında yüzde 61'e varan düşüş raporlanıyor. | Sektör çalışmaları (Seer Interactive ve derlemeler) | Tıklamasız etki P2'yi doğrular |
| Karşı denge: geleneksel organik arama, 2025 sonu itibarıyla ChatGPT + Gemini + Perplexity toplamından yaklaşık 345 kat fazla trafik gönderiyor. | Ahrefs | Organik kanal ölü değil; geçiş erken evrede |
| AI kaynaklı bot trafiği hızla büyüyor: GPTBot trafiği yıllık yüzde 147 artış; bir analizde AI bot trafiği Ocak 2025 - Mart 2026 arasında yüzde 300'ün üzerinde artış. | Cloudflare verisi aktarımları; seoscore.tools log analizi | Arz tarafı da büyüyor |

Dürüst okuma: davranış değişimi gerçek ve hızlı, ancak hacim bugün hâlâ klasik aramada. Kategorinin değeri "trafik ikamesi"nden önce "karar etkisi"nden gelir: AI yanıtına giren az sayıda marka, tıklama olmadan tercih şekillendirir. Bu, H5 (talep aciliyeti) için olumlu bir sinyaldir; sayısal pazar kanıtı 0105'te derinleştirilir.

## 5. Motor Alıntı Davranışları (H2 ve H4 ön kanıtı)

Büyük ölçekli alıntı çalışmaları motorlar arasında keskin profil farkları gösteriyor:

| Motor | Baskın kaynak profili | Örnek veri (payda belirtilmiş) |
|---|---|---|
| ChatGPT | Wikipedia ağırlıklı; Reddit, editoryal siteler, LinkedIn yükselişte | İlk-10 alıntı payında Wikipedia ~%47.9 (ZipTie); toplam ABD alıntılarında Wikipedia %13.15 + Reddit %11.97 (Similarweb/5W, Q1 2026) |
| Perplexity | Reddit yoğunluklu; topluluk içeriği | İlk-10 alıntı payında Reddit ~%46.7 (Profound) |
| Google AI Overviews / AI Mode | YouTube ve Google ekosistemi; dağıtık profil | YouTube ilk-10 payında ~%23.3; alıntıların ~%43'ü Google mülklerine (derlemeler) |
| Claude | Kurumsal ve uzun biçimli kaynaklar, bloglar; düşük sosyal pay | İlk-10 payında blog türü ~%43.8; sağlık alıntılarının ~%97.8'i kurumsal kaynaklardan (derlemeler) |

İki bulgu ürün açısından belirleyicidir. Birincisi, kesişim düşüklüğü: aynı sorgu için ChatGPT ve Perplexity'nin alıntıladığı alan adlarının yalnızca yüzde 11'i ortaktır; tüm alıntı kaynaklarının yüzde 71'i tek platformda görülür (ZipTie). Bu, H2'nin ("tek motor izlemek yetmez") güçlü ön kanıtıdır. İkincisi, oynaklık: Semrush'ın 13 haftalık, 230 bin promptluk izlemesinde ChatGPT'nin Reddit'i alıntılama oranı Eylül 2025'te iki hafta içinde yanıtların yaklaşık yüzde 60'ından yüzde 10'una düşmüştür. Motor politikası tek hamlede görünürlük dağılımını değiştirebilmektedir; bu, sürekli izleme (G6) ve örnekleme (H3) gerekçesini pekiştirir.

Karşı bulgu notu: Yext'in 6.8 milyon alıntılık analizi, alıntıların yüzde 86'sının marka kontrolündeki kaynaklardan geldiğini raporlar. Fark büyük olasılıkla sorgu evreni ve sınıflandırma metodolojisinden kaynaklanır (markalı sorgular ile kategori sorguları farklı davranır); 0309 prompt seti tasarımı bu ayrımı açıkça modellemelidir.

## 6. Bilinen Görünürlük Faktörleri

Faktörler kanıt derecesiyle sunulur: Deneysel (kontrollü çalışma), Korelasyonel (büyük veri gözlemi), Uygulayıcı (yaygın pratik, kontrollü kanıt yok).

| Faktör | Özet | Kanıt | Kaynak |
|---|---|---|---|
| Alıntılanabilir yapı | İstatistik, kaynaklı alıntı ve net iddialar eklemek görünürlüğü yüzde 40'a kadar artırıyor; pasaj düzeyinde çıkarılabilirlik (kısa, olgusal ifadeler, soru-önce yapı) belirleyici. | Deneysel | KDD'24 GEO makalesi |
| Üçüncü taraf platform varlığı | Reddit, YouTube, LinkedIn ve Wikipedia'daki varlık alıntılanmayla güçlü ilişkili; YouTube varlığı AI görünürlüğüyle 0.737 korelasyon (75 bin marka analizi). | Korelasyonel | Ahrefs; Profound; Semrush |
| Otorite ve tazelik sinyalleri | Adlandırılmış yazar, birincil veri, güncel tarih; ilk-sayfa-dışı kaynaklar da alıntılanıyor (Perplexity alıntılarının ~%67'si Google ilk sayfası dışından). | Korelasyonel | Sektör çalışmaları |
| Yapılandırılmış veri (schema) | Varlık ve ilişki netliği; yaygın öneri, bağımsız kontrollü kanıt sınırlı. | Uygulayıcı | Sektör pratiği |
| Teknik erişim | AI botlarına erişim izni ön koşul: sitelerin ~%18.7'si GPTBot'u engelliyor ve bir kısmı bunu kazara yapıyor; SSR ve temiz HTML botların işine yarıyor. | Korelasyonel | seoscore.tools; Goodie |

## 7. Standartlar ve Erişim Katmanı

Motor tarafı erişimi üç işlev tipine ayrılmış durumda; robots.txt (RFC 9309) gönüllü bir protokoldür ve uygulama bot bazında değişir:

| İşlev | Örnek user-agent | Not |
|---|---|---|
| Model eğitimi | GPTBot, ClaudeBot, Google-Extended, Meta-ExternalAgent | Engellenmesi eğitimden çıkarır; yanıt görünürlüğünü doğrudan belirlemez |
| Arama dizinleme | OAI-SearchBot, Claude-SearchBot, PerplexityBot | Engellenmesi ilgili motorun yanıtlarından düşürür |
| Kullanıcı adına getirme | ChatGPT-User, Claude-User | Kullanıcının verdiği URL'yi anlık okur |

llms.txt gerçekçi durum tespiti: benimseme yaklaşık yüzde 10 (SE Ranking, 300 bin alan adı) ve teknik sitelerde yoğunlaşıyor; Google Search resmî olarak kullanmadığını açıkladı (Haziran 2026 dokümantasyonu), buna karşın Chrome Lighthouse 13.3 dosyayı "Agentic Browsing" kategorisinde denetliyor. Bot günlükleri fiili ilgiyi düşük gösteriyor: 500 milyonu aşkın AI bot olayında yaklaşık 408 llms.txt isteği (Limy); 62 bin ziyarette 84 istek (Otterly). Bugünkü gerçek kullanım alanı, kodlama ajanları ve agentic tarayıcılar için yönlendirme dosyası olmasıdır; alıntı artışı üzerinde ölçülebilir etki bulunamamıştır. Erişim ekonomisi de şekilleniyor: Cloudflare'in yönetilen AI bot listesi ve pay-per-crawl modeli, engelle/izin ver ikiliğinin ötesine geçiyor.

## 8. AVIP için Çıkarımlar

1. Motor bazlı ayrık ölçüm zorunludur: yüzde 11'lik kesişim, tek motorlu izlemeyi geçersiz kılar (H2 ön doğrulaması; 0308 çok motor mimarisi).
2. Kaynak tipolojisi sınıflandırıcısı (topluluk, ansiklopedi, video, haber, marka mülkü) skor ve öneri motorunun çekirdek girdisidir (0309; H4).
3. Platform düzeyi oynaklık gerçek: zaman serisi, kırılma tespiti ve uyarı (G6) ürünün ayırt edici değeridir; tek sorguluk anlık görüntü yanıltır (H3, M5).
4. Prompt seti tasarımı markalı ve kategori sorgularını ayrı modellemelidir; aksi halde Yext tipi ve Profound tipi bulgular arasındaki fark ürün içinde tekrarlanır.
5. Faktör kataloğu kanıt dereceli tutulmalıdır: öneri motoru deneysel ve korelasyonel kanıtı önceler, uygulayıcı pratiklerini "denenebilir" etiketiyle sunar (NG10 uyumu).
6. Site erişim denetimi (bot izinleri, SSR, schema) düşük maliyetli ve yüksek değerli bir denetim modülü adayıdır; kazara GPTBot engeli yaygın bir hızlı kazanımdır (0205 aday özellik).
7. llms.txt denetimde raporlanır ancak etki vaadi verilmez; kanıt yokluğu açıkça söylenir (şeffaflık ilkesi).

## 9. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| O-1 | TR pazarında motor kullanım dağılımı ve TR içerik alıntı davranışı | **AN eylem planı (21.07.2026):** Masabaşı araştırma (Similarweb TR, yerel kaynaklar) pilot öncesi. AN yürütür, bulgular 0007 haftalık senkronunda raporlanır. 0007 D-78. 0102/0105'te TR sinyali aranacak. |
| O-2 | Bağlam (sentiment) sınıflandırmasının müşteri kararlarındaki ağırlığı | **AN eylem planı (21.07.2026):** 0201 görüşme kılavuzuna madde eklendi. Pilot öncesi tamamlanır. AN yürütür. 0007 D-78. 0005 O-2 ile bağlantılı. |

---

## Kaynaklar

- Aggarwal ve ark., "GEO: Generative Engine Optimization", KDD 2024
- searchengineland.com · GEO 2026 rehberi ve 30M kaynaklı alıntı çalışması haberi
- tryprofound.com · AI Platform Citation Patterns (30M alıntı, 2024-2025)
- semrush.com · Most-Cited Domains in AI (13 hafta, 230 bin prompt)
- ziptie.dev · Platformlar arası alıntı kesişimi analizi
- prnewswire.com · 5W Citation Source Audit Q1 2026 (9 veri seti sentezi)
- citeflow.io · Reddit/Wikipedia alıntı yoğunlaşması derlemesi
- aithinkerlab.com · GEO 2026, Google Search Central pozisyonu ve Ahrefs 345× verisi
- llmpulse.ai · Gartner projeksiyonları aktarımı ve beş yüzey çerçevesi
- geoptie.com · Zero-click ve AI Overviews CTR derlemesi
- baselinelabs.ai · llms.txt ve Google Search Central (Haziran 2026)
- limy.ai · llms.txt 500M+ bot olayı log analizi
- derivatex.agency · SE Ranking 300 bin alan adı benimseme çalışması ve Otterly bulgusu
- glasp.co · AI crawler taksonomisi, Cloudflare verileri
- elementera.com · OpenAI ve Anthropic resmî crawler dokümantasyonu aktarımı; Yext 6.8M alıntı
- seoscore.tools · GPTBot engelleme oranı ve bot trafiği analizi
- higoodie.com · Zero-click 2026 ve crawler pratikleri

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 12.07.2026 | İlk yayın: canlı araştırmayla disiplin haritası, payda-açık alıntı verileri, H2/H3/H4 ön kanıtları, kanıt dereceli faktör kataloğu, bot taksonomisi ve llms.txt durum tespiti, 7 ürün çıkarımı. |
| 1.1 | 21.07.2026 | O-1/O-2 eylem planı eklendi: TR verisi masabaşı araştırma + görüşmeler pilot öncesi. 0007 D-78. |
