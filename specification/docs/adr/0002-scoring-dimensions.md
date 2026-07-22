# ADR-0002 · Skor Bileşenleri

| Alan | Değer |
|---|---|
| ADR ID | 0002 |
| Proje | GeoLens Specification |
| Tarih | 22 Temmuz 2026 |
| Durum | Kabul Edildi |
| İlişkili | 0104 (S3) |

---

## Bağlam

Bileşik skor hangi boyutlardan oluşmalı ve her boyutun ağırlığı ne olmalı?

## Karar

Dört bileşen: Varlık (0.35), Konum (0.25), Kaynak (0.25), Rakip (0.15).

Gerekçe:
- Varlık en temel boyuttur — marka yanıtta yoksa diğer boyutlar anlamsızdır
- Konum ve kaynak eşit ağırlıktadır — ikisi de görünürlüğün farklı yüzleridir
- Rakip karşılaştırması tamamlayıcıdır, en düşük ağırlığı alır

## Alternatifler

| Alternatif | Gerekçe |
|------------|---------|
| Eşit ağırlık (%25/%25/%25/%25) | Farklılaşmayı zayıflatır |
| Tek skor (tekil metrik) | Çok boyutlu görünürlüğü yakalayamaz |
| 5+ bileşen | Karmaşıklık artar, anlaşılırlık azalır |

## Sonuçlar

- Pozitif: Dört boyut görünürlüğü dengeli temsil eder
- Pozitif: Ağırlıklar sezgiseldir
- Negatif: Ağırlıklar sektöre göre değişebilir (gelecek sürümlerde yapılandırılabilir)

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk karar. |
