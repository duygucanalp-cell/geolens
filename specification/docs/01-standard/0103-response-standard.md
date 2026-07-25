# 0103 · Yanıt Standardı (GAVF S2)

| Alan | Değer |
|---|---|
| Doküman ID | 0103 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0101 (S2), 0107, 0208, 0209 |

---

## 1. Amaç

AI motor yanıtlarından anlamlı veri çıkarma sürecini tanımlar. GAVF S2 kapsamındadır.

## 2. Yanıt Bileşenleri

Her işlenmiş yanıt şunları içermelidir:

| Bileşen | Zorunlu | Açıklama |
|---------|:-------:|----------|
| Ham yanıt | ✅ | Motorun döndürdüğü orijinal metin |
| Alıntılar | ✅ | 0107 şemasına uygun alıntı listesi |
| Varlıklar | ✅ | Tanınan marka/ürün/kişi varlıkları |
| Sınıflandırma | ✅ | Yanıt türü sınıflandırması |

## 3. Varlık Tanıma

Yanıt içinde marka adı, ürün adı ve rakip adı gibi varlıklar tanınmalıdır. Tanıma yöntemi implementasyona bağlıdır ancak sonuçlar aynı şemada raporlanmalıdır.

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 22.07.2026 | İlk yayın: S2 yanıt standardı. |
