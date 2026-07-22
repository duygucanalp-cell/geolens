# 0301 · Çekirdek Kavramlar

| Alan | Değer |
|---|---|
| Doküman ID | 0301 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0006, 0204, 0302, 0303, 0304, 0305, 0306 |

---

## 1. Amaç

Bu doküman, AI görünürlük alanının temel kavramlarını ve bu kavramlar arasındaki ilişkileri sabitler. GeoLens Platform'un inşa edildiği zihinsel modeli tanımlar; 0302 (Domain Model), 0303 (Aggregates), 0304 (Domain Events), 0305 (Bounded Contexts) ve 0306 (Domain Services) bu kavramlar üzerine türetilir.

> **Tasarım filtresi bağlantısı:** Bu doküman **F5** (moat — kavramsal netlik rakip taklidini zorlaştırır) ve **F6** (kategori — kavramlar GAVF standardıyla hizalanır) filtrelerine kanıt sağlar.

---

## 2. Kavram Hiyerarşisi

AI görünürlük alanı dört katmandan oluşur:

```
İzleme Katmanı (observation)
    ↓
Ölçüm Katmanı (measurement)
    ↓
Skorlama Katmanı (scoring)
    ↓
Aksiyon Katmanı (action)
```

| Katman | Sorumluluk | Temel Kavramlar |
|--------|-----------|-----------------|
| **İzleme** | AI motorlarında markanın nasıl göründüğünü gözlemleme | Prompt, motor, ham yanıt, alıntı, kademe |
| **Ölçüm** | Gözlemleri örneklemeli, tekrarlanabilir biçimde toplama | Panel, ölçüm işi, örnekleme, idempotent anahtar |
| **Skorlama** | Ham veriyi bileşik, yorumlanabilir skora dönüştürme | Calculation run, faktör, fidelite etiketi, güven aralığı |
| **Aksiyon** | Skor değişimlerinden öneri, uyarı ve rapor üretme | Öneri, uyarı, rapor, bildirim kanalı |

---

## 3. Çekirdek Kavramlar

### 3.1 AI Görünürlük (AI Visibility)

Bir markanın, büyük dil modelleri (ChatGPT, Gemini, Perplexite, Claude vb.) tarafından üretilen yanıtlarda ne sıklıkta, hangi bağlamda ve hangi nitelikte göründüğünün ölçümüdür. Geleneksel SEO'dan farkı: arama motoru sıralaması değil, **olasılıksal dil modeli çıktısındaki varlık, konum ve bağlamdır**.

> **Temel denklem:** Görünürlük = f(varlık, konum, kaynak güvenilirliği, rakip bağlamı)

### 3.2 Panel (Panel)

Prompt seti, motor kapsamı ve pazarın birlikte dondurulmuş halidir. Ölçüm dürüstlüğünün temel birimidir. İki eksenli versiyonlama taşır: **ne sorulduğu** (panel versiyonu) ve **nasıl hesaplandığı** (algoritma versiyonu). Panel değişmedikçe skorlar doğrudan karşılaştırılabilir.

### 3.3 Motor Kademesi (Engine Tier)

AI motorlarının erişim yöntemine göre sınıflandırılması (0102'den türetilmiştir):

| Kademe | Anlamı | Örnek |
|:------:|--------|-------|
| **Kademe 1 — Doğrudan (direct)** | API doğrudan AI yanıtını döndürür | Perplexity Sonar |
| **Kademe 2 — Resmî Vekil (official_proxy)** | Resmî arama/grounding API'si üzerinden | ChatGPT Responses API, Gemini Grounding |
| **Kademe 3 — Yönsel (directional)** | Dolaylı sinyallerle çıkarım | Copilot, tarayıcı tabanlı gözlem |

Fidelite etiketi, bir skorun hangi kademeden üretildiğini taşır ve hiçbir yüzeyde atlanamaz (İ2, FR-C5).

### 3.4 Fidelite Etiketi (Fidelity Label)

Her skorun üzerinde taşıdığı, ölçümün hangi kademeden ve hangi güven düzeyinden geldiğini gösteren zorunlu etiket. Etiketsiz skor yayınlanamaz.

Bileşenleri: kademe (1/2/3), motor adı, örnekleme sayısı (n), güven aralığı (alt/üst).

### 3.5 Alıntı (Citation)

AI yanıtında markanın göründüğü kaynağın URL, başlık ve konum bilgisini taşıyan meta veri. Her skorun altında kaynağa tıklanabilir biçimde gösterilir (FR-D2).

### 3.6 Calculation Run

Aynı girdilerle tekrarlandığında birebir aynı sonucu üreten, değiştirilemez skorlama kaydı. Girdi kümesi karması, faktör anlık görüntüsü ve algoritma versiyonunu saklar. Deterministik hesap ilkesinin (İ3) veri tabanı karşılığıdır.

### 3.7 Görünürlük Skoru (Visibility Score)

0-100 aralığında, dört bileşenin ağırlıklı toplamından oluşan bileşik metrik: varlık (marka prompt yanıtlarında geçiyor mu), konum (kaçıncı sırada), kaynak (güvenilirlik), rakip bağlamı (rakiplere göre durum). Her skor hesaplandığı panel versiyonuna ve calculation_run'a bağlanır.

### 3.8 Çalışma Alanı (Workspace)

Kiracı altındaki izole çalışma birimi. Ajans modelinde her müşteri ayrı bir çalışma alanıdır (FR-G1). KOBİ modelinde tek çalışma alanı yeterlidir. Her çalışma alanının kendi marka, prompt seti, izleme planı ve rapor şablonu vardır.

### 3.9 Öneri (Recommendation)

Kanıt derecesi etiketli (deneysel/korelasyonel/denenebilir), motor politikalarına aykırı olmayan aksiyon önerisi. Uygulandı/reddedildi olarak işaretlenebilir (FR-E3). HT1'de öneri-etki takibi ile beslenir (FR-E4).

### 3.10 White-label Rapor (White-label Report)

Ajansın kendi logosu, renkleri ve markasıyla müşterisine sunduğu, GAVF standartlarına uygun PDF rapor (FR-F4, FR-G2). Ajans segmentinin B2B2B çarpanının kalbidir.

---

## 4. Kavramlar Arası İlişkiler

```
Kiracı (Tenant)
  └── Çalışma Alanı (Workspace) [1-N]
        ├── Marka (Brand) [1-N]
        ├── Prompt Seti (PromptSet) [1-1]
        ├── İzleme Planı (MonitoringPlan) [1-1]
        ├── Panel Versiyonu (PanelVersion) [1-N]
        ├── Ölçüm İşi (MeasurementJob) [1-N]
        ├── Skor (Score) [1-N]
        ├── Öneri (Recommendation) [1-N]
        ├── Uyarı Kuralı (AlertRule) [1-N]
        └── Rapor (Report) [1-N]
              └── White-label Rapor (FR-G2) [0-N, Business paketi]

Panel Versiyonu (PanelVersion)
  ├── Prompt Seti içeriği [1-1]
  ├── Motor Kapsamı [1-1]
  └── Pazar [1-1]

Calculation Run (CalculationRun)
  ├── Faktör anlık görüntüsü [1-1]
  ├── Algoritma versiyonu [1-1]
  └── Skor (Score) [1-N]

Motor → Kademe (1/2/3) → Fidelite Etiketi
```

---

## 5. Kavram Olgunluk Modeli

Her kavram, ürün yaşam döngüsünde belirli bir olgunluk düzeyine ulaşır:

| Kavram | V1 (MVP) | HT1 | HT2 | Ufuk |
|--------|:---------:|:---:|:---:|:----:|
| AI Görünürlük | ✅ Temel | ✅ | ✅ | ✅ Gelişmiş |
| Panel | ✅ | ✅ | ✅ | ✅ |
| Motor Kademesi | ✅ 3 motor | ✅ 6 motor | ✅ | ✅ Yeni yüzeyler |
| Fidelite Etiketi | ✅ Zorunlu | ✅ | ✅ | ✅ |
| Alıntı | ✅ Temel | ✅ Derin | ✅ | ✅ |
| Calculation Run | ✅ Değiştirilemez | ✅ | ✅ | ✅ |
| Görünürlük Skoru | ✅ 4 bileşen | ✅ | ✅ | ✅ Tahmin |
| Çalışma Alanı | ✅ Temel | ✅ Arşiv | ✅ Devir | ✅ |
| Öneri | ✅ Kural tabanlı | ✅ Etki takibi | ✅ Öğrenen | ✅ |
| White-label Rapor | ✅ PDF | ✅ Zamanlanmış | ✅ API | ✅ |

---

## 6. Kavram-Gereksinim Bağları

| Kavram | FR/NFR Bağları |
|--------|---------------|
| Panel, Panel Versiyonu | FR-B1, FR-B2, FR-B3, FR-B5 |
| Ölçüm İşi | FR-C1, FR-C2, FR-C3, NFR-8 |
| Calculation Run | FR-C4, NFR-7 |
| Fidelite Etiketi | FR-C5, İ2 |
| Güven Aralığı | FR-C6 |
| Tazelik Damgası | FR-C7 |
| Alıntı | FR-D2 |
| Öneri | FR-E1, FR-E2, FR-E3, FR-E4 |
| Uyarı, Bildirim Kanalı | FR-F1, FR-F2, FR-F3 |
| White-label Rapor | FR-F4, FR-F5, FR-G2 |
| Çalışma Alanı | FR-G1, FR-G3 |
| Kiracı, Üyelik, Rol | FR-A1, FR-A2, FR-A3, FR-A5 |

---

## 7. GeoLens İçin Çıkarımlar

1. **Panel, ölçüm dürüstlüğünün temel birimidir.** Panel değişikliği yeni versiyon üretir, skorlar eski panelde kalır. Trend karşılaştırması aynı panel içinde yapılır.
2. **Calculation Run değiştirilemezdir.** Bu, NFR-7 (deterministik yeniden hesap) ve İ3 (açıklanabilirlik) ilkelerinin veri tabanı yansımasıdır.
3. **Fidelite etiketi tüm paketlerde zorunludur.** Free kademede dahi etiketsiz skor gösterilmez. Bu, güven inşasının olmazsa olmaz koşuludur.
4. **Kavramlar, GAVF standardı ile birebir eşlenir.** 0302 Domain Model'deki her kavramın specification reposunda karşılığı vardır.
5. **Çalışma alanı kavramı, ajans ve KOBİ segmentlerini tek platformda birleştirir.** İ1 (tek platform) ilkesinin alan düzeyindeki karşılığıdır.

---

## 8. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | Görünürlük skoru bileşen ağırlıkları | ⏳ 0309'da kalibrasyon yapılacak. AVIP D-89: AN hazırlar, TL+PO onaylar. |
| O-2 | Öneri kanıt derecelerinin kesin tanımı | ⏳ 0309 ile birlikte netleşir. AVIP D-52 (kural kütüphanesi) devralındı. |

### Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|----|-------|--------|
| **D-53** | **Walking skeleton:** 4 dilimli plan. PO+TL 21.07.2026. | AVIP 0301 O-1 |
| **D-72** | **Worker profilleri:** V1'de tek replika seti. TL 21.07.2026. | AVIP 0301 O-2 |
| **D-73** | **Redis kilit kaybı:** Anında pasif. TL 21.07.2026. | AVIP 0301 O-3 |

---

## Kaynaklar

- 0006 Glossary — sözlük ve terminoloji
- 0204 PRD — FR/NFR tanımları, ürün ilkeleri
- 0207 Feature Catalog — özellik-envanter bağları
- 0102 Rekabet Analizi — motor kademe modeli
- archive/avip-v1/0302-domain-model.md — AVIP alan modeli referansı

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: GeoLens çekirdek kavramları, 4 katmanlı hiyerarşi, 10 temel kavram tanımı, kavram ilişkileri, olgunluk modeli, FR/NFR bağları. 0204/0207/0102'den türetilmiştir. |
| 1.1 | 22.07.2026 | AVIP kapalı kararları taşındı: D-53 (walking skeleton), D-72 (worker), D-73 (kilit). Devralınan Kararlar eklendi. |
