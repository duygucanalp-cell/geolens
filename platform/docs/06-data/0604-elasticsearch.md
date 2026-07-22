# 0604 · Elasticsearch (Rezerve)

| Alan | Değer |
|---|---|
| Doküman ID | 0604 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0601, 0602, 0603, 0206 |

---

## 1. Amaç

Bu doküman, GeoLens Platform'da ileride kullanılmak üzere rezerve edilmiş Elasticsearch entegrasyon noktalarını tanımlar. **MVP'de Elasticsearch kullanılmamaktadır.** Tüm metin arama ve analiz işlemleri PostgreSQL üzerinden yapılır.

---

## 2. Elasticsearch İhtiyacı

| İhtiyaç | MVP Çözümü | ES Geçiş Kriteri |
|---------|:----------:|:-----------------:|
| Prompt içeriği tam metin arama | PostgreSQL LIKE/trgm | 10K+ prompt satırı |
| Alıntı URL/index sorguları | PostgreSQL indeks | Sorgu süresi >2s |
| Denetim izi arama | PostgreSQL BRIN | 1M+ satır |
| Ham yanıt içerik arama | — | Ufuk (üçüncül öncelik) |

---

## 3. Rezerve İndeks Tasarımı (Gelecek)

```json
{
  "index": "citations",
  "mappings": {
    "properties": {
      "url": { "type": "keyword" },
      "title": { "type": "text", "analyzer": "turkish" },
      "domain": { "type": "keyword" },
      "brand_id": { "type": "keyword" },
      "tenant_id": { "type": "keyword" },
      "measured_at": { "type": "date" }
    }
  }
}
```

---

## 4. Alternatif: PostgreSQL Full-Text Search

MVP'de PostgreSQL full-text search (tsvector) yeterlidir:

```sql
ALTER TABLE prompts ADD COLUMN search_vector tsvector;
CREATE INDEX prompts_search_idx ON prompts USING GIN(search_vector);

-- Sorgu
SELECT * FROM prompts 
WHERE search_vector @@ plainto_tsquery('turkish', 'besikta');
```

---

## Kaynaklar

- 0601 Data Model — veri kategorileri
- 0602 PostgreSQL Schema — mevcut şema
- 0206 Roadmap — HT2, Platform Ufku
- 0304 Technology Selection — arama motoru kararları

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: Elasticsearch rezervasyonu, MVP'de kullanılmaz. PostgreSQL FTS alternatifi. |
