# 0507 · Çok Kiracılı Mimari (Multi-Tenancy)

| Alan | Değer |
|---|---|
| Doküman ID | 0507 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0501, 0508, 0302, 0305, 0310, ADR-004 |

---

## 1. Amaç

Bu doküman GeoLens'in çok kiracılı mimarisini tanımlar. Tek şema + RLS (Row-Level Security) yaklaşımıyla kiracı izolasyonunu ve veri güvenliğini detaylandırır.

---

## 2. İzolasyon Modeli — Beş Katman

| Katman | Mekanizma |
|:------:|-----------|
| 1. Uygulama sözleşmesi | Tüm repository'ler kiracı bağlamı zorunlu |
| 2. Veri/RLS | `SET LOCAL app.tenant_id` + RLS politikaları |
| 3. Kuyruk | Kiracı önekli Redis anahtarları |
| 4. Depolama | S3 anahtar şeması: `raw/{tenant}/{workspace}/...` |
| 5. Worker | Yük içi kiracı bağlam doğrulama |

---

## 3. Kiracı Türleri

| Tür | Özellik | Örnek |
|:---:|---------|-------|
| **Standart** | Tek çalışma alanı, kendi markaları | KOBİ (P2) |
| **Ajans** | Çoklu çalışma alanı, müşteri izolasyonu | Ajans (P3) |

---

## 4. RLS Politikası (PostgreSQL)

```sql
-- Tüm tablolar için şablon politika
CREATE POLICY tenant_isolation ON scores
    USING (tenant_id = current_setting('app.tenant_id')::text);

-- Her işlem başında (ULID: 26 karakter metin)
SET LOCAL app.tenant_id = 'tenant-ulid';
```

---

## 5. Şema Yapısı

```
public.identity.*    — BC1 tabloları
public.config.*      — BC2 tabloları
public.measure.*     — BC3 tabloları
public.insight.*     — BC4 tabloları
public.delivery.*    — BC5 tabloları
public.gov.*         — BC6 tabloları
```

---

## 6. İzolasyon Doğrulama Testleri

| Test | Beklenen |
|:----:|----------|
| A kiracısı B verisini okuyamaz | 0 satır |
| A kiracısı B verisine yazamaz | Hata / 0 satır |
| A kiracısı B'nin listesini göremez | Sadece A verisi |
| Bağlamsız sorgu reddedilir | Hata |
| Çapraz kiracı S3 URL'si üretilemez | Hata |

---

## Kaynaklar

- 0501 System Architecture — 5 katmanlı izolasyon
- 0508 Security — tehdit modeli, RBAC
- 0310 Security — RLS, IAM, sır yönetimi
- ADR-004 — tek şema + RLS kararı
- archive/avip-v1/0310-security-multi-tenancy.md

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: 5 katmanlı izolasyon, kiracı türleri, RLS politikası, doğrulama testleri. |
