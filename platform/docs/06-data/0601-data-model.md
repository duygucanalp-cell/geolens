# 0601 · Veri Modeli (Data Model)

| Alan | Değer |
|---|---|
| Doküman ID | 0601 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0302, 0303, 0602, 0605, 0606, 0204 |

---

## 1. Amaç

Bu doküman GeoLens Platform'un veri modelini üst düzeyde tanımlar: veri kategorileri, veri akışları, saklama stratejisi ve veri sahipliği. 0602 (PostgreSQL), 0605 (retention) ve 0606 (quality) bu modelin alt detaylarıdır.

---

## 2. Veri Kategorileri

| Kategori | Örnekler | Hacim | Kritiklik |
|:--------:|----------|:-----:|:---------:|
| **Kiracı verisi** | Tenant, workspace, user, membership | Küçük | Yüksek |
| **Yapılandırma** | Brand, prompt seti, panel, izleme planı | Küçük | Yüksek |
| **Ölçüm verisi** | Raw response, citation, score | Büyük | Yüksek |
| **İçgörü** | Recommendation, opportunity | Orta | Orta |
| **Bildirim** | Alert, report, digest | Orta | Düşük |
| **Denetim** | Audit log, usage record | Büyük | Yüksek |
| **Geçici** | Outbox, cache, lock | Büyük/geçici | Orta |

---

## 3. Veri Akışı

```
Girdi (ölçüm) → Ham veri (S3) → Meta (PG) → Skor (PG) → İçgörü (PG) → Rapor (S3)
     ↓                                                            ↑
  Kuyruk (Redis) ────────────────────────────────────────────────┘
```

---

## 4. Veri Sahipliği

| Veri | Sahip Bağlam | Depolama |
|:----:|:------------:|:--------:|
| Kiracı | BC1 Identity | PostgreSQL |
| Marka/prompt | BC2 Config | PostgreSQL |
| Ham yanıt | BC3 Measure | S3 + PostgreSQL (meta) |
| Skor | BC3 Measure | PostgreSQL |
| Öneri | BC4 Insight | PostgreSQL |
| Uyarı/rapor | BC5 Delivery | PostgreSQL + S3 (PDF) |
| Denetim/kota | BC6 Governance | PostgreSQL |

---

## 5. Veri Hacim Tahminleri (MVP)

| Tablo | Aylık Büyüme | 6 Ay Sonra | Not |
|-------|:------------:|:----------:|-----|
| measurement_jobs | 3.000 | 18.000 | 10 kiracı × günlük |
| raw_responses | 9.000 | 54.000 | 3 motor (MVP tabanı) × 3 tekrar |
| scores | 9.000 | 54.000 | Her ölçümden |
| citations | 18.000 | 108.000 | Motor başına 2-5 alıntı |
| audit_log | 15.000 | 90.000 | API istekleri + işlemler |

---

## Kaynaklar

- 0302 Domain Model — varlıklar ve bağlamlar
- 0303 Aggregates — toplam kökleri
- 0602 PostgreSQL Schema — DDL detayı
- 0605 Data Retention — saklama süreleri
- archive/avip-v1/0303-database-design.md

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: veri kategorileri, akış, sahiplik, hacim tahminleri. |
| 1.1 | 04.08.2026 | **Motor senkronu:** §5 hacim tahminlerindeki "3 motor" notu "3 motor (MVP tabanı)" olarak işaretlendi — üretimde 8 motor vardır (0308 v1.3); hacim değerleri MVP tabanına aittir. |
