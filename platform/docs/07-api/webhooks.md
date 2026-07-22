# Webhooks

| Alan | Değer |
|---|---|
| Doküman ID | 07-api/webhooks |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 07-api/rest-api, 0503, 0304, 0204 |

---

## 1. Amaç

Bu doküman GeoLens Platform webhook bildirim sistemini tanımlar. Webhook'lar, belirli olaylar (skor değişimi, öneri üretimi, rapor hazır) dış sistemlere HTTP çağrısı ile bildirim göndermek için kullanılır.

---

## 2. Desteklenen Olaylar

| Olay | Tetikleyici | Açıklama |
|:----:|:-----------:|----------|
| `score.changed` | Anlamlı skor değişimi | Skor eşik aşımında bildirim |
| `alert.triggered` | Uyarı üretimi | Yeni uyarı oluştuğunda |
| `report.generated` | Rapor hazır | PDF rapor üretildiğinde |
| `recommendation.new` | Yeni öneri | Öneri üretildiğinde |
| `measurement.completed` | Ölçüm tamam | Ölçüm döngüsü bittiğinde |

---

## 3. İstek Formatı

```http
POST {webhook_url}
Content-Type: application/json
X-GeoLens-Signature: {hmac_sha256_hex}
X-GeoLens-Timestamp: 2026-07-22T12:00:00Z

{
  "event": "score.changed",
  "event_id": "ulid",
  "timestamp": "2026-07-22T12:00:00Z",
  "tenant_id": "tenant-ulid",
  "data": {
    "workspace_id": "ws-ulid",
    "brand_id": "brand-ulid",
    "old_value": 65.4,
    "new_value": 72.1,
    "change_percent": 10.2
  }
}
```

---

## 4. Güvenlik

| Mekanizma | Açıklama |
|-----------|----------|
| HMAC imzası | Payload HMAC-SHA256 ile imzalanır, `X-GeoLens-Signature` başlığında gönderilir |
| Zaman damgası | `X-GeoLens-Timestamp` başlığı; yeniden oynatma saldırısını önler |
| Yeniden deneme | Başarısız teslimatlar 3 kez yeniden dener (üstel geri çekilme) |
| Alıcı doğrulama | Webhook URL kontrol panelden onaylanmalıdır |

---

## 5. Webhook Yönetimi (API)

| Metot | Uç | Açıklama |
|:-----:|:---|----------|
| POST | /v1/workspaces/{ws}/channels | Webhook kanalı ekleme |
| GET | /v1/workspaces/{ws}/channels | Kanal listesi |
| PATCH | /v1/workspaces/{ws}/channels/{id} | Kanal güncelleme |
| DELETE | /v1/workspaces/{ws}/channels/{id} | Kanal silme |
| POST | /v1/workspaces/{ws}/channels/{id}/test | Test bildirimi |

---

## Kaynaklar

- 07-api/rest-api — REST API uçları
- 0503 Event-Driven — olay kuyruğu ve garantileri
- 0304 Domain Events — 21 alan olayı kataloğu
- 0204 PRD — FR-F1, FR-F2 (bildirim)
- 07-api/authentication — HMAC imza detayı

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: 5 webhook olayı, istek formatı, HMAC güvenlik, webhook yönetimi. |
