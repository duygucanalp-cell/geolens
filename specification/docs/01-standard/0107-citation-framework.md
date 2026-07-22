# 0107 · Alıntı Çerçevesi (GAVF S2)

| Alan | Değer |
|---|---|
| Doküman ID | 0107 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0101 (S2), 0209, platform/docs/0405 |

---

## 1. Amaç

AI motor yanıtlarından alıntıların nasıl çıkarılacağını, doğrulanacağını ve sınıflandırılacağını tanımlar. GAVF S2 (Yanıt Standardı) kapsamındadır.

---

## 2. Alıntı Türleri

| Tür | Anlamı | Kaynak |
|:---:|--------|--------|
| **Doğrudan (direct)** | AI yanıtında URL olarak verilen kaynak | citations[] listesi |
| **Atıf (attribution)** | Metin içinde marka/URL referansı | Metin analizi |
| **Yönsel (directional)** | AI'nın yönlendirdiği kaynak | Öneri bağlamı |

---

## 3. Alıntı Şeması

| Alan | Tip | Zorunlu | Açıklama |
|------|-----|:-------:|----------|
| url | string | ✅ | Kaynak URL (tam, tıklanabilir) |
| title | string | ✅ | Kaynak başlığı |
| position | int | ✅ | Yanıt içindeki sıra (1-tabanlı) |
| engine | string | ✅ | Üreten motor adı |
| domain | string | ✅ | URL'den çözümlenmiş alan adı |
| type | enum | ✅ | direct / attribution / directional |

---

## 4. Doğrulama Kuralları

| Kural | Açıklama |
|:----:|----------|
| URL geçerlilik | Tüm URL'ler http/https şemasıyla başlamalıdır |
| Domain çözümleme | Yönlendirme zinciri son hedefe kadar çözümlenir |
| Yinelenen filtre | Aynı yanıtta aynı URL birden fazla kaydedilmez |
| Boş kaynak işareti | Alıntı listesi boşsa `no_citations` bayrağı işaretlenir |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 22.07.2026 | İlk yayın: alıntı türleri, şema, doğrulama kuralları. |
