# 0404 · Prompt Ağırlıklandırma

| Alan | Değer |
|---|---|
| Doküman ID | 0404 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0402, 0403, 0409, 0309 |

---

## 1. Amaç

Bu doküman, farklı prompt türlerinin birleşik skora katkısını belirleyen ağırlıklandırma şemasını tanımlar. Her prompt türü, ölçtüğü boyutun önemine göre farklı ağırlık taşır.

---

## 2. Prompt Türü Ağırlıkları

| Prompt Türü | Varsayılan Ağırlık | Gerekçe |
|:-----------:|:------------------:|---------|
| Varlık (presence) | %30 | Görünürlüğün temel sinyali |
| Karşılaştırma (comparison) | %25 | Rekabet bağlamındaki konum |
| Öneri (recommendation) | %25 | AI'nın kullanıcıya yönlendirme gücü |
| Kategori (category) | %10 | Genel kategori görünürlüğü |
| Problem (problem) | %10 | Çözüm odaklı görünürlük |

> Ağırlıklar `factor_snapshot` parametresidir ve pilot verisiyle kalibre edilir.

---

## 3. Markalı vs Kategorik Prompt Dengesi

| Prompt Türü | Varsayılan Oran | Amaç |
|:-----------:|:--------------:|------|
| Markalı (branded) | %40 | Doğrudan marka bilinirliği ölçümü |
| Kategorik (categorical) | %40 | Kategori bağlamında görünürlük |
| Rakip (competitor) | %20 | Rekabet pozisyonu |

---

## 4. Sektör Bazlı Kalibrasyon

| Sektör | Varlık Ağırlığı | Öneri Ağırlığı | Not |
|--------|:--------------:|:--------------:|-----|
| E-ticaret | %35 | %20 | Varlık daha kritik |
| SaaS | %25 | %30 | Öneri daha kritik |
| Finans | %30 | %20 | Denge |
| Sağlık | %35 | %15 | Güvenilirlik ön planda |

---

## Kaynaklar

- 0402 Prompt Taxonomy — prompt türleri
- 0409 Visibility Score — birleşik skor modeli
- 0309 Scoring Engine — factor_snapshot ve kalibrasyon

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: prompt ağırlıkları, markalı/kategorik dengesi, sektör kalibrasyonu. |
