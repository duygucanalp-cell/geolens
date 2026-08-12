# Ödev-01 Veri Seti — GeoLens prompt sınıflandırıcısı entegrasyonu

| Alan       | Değer                                                                  |
|------------|------------------------------------------------------------------------|
| Doküman ID | VERİ-ÖDEV01                                                            |
| Proje      | GeoLens                                                                |
| Versiyon   | 1.0                                                                    |
| Durum      | Draft                                                                  |
| Sahip      | Eğitmen                                                                |
| Tarih      | 2026-08-12                                                             |
| İlişkili   | ÖDEV-01 teslimi (docs/GeoLensAI-Proje) · ml/data/SCHEMA.md (0421 A0-4) |

## Amaç

Ödev-01 teslimindeki **9.000 etiketli soru** ve **600 markalık katalog**, platformun
`ml/data` taksonomisine uygun JSON Lines veri seti olarak bu dizine entegre edildi.
Hedef: `prompt_classifier` eğitim hattının (`geolens/prompt_classifier/train.py`)
doğrudan tüketebileceği, şeması belgelenmiş ve doğrulanabilir bir veri artefaktı
sağlamak.

## Dosyalar

| Dosya                                  | İçerik                                     |
|----------------------------------------|--------------------------------------------|
| `prompts_v1.jsonl`                     | 9.000 soru — zengin taksonomi (kayıpsız)   |
| `prompts_v1.mapped.jsonl`              | 9.000 soru — platform 5'li taksonomisine haritalanmış |
| `split/train_prompts_v1.mapped.jsonl`  | Haritalı verinin %80'i (7200)              |
| `split/test_prompts_v1.mapped.jsonl`   | Haritalı verinin %20'si (1800)             |
| `brands_v1.jsonl`                      | 600 markalık katalog                       |

## Şema

İki dosya da `SCHEMA.md`'deki `Prompt` şemasını takip eder
(`id, lang, text, intent, topic, persona, funnel, sector`); `prompts_v1.jsonl`
ek olarak kaynak etiketleri korur (`category, brand, competitor, source`).

```json
{"id": "odev01_00001", "lang": "tr", "text": "Ziraat Bankası tam olarak ne sunuyor, kimler için mantıklı?", "intent": "presence", "topic": "service", "persona": "consumer", "funnel": "awareness", "sector": "bankacılık", "brand": "Ziraat Bankası"}
```

## Taksonomi Haritalama

Ödev-01 etiketleri (8 intent / 5 persona / 5 funnel) ile platform taksonomisi
(5 intent / 4 persona / 2 funnel) birebir örtüşmez. `prompts_v1.mapped.jsonl`
aşağıdaki kayıplı haritalamayla üretildi; kayıpsız sürüm `prompts_v1.jsonl`.

### Intent (8 → 5)

| Ödev-01 intent | Platform intent | Gerekçe                                   |
|----------------|-----------------|-------------------------------------------|
| information    | presence        | marka varlığı/sinyali sorgusu             |
| news           | presence        | güncel varlık sorgusu                     |
| opinion        | category        | kategorideki görünürlük/görüş sorgusu     |
| recommendation | recommendation  | doğrudan eşleşir                          |
| purchase       | recommendation  | satın alma yönlendirmesi ≈ tavsiye        |
| comparison     | comparison      | doğrudan eşleşir                          |
| problem        | problem         | doğrudan eşleşir                          |
| complaint      | problem         | şikayet bir problem ifadesidir            |

### Persona (5 → 4)

`end_user→consumer` · `technical_expert→expert` · `executive→expert` ·
`journalist→journalist` · `investor→investor`

### Funnel (5 → 2)

`awareness→awareness` · `consideration→awareness` · `loyalty→awareness` ·
`decision→decision` · `purchase→decision`

### Topic (türetilmiş)

- `competitor` doluysa → `brand` (rakip marka bağlamı)
- hizmet sektörü → `service`, ürün sektörü → `product` (sınıflandırma
  `SERVICE_SECTORS` kümesiyle yapılır; liste script içinde belgelenmiştir)

> Not: Bu haritalama kayıplıdır; `opinion/news/purchase/complaint` ayrımı
> 5'li taksonomide erir. Zengin taksonomiye geçiş kararında `prompts_v1.jsonl`
> kaynak olarak kullanılmalı, `measure` intent ağırlık haritası
> (`internal/measure/service.go`) buna göre genişletilmelidir.

## Kullanım

```bash
# Tek başına Ödev-01 verisiyle eğitim
python -m geolens.prompt_classifier.train \
  --train ml/data/odev01/split/train_prompts_v1.mapped.jsonl \
  --test  ml/data/odev01/split/test_prompts_v1.mapped.jsonl

# Mevcut sentetik veriyle birleştirik eğitim (cat ile)
cat ml/data/odev01/split/train_prompts_v1.mapped.jsonl ml/data/train/prompts.jsonl > /tmp/train.jsonl
```

## Üretim Metadatası

- Kaynak: `docs/GeoLensAI-Proje/prompts.xlsx` (Ödev-01 teslimi)
- `source`: hepsi `odev01` (kurgu sorular; gerçek kullanıcı verisi değil)
- `lang`: tamamı `tr`
- `sector`: Ödev-01'in 30 sektörü, küçük harfe normalize edilmiş
- Split: `intent × sector` kırılımında stratifiye, rastgele çekirdek 42, %80/%20
- Doğrulama: eğitim hattı bu veriyle test edildi (bkz. Changelog)

## Changelog

| Versiyon | Tarih     | Değişiklik                                       | Kişi  |
|----------|-----------|--------------------------------------------------|-------|
| 1.0      | 2026-08-12| Ödev-01 verisi ml/data taksonomisine entegre edildi | Eğitmen |
| 1.1      | 2026-08-12| Tüm veriyle model yeniden eğitildi (8000 train / 2000 test, `data/full/`); model sürümü 1.1.0 | Eğitmen |