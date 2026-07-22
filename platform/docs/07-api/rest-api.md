# REST API

| Alan | Değer |
|---|---|
| Doküman ID | 07-api/rest-api |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0504, 0306, 0204, 07-api/* |

---

## 1. Amaç

Bu doküman GeoLens Platform REST API sözleşmesini tanımlar. Sözleşme-öncelikli yaklaşımla, tüm API uçları openapi.yaml dosyasında yaşar; bu doküman standartları ve temsili uçları açıklar.

---

## 2. Sözleşme Standartları

| Standart | Kural |
|----------|-------|
| Format | OpenAPI 3.1 |
| Sürümleme | URL ana sürümü: `/v1/` |
| Kimlikler | Opak ULID |
| Tarihler | UTC ISO-8601 |
| Dil | Türkçe-öncelikli (i18n), İngilizce hazır |
| İstemci tipleri | OpenAPI'den üretilir (oapi-codegen) |

---

## 3. Kaynak Uçları

### Kimlik ve Kiracı (BC1)

| Metot | Uç | Açıklama | FR |
|:-----:|:---|----------|:--:|
| POST | /v1/auth/register | Kayıt | FR-A1 |
| POST | /v1/auth/login | Giriş | FR-A3 |
| POST | /v1/auth/logout | Çıkış | FR-A3 |
| POST | /v1/auth/reset-password | Parola sıfırlama | FR-A3 |
| GET | /v1/me | Profil ve üyelikler | FR-A2 |
| GET | /v1/tenant | Kiracı bilgisi | FR-A5 |
| GET | /v1/tenant/members | Üye listesi | FR-A2 |
| POST | /v1/tenant/invitations | Davet gönderme | FR-A2 |

### Marka ve Yapılandırma (BC2)

| Metot | Uç | Açıklama | FR |
|:-----:|:---|----------|:--:|
| GET/POST | /v1/workspaces/{ws}/brands | Marka listesi/ekleme | FR-B1 |
| GET/PUT/DELETE | /v1/workspaces/{ws}/brands/{id} | Marka detayı | FR-B1 |
| GET/POST | /v1/workspaces/{ws}/sites | Site yönetimi | FR-B1 |
| GET/POST | /v1/workspaces/{ws}/prompt-sets | Prompt setleri | FR-B2 |
| GET | /v1/prompt-templates | Şablon kütüphanesi | FR-B2 |
| POST | /v1/workspaces/{ws}/site-audits | Site denetimi | FR-B4 |

### Ölçüm ve Skor (BC3)

| Metot | Uç | Açıklama | FR |
|:-----:|:---|----------|:--:|
| POST | /v1/workspaces/{ws}/measurements | Ölçüm tetikleme | FR-C1 |
| GET | /v1/workspaces/{ws}/measurements/{id} | İş durumu | FR-C1 |
| GET | /v1/workspaces/{ws}/scores | Skor listesi | FR-C4-C7 |
| GET | /v1/workspaces/{ws}/scores/{id} | Skor detayı | FR-C4-C7 |
| GET | /v1/workspaces/{ws}/calculation-runs/{id} | Hesap koşusu | İ3 |
| GET | /v1/workspaces/{ws}/trends | Trend verisi | FR-D4 |

### İçgörü (BC4)

| Metot | Uç | Açıklama | FR |
|:-----:|:---|----------|:--:|
| GET | /v1/workspaces/{ws}/recommendations | Öneri listesi | FR-E1 |
| PATCH | /v1/workspaces/{ws}/recommendations/{id} | Öneri işaretleme | FR-E3 |

### Bildirim ve Rapor (BC5)

| Metot | Uç | Açıklama | FR |
|:-----:|:---|----------|:--:|
| GET/POST | /v1/workspaces/{ws}/alert-rules | Uyarı kuralları | FR-F2 |
| GET | /v1/workspaces/{ws}/alerts | Uyarı listesi | FR-F1 |
| POST | /v1/workspaces/{ws}/alerts/{id}/feedback | Geri bildirim | FR-F1 |
| POST | /v1/workspaces/{ws}/reports | Rapor talep | FR-F4 |
| GET | /v1/workspaces/{ws}/reports/{id} | Rapor durumu | FR-F4 |
| GET | /v1/workspaces/{ws}/reports/{id}/download | Rapor indirme | FR-F4 |
| GET/POST | /v1/workspaces/{ws}/channels | Bildirim kanalları | FR-F2 |

---

## 4. Eşzamansız İş Deseni

Tüm uzun işler (ölçüm, denetim, rapor) aynı deseni kullanır:

```http
POST /v1/workspaces/{ws}/measurements
Content-Type: application/json
Idempotency-Key: unique-key

→ 202 Accepted
Location: /v1/workspaces/{ws}/measurements/job-ulid

GET /v1/workspaces/{ws}/measurements/job-ulid
→ 200 OK
{
  "status": "completed",
  "result": { ... }
}
```

Durumlar: `queued`, `running`, `completed`, `partial`, `failed`

---

## 5. Hata Yanıt Formatı

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Marka adı zorunludur",
  "details": [
    { "field": "name", "error": "required" }
  ],
  "correlation_id": "req-ulid"
}
```

| HTTP | Kod | Anlamı |
|:----:|:---:|--------|
| 401 | AUTH_TOKEN_EXPIRED | Oturum süresi doldu |
| 403 | ENTITLEMENT_DENIED | Paket hakkı yetersiz |
| 404 | NOT_FOUND | Kaynak bulunamadı (ayrımsız) |
| 422 | VALIDATION_ERROR | Doğrulama hatası |
| 429 | RATE_LIMITED / QUOTA_EXCEEDED | Hız/kota aşımı |

---

## Kaynaklar

- 0504 API Architecture — API tasarım standartları
- 0306 API Design — detaylı API sözleşmeleri
- 0204 PRD — FR gereksinimleri
- 07-api/authentication — auth detayı
- archive/avip-v1/0306-api-design.md

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: REST API sözleşmesi, 30+ uç, eşzamansız desen, hata formatı. |
