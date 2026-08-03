# 0416 · AI Araştırma İş Paketleri

| Alan | Değer |
|---|---|
| Doküman ID | 0416 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Product |
| Tarih | 27 Temmuz 2026 |
| İlişkili | 0401–0415, 0205 (MVP), 0206 (Roadmap), 0207 (Feature Catalog), project-plan |

---

## 1. Amaç

Bu doküman, yapay zeka uzmanı (doktora seviyesi) için tanımlanmış araştırma odaklı iş paketlerini (Work Package) kapsar. Her iş paketi:

- **Araştırma sorusunu** tanımlar
- **Teslimatları** listeler
- **Kod/doküman karşılığını** belirtir (nerede kullanılacağı)
- **Ne işe yaradığını** belirtir (hangi ürün özelliğini mümkün kıldığı)
- **Başarı kriterini** tanımlar

Böylece her paketin sonunda ölçülebilir bir artefakt üretilir ve doğrudan ürünün çekirdek algoritmalarına, fikri mülkiyetine veya rekabet avantajına dönüşür. Araştırma soruları bilgi üretir, kod/doküman karşılığı o bilginin nereye işleneceğini gösterir, "Ne İşe Yarar" sütunu ise o bilginin üründe hangi somut özelliğe dönüştüğünü açıklar.

---

## 2. İş Paketleri

### WP-01 · AI Visibility Landscape Research

| Alan | Detay |
|------|-------|
| Amaç | AI arama ve cevap ekosistemini analiz etmek |
| Kritiklik | ⭐⭐⭐⭐⭐ |

**Araştırma Soruları:**
- ChatGPT, Gemini, Claude, Copilot, Perplexity, Grok hangi davranışsal farklılıkları gösteriyor?
- Citation mekanizmaları motorlar arasında nasıl farklılaşıyor?
- Brand recommendation hangi koşullarda tetikleniyor?
- AI Search trendleri hangi yönde evriliyor?

**Teslimatlar:**

**1. AI Visibility Landscape Report**
- Her AI motoru (ChatGPT, Gemini, Claude, Copilot, Perplexity, Grok) için: cevap yapısı, citation davranışı, recommendation eğilimi, gecikme profili, dil desteği
- Ekran görüntüleri ve örnek cevaplarla desteklenmiş 10+ senaryo analizi
- Motor bazında güçlü/zayıf yönler matrisi

**2. AI Platform Comparison Matrix**
- 6 motor × 15 kriter karşılaştırma tablosu (citation doğruluğu, marka tanıma, güncellik, maliyet, hız, vs.)
- Her kriter için puanlama (1-5) ve ağırlıklandırma
- Radar grafiği ve sıralama

**3. Risk & Opportunity Analysis**
- Her motor için: citation vermeme, halüsinasyon, marka karıştırma, yanlı bilgi üretme riskleri
- GeoLens özelinde: hangi motora bağımlılık ne kadar risk taşır?
- Fırsat alanları: hangi motor hangi sektörde daha iyi sonuç verir?

**Kod/Doküman Karşılığı:**

| Çıktı | Nerede Kullanılır | Ne İşe Yarar | Açıklama |
|-------|-------------------|-------------|----------|
| Landscape Report | `platform/docs/01-business/0107-sales-playbook.md` | Satış ekibi müşteriye "AI ekosistemi şöyle işliyor" diye anlatır, güven kazanır | Satış ve pazarlama pozisyonlama |
| Platform Matrix | `platform/engine/` (adapter seçim kriterleri) | Hangi motorlara adapter yazılacağına öncelik sırası verilir, geliştirme maliyeti optimize edilir | Yeni adapter geliştirme önceliklendirme |
| Risk Analysis | `platform/docs/02-product/0209-backlog.md` | "X motoru citation vermezse skor düşer" gibi riskler önceden bilinir, ürün kararları ona göre alınır | Ürün yol haritası risk kaydı |

**Başarı Kriteri:** 6 AI motorunun davranış analizi tamamlanmış, karşılaştırma matrisi dokümante edilmiş olmalı.

---

### WP-02 · Competitive Intelligence

| Alan | Detay |
|------|-------|
| Amaç | Rakip ürünleri teknik olarak analiz etmek |
| Kritiklik | ⭐⭐⭐⭐⭐ |

**Araştırma Soruları:**
- Profound, Scrunch AI, Otterly.AI, Peec AI hangi ölçüm metodolojilerini kullanıyor?
- Semrush AI Toolkit ve Ahrefs AI hangi AI özelliklerini sunuyor?
- GeoLens'in farklılaşma alanları neler?

**Teslimatlar:**

**1. Competitor Matrix**
- Her rakip için: kuruluş yılı, hedef pazar, fiyatlandırma modeli, desteklenen motor sayısı, ölçüm metodolojisi
- 5+ rakibin aynı kriterler altında karşılaştırıldığı tablo

**2. Feature Comparison**
- 50+ özellik bazında karşılaştırma: prompt yönetimi, citation çıkarma, skorlama, raporlama, entegrasyon, API desteği
- Feature presence matrix (var/yok/kısmi)

**3. Gap Analysis**
- GeoLens'te olmayıp rakiplerde olan özellikler listesi
- Her gap için: etki seviyesi (yüksek/orta/düşük), implementasyon tahmini, rekabet avantajı değerlendirmesi

**4. Differentiation Report**
- GeoLens'in rakipsiz olduğu alanlar (moat analizi)
- Rakiplerin kolayca kopyalayamayacağı özellikler
- Pozisyonlama önerisi: hangi mesaj hangi rakibe karşı kullanılmalı?

**Kod/Doküman Karşılığı:**

| Çıktı | Nerede Kullanılır | Ne İşe Yarar | Açıklama |
|-------|-------------------|-------------|----------|
| Competitor Matrix | `platform/docs/01-business/0106-go-to-market.md` | Pazarlama ekibi rakiplere karşı net konumlanma mesajı üretir | Pazar konumlandırma stratejisi |
| Gap Analysis | `platform/docs/02-product/0207-feature-catalog.md` | Rakiplerde olup GeoLens'te olmayan özellikler belirlenir, roadmap'e eklenir | Eksik özelliklerin backlog'a eklenmesi |
| Differentiation Report | `platform/docs/01-business/0108-investor-thesis.md` | Yatırımcılara "Bu 5 alanda rakiplerden ayrışıyoruz" kanıtı sunulur | Yatırımcı sunumu farklılaşma argümanı |

**Başarı Kriteri:** En az 5 rakip ürün analiz edilmiş, Gap Analysis ile GeoLens'in eksik/güçlü yönleri belirlenmiş olmalı.

---

### WP-03 · AI Visibility Measurement Standard (AVMS)

| Alan | Detay |
|------|-------|
| Amaç | AI görünürlüğünü ölçen metodolojiyi tanımlamak |
| Kritiklik | ⭐⭐⭐⭐⭐ |

**Araştırma Soruları:**
- Visibility, Mention, Recommendation, Citation, Authority, Confidence kavramları nasıl tanımlanmalı?
- GAVF standardı hangi metric tanımlarını içermeli?

**Teslimatlar:**

**1. AVMS v1.0 (AI Visibility Measurement Standard)**
- Visibility, Mention, Recommendation, Citation, Authority, Confidence kavramlarının kesin tanımları
- Her kavram için: matematiksel formül, ölçüm birimi, alt ve üst sınırlar
- Ölçüm prensipleri: n=3 örnekleme, temp=0 determinizmi, güven aralığı hesaplama
- Versiyonlama şeması: major/minor/patch ne zaman artar?

**2. Metric Definitions**
- Her metric için: ad, sembol, formül, girdi değişkenleri, çıktı aralığı, örnek hesaplama
- Metrikler arası ilişki matrisi (hangi metrik hangisini besler?)
- Kabul testi senaryoları

**3. Terminology Guide**
- 30+ terim için: İngilizce karşılık, Türkçe tanım, kullanım bağlamı, varsa alternatif terimler
- Yanlış kullanım uyarıları (örneğin "visibility ≠ görünürlük, kavram farklı")

**Kod/Doküman Karşılığı:**

| Çıktı | Nerede Kullanılır | Ne İşe Yarar | Açıklama |
|-------|-------------------|-------------|----------|
| AVMS v1.0 | `platform/docs/04-ai-framework/0401-ai-visibility-standard.md` | Ürünün tüm skorlama mantığının dayandığı standart — "Görünürlük" dendiğinde ne kastedildiği herkes için nettir | GAVF standardının güncellenmesi |
| Metric Definitions | `specification/docs/01-standard/0104-scoring-standard.md` | GeoLens'i kullanan herkes (müşteri, denetçi, entegratör) aynı metriği aynı şekilde anlar, güven oluşur | Açık standart metrik tanımları |
| Terminology Guide | `specification/docs/00-overview/0002-glossary.md` | Satış, destek ve dokümantasyon aynı dili konuşur — "visibility" herkes için aynı anlama gelir | Ortak terminoloji sözlüğü |

**Başarı Kriteri:** Tüm metriklerin matematiksel tanımı yapılmış, GAVF standardına entegre edilmiş olmalı.

---

### WP-04 · Prompt Taxonomy

| Alan | Detay |
|------|-------|
| Amaç | Prompt sınıflandırma modelini oluşturmak |
| Kritiklik | ⭐⭐⭐⭐⭐ |

**Araştırma Soruları:**
- Intent, Topic, Persona, Funnel aşamaları nasıl sınıflandırılmalı?
- Prompt ağırlıkları hangi kriterlere göre belirlenmeli?

**Teslimatlar:**

**1. Prompt Taxonomy**
- Intent sınıfları (presence, comparison, recommendation, category, problem) ve her biri için alt türler
- Topic sınıfları (ürün, hizmet, marka, sektör, teknoloji) ve hiyerarşik yapı
- Persona sınıfları (tüketici, uzman, gazeteci, yatırımcı, öğrenci)
- Funnel aşamaları (farkındalık, değerlendirme, karar, satın alma)
- Her bir sınıf için örnek prompt'lar

**2. Prompt Schema**
- JSON Schema formatında prompt yapısı: `intent`, `topic`, `persona`, `funnel`, `lang`, `target_brand`, `context`
- Schema validasyon kuralları
- Prompt varyasyon üretme kuralları (marka adı değişince prompt nasıl değişir?)

**3. 1000 örnek prompt**
- 6 dilde (Türkçe + İngilizce + sektöre göre diğer diller)
- 5 sektör × 5 intent × 5 topic × 4 persona × 2 funnel = çapraz dağılım
- Her prompt için: etiket seti (intent, topic, persona, funnel, sektör)
- CSV/JSON formatında makine okunabilir dosya

**Kod/Doküman Karşılığı:**

| Çıktı | Nerede Kullanılır | Ne İşe Yarar | Açıklama |
|-------|-------------------|-------------|----------|
| Prompt Taxonomy | `platform/docs/04-ai-framework/0402-prompt-taxonomy.md` | Ölçüm motoru hangi soruyu hangi amaçla sorduğunu bilir, sonuçlar anlamlı kategorilere ayrılır | Mevcut taksonominin genişletilmesi |
| Prompt Schema | `platform/docs/04-ai-framework/0403-prompt-generator.md` | Yeni marka eklendiğinde otomatik prompt üretimi çalışır, manuel işgücü sıfırlanır | Prompt üretecinin şema tanımı |
| Prompt Ağırlıkları | `platform/docs/04-ai-framework/0404-prompt-weighting.md` | Her prompt türü skoru farklı etkiler — örn. "tavsiye" prompt'u "bilgi" prompt'undan daha ağırlıklıdır | Ağırlıklandırma metodolojisi |
| 1000 örnek prompt | `platform/docs/06-data/` | Tüm algoritma testleri aynı prompt setiyle çalışır, "benim bilgisayarımda çalışıyordu" problemi kalkar | Test verisi olarak kullanım |

**Başarı Kriteri:** En az 5 intent sınıfı, 10 topic sınıfı tanımlanmış ve 1000 adet etiketlenmiş prompt havuzu oluşturulmuş olmalı.

---

### WP-05 · Gold Standard Dataset

| Alan | Detay |
|------|-------|
| Amaç | Algoritmaların doğrulanacağı referans veri setini oluşturmak |
| Kritiklik | ⭐⭐⭐⭐⭐ |

**Araştırma Soruları:**
- Her prompt için beklenen cevap, mention, citation, entity, recommendation, sentiment nasıl etiketlenmeli?
- Etiketleme kuralları hangi güvenilirlik seviyesini sağlamalı?

**Teslimatlar:**

**1. Gold Dataset v1**
- 500+ prompt için manuel etiketlenmiş referans veri seti
- Her prompt için: beklenen cevap özeti, mention listesi (marka/ürün/rakip), citation listesi (URL + tür), entity listesi (tip + değer), recommendation var/yok, sentiment (pozitif/nötr/negatif)
- Format: JSON Lines (her satır bir örnek)
- Train/test split: %80 / %20

**2. Annotation Guide**
- Adım adım etiketleme talimatı: her alanın nasıl doldurulacağı
- Karar ağaçları: "Bu bir mention mı? → Evet ise → Marka mı, ürün mü?"
- Sık yapılan hatalar ve örnekleri
- 10 örnek üzerinde adım adım etiketleme gösterimi

**3. Labeling Rules**
- Mention tespit kuralları: tam eşleşme, kısmi eşleşme, eş anlamlı, yanlış pozitif uyarıları
- Citation sınıflandırma kuralları: direct/attribution/directional ayrımı
- Entity tipi belirleme kuralları: bağlam bazlı karar matrisi
- Sentiment belirleme kuralları: keyword tabanlı + bağlam tabanlı
- İhtilaflı durumlar için bağlayıcı kurallar

**Kod/Doküman Karşılığı:**

| Çıktı | Nerede Kullanılır | Ne İşe Yarar | Açıklama |
|-------|-------------------|-------------|----------|
| Gold Dataset | `platform/internal/measure/` | Algoritma değişikliklerinde "eskisinden daha mı iyi?" sorusu objektif ölçülür, subjektif değerlendirme kalkar | Skor doğrulama testleri |
| Annotation Guide | `platform/docs/06-data/0606-data-quality.md` | Veri seti büyürken tutarlılık korunur, farklı kişiler aynı şekilde etiketler | Veri kalite standardı |
| Labeling Rules | `platform/docs/04-ai-framework/0407-entity-recognition.md` | "Bu bir marka mı, ürün mü?" karışıklığı önlenir, çıkarım doğruluğu artar | Varlık tanıma kuralları |

**Başarı Kriteri:** En az 500 prompt için manuel etiketleme tamamlanmış, etiketleyiciler arası uyum (inter-annotator agreement) > %90 olmalı.

---

### WP-06 · LLM Evaluation Framework

| Alan | Detay |
|------|-------|
| Amaç | AI cevaplarını değerlendirme metodolojisi geliştirmek |
| Kritiklik | ⭐⭐⭐⭐⭐ |

**Araştırma Soruları:**
- Hallucination, factuality, consistency, explainability nasıl ölçülmeli?
- Hangi evaluation metric seti kullanılmalı?

**Teslimatlar:**

**1. Evaluation Framework**
- Değerlendirme kriterleri tanımları: hallucination, factuality, consistency, completeness, explainability, relevance
- Her kriter için: ölçüm yöntemi, puanlama skalası (0-1), kabul eşiği
- Test senaryosu şablonu: girdi prompt'ları, beklenen çıktı, hangi kriterlerin kontrol edileceği

**2. Benchmark Methodology**
- Benchmark test seti tasarımı: kaç prompt, hangi dağılım, hangi motorlar
- Değerlendirme protokolü: her motor aynı koşullarda çalıştırılır, sıcaklık sıfır, timeout süresi sabit
- Skor hesaplama: her kriter için ağırlıklandırma, bileşik benchmark skoru
- Sonuç raporlama formatı: tablo + grafik + yorum

**3. Evaluation Metrics**
- Her metric için: ad, formül, girdi değişkenleri, çıktı aralığı, hesaplama örneği
- Metric seti A: hallucination için (factual consistency, source attribution, contradiction rate)
- Metric seti B: quality için (relevance, completeness, coherence, fluency)
- Metric seti C: performance için (latency, token efficiency, cost per query)

**Kod/Doküman Karşılığı:**

| Çıktı | Nerede Kullanılır | Ne İşe Yarar | Açıklama |
|-------|-------------------|-------------|----------|
| Evaluation Framework | `platform/docs/04-ai-framework/` | Müşteriye "AI cevaplarının doğruluğunu şu kriterlerle ölçüyoruz" denir, ürün güvenilirliği kanıtlanır | Yeni doküman: evaluation standardı |
| Benchmark Methodology | `platform/internal/benchmark/` | "X motoru Y motorundan daha doğru" iddiası veriyle desteklenir, satışta teknik argüman olur | Benchmark handler'ının beslenmesi |
| Evaluation Metrics | `platform/internal/guardrail/` | Halüsinasyonlu cevaplar otomatik tespit edilir, müşteri raporunda yanıltıcı veri gösterilmez | Halüsinasyon tespit kurallarının iyileştirilmesi |

**Başarı Kriteri:** En az 5 evaluation metric tanımlanmış, benchmark metodolojisi dokümante edilmiş olmalı.

---

### WP-07 · Citation Framework

| Alan | Detay |
|------|-------|
| Amaç | Citation analiz algoritmasını tasarlamak |
| Kritiklik | ⭐⭐⭐⭐☆ |

**Araştırma Soruları:**
- Citation türleri (direct, attribution, directional) nasıl ayrıştırılmalı?
- Citation kalite modeli ve güven skoru nasıl hesaplanmalı?

**Teslimatlar:**

**1. Citation Framework**
- Citation türleri: direct (doğrudan alıntı), attribution (atıf), directional (yönlendirme), implicit (örtük referans)
- Her tür için: tespit yöntemi, örnekler, sınır durumlar
- Citation çıkarma pipeline'ı: raw response → citation parsing → tür sınıflandırma → doğrulama
- Her motorun citation formatı için özel ayrıştırıcı gereksinimleri

**2. Citation Quality Model**
- Kalite boyutları: kaynak güvenilirliği (.gov/.edu/resmi site vs forum/sosyal medya), güncellik, bağlam uyumu, erişilebilirlik
- Her boyut için: puanlama kriterleri (1-5), ağırlık, hesaplama formülü
- Toplam citation kalite skoru: ağırlıklı toplam + normalizasyon

**3. Citation Scoring Guide**
- Citation skorunun Visibility Index'e entegrasyonu
- Ağırlıklandırma önerileri: yüksek kaliteli citation ×1.0, orta ×0.6, düşük ×0.2
- Citation skorunun zamansal değişimi: yaşlanma fonksiyonu (eski citation daha düşük puan)
- Doğrulama senaryoları: 10 edge case (kaynak yok, tüm kaynak düşük kaliteli, vs.)

**Kod/Doküman Karşılığı:**

| Çıktı | Nerede Kullanılır | Ne İşe Yarar | Açıklama |
|-------|-------------------|-------------|----------|
| Citation Framework | `platform/docs/04-ai-framework/0405-citation-framework.md` | Hangi citation'ın doğrudan alıntı, hangisinin yönlendirme olduğu bilinir, skor manipülasyonu önlenir | Mevcut framework'ün genişletilmesi |
| Citation Quality Model | `platform/engine/registry.go` | "Wikipedia mı, resmi site mi, forum mu?" ayrımı yapılır — güvenilir kaynak yüksek, forum düşük puan alır | `Citation` struct'ına kalite alanları eklenmesi |
| Citation Scoring | `platform/internal/measure/` | Tüm citation'lar eşit değildir — kaliteli kaynaktan gelen mention daha yüksek Visibility Index verir | Skor hesaplamada citation kalite faktörü |

**Başarı Kriteri:** Her citation türü için kalite metriği tanımlanmış, güven skoru formülü belirlenmiş olmalı.

---

### WP-08 · Entity Recognition Framework

| Alan | Detay |
|------|-------|
| Amaç | AI cevaplarından bilgi çıkarımı |
| Kritiklik | ⭐⭐⭐⭐☆ |

**Araştırma Soruları:**
- Marka, ürün, rakip, teknoloji, organizasyon, lokasyon varlıkları nasıl ayırt edilmeli?
- Extraction rules hangi yaklaşımla (regex/NER/LLM-as-judge) tasarlanmalı?

**Teslimatlar:**

**1. Entity Schema**
- Varlık tipleri: brand, product, competitor, technology, organization, location, person, event
- Her tip için: tanım, örnekler, sınır durumlar (örneğin "Apple" → brand mi product mı?)
- Entity hiyerarşisi: parent-child ilişkileri (örneğin Toyota → Corolla)
- JSON Schema formatında entity yapısı

**2. Extraction Rules**
- Regex tabanlı kurallar: marka adı desenleri, URL çıkarma, mention konumu belirleme
- NER (Named Entity Recognition) kuralları: hangi entity tipi hangi bağlamda aranır?
- LLM-as-judge kuralları: regex/NER yetmediğinde LLM'a danışma protokolü
- Post-processing: duplicate removal, çelişki çözümü, confidence scoring
- Her kural için: öncelik sırası, yanlış pozitif/negatif oranı, override koşulları

**3. Benchmark Dataset**
- 200+ manuel etiketlenmiş entity içeren test seti
- Her test için: raw AI cevabı, beklenen entity listesi, beklenen mention pozisyonları
- Zor senaryolar: eş sesli markalar, yabancı dilde entity'ler, kısmi mention'lar

**Kod/Doküman Karşılığı:**

| Çıktı | Nerede Kullanılır | Ne İşe Yarar | Açıklama |
|-------|-------------------|-------------|----------|
| Entity Schema | `platform/docs/04-ai-framework/0407-entity-recognition.md` | "Rakip mi, teknoloji mi, ürün mü?" karışıklığı önlenir, her mention doğru kategoride raporlanır | Varlık tanıma dokümanı güncellemesi |
| Extraction Rules | `platform/internal/measure/` | AI cevabından "Apple" geçince bunun şirket mi, meyve mi olduğu ayırt edilir | Answer parser entity çıkarma mantığı |
| Benchmark Dataset | `platform/internal/benchmark/` | Entity çıkarımının doğruluğu sürekli ölçülür, kötüleşme anında tespit edilir | Entity recognition benchmark testleri |

**Başarı Kriteri:** En az 6 varlık tipi tanımlanmış, extraction rules gold dataset üzerinde > %85 F1 skoru almalı.

---

### WP-09 · Visibility Index Algorithm

| Alan | Detay |
|------|-------|
| Amaç | GeoLens'in ana skor algoritmasını tasarlamak |
| Kritiklik | ⭐⭐⭐⭐⭐ |

**Araştırma Soruları:**
- Mention, Position, Citation, Recommendation, Authority, Freshness, Confidence skorları nasıl birleştirilmeli?
- Ağırlıklandırma hangi yöntemle (AHP/regresyon/uzman görüşü) belirlenmeli?

**Teslimatlar:**

**1. Matematiksel Model**
- Visibility Index formülü: `VI = w₁·M + w₂·P + w₃·C + w₄·R + w₅·A + w₆·F + w₇·S`
  - M = Mention Score, P = Position Score, C = Citation Score
  - R = Recommendation Score, A = Authority Score, F = Freshness Score, S = Confidence Score
- Her bileşenin alt formülü ve normalizasyon yöntemi
- Toplam skor aralığı: 0-100, güven aralığı: ±5
- Matematiksel notasyonla yazılmış, LaTeX/PDF uyumlu doküman

**2. Ağırlıklandırma Yöntemi**
- Ağırlık belirleme yaklaşımı: AHP (Analytic Hierarchy Process) veya uzman paneli
- Her bir ağırlık için: gerekçe, duyarlılık analizi (ağırlık değişince skor nasıl etkilenir?), alternatif değerler
- Sektör bazlı ağırlık önerileri (e-ticaret → recommendation daha ağırlıklı, sağlık → authority daha ağırlıklı)
- Varsayılan ağırlık seti + 3 sektör profili

**3. Doğrulama Raporu**
- Gold dataset üzerinde model doğrulaması: MAE, RMSE, R², Spearman korelasyonu
- 10+ edge case testi (sıfır mention, tüm mention'lar citationsız, vs.)
- Duyarlılık analizi: her bileşenin toplam skora katkısı
- Rakiplerle karşılaştırma: GeoLens skoru vs manuel değerlendirme korelasyonu

**Kod/Doküman Karşılığı:**

| Çıktı | Nerede Kullanılır | Ne İşe Yarar | Açıklama |
|-------|-------------------|-------------|----------|
| Matematiksel Model | `platform/docs/04-ai-framework/0409-visibility-score.md` | Ürünün en kritik rakamı olan Visibility Index'in nasıl hesaplandığı şeffaftır — müşteri ve denetçi güvenir | Skor algoritması dokümantasyonu |
| Ağırlıklandırma | `platform/internal/measure/service.go` | "Presence mı daha önemli, position mı?" sorusu matematiksel olarak cevaplanır, keyfilik kalkar | `CalculateScore` fonksiyonunun iyileştirilmesi |
| Doğrulama Raporu | `platform/internal/measure/` | Algoritma değişince "önceki skorlar geçersiz mi?" sorusu cevaplanır, müşteri güveni korunur | Test senaryoları ve validasyon |

**Başarı Kriteri:** Visibility Index'in matematiksel formülasyonu tamamlanmış, gold dataset üzerinde doğrulama yapılmış olmalı.

---

### WP-10 · Opportunity Engine

| Alan | Detay |
|------|-------|
| Amaç | AI görünürlüğünü artıracak öneriler üretmek |
| Kritiklik | ⭐⭐⭐⭐☆ |

**Araştırma Soruları:**
- Eksik FAQ, eksik içerik, citation açığı, otorite eksikliği, yapısal veri eksikliği nasıl tespit edilmeli?
- Öneriler nasıl önceliklendirilmeli ve puanlanmalı?

**Teslimatlar:**

**1. Recommendation Model**
- Öneri kategorileri: visibility kaybı, citation açığı, içerik eksikliği, otorite zafiyeti, yapısal veri hatası, rakip tehdidi
- Her kategori için: tespit koşulu, öneri metni şablonu, kanıt etiketi (deneysel/korelasyonel/denenebilir)
- Öneri önceliklendirme: etki (yüksek/orta/düşük) × çaba (az/orta/çok) = öncelik matrisi

**2. Rule Set**
- 10+ tespit kuralı, her biri için: input (ScoreSnapshot + AuditSnapshot), koşul ifadesi, output (öneri + kanıt)
- Örnek kurallar:
  - "Son 30 günde skor %15+ düştü" → visibility kaybı önerisi
  - "Citation kaynağı sadece 1 domain" → citation çeşitlendirme önerisi
  - "Rakibin skoru sizi geçti" → rekabet analizi önerisi
  - "robots.txt AI bot'larını engelliyor" → teknik düzeltme önerisi
- Her kural için: sektör filtresi (tümü/e-ticaret/sağlık/finans)

**3. Opportunity Scoring**
- Her öneri için skor formülü: `OpportunityScore = Impact × Urgency × Confidence`
- Impact: müşterinin visibility'ine potansiyel etki (1-10)
- Urgency: ne kadar hızlı aksiyon alınmazsa kayıp büyür? (1-10)
- Confidence: tespitin doğruluk olasılığı (0-1)
- Öneri sıralama ve filtreleme kriterleri

**Kod/Doküman Karşılığı:**

| Çıktı | Nerede Kullanılır | Ne İşe Yarar | Açıklama |
|-------|-------------------|-------------|----------|
| Recommendation Model | `platform/docs/04-ai-framework/0412-opportunity-engine.md` | Müşteriye sadece "skorun düşmüş" değil, "citation kaynağını çeşitlendir" gibi somut aksiyon önerisi verilir | Fırsat motoru dokümantasyonu |
| Rule Set | `platform/internal/recommendation/service.go` | 10 farklı senaryo (citation açığı, FAQ eksikliği, otorite kaybı) için otomatik öneri üretilir, manuel analiz ihtiyacı kalkar | Öneri kurallarının güncellenmesi |
| Opportunity Scoring | `platform/internal/optimize/` | Öneriler "etki/çaba" matrisinde sıralanır, müşteri önce en hızlı kazancı görür | Optimizasyon önerilerinin iyileştirilmesi |

**Başarı Kriteri:** En az 10 öneri kuralı tanımlanmış, her kuralın etki/çaba skoru belirlenmiş olmalı.

---

### WP-11 · AI Benchmark

| Alan | Detay |
|------|-------|
| Amaç | AI platformlarını karşılaştırmak |
| Kritiklik | ⭐⭐⭐⭐☆ |

**Araştırma Soruları:**
- ChatGPT, Gemini, Claude, Copilot, Perplexity, Grok — doğruluk, gecikme, citation kalitesi açısından nasıl sıralanıyor?
- Maliyet/performans optimizasyonu hangi motorları önceliyor?

**Teslimatlar:**

**1. Benchmark Report**
- 6 AI motorunun 10+ metric üzerinden karşılaştırmalı sonuçları
- Her motor için: accuracy (%), avg latency (ms), cost per 1000 queries ($), citation rate (%), hallucination rate (%)
- Sektör bazlı kırılım: e-ticaret, sağlık, finans, teknoloji
- Detaylı test sonuçları tablosu + görsel grafikler

**2. Comparative Analysis**
- Motor bazında güçlü/zayıf yönler profili
- Use case bazında motor önerileri: "En doğru cevap için → Claude, en hızlı için → Grok, en ucuz için → Perplexity"
- Maliyet-performans optimizasyonu: hangi motor hangi senaryoda tercih edilmeli?
- GeoLens adapter stratejisi önerisi: birincil, ikincil, yedek motor atamaları

**3. Performance Dashboard**
- Motor performans metriklerini gösteren interaktif dashboard tasarımı
- Zaman bazlı performans trendi (haftalık/aylık)
- Maliyet takibi: her motor için harcama, query başına maliyet, bütçe uyarı eşikleri
- UI mockup'ları (Figma veya görsel prototip)

**Kod/Doküman Karşılığı:**

| Çıktı | Nerede Kullanılır | Ne İşe Yarar | Açıklama |
|-------|-------------------|-------------|----------|
| Benchmark Report | `platform/docs/04-ai-framework/` | Hangi AI motorunun hangi senaryoda daha başarılı olduğu bilinir, müşteriye öneri kalitesi kanıtlanır | Yeni doküman: benchmark sonuçları |
| Comparative Analysis | `platform/engine/` | "En pahalı motor en iyisi mi?" sorusu cevaplanır, maliyet/performans dengesi kurulur | Adapter seçim ve yapılandırma kararları |
| Performance Dashboard | `platform/web/` | Kullanıcı hangi motorların ölçümde kullanıldığını ve performanslarını görür, şeffaflık sağlanır | UI'da benchmark görselleştirmeleri |

**Başarı Kriteri:** 6 AI motoru için standart benchmark test seti tanımlanmış, her motor en az 100 prompt ile test edilmiş olmalı.

---

### WP-12 · Proof of Concept (PoC)

| Alan | Detay |
|------|-------|
| Amaç | Kritik algoritmaları küçük ölçekli doğrulamak |
| Kritiklik | ⭐⭐⭐⭐⭐ |

**Araştırma Soruları:**
- Citation Extraction, Entity Extraction, Recommendation Detection, Prompt Classification, Visibility Score hesaplama prototipleri çalışıyor mu?
- Doğruluk metrikleri kabul edilebilir seviyede mi?

**Teslimatlar:**

**1. Çalışan prototipler (Python veya Go)**
- **Citation Extraction PoC**: AI cevabından citation çıkarma, tür sınıflandırma, URL doğrulama
- **Entity Extraction PoC**: Marka/ürün/rakip entity çıkarımı, bağlam bazlı belirsizlik çözümü
- **Recommendation Detection PoC**: "Öneririm/tavsiye ederim/alternatif" ifadelerini tespit, recommendation gücü skorlama
- **Prompt Classification PoC**: Gelen prompt'u intent/topic/persona/funnel sınıflarına ayırma
- **Visibility Score PoC**: 7 bileşenli Visibility Index hesaplama, ağırlıklandırma, duyarlılık analizi

**2. Performans raporları**
- Her PoC için: işlem süresi (ms), bellek kullanımı (MB), QPS (query per second)
- Ölçeklenebilirlik notları: 10× veride nasıl performans gösterir?
- Darboğaz analizi

**3. Doğruluk metrikleri**
- Her PoC için: accuracy, precision, recall, F1-score, confusion matrix
- Gold dataset üzerinde doğrulama sonuçları
- Edge case analizi: hangi senaryolarda başarısız? İyileştirme önerileri
- Minimum kabul eşiği: tüm metric'lerde > %80

**Kod/Doküman Karşılığı:**

| Çıktı | Nerede Kullanılır | Ne İşe Yarar | Açıklama |
|-------|-------------------|-------------|----------|
| Citation Extraction PoC | `platform/engine/` + `platform/internal/measure/` | AI cevabındaki kaynakların otomatik çıkarımı çalışır mı? Risk almadan önce kanıtlanır | Citation çıkarma algoritmasının referans implementasyonu |
| Entity Extraction PoC | `platform/docs/04-ai-framework/0407-entity-recognition.md` | "Marka mı, teknoloji mi?" ayrımının yapılabildiği kanıtlanır | Entity tanıma yaklaşımının doğrulanması |
| Recommendation Detection PoC | `platform/internal/recommendation/` | AI'nın "X'i öneririm" dediğini tespit edebiliyor muyuz? Kod yazılmadan önce doğrulanır | Öneri tespit mantığının prototipi |
| Prompt Classification PoC | `platform/internal/prompt/` | "Tavsiye mi, karşılaştırma mı?" sorusunun sınıflandırılabildiği kanıtlanır | Prompt sınıflandırma modeli |
| Visibility Score PoC | `platform/internal/measure/service.go` | Visibility Index matematiksel modelinin gerçek veride çalıştığı gösterilir | Skor algoritmasının prototip implementasyonu |

**Başarı Kriteri:** Her PoC için doğruluk (accuracy/precision/recall) metrikleri raporlanmış, minimum %80 başarı oranı sağlanmış olmalı.

---

### WP-13 · Patent & Intellectual Property

| Alan | Detay |
|------|-------|
| Amaç | Fikri mülkiyet oluşturmak |
| Kritiklik | ⭐⭐⭐⭐☆ |

**Araştırma Soruları:**
- GeoLens'in hangi algoritmaları patentlenebilir nitelikte?
- Prior art durumu nedir?
- Hangi patent başvuru stratejisi izlenmeli?

**Teslimatlar:**

**1. Patent Disclosure**
- Her aday algoritma için: teknik açıklama, yenilik unsuru, mevcut çözümlerden farkı, patent başlığı ve özet
- Disclosure formatı: patent başvurusuna hazır, patent vekiline verilecek seviyede detaylı
- En az 1 adet tamamlanmış disclosure

**2. Prior Art Analysis**
- Her aday algoritma için: benzer patent taraması (USPTO, WIPO, Google Patents)
- Prior art bulunduysa: farklılaşma analizi — bizimkinin farkı ne?
- Prior art bulunamadıysa: yenilik iddiasını güçlendiren argümanlar
- Patent alabilirlik değerlendirmesi: yüksek/orta/düşük olasılık

**3. Patent Adayları Listesi**
- En az 3 patent adayı, her biri için: algoritma adı, teknik alan, yenilik özeti, patent sınıfı, öncelik (yüksek/orta/düşük)
- Başvuru takvimi önerisi: hangi aday önce başvurulmalı?
- Tahmini maliyet ve süre

**Kod/Doküman Karşılığı:**

| Çıktı | Nerede Kullanılır | Ne İşe Yarar | Açıklama |
|-------|-------------------|-------------|----------|
| Patent Disclosure | `platform/docs/01-business/0108-investor-thesis.md` | Yatırımcıya "Bu algoritma patentli, rakip kopyalayamaz" denir, şirket değerlemesi artar | Yatırımcı tezinde IP varlığı |
| Prior Art Analysis | `platform/docs/adr/` | Patent başvurusu reddedilmeden önce benzer patentler taranır, başvuru başarı oranı artar | ADR kararlarında patent değerlendirmesi |
| Patent Adayları | `platform/docs/02-product/0209-backlog.md` | Hangi algoritmaların patentlenebileceği bilinir, Ar-Ge yatırımı doğru alana yapılır | Backlog'da IP kalemi |

**Başarı Kriteri:** En az 3 patent adayı belirlenmiş, prior art taraması tamamlanmış, en az 1 disclosure hazırlanmış olmalı.

---

### WP-14 · Whitepaper

| Alan | Detay |
|------|-------|
| Amaç | GeoLens metodolojisini akademik ve sektörel olarak yayınlamak |
| Kritiklik | ⭐⭐⭐⭐☆ |

**Araştırma Soruları:**
- AI Visibility Framework hangi akademik çalışmalarla desteklenmeli?
- Hangi konferans/dergi hedef kitleyle örtüşüyor?

**Teslimatlar:**

**1. Whitepaper v1**
- 10-15 sayfa, akademik formatta (abstract, introduction, methodology, results, conclusion, references)
- Konu: "AI Visibility: A Measurement Framework for Brand Presence in LLM Responses"
- Literatür taraması: AI görünürlüğü alanında mevcut çalışmalar
- GeoLens metodolojisi detaylı anlatım: GAVF, skorlama, citation framework
- Pilot çalışma sonuçları (varsa)
- Referanslar: 30+ akademik kaynak

**2. Konferans Sunumu Taslağı**
- 15-20 slayt: problem → metodoloji → bulgular → sonuç
- Hedef konferans listesi: BrightonSEO, SearchLove, SMX, MozCon, AI Summit
- Her konferans için: sunum özeti (CFP submission), hedef kitle, mesaj stratejisi

**3. Blog Serisi**
- 4-6 blog yazısı:
  1. "AI Visibility Nedir ve Neden Önemlidir?"
  2. "ChatGPT'te Markanız Ne Sıklıkta Geçiyor?" (ölçüm metodolojisi)
  3. "AI Motorlarında Citation: Doğrudan Alıntı mı, Yönlendirme mi?"
  4. "SEO'den GEO'ya: AI Çağında Görünürlük Stratejisi"
  5. "Visibility Index: Markanızın AI Skorunu Nasıl Ölçeriz?"
  6. "2026 AI Search Trendleri: Neler Değişiyor?"
- Her yazı için: anahtar kelime araştırması, SEO optimizasyonu, görsel önerileri

**Kod/Doküman Karşılığı:**

| Çıktı | Nerede Kullanılır | Ne İşe Yarar | Açıklama |
|-------|-------------------|-------------|----------|
| Whitepaper | `specification/docs/04-whitepapers/0401-ai-visibility-whitepaper.md` | Sektörde "AI Visibility" denince akla GeoLens gelir — düşünce liderliği pozisyonu | Açık standart whitepaper |
| Konferans Sunumu | `platform/docs/01-business/0107-sales-playbook.md` | Konferanslarda sunum yapılır, potansiyel müşteri lead'i toplanır | Satış ve pazarlama materyali |
| Blog Serisi | `platform/docs/01-business/0106-go-to-market.md` | Organik trafik çekilir, "AI Visibility" aramalarında GeoLens bulunur | İçerik pazarlama stratejisi |

**Başarı Kriteri:** Whitepaper taslağı tamamlanmış, hedef konferans listesi belirlenmiş, en az 3 blog yazısı hazırlanmış olmalı.

---

### WP-15 · AI Research Backlog

| Alan | Detay |
|------|-------|
| Amaç | Sürekli araştırma kültürü oluşturmak |
| Kritiklik | ⭐⭐⭐☆☆ |

**Araştırma Soruları:**
- AI görünürlüğü alanında yayınlanan güncel akademik makaleler neler?
- Yeni çıkan AI araçları ve platformları hangi fırsatları sunuyor?
- Hangi deneyler yapılmalı?

**Teslimatlar:**

**1. Monthly Research Report**
- 10 yeni akademik makale özeti: araştırma sorusu, yöntem, bulgular, GeoLens için çıkarımlar
- 5 yeni araç/platform analizi: özellik, potansiyel tehdit/fırsat, entegrasyon olasılığı
- 3 yeni deney önerisi: hipotez, yöntem, beklenen çıktı, kaynak ihtiyacı
- AI sektör haberleri özeti

**2. Literature Review**
- AI görünürlüğü, LLM evaluation, citation analysis, brand mention detection alanlarında kümülatif literatür veritabanı
- Her makale için: bibtex, özet, anahtar bulgular, GeoLens ile ilişkisi
- Zaman içinde literatür trend analizi

**3. Innovation Backlog**
- Önerilen deneylerin öncelikli listesi
- Her deney için: hipotez, yöntem, beklenen etki, kaynak ihtiyacı, risk değerlendirmesi
- Aylık güncellenen öncelik sıralaması

**Kod/Doküman Karşılığı:**

| Çıktı | Nerede Kullanılır | Ne İşe Yarar | Açıklama |
|-------|-------------------|-------------|----------|
| Research Report | `platform/docs/02-product/0209-backlog.md` | Rakiplerin yeni özelliği veya yeni bir AI standardı kaçırılmaz, ürün güncel kalır | Backlog besleme |
| Literature Review | `platform/docs/04-ai-framework/` | Akademik gelişmeler framework dokümanlarına işlenir, metodoloji bilimsel temelde kalır | Framework dokümanlarına referans |
| Innovation Backlog | `platform/docs/02-product/0205-mvp.md` | "Gelecekte ne yapacağız?" sorusunun cevabı burada birikir, Ar-Ge yönü belirlenir | MVP sonrası inovasyon havuzu |

**Başarı Kriteri:** Her ay en az 10 akademik makale incelenmiş, 5 araç analiz edilmiş, 3 yeni deney önerisi sunulmuş olmalı.

---

## 3. Öncelik Sırası ve Takvim

| Sıra  | Work Package                   | Kritiklik  |     Bağımlılık      |
|:-----:|--------------------------------|:----------:|:-------------------:|
|   1   | WP-01 AI Visibility Landscape  | ⭐⭐⭐⭐⭐    |          —          |
|   2   | WP-02 Competitive Intelligence | ⭐⭐⭐⭐⭐    |        WP-01        |
|   3   | WP-03 AVMS                     | ⭐⭐⭐⭐⭐    |        WP-01        |
|   4   | WP-04 Prompt Taxonomy          | ⭐⭐⭐⭐⭐    |        WP-03        |
|   5   | WP-05 Gold Standard Dataset    |  ⭐⭐⭐⭐⭐    |        WP-04        |
|   6   | WP-06 LLM Evaluation Framework |   ⭐⭐⭐⭐⭐    |        WP-03        |
|   7   | WP-07 Citation Framework       |   ⭐⭐⭐⭐☆    |        WP-03        |
|   8   | WP-08 Entity Recognition       |   ⭐⭐⭐⭐☆    |    WP-05, WP-06     |
|   9   | WP-09 Visibility Index         |   ⭐⭐⭐⭐⭐    | WP-03, WP-05, WP-07 |
|  10   | WP-10 Opportunity Engine       |   ⭐⭐⭐⭐☆    |        WP-09        |
|  11   | WP-11 AI Benchmark             |   ⭐⭐⭐⭐☆    |        WP-06        |
|  12   | WP-12 PoC Geliştirme           |   ⭐⭐⭐⭐⭐    | WP-07, WP-08, WP-09 |
|  13   | WP-13 Patent & IP              |   ⭐⭐⭐⭐☆    |    WP-09, WP-12     |
|  14   | WP-14 Whitepaper               |   ⭐⭐⭐⭐☆    |    WP-03, WP-09     |
|  15   | WP-15 Sürekli Araştırma        |   ⭐⭐⭐☆☆    |          —          |

### Bağımlılık Grafiği

```
WP-01 ──→ WP-02
  │
  └──→ WP-03 ──→ WP-04 ──→ WP-05 ──→ WP-08 ──→ WP-12
         │         │                    │
         │         └──→ WP-06 ──→ WP-11  │
         │                                │
         └──→ WP-07 ──────────────────────┘
                │
                └──→ WP-09 ──→ WP-10
                         │
                         ├──→ WP-13
                         └──→ WP-14

WP-15 (sürekli, tüm paketleri besler)
```

---

## 4. Kod/Doküman Haritası (Özet)

| Paket | WP Katkısı |
|-------|:----------:|
| `platform/engine/` | WP-01 (adapter seçimi), WP-07 (citation model), WP-11 (benchmark), WP-12 (PoC) |
| `platform/internal/measure/` | WP-05 (gold dataset), WP-07 (citation scoring), WP-08 (entity extraction), WP-09 (visibility index), WP-12 (PoC) |
| `platform/internal/recommendation/` | WP-10 (rule set), WP-12 (PoC) |
| `platform/internal/prompt/` | WP-04 (taxonomy), WP-12 (PoC) |
| `platform/internal/benchmark/` | WP-06 (evaluation), WP-08 (entity benchmark), WP-11 (AI benchmark) |
| `platform/internal/guardrail/` | WP-06 (hallucination detection) |
| `platform/internal/optimize/` | WP-10 (opportunity scoring) |
| `platform/internal/bias/` | WP-06 (fairness metrics) |
| `platform/docs/04-ai-framework/` | WP-03 (AVMS), WP-04 (taxonomy), WP-07 (citation), WP-08 (entity), WP-09 (visibility), WP-10 (opportunity) |
| `platform/docs/01-business/` | WP-01 (landscape), WP-02 (competitive), WP-13 (patent), WP-14 (whitepaper) |
| `platform/docs/02-product/` | WP-01 (risk), WP-02 (gap), WP-13 (IP backlog), WP-15 (innovation) |
| `specification/docs/` | WP-03 (metrics), WP-14 (whitepaper) |

---

## 5. Değerlendirme Kriterleri

Her iş paketi için teslimat kalitesi şu kriterlerle değerlendirilir:

| Kriter | Açıklama |
|--------|----------|
| **Tamamlanma** | Teslimat listesindeki tüm maddeler eksiksiz sunulmuş mu? |
| **Derinlik** | Analiz yüzeysel mi yoksa detaylı ve eyleme dönüştürülebilir mi? |
| **Kod/Doküman Entegrasyonu** | Çıktılar ilgili kod/doküman dosyalarına işlenmiş mi? |
| **Doğrulanabilirlik** | İddialar veri ve referanslarla desteklenmiş mi? |
| **Tekrarlanabilirlik** | Başka bir araştırmacı aynı yöntemle aynı sonuçlara ulaşabilir mi? |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 27.07.2026 | İlk yayın: 15 iş paketi, kod/doküman haritası, bağımlılık grafiği, öncelik sırası. |
| 1.1 | 27.07.2026 | "Ne İşe Yarar" sütunu eklendi — her teslimatın hangi ürün özelliğini mümkün kıldığı belirtildi. |
| 1.2 | 27.07.2026 | Teslimat içerikleri detaylandırıldı — her teslimatın içinde hangi bölümlerin olacağı, hangi soruları cevaplayacağı tanımlandı. |
