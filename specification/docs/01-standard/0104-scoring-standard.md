# 0104 · Skor Standardı (GAVF S3)

| Alan | Değer |
|---|---|
| Doküman ID | 0104 |
| Proje | GeoLens Specification |
| Versiyon | 1.1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 27 Temmuz 2026 |
| İlişkili | 0101 (S3), 0202, 0203, 0204, 0205 |

---

## 1. Amaç

Görünürlük skorunun hesaplanmasını ve bileşenlerini tanımlar. GAVF S3 kapsamındadır.

## 2. Skor Bileşenleri

| # | Bileşen | Açıklama | Ağırlık |
|:-:|---------|----------|:-------:|
| 1 | **Varlık (Presence)** | Markanın AI yanıtında geçme sıklığı | 0.25 |
| 2 | **Konum (Position)** | Markanın yanıt içindeki sırası ve bağlamı | 0.15 |
| 3 | **Kaynak (Citations)** | Markanın alıntı kaynağı olarak görünme sıklığı | 0.20 |
| 4 | **Rakip (Competitive)** | Rakiplere göre göreceli konum | 0.10 |
| 5 | **Duygu (Sentiment)** | AI yanıtlarındaki duygu durumu skoru (0.0-1.0 → 0-100) | 0.05 |
| 6 | **Otorite (Authority)** | Kaynak domainlerin otorite puanı | 0.10 |
| 7 | **Competitive Gap** | 5 boyutlu rakip-marka farkı skoru | 0.15 |

## 3. Bileşik Skor Hesaplama

```
Bileşik Skor = (Varlık × 0.25) + (Konum × 0.15) + (Kaynak × 0.20) + (Rakip × 0.10) + (Duygu × 0.05) + (Otorite × 0.10) + (CompetitiveGap × 0.15)
```

Tüm bileşenler 0-100 skalasındadır. Bileşik skor da 0-100 arasındadır.

## 4. Competitive Gap Skorları

Rakip-marka farkı 5 boyutta ölçülür. Detaylı metodoloji için **platform/docs/0419-competitive-gap-analysis.md** dokümanına bakınız.

| Gap Türü | Açıklama | Skala |
|:--------:|----------|:----:|
| **Visibility Gap** | Genel görünürlük farkı (SOV bazlı) | -100..+100 |
| **Citation Gap** | Alıntı oranı farkı | -100..+100 |
| **Content Gap** | İçerik kapsama farkı | -100..+100 |
| **Topic Gap** | Konu/kategori bazlı fark | -100..+100 |
| **Prompt Gap** | Prompt kapsama farkı | -100..+100 |

Normalizasyon: `gap_puanı = 50 + (gap_değeri / max_gap) × 50` → 0-100 skalası.

## 5. Versiyon Notu

> **Ağırlık değişikliği uyarısı:** Mevcut ağırlıklar yeniden dağıtılmıştır (Varlık 0.35→0.25, Konum 0.25→0.15, Kaynak 0.25→0.20, Rakip 0.15→0.10). Aynı girdilerle yeni ağırlıklar farklı bir bileşik skor üretir. Bu değişiklik **major** kırıcı değişiklik kategorisine girer ancak platform ve specification arasındaki eşzamanlı major değişiklikten kaçınmak için **minor (1.1.0)** olarak işaretlenmiştir. Eski ağırlıklarla hesaplanan skorlar korunur ve geriye dönük olarak okunabilir.

## 6. Güven Aralığı

Her skor bir güven aralığıyla birlikte raporlanmalıdır. Varsayılan: %95 güven aralığı.

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.1.0 | 27.07.2026 | Turkcell RFP kapsamında genişletme: 4 bileşenden 7 bileşene çıkarıldı. Sentiment, Authority, Competitive Gap eklendi. Ağırlıklar yeniden dağıtıldı (bkz. §5 Versiyon Notu). Competitive Gap skor türleri tanımlandı. Platform 0419 ile senkronize edildi. |
| 1.0.0 | 22.07.2026 | İlk yayın: S3 skor standardı, 4 bileşen ve ağırlıklar. |
