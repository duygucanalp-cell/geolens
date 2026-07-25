# 0001 · Vizyon — GeoLens Specification

| Alan | Değer |
|---|---|
| Doküman ID | 0001 |
| Proje | GeoLens Specification |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0000, platform/docs/0001 |

---

## 1. Vizyon

> **AI görünürlüğü ölçümünde dünyanın referans standardı olmak.**

Tıpkı ISO'nun kalite yönetiminde, W3C'nin web standartlarında, GAAP'ın muhasebede bir referans olması gibi, **GAVF (GeoLens AI Visibility Framework)** da AI görünürlüğü ölçümünde evrensel referans olmayı hedefler.

---

## 2. Neden Açık Standart?

| Yaklaşım | Dezavantaj |
|----------|-------------|
| **Kapalı / Tescilli** | Sektör güvenmez; rakipler kendi standardını yaratır; ekosistem oluşmaz |
| **Açık / Kamu** | Güven inşa eder; denetlenebilir; sektör oyuncuları katkı yapabilir; akademik atıf alır |

GeoLens Specification **açık standardı** seçer çünkü:

1. **Güven**, AI ölçümünde en büyük değerdir. Açık olmayan bir skorlama sistemine kimse güvenmez.
2. **Ekosistem etkisi**: Standart ne kadar çok kullanılırsa, o kadar değerli olur. Açıklık bu döngüyü hızlandırır.
3. **Akademik kabul**: Açık standartlar akademik yayınlarda referans gösterilir, bu da standardın meşruiyetini artırır.
4. **Platform için moat**: Standart açık olsa da, platformun uygulaması — veri birikimi, bağdaştırıcı ağı, kullanıcı tabanı — taklidi zor bir hendek yaratır.

---

## 3. Başarı Kriterleri

| Kriter | 1. Yıl | 3. Yıl | 5. Yıl |
|--------|:------:|:------:|:------:|
| GAVF uyumlu ürün sayısı | 1 (GeoLens) | 5+ | 20+ |
| Akademik atıf sayısı | 0 | 10+ | 100+ |
| GitHub yıldız | 100+ | 1.000+ | 5.000+ |
| RFC katılımcı sayısı | — | 10+ kurum | 50+ kurum |
| Sertifikalı ürün sayısı | — | 2+ | 10+ |

---

## 4. GeoLens Platform ile İlişki

```
GeoLens Specification (açık)
    ↑ besler / doğrular
    ↓ uygular
GeoLens Platform (ticari)
```

- Platform, Specification'ın **referans uygulamasıdır**
- Specification'daki her metodoloji platformda test edilir ve doğrulanır
- Platformdaki yenilikler (ölçek, bağdaştırıcı çeşitliliği) Specification'a geri beslenir
- Specification kesinlikle platformdan bağımsız okunabilir ve uygulanabilir olmalıdır

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: Specification vizyonu, gerekçe, başarı kriterleri. |
