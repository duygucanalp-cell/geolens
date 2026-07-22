# 0406 · Yanıt Ayrıştırıcı (Answer Parser)

| Alan | Değer |
|---|---|
| Doküman ID | 0406 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0405, 0407, 0408, 0308, 0309 |

---

## 1. Amaç

Bu doküman, AI motorlarından gelen ham yanıtların yapılandırılmış veriye dönüştürülmesini tanımlar. Yanıt ayrıştırıcı (answer parser), her motorun farklı yanıt formatını normalize eder ve sonraki aşamalar (alıntı çıkarma, varlık tanıma, skorlama) için hazır hale getirir.

---

## 2. Motor Bazlı Ayrıştırma

| Motor | Yanıt Formatı | Ayrıştırma Yöntemi |
|-------|:-------------:|--------------------|
| ChatGPT (Responses API) | JSON (structured) | Alan bazlı çıkarım: `output_text`, `url_citations` |
| Gemini (Grounding) | JSON (groundingMetadata) | `groundingMetadata.sources` → citation listesi |
| Perplexity (Sonar) | JSON | `citations` dizisi, `content` metni |
| Claude (HT1) | JSON | `content` blokları |
| Grok (HT1) | JSON | Standart API yanıtı |

---

## 3. Ayrıştırma Çıktısı

| Alan | Tip | Kaynak |
|------|-----|--------|
| normalized_text | string | Tüm motorlardan normalize edilmiş metin |
| citations | Citation[] | 0405 şemasına uygun alıntılar |
| has_search | bool | Arama yapıldı mı? (arama-yapılmadı bayrağı) |
| engine_meta | object | Motor/sürüm bilgisi |
| raw_response_ref | string | S3 referansı (orijinal yanıt) |

---

## 4. Normalizasyon Kuralları

| Kural | Açıklama |
|:----:|----------|
| Metin temizleme | HTML etiketleri, gereksiz boşluklar temizlenir |
| Kod blokları | Kod blokları korunur ancak işaretlenir |
| Liste normalizasyonu | Tüm liste biçimleri (numbered/bullet) standart formata dönüştürülür |
| Dil tespiti | Yanıtın dili otomatik tespit edilir |
| Kesme | Maksimum yanıt uzunluğu aşımında kesme ve işaretleme |

---

## Kaynaklar

- 0405 Citation Framework — alıntı çıkarma
- 0407 Entity Recognition — varlık tanıma
- 0308 AI Connectors — ProbeResult, hata sınıfları
- 0309 Scoring Engine — örnekleme, hesaplama

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: motor bazlı ayrıştırma, çıktı şeması, normalizasyon kuralları. |
