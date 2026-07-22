# 0504 · Versiyon Geçiş Rehberi

| Alan | Değer |
|---|---|
| Doküman ID | 0504 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0000 §5, adr/0005 |

---

## 1. Amaç

GAVF versiyonları arasında geçiş yapmak için rehber.

## 2. Geçiş Tipleri

| Geçiş | Etki | Aksiyon |
|-------|------|---------|
| Patch (1.0.0 → 1.0.1) | Skor değişmez | Dokümantasyon güncellemesi yeterli |
| Minor (1.0 → 1.1) | Yeni özellik, eski korunur | Yeni bileşen eklenebilir |
| Major (1.x → 2.0) | Skor değişebilir | Algoritma güncellemesi gerekli |

## 3. Major Geçiş Prosedürü

1. Eski ve yeni algoritma 30 gün paralel çalıştırılır
2. Kullanıcılara geçiş bildirimi yapılır
3. Eski skorlar arşivlenir, yeni skorlar ana akışa alınır
4. 12 ay sonra eski versiyon deprecated ilan edilir

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 22.07.2026 | İlk yayın: geçiş rehberi. |
