# 0700 · API Katmanı

| Alan | Değer |
|---|---|
| Doküman ID | 0700 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 25 Temmuz 2026 |
| İlişkili | 0701–0705, 0504, 0204 |

---

## 1. Amaç

Bu doküman, GeoLens Platform'un API katmanı dokümantasyonuna giriş niteliğindedir. API tasarım prensiplerini ve dizin yapısını tanımlar.

---

## 2. Dizin Kapsamı

| # | Doküman | Konu |
|:-:|---------|------|
| 0701 | REST API | Tüm REST endpoint'leri, istek/yanıt formatları |
| 0702 | Kimlik Doğrulama | JWT, OAuth, API anahtarları |
| 0703 | GraphQL | GraphQL sorgu şeması (varsa) |
| 0704 | Webhook'lar | Olay bazlı webhook bildirimleri |
| 0705 | Hız Sınırları | Rate limiting, kota yönetimi |

---

## 3. API Tasarım İlkeleri

| İlke | Açıklama |
|------|----------|
| Sürümleme | `/v1/` prefix |
| Format | JSON (Content-Type: application/json) |
| Kimlik | JWT Bearer token (Authorization header) |
| Kiracı | URL path: `/v1/workspaces/{ws}/...` |
| Sayfalama | `cursor` veya `offset/limit` |
| Hata formatı | `{ "error": { "code": "...", "message": "..." } }` |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 25.07.2026 | İlk yayın: API katmanı giriş ve tasarım ilkeleri |
