# 0113 · Rekabetçi Boşluk Standardı

| Alan | Değer |
|---|---|
| Doküman ID | 0113 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 10 Ağustos 2026 |
| İlişkili | 0104 (S3), 0105 (S4), 0000 (master plan §4), platform/docs/0419 |

---

## 1. Amaç

Bir markanın rakiplerine göre AI görünürlüğündeki **boşluklarını (gap)** ölçen ve raporlayan metodolojiyi tanımlar. GAVF Skor Standardı (S3) kapsamındadır; görünürlük skorunun rekabet bağlamında yorumlanmasını sağlar.

## 2. Gap Türleri

| Gap Türü | Tanım | Kaynak metrik |
|----------|-------|---------------|
| Görünürlük (Visibility) | Skor farkı | Görünürlük skoru (0203) |
| Alıntı (Citation) | Alıntı kaynağı farkı | Alıntı sayısı/çeşitliliği (0107) |
| İçerik (Content) | İçerik kapsamı farkı | Content gap (0112) |
| Konu (Topic) | Konu kapsamı farkı | Topic kapsamı |
| Prompt (Prompt) | Prompt bazlı fark | Prompt kategorileri (0106) |

## 3. Gap Skoru Hesaplama

Normalize edilmiş gap: `Gap = (rakip − marka) / maks(rakip, 1)`

- Gap ≥ 0: marka rakipten geride (fırsat)
- Gap < 0: marka rakipten önde
- Skor 0-100 aralığına ölçeklenir; yüksek skor = büyük boşluk = yüksek fırsat.

### 3.1 Örnek

```json
{
  "brand_id": "brand-1",
  "competitor_id": "brand-2",
  "period_start": "2026-08-01",
  "period_end": "2026-08-07",
  "gaps": {
    "visibility": 18.4,
    "citation": 12.1,
    "content": 41.0,
    "topic": 33.2,
    "prompt": 8.9
  }
}
```

## 4. Alert Eşikleri

| Gap Türü | Bilgilendirme | Uyarı | Kritik |
|----------|:-------------:|:-----:|:------:|
| Görünürlük | ≥ 10 | ≥ 20 | ≥ 30 |
| Alıntı | ≥ 10 | ≥ 20 | ≥ 30 |
| İçerik | ≥ 15 | ≥ 30 | ≥ 45 |
| Konu | ≥ 15 | ≥ 30 | ≥ 45 |
| Prompt | ≥ 10 | ≥ 20 | ≥ 30 |

Eşik aşıldığında S4 (Aksiyon Standardı) kapsamında öneri üretilir.

## 5. Ölçeklenme Notları

- MVP: birebir marka-rakip karşılaştırması.
- 5+ rakip durumunda çoklu rakip ortalaması HT1'de devreye girer.
- Anlamlı trend için en az 2 veri noktası (2 hafta); güçlü trend için 4+ hafta önerilir.

## 6. GAVF Uyumu

- S3 kapsamında opsiyonel bir skor katmanıdır; görünürlük skorunun kendisini değiştirmez.
- Uyumluluk seviyeleri (0109): İleri → tek gap türü raporu; Tam → 5 gap türü raporu + alert.

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 10.08.2026 | İlk yayın: 5 gap türü, normalizasyon, örnek JSON, alert eşikleri, ölçeklenme notları. Platform 0419'dan türetilmiştir. |
