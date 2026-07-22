# 0503 · Event-Driven Architecture (Olay Odaklı Mimari)

| Alan | Değer |
|---|---|
| Doküman ID | 0503 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0501, 0304, 0307, ADR-005 |

---

## 1. Amaç

Bu doküman GeoLens'in olay odaklı mimarisini tanımlar. Redis Streams + outbox pattern ile bağlamlar arası asenkron iletişim altyapısını detaylandırır.

---

## 2. Outbox Pattern

Tüm olay üretimi transactional outbox deseniyle yapılır:

```
1. İşlem → EventOutbox kaydı (aynı PG işleminde)
2. Outbox dağıtıcısı → pending kayıtlar (SKIP LOCKED)
3. Redis Streams kuyruğuna yazma
4. Tüketici (worker) → kuyruktan okuma
5. dispatched işaretleme
```

---

## 3. Kuyruk Yapısı (Redis Streams)

| Kuyruk | Tüketici Grubu | İş Sınıfları |
|--------|:--------------:|--------------|
| q:measure | cg:measure | measure.panel, measure.manual |
| q:audit | cg:measure | audit.site |
| q:report | cg:report | report.render, report.scheduled |
| q:notify | cg:notify | notify.alert, digest.weekly |
| q:dead | — | Zehirli mesajlar (DLQ) |

---

## 4. Tüketim Garantileri

| Garanti | Mekanizma |
|---------|-----------|
| At-least-once | XREADGROUP + XACK sonrası onay |
| Transactional outbox | EventOutbox + PG işlemi |
| İdempotent tüketim | Idempotency_key + koşullu güncelleme |
| Devralma | XAUTOCLAIM (boşta kalma süresi sonrası) |
| DLQ | Max deneme sonrası zehirli mesaj taşıma |

---

## 5. Olay Akış Şeması

```
scheduler → q:measure → worker:measure → ScoreCalculated → outbox
                                                                ↓
                                                q:notify → worker:notify → Alert
                                                q:report → worker:report → PDF
```

---

## Kaynaklar

- 0501 System Architecture — ölçüm hattı
- 0304 Domain Events — 21 alan olayı kataloğu
- 0307 Background Jobs — kuyruk tasarımı
- ADR-005 — Redis Streams kararı
- archive/avip-v1/0307-background-jobs-scheduling.md

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: outbox pattern, kuyruk yapısı, tüketim garantileri, olay akışı. |
