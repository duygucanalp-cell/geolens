# 0800 · Kullanıcı Arayüzü Katmanı

| Alan | Değer |
|---|---|
| Doküman ID | 0800 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 25 Temmuz 2026 |
| İlişkili | 0801–0805, 0202, 0204 |

---

## 1. Amaç

Bu doküman, GeoLens Platform'un kullanıcı arayüzü katmanı dokümantasyonuna giriş niteliğindedir. UI tasarım prensiplerini ve dizin yapısını tanımlar.

---

## 2. Dizin Kapsamı

| # | Doküman | Konu |
|:-:|---------|------|
| 0801 | Tasarım Sistemi | Renk paleti, tipografi, bileşen kütüphanesi |
| 0802 | Pano | Dashboard, skor kartı, trend grafikleri |
| 0803 | Navigasyon | Menü yapısı, sayfa akışı, routing |
| 0804 | Onboarding | İlk kullanıcı deneyimi, tutorial |
| 0805 | Erişilebilirlik | WCAG uyumu, klavye navigasyonu |

---

## 3. Teknoloji

- **Framework:** React 18 + TypeScript
- **Build:** Vite
- **Grafik:** Recharts (ComposedChart, BarChart)
- **State:** useState + useEffect (MVP'de state management yok)
- **Proxy:** Vite proxy `/v1` → `localhost:8080`

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 25.07.2026 | İlk yayın: UI katmanı giriş ve teknoloji seçimleri |
