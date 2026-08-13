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
  "topic": "brand",
  "persona": "end_user",
  "funnel": "consideration",
  "sector": "telekom"
}
```

- `lang`: `tr|en` (+ün gelecekte `de|fr|es` — dil embedding'i ile genişletilebilir)
- `intent`: `information|recommendation|comparison|complaint|problem|purchase|opinion|news` (0421-8INTENT ile 5→8 genişletildi)
- `topic`: `product|service|brand|sector|technology`
- `persona`: `end_user|technical_expert|executive|journalist|investor`
- `funnel`: `awareness|consideration|decision|purchase|loyalty`
- Çapraz dağılım: 1000 örnekli sentetik set eski taksonomiyle üretildi (5×5×5×4×2,
  0420 İP-01, 500 TR / 500 EN); kanonik kaynak `odev01/prompts_v1.jsonl` ise
  8-intent / 5-persona / 5-funnel ile **9.000 örnek** içerir (0421-8INTENT Faz A).

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

## Gerçek Veri Etiketleme Akışı (0421 A1-2 kapanışı)

`gold.jsonl` sentetik üreticinin çıktısıdır — şema + ölçüm pipeline'ı için.
Üretime alınacak gerçek gold dataset şu akışla oluşur:

```bash
# 1) Gerçek motor cevaplarını etiketlenmemiş şablonlara export et
#    (measure.raw_responses → GoldRecord şeması; mention/citation/entity
#    boş, etiketleyici doldurur). --dsn verilmezse örnek veriyle çalışır.
python data/export_unlabeled.py --dsn "$DATABASE_URL" \
    --tenant T01 --workspace WS01 --brand B01 --limit 200 \
    --out real/real_20260812.jsonl

# 2) İki etiketleyici şablonu bağımsız doldurur (Annotation Guide 0606 §5.1 kurallarıyla)
cp real/real_20260812.jsonl real/annotator1.jsonl
cp real/real_20260812.jsonl real/annotator2.jsonl
#    ... editör ile mentions/citations/entities/hallucination doldurulur ...

# 3) Şema doğrulama (hatalı satırları yakalar)
python data/validate_labeled.py real/annotator1.jsonl real/annotator2.jsonl

# 4) IAA ölçümü — hedef >%90 (0420 İP-02)
python data/iaa.py --label sentiment real/annotator1.jsonl real/annotator2.jsonl

# 5) Uzlaşılan kayıtlar gold.jsonl'e birleştirilir → train/test split (split_data.py)
```

Notlar:
- `export_unlabeled.py` DB'ye `psycopg` ile bağlanır (dev bağımlılığı, 0421 A1-2).
- Etiketleme kuralları `docs/06-data/0606-data-quality.md §5.1` Annotation Guide'dır;
  IAA eşiği aşılmadan kayıt gold setine alınmaz.
- Çıktı dizini `data/real/` `.gitignore`'da değildir — etiketli kayıtlar CI'da
  `ml-eval` job'ıyla ölçülebilir.

## İlgili Kurallar

- Train/test split **%80/%20** (0420 İP-02), dil dengesi (%50 TR / %50 EN).
- Etiketleyiciler arası uyum (IAA) hedef: **>%90** (0420 İP-02).
- `data/` dizini `.gitignore` içinde değildir — dataset CI'da `ml-eval` job'ı
  tarafından kullanılır (0421 A0-5).