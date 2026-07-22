# 0005 · Glossary

| Alan | Değer |
|---|---|
| Doküman ID | 0005 |
| Proje | AI Visibility Intelligence Platform (kod adı: AVIP) |
| Versiyon | 1.3 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 18 Temmuz 2026 |
| Karşıladığı madde | · (metodoloji gereği; 0000 Faz 0) |
| İlişkili | Tüm dokümanlar |

---

## 1. Amaç ve Kapsam

Bu doküman, doküman setinde ve üründe kullanılan terimlerin tek doğruluk kaynağıdır. Amaç, ekip içinde ve müşteriyle aynı kelimenin aynı anlama gelmesidir. Terimler dört kategoride toplanır: alan (GEO ekosistemi), ürün ve metrik, teknik ve mimari, süreç ve doküman.

## 2. Kullanım Kuralları

1. Türkçe terim esastır; ilk kullanımda İngilizce karşılık parantezle verilir, sonrasında Türkçesi kullanılır.
2. Yerleşik teknik terimler çevrilmez ve olduğu gibi kullanılır: prompt, guardrail, changelog, audit log, calculation_run_id.
3. Kısaltmalar yalnızca bu sözlükteki açılımıyla kullanılır; sözlükte olmayan kısaltma dokümana giremez.
4. Yeni terim ekleme veya tanım değişikliği 0007 süreciyle yapılır ve changelog'a işlenir; tanım değişikliği, terimi kullanan dokümanlarda tarama gerektirir.
5. Skor ve metrik adları (Görünürlük Skoru, WAT%) ürün arayüzünde de bu sözlükteki yazımla kullanılır.

## 3. Alan Terimleri

| Terim | İngilizce | Tanım |
|---|---|---|
| **AI yanıt motoru** | AI answer engine | Kullanıcı sorusuna bağlantı listesi yerine sentezlenmiş tek yanıt üreten sistem: ChatGPT, Gemini, Claude, Perplexity, Copilot, Grok vb. |
| **Büyük dil modeli** | LLM, Large Language Model | Yanıt motorlarının temelindeki, metin üreten olasılıksal model. |
| **GEO** | Generative Engine Optimization | İçeriğin AI yanıt motorlarındaki görünürlüğünü artırma disiplini. Bu doküman setinde GEO coğrafya anlamında kullanılmaz (0006 semantik kararı). |
| **AEO** | Answer Engine Optimization | GEO ile büyük ölçüde örtüşen sektör terimi; kaynaklarda birlikte geçer. |
| **Prompt** | Prompt | Motora verilen soru veya istem; klasik aramadaki anahtar kelimenin karşılığı (0001 §6). |
| **Prompt seti** | Prompt set | Ölçüm için tanımlanmış, versiyonlanmış prompt koleksiyonu; ölçümün tekrarlanabilirlik temeli. |
| **Mention** | Mention | Yanıt metninde markanın adının geçmesi; en zayıf görünürlük sinyali. |
| **Öneri** | Recommendation | Motorun markayı bir seçenek olarak açıkça önermesi; mention'dan güçlü sinyal. |
| **Alıntı** | Citation | Yanıtın kaynak olarak bir URL veya siteyi göstermesi. |
| **Kaynak** | Source | Yanıtı besleyen içerik; alıntılanan veya alıntısız etkileyen. |
| **Bağlam değerlendirmesi** | Context / framing | Markanın yanıt içindeki konumlanışı: olumlu, nötr, olumsuz veya koşullu. |
| **Görünürlük payı** | Share of voice | Belirli bir prompt setinde markanın rakiplere göre görünürlük oranı. |
| **Tıklamasız etki** | Zero-click impact | Kullanıcının kaynağa tıklamadan yanıttan tatmin olması; web analitiğinin göremediği etki (0002 P2). |
| **Halüsinasyon** | Hallucination | Motorun doğru olmayan bilgiyi yanıt olarak üretmesi. |
| **Temellendirme** | Grounding / RAG | Motorun yanıtını gerçek zamanlı arama veya harici veriyle desteklemesi; alıntı davranışının kaynağı. |
| **Eğitim verisi kesimi** | Training cutoff | Modelin yerleşik bilgisinin sınır tarihi; temellendirme bu sınırı aşar. |
| **Motor** | Engine | Ölçüm yapılan AI sistemi; bu sette "AI yanıt motoru" ile eş anlamlı kısa kullanım. |

## 4. Ürün ve Metrik Terimleri

| Terim | İngilizce | Tanım |
|---|---|---|
| **AVIP** | · | Ürünün geçici kod adı: AI Visibility Intelligence Platform. Nihai isim 0006 protokolüyle belirlenecek. |
| **Kiracı** | Tenant | Platformdaki izole müşteri alanı; tüm veriler kiracı bazında ayrıştırılır. |
| **Görünürlük Skoru** | Visibility Score | Prompt seti × motor kesitinde markanın varlığını özetleyen 0-100 bileşik skor; bileşen ve ağırlıkları 0309'da tanımlanır. |
| **Ölçüm koşusu** | Measurement run | Bir prompt setinin bir veya birden çok motorda örneklemli olarak yürütülmesi. |
| **Örnekleme** | Sampling | Aynı prompt ve motor için n tekrar sorgu; yanıt değişkenliğini (H3) istatistiksel olarak yönetme yöntemi. |
| **Güven aralığı** | Confidence interval | Skor kararlılığının istatistiksel ifadesi; M5 eşiğinin birimi. |
| **calculation_run_id** | · | Her hesaplamanın benzersiz kimliği; denetlenebilirlik zincirinin anahtarı (G2, M7). |
| **Faktör anlık görüntüsü** | Factor snapshot | Hesap anında kullanılan girdi ve ağırlıkların dondurulmuş kopyası; aynı skorun yeniden üretilebilmesini sağlar. |
| **Şablon versiyonu** | Template version | Skor formülünün versiyonu; formül değiştiğinde artar, eski skorlar eski versiyonla okunur. |
| **WAT%** | Weekly Active Tenant % | North Star metriği: haftalık aktif kiracı oranı (0004 §3). |
| **Sert kural** | Hard rule | Pazarlığa kapalı eşik: M6, M7, M12, M14 (0004 §7). |
| **[K]** | Calibration flag | Başlangıç kalibrasyonuna tabi eşik işareti; pilot verisiyle revize edilir. |
| **Koruma metriği** | Guardrail | Hedef değil sınır; ihlali ilgili hedefi duraklatır (0004 §5). |
| **Pilot** | Pilot | MVP değer doğrulama programı; G8 ve M1–M3'ün ölçüm zemini. |

## 5. Teknik ve Mimari Terimler

| Terim | İngilizce | Tanım |
|---|---|---|
| **Çok kiracılık** | Multi-tenancy | Tek platform örneğinin birden çok kiracıya izole hizmet vermesi. |
| **Tenant izolasyonu** | Tenant isolation | Kiracı verilerinin birbirinden erişilemez biçimde ayrılması; M12 ile sürekli test edilir. |
| **RBAC** | Role-Based Access Control | Rol tabanlı yetkilendirme; her uç noktada zorunlu (0310). |
| **IDOR** | Insecure Direct Object Reference | Nesne kimliği üzerinden yetkisiz erişim zafiyeti; izolasyon testlerinin birincil hedefi. |
| **Denetim izi** | Audit log | Kritik işlemlerin kim-ne-ne zaman kaydı; M14 kapsamı. |
| **İş / işçi** | Job / worker | Arka planda çalışan görev ve onu yürüten süreç (0307). |
| **Kuyruk** | Queue | İşlerin sıralandığı aracı katman; Redis tabanlı (0307). |
| **Zamanlayıcı** | Scheduler | Zamanlanmış ölçüm ve raporları tetikleyen bileşen (0307, madde 23). |
| **SLO / SLA** | Service Level Objective / Agreement | İç hedef / sözleşmesel taahhüt ayrımı; 0004 O-3. |
| **Kota / hız sınırı** | Quota / rate limit | Motor API'lerinin çağrı sınırları; K1 maliyet yönetiminin girdisi (0308). |

## 6. Süreç ve Doküman Terimleri

| Terim | İngilizce | Tanım |
|---|---|---|
| **ADR** | Architecture Decision Record | Numaralı, gerekçeli mimari karar kaydı; docs/adr/ altında tutulur. |
| **DoD** | Definition of Done | Bir işin bitmiş sayılma ölçütleri; doküman DoD'si 0000 §6'da. |
| **PRD** | Product Requirements Document | Gereksinim dokümanı (0204); madde 12 ve 15'i karşılar. |
| **MVP** | Minimum Viable Product | Değer hipotezini doğrulayan en küçük ürün kesiti (0205). |
| **North Star** | North Star metric | Ürünün tek öncelikli başarı metriği (0004 §3). |
| **Faz** | Phase | 0000 metodolojisindeki beş aşama: Foundation, Research, Product, Architecture, Development. |
| **Madde izlenebilirliği** | Item traceability | Her dokümanın 25 maddelik gereksinim listesindeki karşılığının künyede belirtilmesi (0000 §5). |
| **Changelog** | Changelog | Doküman sonunda zorunlu versiyon-tarih-değişiklik tablosu. |

### v1.1 terim eklemeleri (0302 §8 ve 0202/0306 devri)

| Terim | Tanım |
|---|---|
| Çalışma alanı (workspace) | Kiracı altındaki izole çalışma birimi; ajans kiracısında müşteri karşılığı |
| Panel versiyonu | Prompt seti + motor kapsamı + pazar üçlüsünün dondurulmuş hali; trend kırılım ekseni |
| Bulgu (finding) | Site denetiminin önem dereceli tekil çıktısı |
| Digest | Aynı gün içindeki uyarı tetiklerinin birleştirildiği toplu bildirim |
| İlk değer | Kayıt sonrası ilk anlamlı çıktının (etiketli skor veya denetim özeti) alındığı an |
| Aktivasyon | Kurulum omurgasının tamamlanması; aktif kullanım öncülü |
| Derin bağlantı | E-postadan doğrudan hedef kaynağa inen imzalı kısa bağlantı (/l/token) |

## 7. Açık Sorular

| ID | Soru | Not |
|---|---|---|
| O-1 | Görünürlük Skoru bileşen adları (mention, öneri, alıntı, bağlam ağırlıkları) | **AN+TL eylem planı (21.07.2026):** Pilot öncesi AN hazırlar, TL onaylar, PO onaylar. 0309 skor tasarımı girdisi. Bulgular 0007 haftalık senkronunda raporlanır. 0007 D-89. |
| ~~O-2~~ | ~~Bağlam değerlendirmesinin (sentiment) V1 kapsamına girip girmeyeceği~~ | ✅ **KAPANDI**: V1 kapsamı dışı. Terim "platform ufku" notuyla sözlükte kalır. Mention+öneri+alıntı üçlüsü MVP için yeterli. 0007 D-81. |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 1.0 | 12.07.2026 | İlk yayın: 4 kategoride 49 terim, kullanım kuralları, sözlük değişiklik süreci. |
| 1.1 | 18.07.2026 | Yedi terim eklendi: çalışma alanı, panel versiyonu, bulgu, digest, ilk değer, aktivasyon, derin bağlantı (0302 §8 ve 0202/0306 devri). |
| 1.2 | 21.07.2026 | O-2 kapandı: sentiment V1 kapsamı dışı. 0007 D-81. |
| 1.3 | 21.07.2026 | O-1 eylem planı eklendi: AN+TL pilot öncesi skor bileşen adlarını netleştirecek. 0007 D-89. |
