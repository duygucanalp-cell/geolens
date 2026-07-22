# 0603 · ClickHouse Şeması (Rezerve)

| Alan | Değer |
|---|---|
| Doküman ID | 0603 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0601, 0602, 0206 |

---

## 1. Amaç

Bu doküman, GeoLens Platform'da ileride kullanılmak üzere rezerve edilmiş ClickHouse entegrasyon noktalarını tanımlar. **MVP'de ClickHouse kullanılmamaktadır.** Tüm veri PostgreSQL'de saklanır.

---

## 2. ClickHouse İhtiyacı

| İhtiyaç | MVP Çözümü | ClickHouse Geçiş Kriteri |
|---------|:----------:|-------------------------|
| Uzun dönem trend sorguları | PostgreSQL (indeksli) | 6 ay + 50M skor satırı |
| Büyük hacimli analitik sorgular | PostgreSQL (sayfalama) | Sorgu süresi >5s p50 |
| Anonim benchmark toplulaştırması | PostgreSQL (HT2) | ≥5 kiracı + HT2 etkin |

---

## 3. Rezerve Şema Tasarımı (Gelecek)

```sql
-- Rezerve: ileride aktifleştirilecek
CREATE TABLE scores_analytics (
    tenant_id String,
    brand_id String,
    engine Nullable(String),
    value Float32,
    ci_low Float32,
    ci_high Float32,
    measured_at DateTime,
    panel_version_id String
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(measured_at)
ORDER BY (tenant_id, brand_id, measured_at);
```

---

## 4. Geçiş Planı

| Aşama | Koşul | Aksiyon |
|:-----:|-------|---------|
| Değerlendirme | Sorgu süresi eşiği aşıldı | Proof of concept |
| Pilot | Doğrulama tamam | Paralel çalıştırma (PG + CH) |
| Geçiş | Pilot başarılı | Tarihsel veri taşıma |
| Kesin | 2 hafta stabil | PostgreSQL'den okuma durdurma |

---

## Kaynaklar

- 0601 Data Model — veri kategorileri, hacim tahminleri
- 0602 PostgreSQL Schema — mevcut şema
- 0206 Roadmap — HT2, Platform Ufku
- 0304 Technology Selection — veri deposu kararları

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: ClickHouse rezervasyonu, MVP'de kullanılmaz. Geçiş kriterleri ve planı. |
