# 0006 · Sözlük

| Alan | Değer |
|---|---|
| Doküman ID | 0006 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Approved |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
| İlişkili | Tüm dokümanlar |

---

## 1. Amaç

Doküman setinde ve üründe kullanılan terimlerin tek doğruluk kaynağı. Amaç, ekip içinde ve müşteriyle aynı kelimenin aynı anlama gelmesidir.

**Kullanım kuralları:**
1. Türkçe terim esastır; ilk kullanımda İngilizce parantezle verilir.
2. Yerleşik teknik terimler çevrilmez: prompt, guardrail, calculation_run_id.
3. Kısaltmalar yalnızca bu sözlükteki açılımıyla kullanılır.

---

## 2. Alan Terimleri

| Terim | İngilizce | Tanım |
|-------|-----------|-------|
| **AI yanıt motoru** | AI answer engine | Kullanıcı sorusuna bağlantı listesi yerine sentezlenmiş yanıt üreten sistem. |
| **GEO** | Generative Engine Optimization | İçeriğin AI yanıt motorlarındaki görünürlüğünü artırma disiplini. |
| **Prompt** | Prompt | Motora verilen soru veya istem. |
| **Prompt seti** | Prompt set | Ölçüm için tanımlanmış, versiyonlanmış prompt koleksiyonu. |
| **Mention** | Mention | Yanıt metninde markanın adının geçmesi. |
| **Alıntı** | Citation | Yanıtın kaynak olarak bir URL göstermesi. |
| **Kaynak** | Source | Yanıtı besleyen içerik. |
| **Görünürlük payı** | Share of voice | Markanın rakiplere göre görünürlük oranı. |
| **Tıklamasız etki** | Zero-click impact | Kullanıcının tıklamadan yanıttan tatmin olması. |
| **Temellendirme** | Grounding / RAG | Motorun yanıtını gerçek zamanlı aramayla desteklemesi. |
| **Halüsinasyon** | Hallucination | Motorun doğru olmayan bilgi üretmesi. |
| **Fidelite** | Fidelity | Ölçüm yönteminin tüketici yüzeyine yakınlık derecesi (Kademe 1/2/3). |
| **AI Mode** | AI Mode | Google'ın gelişmiş AI arama modu; derinlemesine araştırma ve karşılaştırma yanıtları üretir. |
| **Google AI Overview** | Google AI Overview | Google arama sonuçlarının üstünde görünen AI tarafından sentezlenmiş yanıt. |
| **Conversation Replay** | Conversation Replay | AI motorunun ürettiği yanıtın anlık görüntü olarak saklanması, sonradan incelenebilmesi. |
| **Response Archive** | Response Archive | Geçmiş AI yanıtlarının tarihsel karşılaştırma yapılabilmesi için versiyonlu saklanması. |
| **Recommendation Rate** | Recommendation Rate | AI yanıtlarında markanın önerilme/öne çıkarılma oranı. |
| **Appearance Rate** | Appearance Rate | Prompt setindeki sorgularda markanın AI yanıtlarında görünme sıklığı. |
| **Sentiment / Algı Skoru** | Sentiment / Brand Perception Score | AI yanıtlarında marka hakkındaki duygu durumu (olumlu/nötr/olumsuz). |
| **Hallüsinasyon Tespiti** | Hallucination Detection | AI motorunun marka hakkında ürettiği yanlış/gerçek dışı bilgilerin otomatik tespiti. |
| **Competitive Visibility Score** | Competitive Visibility Score | Markanın rakiplere göre normalize edilmiş AI görünürlük performansı. |
| **Prompt Coverage Score** | Prompt Coverage Score | Tanımlı prompt setinde markanın ne kadarında geçtiğinin oranı. |
| **Competitive Gap** | Competitive Gap | Belirli bir boyuttaki rakip-marka farkı (Visibility/Citation/Content/Topic/Prompt Gap). |
| **Content Gap** | Content Gap | AI sistemlerinin sektörel sorularda eksik bulduğu içerik alanları. |
| **Structured Data** | Structured Data / Schema | Web sayfasındaki Schema.org yapılandırılmış veri işaretlemesi. |
| **LLM Bot** | LLM Bot | AI motorlarının web sitesini taramak için kullandığı tarayıcı bot (GPTBot, Google-Extended, PerplexityBot vb.). |
| **Earned AI Visibility** | Earned AI Visibility | Organik olarak (ücretli/izinsiz yöntem olmadan) kazanılmış AI görünürlüğü. |
| **Mistral** | Mistral | Fransız AI şirketi Mistral AI'nın dil modeli ailesi. |

---

## 3. Ürün ve Metrik Terimleri

| Terim | Tanım |
|-------|-------|
| **Kiracı (tenant)** | Platformdaki izole müşteri alanı. |
| **Görünürlük Skoru** | 0-100 bileşik skor. |
| **Ölçüm koşusu** | Prompt setinin motorlarda örneklemli yürütülmesi. |
| **Örnekleme** | Aynı prompt için n tekrar sorgu. |
| **Güven aralığı** | Skor kararlılığının istatistiksel ifadesi. |
| **calculation_run_id** | Her hesaplamanın benzersiz kimliği. |
| **Fidelite etiketi** | Her skorda görünen Kademe (1/2/3) bilgisi. |
| **WAT%** | Haftalık aktif kiracı oranı (North Star). |
| **Sert kural** | Pazarlığa kapalı eşik. |
| **Koruma (guardrail)** | Hedef değil sınır; ihlali hedefi duraklatır. |
| **Pilot** | MVP değer doğrulama programı. |
| **GAVF** | GeoLens AI Visibility Framework — açık standart. |

---

## 4. Teknik Terimler

| Terim | Tanım |
|-------|-------|
| **Çok kiracılık** | Tek platformun birden çok kiracıya izole hizmet vermesi. |
| **RBAC** | Rol tabanlı yetkilendirme. |
| **Denetim izi** | Kritik işlemlerin kim-ne-ne zaman kaydı. |
| **IDOR** | Nesne kimliği üzerinden yetkisiz erişim zafiyeti. |
| **Kota / hız sınırı** | Motor API çağrı sınırları. |
| **SLO / SLA** | İç hedef / sözleşmesel taahhüt. |

---

## 5. Süreç Terimleri

| Terim | Tanım |
|-------|-------|
| **ADR** | Numaralı, gerekçeli mimari karar kaydı. |
| **DoD** | Definition of Done — işin bitmiş sayılma ölçütleri. |
| **PRD** | Ürün gereksinim dokümanı. |
| **MVP** | Değer hipotezini doğrulayan en küçük ürün. |
| **North Star** | Tek öncelikli başarı metriği. |
| **Changelog** | Versiyon-tarih-değişiklik tablosu. |

---

## 6. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | Yeni terim ekleme süreci | ⏳ AVIP 0005 §2 kullanım kuralları devralındı: yeni terim 0007 karar süreciyle eklenir, tüm dokümanlarda tarama gerekir. |
| O-2 | Conversation Replay depolama süresi | ⏳ Pilot deneyiyle belirlenecek. KVKK/GDPR saklama politikalarıyla uyumlu olmalıdır. |

---

## Kaynaklar

- archive/avip-v1/0005-glossary.md (AVIP Faz 0 seti)

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: GeoLens sözlüğü. Alan, ürün, teknik ve süreç terimleri. |
| 1.1 | 22.07.2026 | AVIP kapalı kararları taşındı: terim ekleme süreci AVIP 0005'ten devralındı. |
| 1.2 | 27.07.2026 | Turkcell RFP kapsamında yeni terimler eklendi: AI Mode, Google AI Overview, Conversation Replay, Response Archive, Recommendation Rate, Appearance Rate, Sentiment/Algı Skoru, Hallüsinasyon Tespiti, Competitive Visibility Score, Prompt Coverage Score, Competitive Gap, Content Gap, Structured Data, LLM Bot, Earned AI Visibility, Mistral. |
