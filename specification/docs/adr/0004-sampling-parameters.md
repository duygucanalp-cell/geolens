# ADR-0004 · Örnekleme Parametreleri

| Alan | Değer |
|---|---|
| ADR ID | 0004 |
| Proje | GeoLens Specification |
| Tarih | 22 Temmuz 2026 |
| Durum | Kabul Edildi |
| İlişkili | 0201 |

---

## Bağlam

AI motor yanıtları deterministik değildir (aynı prompt farklı yanıtlar üretebilir). Güvenilir ölçüm için örnekleme gerekir.

## Karar

- n = 3 (her prompt 3 kez koşulur)
- temperature = 0 (determinizmi maksimize eder)
- Bayraklı oran: Olağandışı yanıt oranı (örn. %30+ farklılık) hesaplanır ve raporlanır

## Alternatifler

| Alternatif | Gerekçe |
|------------|---------|
| n=1 | Yetersiz, güven aralığı hesaplanamaz |
| n=5+ | Maliyet artar, getiri azalır |
| temp > 0 | Determinizmi kırar, G2 ilkesini ihlal eder |

## Sonuçlar

- Pozitif: n=3 maliyet-güven dengesi
- Pozitif: temp=0 G2 (determinizm) ilkesini sağlar
- Negatif: temp=0 bazı motorlarda creativity'yi sınırlar (kabul edilebilir)

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk karar. |
