# ADR-004 · Mesaj Kuyruğu Seçimi — Kafka (Red Kararı) / Redis Streams (Kabul)

| Alan | Değer |
|---|---|
| ADR ID | ADR-004 |
| Durum | Kabul |
| Tarih | 22.07.2026 |
| Karar veren | TL + PO |
| İlişkili | ADR-002, 0307, 0503, 0206 |

---

## Bağlam

Bağlamlar arası asenkron iletişim için bir mesaj kuyruğu seçilmelidir. Apache Kafka sektörde yaygın bir seçenektir ancak operasyonel karmaşıklığı yüksektir.

---

## Karar

**Apache Kafka kullanılmayacaktır. Redis Streams tercih edilmiştir.**

Gerekçeler:

| Gerekçe | Açıklama |
|---------|----------|
| **Operasyonel basitlik** | Redis zaten bağımlılık; Kafka ek altyapı ve bakım gerektirir |
| **Ekip kapasitesi** | 4 kişilik ekip Kafka operasyonu için yeterli uzmanlığa sahip değil |
| **V1 ölçeği** | Kafka'nın bölümleme ve çoğaltma avantajları V1 ölçeğinde gerekli değil |
| **Outbox entegrasyonu** | Redis Streams + PostgreSQL outbox V1 için yeterli teslimat garantisi sağlar |

---

## Alternatifler

| Seçenek | Değerlendirme |
|---------|---------------|
| **Apache Kafka** | Operasyonel yük nedeniyle red. Gelecekte (Platform Ufku) yeniden değerlendirilebilir. |
| **RabbitMQ** | Redis Streams kadar native tüketici grubu yok |
| **Redis Streams** | ✅ Seçilen çözüm |

---

## Sonuçlar

- ADR-002 ile birlikte Redis Streams + outbox mimari kararı kesinleşmiştir
- Kafka'ya geçiş kriteri: 10.000+ iş/gün veya bölümleme ihtiyacı
- 0307 Background Jobs kuyruk tasarımını detaylandırır

---

## Değişiklik Geçmişi

| Tarih | Değişiklik |
|-------|------------|
| 22.07.2026 | İlk karar: Kabul |
