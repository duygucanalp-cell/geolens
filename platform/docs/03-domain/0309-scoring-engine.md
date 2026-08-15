# 0309 · Hesap Motoru (Scoring Engine)

| Alan | Değer |
|---|---|
| Doküman ID | 0309 |
| Proje | GeoLens Platform |
| Versiyon | 1.4 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 12 Ağustos 2026 |
| İlişkili | 0306, 0409, 0410, 0411, 0302, 0606, 0204, 0308 |

---

## 1. Amaç

Bu doküman, GeoLens Hesap Motoru'nun (Scoring Engine) algoritmik tasarımını tanımlar. `scoringService.CalculateScore` ve `fidelityService.ComputeFidelity` fonksiyonlarının detaylı hesaplama kurallarını, güven aralığı metodolojisini, determinizm garantilerini ve kalibrasyon sürecini kapsar.

---

## 2. Skor Hesaplama

### 2.1 Dört Bileşenli Skor

```
Görünürlük Skoru = presence_share × 0.35 + position_weight × 0.25 + source_share × 0.20 + competitor_context × 0.20
```

Her bileşen 0-100 aralığına normalize edilir:

| Bileşen | Normalizasyon | Açıklama |
|---------|:-------------:|----------|
| presence_share | (geçme_sayısı / toplam_yanıt) × 100 | 0407'den gelen ham varlık verisi |
| position_weight | max(0, 100 - sıralama × 10) | İlk 10'da lineer düşüş |
| source_share | (marka_kaynağı / toplam_kaynak) × 100 | 0405 citation verisi |
| competitor_context | 50 + (marka_farkı / max_fark) × 50 | Rakip ortalamasına göre normalize |

### 2.2 Kısmi Yayın Kuralı

Bir motor başarısız olursa (timeout/parse hatası), diğer motorların verisiyle kısmi skor hesaplanır:

```
kısmi_skor_ağırlığı = başarılı_motor_sayısı / toplam_motor_sayısı
```

Kısmi skor, etikette `partial: {n_success}/{n_total}` olarak işaretlenir. Tüm motorlar başarısız olursa skor yayınlanmaz.

### 2.3 Örnekleme Birleştirme

Her motor için n=3 paralel istek gönderilir. Sonuçlar birleştirilir:

| Durum | Aksiyon |
|-------|---------|
| 3/3 başarılı | Tümü kullanılır, ortalama alınır |
| 2/3 başarılı | Başarısız örnek atlanır |
| 1/3 başarılı | Tek örnekle devam edilir, düşük güven uyarısı |
| 0/3 başarılı | Motor başarısız sayılır |

---

## 3. Güven Aralığı (GA)

### 3.1 Hesaplama

GA, örneklem standart sapması üzerinden hesaplanır:

```
GA = z × (σ / √n)
```

| Değişken | Değer |
|----------|-------|
| z (%%95) | 1.96 |
| n | başarılı örnek sayısı (max 3) |
| σ | örneklem standart sapması |

### 3.2 Anlamlılık Eşiği

| Kural | Eşik | Referans |
|-------|:----:|----------|
| Mutlak fark anlamlılığı | ≥5 puan | D-31 |
| GA daraltma hedefi (p95) | ≤10 puan | Pilotda kalibre edilecek |
| Trend dönüş sinyali | 2 ardışık hafta zıt yön | 0414 |

---

## 4. Fidelite Hesabı

### 4.1 Fidelite Kademeleri

| Kademe | Koşul | Etiket |
|:------:|-------|--------|
| Tier 1 | Direct API + web arama + temperature=0 | `T1:direct` |
| Tier 2 | Official proxy + search grounding | `T2:official` |
| Tier 3 | Directional / third-party | `T3:directional` |

### 4.2 Güven Düzeyi

```
güven = min(1.0, başarılı_örnek / 3) × 0.7 + min(1.0, veri_tazeliği_gün / 7) × 0.3
```

Yakın zamanda ölçülen veri daha yüksek güven alır. 7 günden eski veri güven katsayısını düşürür.

---

## 5. Determinizm Garantisi (NFR-7)

Aynı girdi seti aynı skoru üretmelidir:

| Gereksinim | Mekanizma |
|------------|-----------|
| Temperature=0 | Tüm adapter'larda sabit |
| AlgorithmVersion | `calculation_run.algorithm_version` ile sürümlenir |
| PanelVersion | Aynı panel versiyonu aynı faktörleri kullanır |
| Partial yayın | Aynı başarısızlık durumunda aynı kısmi skor |

Determinizm doğrulaması: Aynı `calculation_run` ID ile yeniden hesaplama birebir aynı sonucu vermelidir.

---

## 6. Motor Kırılımı (Engine Breakdown)

### 6.1 Hesaplama

Her motorun skora katkısı ayrı ayrı hesaplanır:

```json
{
  "perplexity": 72.3,
  "chatgpt": 68.1,
  "gemini": 74.5,
  "weighted_average": 71.6
}
```

### 6.2 Ağırlıklandırma

| Motor | Varsayılan Ağırlık | Gerekçe |
|-------|:------------------:|---------|
| Perplexity | 0.30 | Tier 1, web arama |
| ChatGPT | 0.30 | Tier 2, search grounding — TR'de en yaygın |
| Gemini | 0.25 | Tier 1, Google Search grounding |
| Google AI Overview | 0.10 | Tier 3, directional — Gemini vekili |
| Claude | 0.05 | Tier 2 — HT1 üretimde |
| Grok | 0.05 | Tier 2 — HT1 üretimde |
| Mistral | 0.05 | Tier 2 — HT1 üretimde |
| Copilot | 0.05 | Tier 3 — HT1 üretimde |
| Google AI Mode | 0 (opsiyonel) | Tier 3, directional — Faz 4 üretimde |

Not: Google AI Overview ve Google AI Mode ağırlıkları başlangıçta düşük/tutulmuştur çünkü Kademe 3 (directional) ölçümüdür. Doğrulama verisi toplandıkça ağırlık artırılabilir.

> **Kod gerçeği (v1.3):** `dev.geolens.measure.MeasureService` içinde motor kırılımı `computeEngineBreakdown()` ile hesaplanır: motor bazlı varlık skoru (içerik varsa 75, yoksa 40; örnekler ortalamalanır) + **per-motor ağırlıklı `weighted_average`** (0309 §6.2 tablosu; `ENGINE_WEIGHTS` env'i ile pilot kalibrasyonu — bilinmeyen motorlar eşit ağırlıkta katılır, partial yayında kalan motorlarla tutarlı). 0308 ile senkron: 8 motor (7 adaptör + AI Mode) üretimde.

---

## 7. Kalibrasyon

### 7.1 Pilot Kalibrasyonu

Pilot sırasında aşağıdaki parametreler kalibre edilir:

| Parametre | Başlangıç Değeri | Kalibrasyon Yöntemi |
|-----------|:----------------:|---------------------|
| Bileşen ağırlıkları | Varsayılan (0409) | Anket + korelasyon analizi |
| Anlamlılık eşiği | ≥5 puan | Pilot verisiyle doğrulama |
| GA hedefi | ≤10 puan (p95) | Pilot verisiyle daraltma |
| n değeri | 3 | Pilot verisiyle optimize |

### 7.2 A/B Karşılaştırma

Yeni algoritma versiyonu eskiyle karşılaştırılır:
1. Aynı girdiyle iki versiyon çalıştırılır
2. Fark anlamlılık eşiğinin altındaysa yeni versiyon kabul edilir
3. `AlgorithmVersion` alanı artırılır

---

## 8. Genişletilmiş Skor Bileşenleri

Turkcell RFP kapsamında skor motoruna eklenen yeni hesaplamalar:

### 8.1 Sentiment Skoru Hesaplama

```
sentiment_skoru = (olumlu_yanıt_sayısı × 1.0 + nötr_yanıt_sayısı × 0.5) / toplam_yanıt
```

| Değer | Anlamı |
|:----:|--------|
| ≥0.7 | Olumlu |
| 0.4-0.7 | Nötr |
| <0.4 | Olumsuz |

### 8.2 Competitive Gap Hesaplama

```
visibility_gap = marka_SOV - rakip_SOV
citation_gap = marka_citation_oranı - rakip_citation_oranı
content_gap = |marka_kaynak_türleri| - |rakip_kaynak_türleri|
topic_gap = her_konu_için_SOV_farkı
prompt_gap = markanın_geçtiği_prompt_seti - rakibin_geçtiği_prompt_seti
```

### 8.3 Hallüsinasyon Tespiti

Hallüsinasyon tespiti için AI yanıtı markanın doğrulanmış bilgileriyle karşılaştırılır:

| Kontrol | Yöntem |
|---------|--------|
| Marka adı doğruluğu | Bilinen marka varyasyonlarıyla eşleme |
| Ürün/hizmet ilişkisi | Marka-ürün matrisi kontrolü |
| Sayısal veri | Tarih, fiyat, puan gibi verilerin doğrulaması |
| Bağlam uyumu | Markanın doğru sektör/bağlamda geçmesi |

---

## 9. Hata Kodları

| Kod | Açıklama | HTTP |
|:---:|----------|:----:|
| SCORE_001 | Tüm motorlar başarısız | 502 |
| SCORE_002 | Faktör konfigürasyonu eksik | 400 |
| SCORE_003 | Panel versiyonu bulunamadı | 404 |
| SCORE_004 | Determinizm doğrulaması başarısız | 500 |
| SCORE_005 | GA hedef dışı (>15 puan) | Uyarı |
| SCORE_006 | Sentiment analizi başarısız | Uyarı |
| SCORE_007 | Hallüsinasyon tespiti zaman aşımı | Uyarı |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 25.07.2026 | İlk yayın: skor hesaplama, GA, fidelite, determinizm, motor kırılımı, kalibrasyon |
| 1.1 | 27.07.2026 | Turkcell RFP kapsamında genişletme: Motor ağırlıkları güncellendi (Google AI Overview, Claude, Grok, Mistral, Copilot). Yeni skor bileşenleri eklendi: Sentiment skoru, Competitive Gap (visibility/citation/content/topic/prompt gap), Hallüsinasyon tespiti. Hata kodlarına SCORE_006-SCORE_007 eklendi. |
| 1.2 | 04.08.2026 | **Motor senkronu:** §6.2 motor ağırlık tablosu 0308 v1.3 ile hizalandı — Google AI Mode eklendi; Claude/Grok/Mistral/Copilot durumları "HT1 adayı" → "HT1 üretimde" olarak güncellendi. Kod gerçeği notu eklendi: motor kırılımı `computeEngineBreakdown()` (varlık tabanlı 40/75 heuristiği) ile hesaplanır; per-motor ağırlıklı ortalama tasarım hedefidir. |
| 1.3 | 12.08.2026 | **Kod gerçeği senkronu:** §6.2 kodlanmış per-motor ağırlıklı `weighted_average` uygulandı (`computeEngineBreakdown()` + `ENGINE_WEIGHTS` env override). Ağırlık tablosu doğrulandı; 3 yeni test (ağırlıklı ortalama, farklı skorlar, bilinmeyen motor eşit ağırlık). |
| 1.4 | 15.08.2026 | **Java geçişi:** Kod gerçeği referansı `dev.geolens.measure.MeasureService` ile güncellendi. |
