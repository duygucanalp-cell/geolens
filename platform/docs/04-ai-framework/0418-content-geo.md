# 0418 · İçerik GEO (Content GEO)

| Alan | Değer |
|---|---|
| Doküman ID | 0418 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · AI |
| Tarih | 27 Temmuz 2026 |
| İlişkili | 0417, 0401, 0402, 0408, 0409, 0411, 0412, 0413, 0204, 0207, 0312, **docs/AI_Visibility_Generative_Search_Intelligence_Platform.md** |

---

## 1. Amaç

Bu doküman, web sitesi içeriğinin AI motorları tarafından **daha sık alıntılanması, daha iyi anlaşılması ve daha doğru şekilde önerilmesi** için yapılabilecek **içerik GEO (Generative Engine Optimization)** müdahalelerini tanımlar.

Turkcell RFP'deki aşağıdaki gereksinimleri karşılar:

| RFP Gereksinimi | FR Karşılığı (0204) |
|:----------------|:-------------------:|
| İçerik Açığı (Content Gap) Analizi | FR-E5 |
| GEO İçerik Önerileri: Topic Cluster, FAQ, Entity, semantik/LSI optimizasyon | FR-E6 |
| Semantik Optimizasyon Önerileri | FR-E6 |
| Eksik konu alanlarının tespiti | FR-E5 |
| Doğruluk ve İtibar Kontrolü (Hallucination & Sentiment) | FR-D7, FR-D8 (Bkz. 0416) |

---

## 2. GAVF Katmanı

Bu doküman GAVF'in **S5 (GEO Standardı)** katmanına eklenir:

| Katman | Adı | Dokümanlar |
|:------:|-----|:-----------:|
| **S5** | GEO Standardı | 0417 (Teknik GEO), **0418 (Content GEO)** |

S5 katmanı, S3 (Skor) ve S4 (Aksiyon) katmanlarından elde edilen bulguları içerik stratejisi önerilerine dönüştürür. Content GEO, AI motorlarının hangi içerikleri neden alıntıladığını ve hangi içeriklerin eksik olduğunu analiz eder.

---

## 3. Content Gap Analizi (FR-E5)

### 3.1 Amaç

AI sistemlerinin sektörel sorularda hangi kaynakları/içerikleri eksik bulduğunu tespit eder. Bu, markanın AI kanalındaki içerik boşluklarını belirleyerek yeni içerik fırsatlarını haritalandırır.

### 3.2 Content Gap Türleri

| Gap Türü | Açıklama | Tespit Yöntemi |
|:--------:|----------|----------------|
| **Konu boşluğu** | Markanın hiç içerik üretmediği konular | Rakip içerik envanteri + AI sorgu analizi |
| **Derinlik boşluğu** | Yüzeysel içerik, derinlemesine değil | İçerik uzunluğu/kapsam karşılaştırması |
| **Güncellik boşluğu** | Eski içerik, güncel bilgi yok | Tarih karşılaştırması |
| **Format boşluğu** | AI'nın tercih ettiği formatta içerik yok | Rakip format analizi (FAQ, rehber, liste) |
| **Otorite boşluğu** | Düşük otoriteli kaynaklardan alıntılanma | 0410 Authority Score |

### 3.3 Tespit Yöntemi

| Yöntem | Açıklama | MVP |
|:-------|----------|:---:|
| **Rakip kaynak taraması** | Rakiplerin hangi konularda alıntılandığının analizi | ✅ |
| **AI sorgu modeli** | Sektörde sık sorulan soruların tespiti | ✅ |
| **Prompt-set coverage** | Markanın prompt setinde hangi sorularda geçmediği | ✅ |
| **Citation domain analizi** | AI'nın hangi domainleri kaynak gösterdiği | ✅ |
| **Topic modeling** | AI yanıtlarındaki konu dağılımı analizi | 🔴 (HT1) |

### 3.4 Content Gap Raporu

```json
{
  "brand": "Acme",
  "period": "last_30_days",
  "total_gaps": 12,
  "gaps": [
    {
      "type": "konu_boşluğu",
      "topic": "AI güvenliği ve etik",
      "severity": "high",
      "description": "Marka AI güvenliği konusunda hiç içerik üretmemiş, rakipler bu konuda 5+ makaleye sahip",
      "competitor_citations": 12,
      "brand_citations": 0,
      "opportunity_score": 85,
      "recommendation": "AI güvenliği ve etik konusunda kapsamlı bir rehber oluşturun"
    },
    {
      "type": "derinlik_boşluğu",
      "topic": "Ürün karşılaştırmaları",
      "severity": "medium",
      "description": "Ürün karşılaştırma içerikleri mevcut ancak yüzeysel (ortalama 300 kelime, rakipler 1500+)",
      "competitor_avg_length": 1500,
      "brand_avg_length": 300,
      "opportunity_score": 70,
      "recommendation": "Ürün karşılaştırma sayfalarını detaylandırın"
    }
  ],
  "opportunity_score": {
    "overall": 72,
    "by_category": {
      "konu_boşluğu": 85,
      "derinlik_boşluğu": 70,
      "güncellik_boşluğu": 45,
      "format_boşluğu": 60,
      "otorite_boşluğu": 55
    }
  }
}
```

### 3.5 Fırsat Puanı

Her content gap için bir fırsat puanı hesaplanır (0-100):

```
fırsat_puanı = (rakip_alıntı_sayısı - marka_alıntı_sayısı) / max(rakip_alıntı, 1) × 40
              + (konu_önemi) × 30
              + (AI_sorgu_sıklığı) × 30
```

| Puan | Anlamı | Aksiyon |
|:----:|--------|---------|
| 70+ | 🟢 Yüksek öncelik | Hemen içerik üretilmeli |
| 40-70 | 🟡 Orta öncelik | Kısa vadeli plana alınmalı |
| <40 | 🔴 Düşük öncelik | Uzun vadeli değerlendirme |

---

## 4. GEO İçerik Önerileri (FR-E6)

### 4.1 Topic Cluster Önerileri

Topic Cluster, bir ana konu (pillar) etrafında gruplanmış alt konulardan oluşan içerik stratejisidir.

| Bileşen | Açıklama | AI Görünürlüğüne Etkisi |
|:-------:|----------|:-----------------------:|
| **Pillar Page** | Ana konuyu kapsamlı şekilde ele alan sayfa | Yüksek — AI tarafından otorite kaynağı olarak görülür |
| **Cluster Content** | Alt konuları detaylandıran makaleler | Orta — pillar'ı destekler, uzun kuyruk sorguları yakalar |
| **Internal Links** | Cluster → Pillar bağlantıları | Yüksek — site otoritesini dağıtır |

#### 4.1.1 Topic Cluster Oluşturma Süreci

```
1. Ana konu belirleme (sektör + marka uzmanlığı)
2. Alt konuları belirleme (AI sorgu analizi + rakip analizi)
3. Mevcut içerik envanteri (hangi konular kapsanıyor?)
4. Boşluk analizi (hangi alt konular eksik?)
5. İçerik takvimi oluşturma (önceliklendirme)
6. Internal link yapısı tasarımı
```

#### 4.1.2 Örnek Topic Cluster

```
Pillar: "AI ve Dijital Dönüşüm"
├── Cluster 1: "Yapay Zeka Nedir?" (giriş seviyesi)
│   ├── "AI Türleri: Makine Öğrenimi, Derin Öğrenme"
│   └── "AI'nın Tarihçesi ve Geleceği"
├── Cluster 2: "AI Sektörel Uygulamalar"
│   ├── "AI Finans Sektöründe"
│   ├── "AI Sağlık Sektöründe"
│   └── "AI Perakende Sektöründe"
└── Cluster 3: "AI ve SEO"
    ├── "GEO Nedir? Generative Engine Optimization"
    ├── "AI Görünürlüğü Nasıl Ölçülür?"
    └── "2026 AI SEO Trendleri"
```

### 4.2 FAQ Önerileri

AI motorları, kullanıcı sorularına doğrudan yanıt vermek için FAQ içeriklerini sıkça kullanır.

| Öneri | Açıklama | Öncelik |
|:------|----------|:-------:|
| **Sektörel SSS oluşturma** | Sektörde sık sorulan 20+ soruyu kapsayan FAQ sayfası | Yüksek |
| **Ürün bazlı SSS** | Her ürün için ayrı SSS bölümü | Yüksek |
| **Karşılaştırma SSS** | "X vs Y" formatında karşılaştırma soruları | Orta |
| **HowTo SSS** | "Nasıl yapılır?" sorularına adım adım yanıtlar | Orta |
| **Güncel SSS** | AI trendlerine göre güncellenen soru bankası | Düşük |

#### 4.2.1 FAQ Şablonu

```json
{
  "faq_section": {
    "topic": "AI Görünürlük Ölçümü",
    "questions": [
      {
        "question": "AI görünürlüğü nedir?",
        "answer": "AI görünürlüğü, bir markanın yapay zeka destekli arama motorlarında (ChatGPT, Gemini, Perplexity) ne sıklıkta ve nasıl göründüğünü ölçen metriktir.",
        "schema_type": "FAQ",
        "target_keywords": ["AI görünürlük", "AI visibility", "GEO ölçümü"]
      },
      {
        "question": "GEO ile SEO arasındaki fark nedir?",
        "answer": "SEO, geleneksel arama motorları (Google, Bing) için optimize ederken, GEO (Generative Engine Optimization) AI destekli arama motorları için optimize eder.",
        "schema_type": "FAQ",
        "target_keywords": ["GEO vs SEO", "generative engine optimization", "AI SEO"]
      }
    ]
  }
}
```

### 4.3 Entity Geliştirme Önerileri

Entity (varlık), AI motorlarının bir markayı, ürünü veya kişiyi tanımasını sağlayan yapılandırılmış bilgidir.

| # | Öneri | Açıklama | AI Etkisi |
|:-:|-------|----------|:---------:|
| 1 | **Marka entity'si oluşturma** | Marka adı, sektör, kuruluş yılı, ürünler | Doğru tanıma |
| 2 | **Ürün entity'si oluşturma** | Her ürün için ayrı entity (özellikler, fiyat, kategori) | Doğru alıntılama |
| 3 | **Kişi entity'si oluşturma** | CEO, kurucu, uzman kadro | Otorite sinyali |
| 4 | **Entity ilişkilendirme** | Marka-ürün, makale-yazar, ürün-kategori | Bağlam zenginliği |
| 5 | **Entity güncelleme** | Değişen bilgilerin (yeni ürün, yeni CEO) güncellenmesi | Güncellik |

### 4.4 Semantik / LSI Optimizasyon Önerileri

AI motorları, anahtar kelime eşlemesinden çok anlamsal (semantik) ilişkilere bakar.

| # | Öneri | Uygulama |
|:-:|-------|----------|
| 1 | **LSI kelime kullanımı** | Ana konu etrafındaki ilgili terimleri doğal şekilde kullanın |
| 2 | **Eş anlamlı çeşitliliği** | Aynı kavramı farklı kelimelerle ifade edin |
| 3 | **Bağlam zenginliği** | İçeriğin sadece anahtar kelime değil, konuyu kapsamlı şekilde ele almasını sağlayın |
| 4 | **Doğal dil** | AI tarafından okunabilir, doğal cümle yapıları kullanın (anahtar kelime doldurma yok) |
| 5 | **Soruları yanıtlama** | Kullanıcıların sorabileceği soruları içerikte yanıtlayın |

#### 4.4.1 Semantik Optimizasyon Örneği

```
Hedef anahtar kelime: "AI görünürlük ölçümü"

LSI/ilgili terimler:
- generative engine optimization
- AI visibility score
- LLM analytics
- citation analysis
- share of voice (AI)
- brand mention rate
- AI search monitoring
- GEO metrics

İçerik yapısı:
1. AI görünürlüğü nedir? (tanım + bağlam)
2. Neden önemlidir? (kullanıcı davranışı değişimi)
3. Nasıl ölçülür? (metodoloji + metrikler)
4. Hangi araçlar kullanılır? (ekosistem)
5. Rakiplerden nasıl ayrışılır? (strateji)
=> AI motoru bu içeriği "AI görünürlük" konusunda kapsamlı ve otoriter bir kaynak olarak değerlendirir
```

---

## 5. Content Hub Stratejisi

Content Hub, AI motorlarının bir markayı belirli bir konuda otorite olarak tanıması için gerekli içerik ekosistemidir.

### 5.1 Content Hub Bileşenleri

| Bileşen | Açıklama | AI Etkisi |
|:-------:|----------|:---------:|
| **Pillar Content** | Konuyu kapsamlı şekilde ele alan ana sayfa | Yüksek |
| **Supporting Content** | Alt konuları detaylandıran sayfalar | Orta |
| **Data/Research** | Özgün veri ve araştırma içerikleri | Yüksek |
| **Expert Opinions** | Uzman görüşleri ve röportajlar | Orta |
| **Case Studies** | Gerçek müşteri başarı hikayeleri | Yüksek |
| **Comparisons** | Ürün/hizmet karşılaştırmaları | Orta |
| **Guides/Tutorials** | Kapsamlı rehberler | Yüksek |

### 5.2 Content Hub Puanı

AI motorlarının bir markayı belirli bir konuda otorite olarak görme olasılığını ölçer:

```
hub_puanı = pillar_varlığı × 30
            + destekleyici_içerik_sayısı (max 5) × 10
            + özgün_veri_varlığı × 20
            + güncellik (ay) × 10 (max 30)
            + internal_link_yapısı × 10
```

---

## 6. API Tasarımı

```
GET    /v1/content-geo/gaps/{brand_id}             — Content gap analizi
GET    /v1/content-geo/recommendations/{brand_id}  — GEO içerik önerileri
GET    /v1/content-geo/topics/{brand_id}           — Topic cluster önerileri
GET    /v1/content-geo/faq/{brand_id}              — FAQ önerileri
GET    /v1/content-geo/hub-score/{brand_id}        — Content Hub puanı
GET    /v1/content-geo/semantic/{brand_id}         — Semantik optimizasyon önerileri
```

---

## 7. Dashboard Entegrasyonu

Operasyonel Dashboard (FR-F9) içinde:

| Bileşen | Açıklama |
|:--------|----------|
| **Content Gap Skoru** | Genel içerik boşluğu puanı (0-100) |
| **Öncelikli Gap Listesi** | Fırsat puanına göre sıralanmış içerik boşlukları |
| **Topic Cluster Önerileri** | AI sorgu analizine dayalı topic cluster haritası |
| **FAQ Öneri Listesi** | Sektörde sık sorulan sorulara dayalı FAQ önerileri |
| **Rakip İçerik Kıyası** | Marka- rakip içerik kapsam karşılaştırması |

---

## 8. Öneri Motoru Entegrasyonu (0413 ile bağlantı)

Content GEO çıktıları, 0413 Öneri Motoru'na girdi sağlar:

| Content GEO Çıktısı | Öneri Türü (0413) | Kanıt Derecesi |
|:-------------------|:-----------------:|:--------------:|
| Content Gap tespiti | "Konu X'te içerik oluşturun (rakip Y alıntılanıyor)" | Deneysel |
| Topic Cluster önerisi | "Topic cluster Y'yi genişletin (Z alt konusu eksik)" | Korelasyonel |
| FAQ önerisi | "SSS sayfasına X sorusunu ekleyin (AI'da sık geçiyor)" | Denenebilir |
| Semantik optimizasyon | "İçerikte X terimini kullanın (rakipler kullanıyor)" | Deneysel |
| Entity geliştirme | "Marka entity'sine X alanını ekleyin" | Denenebilir |

---

## 9. GeoLens İçin Çıkarımlar

1. **Content GEO bir ölçüm değil, bir öneri disiplinidir.** Skor üretmez, ancak FR-E5 ve FR-E6 kapsamında içerik stratejisi önerileri sağlar.
2. **Content Gap Analizi**, 0411'deki Competitive Gap Analysis'i tamamlar. Orada tespit edilen boşlukların içerik tarafını analiz eder.
3. **Topic Cluster ve FAQ önerileri**, AI motorlarının yapılandırılmış içeriği daha kolay alıntılaması prensibine dayanır.
4. **Semantik optimizasyon**, geleneksel SEO'nun LSI kelime stratejisinin AI çağındaki karşılığıdır.
5. **Öneri motoru entegrasyonu:** Content GEO çıktıları doğrudan 0413 Recommendation Engine'e kanıt dereceli öneri olarak beslenir.
6. **Specification bağlantısı:** Content GEO metodolojisi, GAVF S5 (GEO Standardı) kapsamında specification reposuna eklenmelidir.

---

## 10. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | Content Gap analizi için rakip içerik taraması ne sıklıkla yapılmalı? | ⏳ MVP'de haftalık tarama. Otomatik aylık tarama HT1. |
| O-2 | Topic Cluster önerileri için AI sorgu modeli nasıl beslenecek? | ⏳ Prompt seti verisi + sektörel araştırma. |
| O-3 | FAQ önerileri otomatik mi oluşturulacak yoksa manuel mi derlenecek? | ⏳ MVP'de AI sorgu analizine dayalı yarı otomatik. Tam otomatik HT1. |

---

## Kaynaklar

- **Turkcell AI Visibility Platform RFP:** `docs/AI_Visibility_Generative_Search_Intelligence_Platform.md`
- 0204 PRD — FR-E5 (content gap), FR-E6 (GEO içerik önerileri)
- 0207 Feature Catalog — FR-E5, FR-E6 özellik tanımları
- 0417 Technical GEO — S5 GEO Standardı teknik boyutu
- 0401 AI Visibility Standard — GAVF S5 katmanı
- 0402 Prompt Taxonomy — prompt türleri ve sınıflandırması
- 0408 Topic Classification — konu sınıflandırma
- 0409 Visibility Score — appearance rate bileşeni
- 0411 Share of Voice — competitive gap, içerik boşluğu
- 0412 Opportunity Engine — fırsat tespiti
- 0413 Recommendation Engine — öneri üretimi ve kanıt dereceleri
- 0312 Conversation Replay — AI yanıt arşivi, değişim tespiti

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 27.07.2026 | İlk yayın: Content GEO metodolojisi. Content Gap Analizi (5 gap türü, fırsat puanı), GEO içerik önerileri (Topic Cluster, FAQ, Entity, semantik/LSI optimizasyon), Content Hub stratejisi ve puanı, Öneri Motoru entegrasyonu. Turkcell RFP gereksinimlerini karşılar (FR-E5, FR-E6). |
