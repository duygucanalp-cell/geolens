# ADR-003 · PostgreSQL Veritabanı Kararları

| Alan | Değer |
|---|---|
| ADR ID | ADR-003 |
| Durum | Kabul |
| Tarih | 22.07.2026 |
| Karar veren | TL |
| İlişkili | 0602, 0507, 0302, 0303 |

---

## Bağlam

Birincil veri deposu seçilmeli ve çok kiracılı izolasyon stratejisi belirlenmelidir. Seçenekler: tek şema + RLS, şema başına kiracı, veritabanı başına kiracı.

---

## Kararlar

| # | Karar | Seçenek |
|:-:|-------|---------|
| 1 | **Birincil veritabanı** | PostgreSQL 16+ |
| 2 | **İzolasyon stratejisi** | Tek şema + RLS (ADR-004) |
| 3 | **Kimlik stratejisi** | ULID (26 karakter, zaman-sıralanabilir) |
| 4 | **Sorgu aracı** | sqlc (SQL-first, tip güvenli) |
| 5 | **Migration aracı** | golang-migrate |

---

## Alternatifler

| Seçenek | Red nedeni |
|---------|------------|
| **Şema başına kiracı** | Migration karmaşıklığı; 100+ kiracıda yönetilemez |
| **Veritabanı başına kiracı** | Operasyonel yük; bağlantı patlaması |
| **SQLite** | Eşzamanlı yazma performansı yetersiz |
| **CockroachDB** | V1 ölçeğinde aşırı; dağıtık ihtiyacı yok |

---

## Sonuçlar

- 0602 PostgreSQL Schema — 25 tablo, 10 tasarım kuralı
- 0507 Multi-Tenancy — RLS politikaları ve 5 katmanlı izolasyon
- Tüm varlıklarda ULID kullanılır
- sqlc ile Go tipleri otomatik üretilir

---

## Değişiklik Geçmişi

| Tarih | Değişiklik |
|-------|------------|
| 22.07.2026 | İlk karar: Kabul |
