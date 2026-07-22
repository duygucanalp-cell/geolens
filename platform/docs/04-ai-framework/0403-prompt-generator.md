# 0403 · Prompt Generator (Prompt Üretici)

| Alan | Değer |
|---|---|
| Doküman ID | 0403 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0402, 0404, 0301, 0204 |

---

## 1. Amaç

Bu doküman, şablon kütüphanesinden markaya özgü promptların nasıl üretildiğini tanımlar. Prompt generator, 0402 taksonomisindeki şablonları alır, marka adı ve bağlam bilgisiyle birleştirir ve motor çağrısına hazır hale getirir.

---

## 2. Üretim Süreci

```
Şablon Kütüphanesi
    ↓ (seçim: sektör + boyut + bağlam)
Prompt Şablonu (ör: "En iyi {kategori} çözümleri nelerdir?")
    ↓ (enjeksiyon: marka adı, eş anlamlılar, kategori)
Markaya Özgü Prompt Seti
    ↓ (paket: panel versiyonuna hazır)
Panel Versiyonu
```

---

## 3. Prompt Şablon Dili

| Öğe | Anlamı | Örnek |
|-----|--------|-------|
| `{brand}` | Marka adı | "XYZ" |
| `{category}` | Sektör/kategori | "e-ticaret" |
| `{synonyms}` | Marka eş anlamlıları | "XYZ, XY, eski adıyla Z" |
| `{competitor}` | Rakip marka | "ABC" |

### Şablon Örnekleri

| Şablon | Üretilmiş Prompt |
|--------|-----------------|
| "{brand} hakkında ne biliyorsun?" | "XYZ hakkında ne biliyorsun?" |
| "{brand} ile {competitor} karşılaştırması" | "XYZ ile ABC arasındaki farklar nelerdir?" |
| "En iyi {category} çözümleri" | "En iyi e-ticaret çözümleri nelerdir?" |
| "{brand} yerine hangisini önerirsin?" | "XYZ yerine hangi markayı önerirsin?" |

---

## 4. Çok Dilli Destek

| Dil | Prompt Dili | Hedef Pazar |
|:---:|:-----------:|:-----------:|
| TR | Türkçe | Türkiye (birincil) |
| EN | İngilizce | Küresel (ikincil, HT1+) |

Prompt dili, ölçümün yapıldığı pazara göre otomatik seçilir.

---

## Kaynaklar

- 0402 Prompt Taxonomy — prompt sınıflandırması
- 0404 Prompt Weighting — prompt ağırlıklandırma
- 0301 Core Concepts — panel, panel versiyonu
- 0204 PRD — FR-B2 (şablon kütüphanesi)

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: prompt üretim süreci, şablon dili, çok dilli destek. |
