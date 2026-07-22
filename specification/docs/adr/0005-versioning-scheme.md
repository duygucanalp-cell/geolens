# ADR-0005 · Versiyonlama Şeması

| Alan | Değer |
|---|---|
| ADR ID | 0005 |
| Proje | GeoLens Specification |
| Tarih | 22 Temmuz 2026 |
| Durum | Kabul Edildi |
| İlişkili | 0000 §5 |

---

## Bağlam

GAVF standardı zamanla değişecektir. Versiyonlama şeması, değişikliklerin etkisini netleştirmeli ve kullanıcılara rehberlik etmelidir.

## Karar

SemVer benzeri `MAJOR.MINOR.PATCH`:

| Bileşen | Değişiklik | Örnek |
|---------|-----------|-------|
| **Major** | Skor hesaplama yöntemi değişir | 1.x → 2.0 |
| **Minor** | Yeni skor bileşeni, geriye uyumlu | 1.0 → 1.1 |
| **Patch** | Açıklama, düzeltme, skor değişmez | 1.0.0 → 1.0.1 |

## Alternatifler

| Alternatif | Gerekçe |
|------------|---------|
| Tarih bazlı (2026.07) | Anlamsal bilgi taşımaz |
| Tek sürüm (1, 2, 3...) | Patch/Minor ayrımı kaybolur |
| Kalender (YY.MM) | Kırıcılık bilgisi vermez |

## Sonuçlar

- Pozitif: SemVer tanıdık, net, yaygın
- Pozitif: Her skor hangi GAVF sürümüyle üretildiğini taşır
- Negatif: Major versiyon geçişlerinde tüm uygulamaların güncellenmesi gerekir

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk karar. |
