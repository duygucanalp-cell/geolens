# 0201 · Örnekleme Metodolojisi

| Alan | Değer |
|---|---|
| Doküman ID | 0201 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0102 (S1), adr/0004 |

---

## 1. Amaç

AI motor yanıtlarının doğası gereği olasılıksal olması nedeniyle, güvenilir ölçüm için örnekleme gereklidir. Bu doküman örnekleme parametrelerini ve metodolojisini tanımlar.

## 2. Parametreler

| Parametre | Değer | Gerekçe |
|-----------|:-----:|---------|
| n (tekrar) | 3 | Maliyet-güven dengesi; G4 ilkesini karşılar |
| temperature | 0 | Determinizm (G2) |
| Bayraklı oran eşiği | %30 | Olağandışı yanıt oranı sinyali |

## 3. Bayraklı Oran Hesaplama

Bayraklı oran = (farklı yanıt sayısı / n) × 100

- Oran ≥ %30 ise: `flagged: true` ile işaretlenir
- Oran <%30 ise: normal kabul edilir

## 4. Uç Durumlar

| Durum | Davranış |
|-------|----------|
| Motor hatası (timeout) | 3 kez yeniden dene, başarısız olursa `engine_error` olarak işaretle |
| Kısmi başarı | 2/3 başarılı ise devam et, 1/3 ise işlemi durdur |
| Tam başarısızlık | İşlemi iptal et, bildirim gönder |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 22.07.2026 | İlk yayın: örnekleme parametreleri, bayraklı oran, uç durumlar. |
