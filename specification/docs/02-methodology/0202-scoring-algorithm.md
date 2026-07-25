# 0202 · Skor Algoritması

| Alan | Değer |
|---|---|
| Doküman ID | 0202 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0104 (S3), 0203, 0204, 0205 |

---

## 1. Amaç

Bileşik skorun hesaplama algoritmasını detaylı olarak tanımlar.

## 2. Hesaplama Akışı

```
Ham Yanıt → Varlık Tespiti → Konum Analizi → Kaynak Analizi → Rakip Karşılaştırma → Bileşik Skor
```

## 3. Bileşen Hesaplama

Her bileşen 0-100 skalasında normalize edilir:

| Bileşen | Normalizasyon |
|---------|---------------|
| Varlık | (marka geçme sayısı / toplam prompt) × 100 |
| Konum | İlk 3'te olma oranı × 100 |
| Kaynak | (alıntı sayısı / toplam yanıt) × 100 |
| Rakip | (marka puanı / rakip ortalama) × 50 |

## 4. Bileşik Skor

```
Bileşik Skor = Σ(bileşen_i × ağırlık_i)
```

Ağırlıklar: Varlık 0.35, Konum 0.25, Kaynak 0.25, Rakip 0.15

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 22.07.2026 | İlk yayın: hesaplama akışı, bileşen normalizasyonu, formül. |
