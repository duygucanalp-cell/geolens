# ADR-0003 · Fidelite Kademe Tanımı

| Alan | Değer |
|---|---|
| ADR ID | 0003 |
| Proje | GeoLens Specification |
| Tarih | 22 Temmuz 2026 |
| Durum | Kabul Edildi |
| İlişkili | 0108 |

---

## Bağlam

AI motorlarına erişim yöntemleri farklılık gösterir. Her yöntemin güven seviyesi farklıdır. Bu farkı skor etiketine yansıtmak gerekir.

## Karar

Üç kademeli fidelite modeli: Kademe 1 (Direct), Kademe 2 (Official Proxy), Kademe 3 (Directional).

## Alternatifler

| Alternatif | Gerekçe |
|------------|---------|
| İki kademe (yüksek/düşük) | Ara seviyeyi yakalayamaz |
| İkili (güvenilir/güvenilmez) | Çok kaba |
| Kademesiz (tümü eşit) | Dürüstlük ilkesini ihlal eder |

## Sonuçlar

- Pozitif: Şeffaflık sağlar
- Pozitif: Kademe 1-2-3 ayrımı net ve anlaşılırdır
- Negatif: Kademe 3 skorların kullanım alanı sınırlıdır

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk karar. |
