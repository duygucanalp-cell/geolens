# 0110 · Duygu ve Hallüsinasyon Standardı

| Alan | Değer |
|---|---|
| Doküman ID | 0110 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 10 Ağustos 2026 |
| İlişkili | 0103 (S2), 0000 (master plan §4), platform/docs/0416 |

---

## 1. Amaç

AI yanıtlarında **duygu durumu (sentiment)** tespiti ve **hallüsinasyon** işaretlemesi için açık, tekrarlanabilir bir metodoloji tanımlar. GAVF Yanıt Standardı (S2) kapsamındadır: marka hakkında üretilen yanıtın olumlu/olumsuz/yanlış olması, marka görünürlüğü skorunun yorumlanmasında bağlam sağlar.

## 2. Duygu Analizi (Sentiment)

### 2.1 Duygu Sınıfları

| Sınıf | Anlamı | Örnek işaretçi |
|-------|--------|----------------|
| Çok olumlu | Markayı üstün/önerilen gösterir | "lider", "en iyi", "önerilir" |
| Olumlu | Markayı olumlu bağlamda geçer | "başarılı", "güvenilir" |
| Nötr | Markayı tarafsız geçer | "bilinen bir firma" |
| Olumsuz | Markayı olumsuz bağlamda geçer | "eleştiriliyor", "sorunlu" |
| Çok olumsuz | Markayı kaçınılacak gösterir | "skandal", "güvenilmez" |

### 2.2 Yöntem

- **Sözlük tabanlı:** Türkçe + İngilizce duygu sözlüğü ile marka geçen cümlelerin duygu puanı toplanır.
- Yanıt başına sentiment puanı: `(olumlu işaretçi sayısı − olumsuz işaretçi sayısı) / toplam işaretçi sayısı`.
- Skor `[−1, +1]` aralığından `[0, 100]` aralığına ölçeklenir (50 = nötr).

### 2.3 Veri Modeli

```json
{
  "brand_id": "brand-1",
  "engine": "chatgpt",
  "response_id": "resp-123",
  "sentiment_score": 82.4,
  "sentiment_class": "positive",
  "keywords": ["lider", "yenilikçi"],
  "sampled_at": "2026-08-10T12:00:00Z"
}
```

## 3. Hallüsinasyon Tespiti

### 3.1 Hallüsinasyon Türleri

| Tür | Tanım |
|-----|-------|
| Bilgi çelişkisi | Yanıt, marka profili verisiyle çelişir |
| Sahte kaynak | Gerçek olmayan URL/kaynak referansı |
| Uydurma istatistik | Kaynaksız sayısal iddia |
| Yanlış bağlam | Markayı alakasız sektöre/olaya bağlar |
| Uydurma ürün/özellik | Markanın sahip olmadığı ürün veya özellik |

### 3.2 Doğrulama Yöntemi

- **Marka profili tabanlı:** Kullanıcı tarafından onaylanmış marka profili (sektör, ürünler, konum) ile yanıt çapraz kontrol edilir.
- MVP'de kural tabanlı eşleşme; HT1+'da ikinci model (LLM-as-judge) değerlendirmesi adaydır.
- Sonuç: `hallucination: true/false` + tür + eşleşen cümle.

### 3.3 Veri Modeli

```json
{
  "brand_id": "brand-1",
  "response_id": "resp-456",
  "hallucinated": true,
  "type": "fake_source",
  "matched_text": "Şirketin 2025 cirosu 12 milyar TL'dir.",
  "confidence": 0.87
}
```

## 4. GAVF Uyumu

- Bu standardın metrikleri S2 (Yanıt Standardı) kapsamında opsiyonel bağlam metrikleridir; görünürlük skorunu doğrudan değiştirmez.
- Uyumluluk seviyeleri (0109): Temel → zorunlu değil; İleri → sentiment raporlanır; Tam → sentiment + hallüsinasyon işaretlemesi raporlanır.

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 10.08.2026 | İlk yayın: duygu sınıfları, sözlük tabanlı yöntem, hallüsinasyon türleri, marka profili doğrulama, veri modeli, GAVF uyumu. Platform 0416'dan türetilmiştir. |
