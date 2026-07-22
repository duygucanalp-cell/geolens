# 0504 · API Mimarisi (API Architecture)

| Alan | Değer |
|---|---|
| Doküman ID | 0504 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0501, 0306, 0310, 0204 |

---

## 1. Amaç

Bu doküman GeoLens REST API'sinin sözleşme standartlarını tanımlar. OpenAPI-öncelikli yaklaşımla, uç yapısı, kimlik doğrulama, hata yönetimi ve sayfalama kurallarını sabitler.

---

## 2. Sözleşme Yaklaşımı

- **Sözleşme-öncelikli:** openapi.yaml tek gerçek kaynak
- **Sürümleme:** URL ana sürümüyle (/v1)
- **Kırıcı değişiklik:** yeni ana sürüm açar
- **Genişletme:** yanıta yeni alan eklemek kırıcı değil
- **Kimlikler:** opak ULID dizeleri
- **Tarihler:** UTC ISO-8601

---

## 3. Kaynak Modeli

Her bağlam için RESTful kaynak uçları:

| Uç | Amaç | FR |
|:--:|------|:--:|
| /v1/auth/* | Giriş, çıkış, parola sıfırlama | FR-A3 |
| /v1/me | Profil, üyelikler | FR-A2 |
| /v1/tenant | Paket, kullanım özeti | FR-H1 |
| /v1/workspaces/{ws}/brands | Marka yönetimi | FR-B1 |
| /v1/workspaces/{ws}/prompt-sets | Prompt setleri | FR-B2 |
| /v1/workspaces/{ws}/panels | Panel ve versiyonlar | FR-B5 |
| /v1/workspaces/{ws}/measurements | Ölçüm tetikleme/durum | FR-C1 |
| /v1/workspaces/{ws}/scores | Skor listesi/detay | FR-C4-C7 |
| /v1/workspaces/{ws}/calculation-runs | Hesap koşusu detayı | İ3 |
| /v1/workspaces/{ws}/trends | Zaman serisi | FR-D4 |
| /v1/workspaces/{ws}/citations | Alıntı analizi | FR-D2 |
| /v1/workspaces/{ws}/recommendations | Öneriler | FR-E1-E3 |
| /v1/workspaces/{ws}/alerts | Uyarılar | FR-F1-F2 |
| /v1/workspaces/{ws}/reports | Rapor üretimi | FR-F4 |
| /v1/tenant/members | Üye yönetimi | FR-A5 |

---

## 4. Eşzamansız İş Deseni

Uzun işler için (ölçüm, rapor, site denetimi):

```
POST → 202 Accepted (Location: /iş/id)
GET /iş/id → { status: queued|running|completed|partial|failed, result: {...} }
```

Idempotency-Key başlığı desteklenir.

---

## 5. Hata Yönetimi

| Kod | HTTP | Anlamı |
|:---:|:----:|--------|
| AUTH_* | 401 | Kimlik doğrulama hatası |
| TENANT_* | 403/404 | Kiracı erişim/varolma |
| ENTITLEMENT_DENIED | 403 | Paket hakkı yetersiz |
| VALIDATION_* | 422 | Doğrulama hatası |
| QUOTA_EXCEEDED | 429 | Kota aşımı |
| RATE_LIMITED | 429 | Hız sınırı |
| NOT_FOUND | 404 | Kaynak bulunamadı (ayrımsız) |

---

## 6. Sayfalama

İmleç tabanlı: `cursor` (ULID) + `limit` parametreleri. Yanıt: `items`, `next_cursor`, `has_more`.

---

## Kaynaklar

- 0501 System Architecture — middleware zinciri
- 0306 API Design — API sözleşmeleri
- 0310 Security — auth, oturum, RBAC
- archive/avip-v1/0306-api-design.md

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: sözleşme yaklaşımı, kaynak modeli, eşzamansız desen, hata yönetimi, sayfalama. |
