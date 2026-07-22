# 0405 · Alıntı Çerçevesi (Citation Framework)

| Alan | Değer |
|---|---|
| Doküman ID | 0405 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0406, 0308, 0204, 0302 |

---

## 1. Amaç

Bu doküman, AI motor yanıtlarından alıntıların nasıl çıkarılacağını, doğrulanacağını ve sınıflandırılacağını tanımlar. Alıntılar, görünürlük skorunun kaynak payı bileşeninin temel girdisidir.

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

## 4. Alıntı Doğrulama Kuralları

| Kural | Açıklama |
|:----:|----------|
| URL geçerlilik | Tüm URL'ler http/https şemasıyla başlamalıdır |
| Domain çözümleme | Yönlendirme zinciri son hedefe kadar çözümlenir (D-39) |
| Yinelenen filtre | Aynı yanıtta aynı URL birden fazla kaydedilmez |
| Boş kaynak işareti | Alıntı listesi boşsa `no_citations` bayrağı işaretlenir |

---

## 5. Alıntı-Marka Eşleme

Alıntıdaki domain, markanın tanımlı alan adlarıyla eşleştirilir:

| Eşleme Sonucu | Anlamı | Skor Etkisi |
|:-------------:|--------|:-----------:|
| Tam eşleşme | Domain markaya ait | Pozitif |
| Alt alan adı | Domain markanın alt alanı | Pozitif (düşük ağırlık) |
| Eşleşme yok | Domain markaya ait değil | Nötr |
| Bilinmiyor | Domain tanınmıyor | Sınıflandırılmamış |

---

## 6. Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|:--:|-------|:------:|
| D-39 | Gemini URI çözümleme: yönlendirme zinciri son hedefe kadar çözümlenir. Gemini bağdaştırıcısı URI çözümleme ve yönlendirme takibi yapar. | AVIP 0308 O-2 (TL 21.07.2026) |

---

## Kaynaklar

- 0308 AI Connectors — alıntı çıkarımı, tier_label
- 0406 Answer Parser — yanıt ayrıştırma
- 0204 PRD — FR-D2 (alıntı/kaynak analizi)
- 0302 Domain Model — Citation varlığı

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: alıntı türleri, şema, doğrulama kuralları, marka eşleme. |
| 1.1 | 23.07.2026 | Eski AVIP referansı düzeltildi (0308 O-2 → D-39). Devralınan AVIP Kararları eklendi: D-39 (URI çözümleme). |
