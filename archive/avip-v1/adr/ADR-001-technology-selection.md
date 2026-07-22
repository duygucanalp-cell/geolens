# ADR-001 · Teknoloji Seçimi

| Alan | Değer |
|---|---|
| ADR ID | ADR-001 |
| Durum | Kabul |
| Tarih | 12.07.2026 |
| Karar veren | PO + TL |
| İlişkili | 0304 Technology Selection |

## Bağlam

AVIP projesi için teknoloji yığını seçilmelidir. Seçim kriterleri: hız, ekosistem olgunluğu, veritabanı uyumu, deployment kolaylığı ve 4 kişilik ekip (W4) kapasitesi.

## Karar

- **Backend:** Go (1.22+)
- **Veritabanı:** PostgreSQL 16+ (RLS ile çoklu kiracı)
- **Cache / Kuyruk:** Redis 7+ (Streams + outbox)
- **Depolama:** S3-uyumlu nesne depolama
- **Frontend:** React + TypeScript SPA (ADR-002 ile)
- **ORM/Query:** sqlc (SQL-first, tip güvenliği)
- **Container:** Docker, Docker Compose

## Alternatifler

| Seçenek | Red nedeni |
|---|---|
| Node.js backend | Ekip Go deneyimli; PostgreSQL entegrasyonunda Go daha güçlü |
| Python + FastAPI | Eşzamanlılık modeli Go kadar verimli değil; deployment karmaşıklığı |
| MongoDB | RLS tabanlı izolasyon PostgreSQL kadar doğal değil; JOIN ihtiyacı |
| Serverless (Lambda) | Soğuk başlama, durum yönetimi zorluğu, 0303 outbox deseniyle uyuşmazlık |

## Sonuçlar

- ADR-001 Kabul statüsüne geçer; teknoloji seçimleri 0304'te kıyaslanarak document edilmiştir.
- Yeni karar bu ADR'nin yerini alır ve çapraz referanslanır.

## Değişiklik Geçmişi

| Tarih | Değişiklik |
|---|---|
| 12.07.2026 | İlk karar: Kabul |
