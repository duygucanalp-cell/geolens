# 0503 · Veri Formatları

| Alan | Değer |
|---|---|
| Doküman ID | 0503 |
| Proje | GeoLens Specification |
| Versiyon | 1.0.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0502, 0107, 0209 |

---

## 1. Amaç

GAVF uyumlu uygulamaların kullanması gereken veri formatlarını tanımlar.

## 2. Skor Çıktı Formatı

```json
{
  "gavf_version": "1.0.0",
  "score": {
    "composite": 72.5,
    "components": {
      "presence": 85.0,
      "position": 70.0,
      "citations": 65.0,
      "competitive": 60.0
    },
    "confidence_interval": {
      "lower": 68.2,
      "upper": 76.8,
      "level": 0.95
    },
    "fidelity_tier": 2,
    "calculated_at": "2026-07-22T12:00:00Z",
    "calculation_run_id": "01J..."
  }
}
```

## 3. Ölçüm İstek Formatı

```json
{
  "engine": "chatgpt",
  "prompts": [
    {"id": "p1", "text": "XYZ hakkında ne biliyorsun?", "type": "presence:branded:tr"}
  ],
  "sampling": {"n": 3, "temperature": 0}
}
```

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 22.07.2026 | İlk yayın: skor çıktı ve ölçüm istek formatları. |
