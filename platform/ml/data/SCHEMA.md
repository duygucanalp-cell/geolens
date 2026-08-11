# Veri Dizini Şeması (0421 A0-4)

Aşama 1'de (`A1-1` 1000 prompt taksonomisi, `A1-2` gold dataset v1) doldurulacak
veri dosyalarının şema tanımı. Format: **JSON Lines** (her satır tek JSON objesi).

## Dosyalar (hedef)

| Dosya | İçerik | Şema |
|-------|--------|------|
| `prompts.jsonl` | 1000 etiketli prompt (A1-1) | Aşağıda `Prompt` |
| `gold.jsonl` | 500+ etiketli referans cevap (A1-2) | Aşağıda `GoldRecord` |
| `train/`, `test/` | %80/%20 split (A1-4) | GoldRecord alt kümesi |

## Şema — Prompt (A1-1)

```json
{
  "id": "prompt_0001",
  "lang": "tr",
  "text": "Acme'nin en iyi rakibi kim?",
  "intent": "comparison",
  "topic": "competitor",
  "persona": "consumer",
  "funnel": "evaluation",
  "sector": "telekom"
}
```

- `lang`: `tr|en` (+ün gelecekte `de|fr|es` — dil embedding'i ile genişletilebilir)
- `intent`: `presence|comparison|recommendation|category|problem`
- `topic`: `product|service|brand|sector|technology`
- `persona`: `consumer|expert|journalist|investor`
- `funnel`: `awareness|decision`
- Sektör × intent × topic × persona × funnel çapraz dağılımı: 5×5×5×4×2 = **1000 örnek** (0420 İP-01), dil dağılımı 500 TR / 500 EN.

## Şema — GoldRecord (A1-2)

```json
{
  "id": "gold_0001",
  "lang": "tr",
  "prompt_id": "prompt_0042",
  "prompt": "Acme hakkında ne biliyor musun?",
  "engine": "perplexity",
  "response_text": "Acme 2005'te kurulmuş bir teknoloji şirketidir...",
  "expected_summary": "Kuruluş bilgisi, sektör, öne çıkan ürün",
  "mentions": [
    {"text": "Acme", "type": "brand", "sentiment": "positive"}
  ],
  "citations": [
    {"url": "https://acme.com/hakkimizda", "type": "direct"}
  ],
  "entities": [
    {"value": "Acme", "type": "brand"},
    {"value": "2005", "type": "date"}
  ],
  "recommendation": false,
  "hallucination": {"type": "none", "severity": null}
}
```

- `mentions[].type`: `brand|product|competitor`
- `citations[].type`: `direct|attribution|directional`
- `entities[].type`: `brand|product|competitor|technology|organization|location|person|event`
- `hallucination.type`: `none|T1|T2|T3|T4|T5|contradiction|fabrication|outdated`

## İlgili Kurallar

- Train/test split **%80/%20** (0420 İP-02), dil dengesi (%50 TR / %50 EN).
- Etiketleyiciler arası uyum (IAA) hedef: **>%90** (0420 İP-02).
- `data/` dizini `.gitignore` içinde değildir — dataset CI'da `ml-eval` job'ı
  tarafından kullanılır (0421 A0-5).