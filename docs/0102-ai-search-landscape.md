# 0102 · AI Search Landscape

| Alan | Değer |
|---|---|
| Doküman ID | 0102 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 12 Temmuz 2026 (araştırma tarihi) |
| Karşıladığı madde | 7 · Market search (bölüm 2/3) |
| İlişkili | 0101, 0103, 0205 (O-2), 0308, 0309; R-03, R-04 |

---

## 1. Amaç ve Kapsam

Bu doküman, ölçüm hedefi olan AI motorlarını tek tek inceler: yanıt üretme mimarileri, programatik erişim yolları, kullanım şartı sınırları, ölçüm sadakati (fidelite) ve maliyet profili. Çıktısı iki karar girdisidir: MVP motor kapsamı (0205 O-2) ve connector mimarisi (0308). Türkiye pazarı sinyalleri, 0101 O-1 açık sorusunu kapatır.

## 2. Motor Mimari Tipolojisi

Motorlar iki eksende ayrışır. Birinci eksen yanıtın bilgi kaynağıdır: parametrik bilgi (modelin eğitim verisi), canlı arama temellendirmesi (grounding) ve ikisinin hibriti. İkinci eksen ürün karakteridir: alıntı-öncelikli motorlar (her cümleyi kaynağa bağlamak üzere tasarlanmış, örnek Perplexity) ve sohbet-öncelikli motorlar (alıntı, gerektiğinde eklenen bir katman; örnek ChatGPT, Gemini, Claude uygulamaları). Bu ayrım ölçümü doğrudan etkiler: alıntı-öncelikli motorlarda kaynak verisi yapısal ve zengindir; sohbet-öncelikli motorlarda mention ve öneri sinyali güçlü, alıntı sinyali değişkendir. 0101 §5'teki profil farklarının mimari kökeni budur.

## 3. Motor Profilleri ve Erişim Matrisi (12 Temmuz 2026 itibarıyla)

| Motor | Resmî programatik yol | Alıntı verisi | Güven kademesi | Not |
|---|---|---|---|---|
| **Perplexity** | Sonar API (resmî, genel erişim) | Yapısal: citations dizisi + kaynak metaverisi her yanıtta | Kademe 1 | Alıntı-doğal tek genel API; ölçüm için en olgun yüzey |
| **ChatGPT** | OpenAI Responses API, web_search aracı | url_citation anotasyonları (konum indeksli) + sources listesi | Kademe 2 | Tüketici uygulamasının kendisi değil, en yakın resmî vekili; araç çağrısı ayrı ücretlendirilir |
| **Gemini** | Gemini API, Google Search grounding | Temellendirme alıntıları | Kademe 2 | Tüketici Gemini ve AI Mode'dan ayrı yüzey; etiketli vekil |
| **Claude** | Anthropic API, web search aracı | Alıntı blokları; Claude-SearchBot dizini | Kademe 2 | Kurumsal kaynak ağırlıklı profil (0101 §5) |
| **Grok** | xAI API, canlı arama | Kaynak referansları | Kademe 2 | Kurumsal veri işleme şartları incelenecek (O-2) |
| **Google AI Overviews / AI Mode** | Resmî API yok | · | Kademe 3 | Programatik erişime kapalı; üçüncü taraf SERP kazıma ToS açısından gri; ticari araçlar da aynı duvarla karşı karşıya |
| **Copilot** | Ölçüme açık resmî yanıt API'si yok | · | Kademe 3 | V1 kapsam dışı önerisi |

Güven kademeleri: Kademe 1 doğrudan ölçüm, Kademe 2 resmî vekil (proxy), Kademe 3 yönsel (directional). Kademe tanımları §4'te.

## 4. Ölçüm Fidelite Sorunu

Kritik dürüstlük ilkesi: API üzerinden ölçülen yanıt, kullanıcının tüketici uygulamasında gördüğü yanıtın kendisi değildir. Responses API'nin web_search aracı, ChatGPT uygulamasının enstrümante edilmiş bir okuması değil, aynı arama temellendirmesinin resmî vekilidir; "ChatGPT bizi öneriyor mu" sorusuna yakın bir soruya cevap verir ve raporda bu sözcüklerle söylenmelidir. Farkı büyüten etkenler: oturum kişiselleştirmesi, konum bazlı yanıt farklılaşması ve uygulama katmanındaki ürün davranışları.

> Ürün kuralı: her motor ölçümü, kullanıcıya görünür bir fidelite etiketi taşır. Kademe 1: doğrudan. Kademe 2: resmî vekil. Kademe 3: yönsel. Etiketsiz skor yayınlanmaz; bu kural 0309 metodolojisine ve arayüz diline (0005 sözlük) girer.

Kademe 2 vekillerin tüketici yüzeyiyle korelasyonu varsayılmaz, ölçülür: 0309 pilotunda aynı prompt seti API ve kontrollü manuel UI örneklemiyle karşılaştırılır (O-1). Ayrıca yanıtlar coğrafi kişiselleştirme taşıdığından, ölçüm bölgesi (TR ve hedef pazarlar) ölçüm tanımının parçasıdır.

## 5. Erişim, Uyum ve Maliyet

Uyum çizgisi (NG9, R-04): yalnızca resmî API'ler ve izinli erişim; Kademe 3 yüzeyler için tüketici arayüzü kazıma yapılmaz. Google yüzeyi için üç seçenek vardır ve karar D-03 olarak kaydedilir: (a) kapsam dışı bırakmak, (b) Gemini grounding'i açıkça etiketlenmiş vekil olarak kullanmak, (c) lisanslı üçüncü taraf veri sağlayıcısını hukuki incelemeyle değerlendirmek. Öneri: MVP'de (b), paralelde (c) incelemesi. OpenAI'nin uyum şartı ürün tasarımına girer: web araması sonuçları son kullanıcıya gösterilirken satır içi alıntılar görünür ve tıklanabilir olmalıdır; pano tasarımı (0204) bu şartla uyumlu kurgulanır.

Maliyet profili (K1, R-03): motor API'leri token ücretine ek istek başına arama ücreti alır; Perplexity'de bu bin istek başına arama bağlamı boyutuna göre kademelidir, OpenAI'de web_search aracı ayrı kalemdir. Sektörde yayımlanmış bir maliyet modeli, 150 promptluk haftalık panelin kaynaklı API harcamasını düşük tek haneli dolar/hafta mertebesinde göstermektedir (Temmuz 2026 fiyatlarıyla; panel ve örnekleme büyüdükçe doğrusal ölçeklenir). Sonuç: maliyet, panel boyutu × motor sayısı × örnekleme n ile öngörülebilir biçimde modellenir; K1 koruması bu üç kolu izler.

## 6. Türkiye Pazarı Sinyalleri

| Bulgu | Kaynak | Yorum |
|---|---|---|
| Türkiye, AI kaynaklı web trafiğinde ChatGPT payı yüzde 94.49 ile dünya birincisi; küresel ortalama yüzde 80.92 (Ekim 2025 verisi). | We Are Social / Meltwater, Digital 2026 Global Overview | TR'de tek motor baskınlığı olağanüstü |
| Statcounter 2025 kullanım verilerinde ChatGPT Türkiye'de ezici pazar lideri; ikinci sırada Gemini (yüzde 4.88). Küresel ikinci ise Perplexity; Perplexity'nin TR payı düşük. | Statcounter aktarımı | TR motor önceliği: ChatGPT, sonra Gemini |
| TR'de yerel ajanslar AIO/GEO hizmet söylemini kurmuş durumda; saha gözlemleri işletmelerin AI asistan yanıtlarında büyük ölçüde görünmediğini raporluyor. | TR sektör kaynakları (Cremicro, Sheltron açıklamaları) | H5 için TR talep sinyali; 0103'e rakip/ekosistem girdisi |

Yorum: TR pazarında iş önceliği ile ölçüm olgunluğu ters yönlüdür. Müşterinin en çok önemsediği yüzey (ChatGPT) Kademe 2'den ölçülürken, en olgun ölçüm API'sine sahip motor (Perplexity) TR'de görece küçüktür. Ürün iletişimi bu gerilimi fidelite etiketiyle dürüstçe taşır; Perplexity ayrıca metodoloji doğrulama katmanı olarak değer üretir (alıntı-zengin referans yüzey).

## 7. MVP Motor Kapsamı Önerisi (0205 O-2 girdisi; karar 0205'te)

| Motor | TR önemi | Küresel önem | Erişim olgunluğu | Kademe | Öneri |
|---|---|---|---|---|---|
| ChatGPT | Çok yüksek | Çok yüksek | Yüksek (Responses) | 2 | **MVP çekirdek** |
| Gemini | Yüksek | Yüksek | Yüksek (grounding) | 2 | **MVP çekirdek** |
| Perplexity | Düşük | Orta | Çok yüksek (Sonar) | 1 | **MVP çekirdek** (referans yüzey) |
| Claude | Düşük | Orta | Yüksek | 2 | İkinci halka (hızlı ekleme) |
| Grok | Düşük | Orta | Orta | 2 | İkinci halka; O-2 sonrası |
| AI Overviews / AI Mode | Yüksek | Yüksek | Yok | 3 | Gemini-vekil, "yönsel" etiketiyle (D-03b) |
| Copilot | Düşük | Orta | Yok | 3 | V1 kapsam dışı |

## 8. AVIP için Çıkarımlar

1. Connector mimarisi kademe farkındalıklı tasarlanır: her connector, yanıt + alıntı + kademe etiketi + maliyet sayaçlarını standart şemayla döndürür (0308).
2. Fidelite etiketi ürün dilinin parçasıdır; pano, rapor ve API çıktılarında zorunludur (0309, 0204).
3. Maliyet modeli panel-tabanlıdır ve üç kolla yönetilir: panel boyutu, motor sayısı, örnekleme n (K1). Motor ekleme kararı bu modele karşı verilir.
4. TR pazar stratejisi ChatGPT + Gemini ölçümünü öne alır; Perplexity metodolojik referans yüzey olarak konumlanır.
5. Alıntı görünürlüğü uyum şartları (OpenAI) pano tasarım gereksinimidir; 0204'e fonksiyonel gereksinim olarak girer.
6. Kademe 3 yüzeyler için kazıma yapılmaz; Google yüzeyi D-03 kararıyla, etiketli vekil üzerinden temsil edilir.

## 9. Açık Sorular

| ID | Soru / Karar | Not |
|---|---|---|
| D-03 | Google yüzeyi stratejisi: (a) kapsam dışı, (b) Gemini etiketli vekil, (c) lisanslı üçüncü taraf veri | Öneri (b) MVP + (c) hukuki inceleme; karar PO+TL, 0205 ile birlikte. 0007 karar kaydına eklenecek. |
| O-1 | Kademe 2 vekil ile tüketici yüzeyi korelasyonunun ölçümü | 0309 pilotunda API vs kontrollü manuel UI örneklemi. |
| O-2 | xAI/Grok kurumsal veri işleme ve gizlilik şartlarının incelenmesi | PY (hukuk) desteğiyle; ikinci halka kararından önce. |

---

## Kaynaklar

- docs.perplexity.ai · Sonar API dokümantasyonu; perplexity.ai · Sonar Pro duyurusu
- digitalapplied.com · GEO Visibility Agent (9 Temmuz 2026): üç kademeli erişim modeli, OpenAI url_citation, AI Mode erişim durumu, OpenAI alıntı gösterim şartı, 150 prompt maliyet modeli
- firecrawl.dev · Perplexity alternatifleri: Gemini Search grounding, ekosistem karşılaştırması
- techjacksolutions.com · Sonar API fiyat kademeleri ve entegrasyon rehberi
- datastudios.org · Perplexity API platform yapısı (Sonar, Agentic, Search API)
- elementera.com · OpenAI ve Anthropic crawler dokümantasyonu aktarımı (OAI-SearchBot, Claude-SearchBot)
- tr.euronews.com · karar.com · shiftdelete.net · We Are Social / Meltwater Digital 2026: TR ChatGPT payı %94.49
- indigodergisi.com · Statcounter 2025 TR/küresel AI araç payları
- sheltron.com.tr · TR pazar gözlemleri ve AIO ekosistem sinyali

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 12.07.2026 | İlk yayın: motor tipolojisi, üç kademeli erişim matrisi, fidelite kuralı, D-03 karar çerçevesi, TR pazar sinyalleri (0101 O-1 kapandı), MVP motor kapsam önerisi. |
