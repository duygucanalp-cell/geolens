# 0503 · Event-Driven Architecture (Olay Odaklı Mimari)

| Alan | Değer |
|---|---|
| Doküman ID | 0503 |
| Proje | GeoLens Platform |
| Versiyon | 1.3 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0501, 0304, 0307, ADR-005, ADR-014 |

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
| q:sentiment | cg:analysis | analysis.sentiment, analysis.hallucination |
| q:replay | cg:analysis | replay.snapshot |
| q:archive | cg:analysis | archive.entry |
| q:gap | cg:analysis | competitive.gap |
| q:technical-geo | cg:analysis | technical.bot, technical.schema |
| q:content-geo | cg:analysis | content.gap, content.hub |
| q:dead | — | Zehirli mesajlar (DLQ) |

> **Faz 4/HT1 kapsamı:** Analiz akışları 0307 §2.1 ve Java tarafında `dev.geolens.queue.QueueProperties` sabitleriyle birebir eşleşir. SEO senkronu (Search Console/GA4) Redis Stream **kullanmaz**; worker içinde zamanlayıcı tabanlıdır. Java geçişi sonrası akışlar tek worker profilinde q:measure + q:governance tüketicileriyle işlenir.

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
scheduler profili → q:measure → worker profili → ScoreCalculated → outbox
                                                          ↓
                                                  q:governance → worker → webhook/Alert
```

---

## 6. Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|:--:|-------|:------:|
| D-74 | Redis Streams + ADR-005. Olay kuyruğu için Redis Streams seçildi (Kafka değil). Bu doküman §3 ile uyumlu. | AVIP 0303 O-1 (TL 21.07.2026) |
| D-75 | DLQ (dead letter queue) oynatma mekanizması: XAUTOCLAIM + max deneme sonrası zehirli mesaj taşıma. Bu doküman §4 ile uyumlu. | AVIP 0307 O-4 (TL 21.07.2026) |
| D-73 | Redis kilit kaybı senaryosu: anında pasif — kilit kaybında üretim durur, yeni lider seçilene kadar beklenir. Bu mimaride scheduler için geçerlidir. | AVIP 0301 O-3 (TL 21.07.2026) |

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
| 1.1 | 23.07.2026 | Devralınan AVIP Kararları eklendi: D-74 (Redis Streams/ADR-005), D-75 (DLQ oynatma), D-73 (kilit kaybı). |
| 1.2 | 04.08.2026 | **Stream senkronu:** §3 kuyruk yapısı kod gerçeğiyle güncellendi (`platform/queue/outbox.go`) — 6 analiz akışı eklendi (q:sentiment, q:replay, q:archive, q:gap, q:technical-geo, q:content-geo). SEO senkronunun stream kullanmadığı not edildi. 0307 §2.1 ile hizalı. |
| 1.3 | 15.08.2026 | **Java geçişi:** Kuyruk sabitleri `dev.geolens.queue.QueueProperties` ile güncellendi; olay akış şeması Java profillerine (scheduler → q:measure → worker; q:governance → webhook) çevrildi. ADR-014 ilişkili listesine eklendi. |
