# 0105 · Aksiyon Standardı (GAVF S4)

| Alan | Değer |
|---|---|
| Doküman ID | 0105 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0101 (S4), 0206, 0207, 0208, 0209 |

---

## 1. Amaç

Skor verisinden aksiyon üretme sürecini tanımlar. GAVF S4 kapsamındadır.

## 2. Aksiyon Türleri

| Tür | Açıklama | Kaynak |
|-----|----------|--------|
| **Fırsat (Opportunity)** | Düşük skorlu ancak yüksek etkili alanlar | Bileşen bazlı skor analizi |
| **Öneri (Recommendation)** | Kanıt dereceli iyileştirme önerileri | Kural kütüphanesi + veri analizi |
| **Trend (Trend)** | Zaman içinde skor değişim yönü | Zaman serisi analizi |
| **Uyarı (Alert)** | İstatistiksel anlamlı değişim bildirimi | Anomali tespiti |

## 3. Öneri Kanıt Dereceleri

| Seviye | Anlamı |
|:------:|--------|
| **Deneysel** | Veri destekli ama kesin kanıt yok |
| **Korelasyonel** | İki değişken arasında korelasyon var |
| **Uygulayıcı** | Önceki uygulamalarda olumlu sonuç vermiş |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 22.07.2026 | İlk yayın: S4 aksiyon standardı. |
