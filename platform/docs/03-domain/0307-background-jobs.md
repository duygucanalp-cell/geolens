# 0307 · Arkaplan İşleri (Background Jobs)

| Alan | Değer |
|---|---|
| Doküman ID | 0307 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 04 Ağustos 2026 |
| İlişkili | ADR-002, ADR-004, 0503, 0304, 0206, platform/queue |

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
| Kaynak | Scheduler (cron) + API handler'ları | Job'ları transaction içinde outbox'a yazar |
| Outbox | `public.event_outbox` | Transactional outbox tablosu (ölçüm + analiz olayları) |
| Dağıtıcı | `platform/queue` Dispatcher | Polling ile pending outbox'ı Redis Streams'e XADD'ler |
| Kuyruk | Redis Streams (11 akış, bkz. §2.1) | XREADGROUP ile okuma |
| Tüketici | Worker (`cmd/worker`) | `q:measure`'i ana tüketici olarak okur; analiz akışlarını aynı grup üzerinden işler |
| DLQ | Redis Streams (`q:dead`) | Max deneme sonrası başarısız işlerin toplandığı akış |

### 2.1 Redis Streams Listesi

Stream sabitleri `platform/queue/outbox.go` içinde tanımlıdır (kaynak doğruluk noktası):

| Stream | Amaç | Tüketici |
|--------|------|:--------:|
| `q:measure` | Ölçüm job'ları (`measurement.requested`) | Worker (ana döngü) |
| `q:audit` | Denetim olayları | Worker |
| `q:report` | Rapor üretim işleri | Worker |
| `q:notify` | Bildirim/alert işleri | Worker |
| `q:sentiment` | Sentiment/hallüsinasyon analizi | Worker (analiz) |
| `q:replay` | Conversation replay işleri | Worker (analiz) |
| `q:archive` | Response archive işleri | Worker (analiz) |
| `q:gap` | Content gap analizi | Worker (analiz) |
| `q:technical-geo` | Teknik GEO analizi (bot/schema) | Worker (analiz) |
| `q:content-geo` | Content GEO analizi (hub-score/topic) | Worker (analiz) |
| `q:dead` | DLQ — başarısız işler | Manuel/operatör |

> SEO senkronu ve benchmark toplayıcı Redis Stream **kullanmaz**; ticker/zamanlayıcı tabanlıdır (`cmd/worker/main.go`).

---

## 3. Worker Ölçekleme

- Consumer group: `cfg.ConsumerGroup` (ör. `measure-workers`) — tüm akışlar aynı grup adını paylaşır
- Aynı anda 1 worker aktif (MVP)
- Her worker 3 paralel goroutine ile 3 engine çağrısını eşzamanlı yapar
- ID'ye dayalı tüketici (XACK ile tamamlama)
- `q:measure` ana döngüde XREADGROUP BLOCK ile okunur; hata durumunda stream/grup yoksa yeniden oluşturulur
- Analiz akışları (`q:sentiment`, `q:replay`, `q:archive`, `q:gap`, `q:technical-geo`, `q:content-geo`) aynı worker içinde işlenir
- Kuyruk derinliği Prometheus gauge'larıyla izlenir (`geolens_queue_*`, 0311)

---

## 4. Hata Yönetimi

| Hata Türü | Aksiyon |
|-----------|---------|
| Engine timeout (>30s) | Yeniden dene (max 3) → DLQ |
| Engine parse hatası | Sample atlanır, partial yayın |
| Outbox gönderim hatası | Backup: scheduler yeniden dener |
| Max deneme aşımı | `q:dead`'e XADD ile taşınır (tüketici grup adı korunur) |
| DLQ birikmesi | Alarm (`q:dead` > 10) → manuel müdahale |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 25.07.2026 | İlk yayın: kuyruk mimarisi, worker tasarımı, hata yönetimi |
| 1.1 | 04.08.2026 | **Stream senkronu:** kuyruk mimarisi kod gerçeğiyle güncellendi (platform/queue/outbox.go). Tek `q:measure` yerine 11 Redis Stream tanımlandı; §2.1 stream tablosu eklendi (analiz akışları: sentiment, replay, archive, gap, technical-geo, content-geo). SEO/benchmark'ın stream kullanmadığı (ticker tabanlı) not edildi. Worker ölçekleme ve DLQ hataları senkronize edildi. 0304 §7.5 ile hizalı. |
