# 0307 · Arkaplan İşleri (Background Jobs)

| Alan | Değer |
|---|---|
| Doküman ID | 0307 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 25 Temmuz 2026 |
| İlişkili | ADR-002, ADR-004, 0503, 0304, 0206 |

---

## 1. Amaç

Bu doküman, GeoLens'in arkaplan iş altyapısını tanımlar. Kuyruk yapısı, worker tasarımı, hata yönetimi ve DLQ (Dead Letter Queue) mekanizmalarını detaylandırır.

---

## 2. Kuyruk Mimarisi

```
Scheduler → PostgreSQL Outbox → Redis Streams → Worker → Engine
                                                   ↓
                                              DLQ (q:dead)
```

| Bileşen | Teknoloji | Açıklama |
|---------|-----------|----------|
| Kaynak | Scheduler (cron) | Panelleri tarar, job üretir |
| Outbox | `measure.event_outbox` | Transactional outbox tablosu |
| Kuyruk | Redis Streams (`q:measure`) | XADD ile yazma, XREADGROUP ile okuma |
| Tüketici | Worker | Stream'den job alır, engine çağırır |
| DLQ | Redis Streams (`q:dead`) | Başarısız işlerin toplandığı akış |

---

## 3. Worker Ölçekleme

- Consumer group: `measure-workers`
- Aynı anda 1 worker aktif (MVP)
- Her worker 3 paralel goroutine ile 3 engine çağrısını eşzamanlı yapar
- ID'ye dayalı tüketici (XACK ile tamamlama)

---

## 4. Hata Yönetimi

| Hata Türü | Aksiyon |
|-----------|---------|
| Engine timeout (>30s) | Yeniden dene (max 3) → DLQ |
| Engine parse hatası | Sample atlanır, partial yayın |
| Outbox gönderim hatası | Backup: scheduler yeniden dener |
| DLQ birikmesi | Alarm (`q:dead` > 10) → manuel müdahale |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 25.07.2026 | İlk yayın: kuyruk mimarisi, worker tasarımı, hata yönetimi |
