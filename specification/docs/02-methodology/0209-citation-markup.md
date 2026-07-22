# 0209 · Alıntı İşaretleme Şeması

| Alan | Değer |
|---|---|
| Doküman ID | 0209 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0107, 0103 (S2) |

---

## 1. Amaç

Alıntıların makine tarafından okunabilir işaretleme şemasını tanımlar.

## 2. JSON Şeması

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "GAVF Citation",
  "type": "object",
  "required": ["url", "title", "position", "engine", "domain", "type"],
  "properties": {
    "url": { "type": "string", "format": "uri" },
    "title": { "type": "string" },
    "position": { "type": "integer", "minimum": 1 },
    "engine": { "type": "string" },
    "domain": { "type": "string" },
    "type": { "enum": ["direct", "attribution", "directional"] }
  }
}
```

## 3. XML Şeması (Alternatif)

```xml
<citation>
  <url>https://example.com</url>
  <title>Örnek Sayfa</title>
  <position>1</position>
  <engine>chatgpt</engine>
  <domain>example.com</domain>
  <type>direct</type>
</citation>
```

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 22.07.2026 | İlk yayın: JSON ve XML şemaları. |
