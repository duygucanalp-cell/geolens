# ADR-002 · Event-Driven Architecture (Olay Odaklı Mimari)

| Alan | Değer |
|---|---|
| ADR ID | ADR-002 |
| Durum | Kabul |
| Tarih | 22.07.2026 |
| Karar veren | TL |
| İlişkili | 0304, 0307, 0503, ADR-004 |

---

## Bağlam

Bağlamlar arası iletişim için bir mekanizma seçilmelidir. Senkron iletişim (doğrudan HTTP/GRPC çağrıları) bağlamlar arası bağımlılığı artırır ve gevşek bağlılık ilkesini zedeler.

---

## Karar

**Redis Streams + transactional outbox pattern kullanılır.**

| Bileşen | Teknoloji |
|---------|-----------|
| Kuyruk | Redis Streams |
| Outbox | PostgreSQL event_outbox tablosu |
| Dağıtıcı | Outbox dağıtıcısı (SKIP LOCKED) |
| Tüketici | Worker (XREADGROUP) |
| DLQ | q:dead akışı |

---

## Alternatifler

| Seçenek | Red nedeni |
|---------|------------|
| **Kafka** | Ek operasyonel yük; 4 kişilik ekip için ağır; V1 ölçeğinde gerekli değil. Redis Streams yeterli. |
| **RabbitMQ** | Redis Streams kadar native tüketici grubu desteği yok |
| **GRPC bidirectional streaming** | Bağlamlar arası sıkı bağlılık; asenkron garantisi yok |
| **SQS/SNS** | Bulut bağımlılığı; yerel geliştirme zorluğu |

---

## Sonuçlar

- 0304 Domain Events outbox pattern ile taşınır
- 0307 Background Jobs kuyruk yapısını detaylandırır
- 0503 Event-Driven Architecture outbox mekanizmasını tanımlar
- Kafka red kararı ADR-004'te kayıtlıdır

---

## Değişiklik Geçmişi

| Tarih | Değişiklik |
|-------|------------|
| 22.07.2026 | İlk karar: Kabul |
