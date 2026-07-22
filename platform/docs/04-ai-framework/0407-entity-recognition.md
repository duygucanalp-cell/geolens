# 0407 · Varlık Tanıma (Entity Recognition)

| Alan | Değer |
|---|---|
| Doküman ID | 0407 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0406, 0409, 0309, 0204 |

---

## 1. Amaç

Bu doküman, AI yanıtlarında marka ve rakip varlıklarının nasıl tanınacağını tanımlar. Varlık tanıma, görünürlük skorunun varlık payı bileşeninin temelidir.

---

## 2. Varlık Türleri

| Tür | Anlamı | Örnek |
|:---:|--------|-------|
| **Marka (brand)** | Kullanıcının tanımladığı kendi markası | "XYZ" |
| **Rakip (competitor)** | Kullanıcının tanımladığı rakip marka | "ABC" |
| **Eş anlamlı (synonym)** | Markanın alternatif isimleri | "XYZ Inc.", "XYZ Şirketi" |
| **Kategori (category)** | Sektör/genel terim | "e-ticaret platformu" |

---

## 3. Tanıma Yöntemi

MVP'de kural tabanlı tanıma kullanılır (makine öğrenimi HT2+):

| Yöntem | Açıklama | MVP |
|--------|----------|:---:|
| Tam eşleşme | Birebir marka adı eşleşmesi | ✅ |
| Kısmi eşleşme | Marka adının alt kümesi | ✅ |
| Eş anlamlı eşleme | Tanımlı eş anlamlılarla eşleşme | ✅ |
| Büyük-küçük harf duyarsız | Case-insensitive eşleşme | ✅ |
| Bağlam analizi | Varlığın hangi bağlamda geçtiği | 🔴 (HT2) |

---

## 4. Varlık Eşleme Kuralları

| # | Kural | Açıklama |
|:-:|-------|----------|
| 1 | Marka adı yanıt metninde geçiyorsa varlık tespit edilir | Tam/kısmi eşleşme |
| 2 | Eş anlamlılar da marka olarak kabul edilir | Konfigüre edilebilir liste |
| 3 | Aynı cümlede marka + rakip geçiyorsa karşılaştırma bağlamı işaretlenir | Bağlam etiketi |
| 4 | Kategori terimleri marka olarak işaretlenmez | Sadece marka/rakip varlıklar |
| 5 | Her varlık tespiti konum bilgisiyle kaydedilir | Hangi pozisyonda geçtiği |

---

## 5. Çıktı Formatı

```json
{
  "entity": "XYZ",
  "type": "brand",
  "mentions": [
    { "position": 3, "context": "XYZ en iyi çözümlerden biridir" },
    { "position": 15, "context": "XYZ'nin müşteri hizmetleri" }
  ],
  "total_mentions": 2,
  "has_comparison_context": true
}
```

---

## Kaynaklar

- 0406 Answer Parser — normalize edilmiş metin girdisi
- 0409 Visibility Score — varlık payı bileşeni
- 0309 Scoring Engine — örnekleme ve hesaplama
- 0204 PRD — FR-C1..FR-C7 (ölçüm)

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: varlık türleri, kural tabanlı tanıma, eşleme kuralları. |
