# 0412 · Fırsat Motoru (Opportunity Engine)

| Alan | Değer |
|---|---|
| Doküman ID | 0412 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0409, 0411, 0413, 0309, 0204 |

---

## 1. Amaç

Bu doküman, AI görünürlük fırsatlarını tespit etme yöntemini tanımlar. Fırsat motoru, markanın düşük performans gösterdiği alanları belirleyerek öneri motoruna (0413) girdi sağlar.

---

## 2. Fırsat Türleri

| Fırsat Türü | Açıklama | Tespit Yöntemi |
|:-----------:|----------|----------------|
| **Motor farkı** | Marka bir motorda iyi, diğerinde kötü | Motor bazlı skor karşılaştırması |
| **Rakip boşluğu** | Rakibin güçlü olduğu alan | SOV analizi (0411) |
| **Kategori boşluğu** | Kategorik promptlarda düşük görünürlük | Prompt türü kırılımı |
| **Trend kırılması** | Son dönemde düşüş | Trend analizi (0414) |
| **Kaynak zayıflığı** | Düşük otorite puanı | Otorite analizi (0410) |

---

## 3. Fırsat Puanlaması

| Faktör | Ağırlık | Açıklama |
|--------|:-------:|----------|
| Etki potansiyeli | %40 | Düzelirse skora etkisi |
| Uygulama maliyeti | %25 | Ne kadar kolay düzeltilebilir |
| Rekabet avantajı | %20 | Rakibe göre kazanım |
| Aciliyet | %15 | Trend yönü |

---

## 4. Fırsat Çıktısı

Her fırsat aşağıdaki alanlarla kaydedilir:

| Alan | Tip | Açıklama |
|------|-----|----------|
| type | enum | Fırsat türü |
| score | 0-100 | Fırsat puanı |
| description | string | Doğal dil açıklaması |
| recommendation_ref | ID | (Varsa) İlgili öneri |
| priority | enum | Düşük/Orta/Yüksek/Kritik |

---

## Kaynaklar

- 0409 Visibility Score — motor kırılımı
- 0411 Share of Voice — rakip analizi
- 0413 Recommendation Engine — öneri üretimi
- 0414 Trend Analysis — trend kırılması tespiti
- 0309 Scoring Engine — hesaplama

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: fırsat türleri, puanlama modeli, çıktı formatı. |
