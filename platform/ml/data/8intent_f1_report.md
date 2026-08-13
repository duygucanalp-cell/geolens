# 0421-8INTENT · 8-Intent Model Doğrulama Raporu (Faz D)

| Alan | Değer |
|------|-------|
| Doküman ID | VERİ-8INTENT-F1 |
| Proje | GeoLens |
| Durum | Draft |
| Tarih | 2026-08-13 |
| Test seti | `data/odev01/split/test_prompts_v1.jsonl` (1800 kayıt) |
| Model sürümü | intent/persona/funnel 2.0.0 (topic 1.1.0) |

## Per-sınıf F1

### intent

```
                precision    recall  f1-score   support

    comparison      1.000     1.000     1.000       240
     complaint      1.000     1.000     1.000       120
   information      1.000     1.000     1.000       360
          news      1.000     1.000     1.000       120
       opinion      1.000     1.000     1.000       480
       problem      1.000     1.000     1.000       120
      purchase      1.000     1.000     1.000       120
recommendation      1.000     1.000     1.000       240

      accuracy                          1.000      1800
     macro avg      1.000     1.000     1.000      1800
  weighted avg      1.000     1.000     1.000      1800

```

### persona

```
                  precision    recall  f1-score   support

        end_user      1.000     1.000     1.000      1342
       executive      1.000     1.000     1.000       125
        investor      1.000     1.000     1.000       106
      journalist      1.000     1.000     1.000       120
technical_expert      1.000     1.000     1.000       107

        accuracy                          1.000      1800
       macro avg      1.000     1.000     1.000      1800
    weighted avg      1.000     1.000     1.000      1800

```

### funnel

```
               precision    recall  f1-score   support

    awareness      1.000     1.000     1.000       346
consideration      1.000     1.000     1.000       861
     decision      1.000     1.000     1.000       233
      loyalty      1.000     1.000     1.000       240
     purchase      1.000     1.000     1.000       120

     accuracy                          1.000      1800
    macro avg      1.000     1.000     1.000      1800
 weighted avg      1.000     1.000     1.000      1800

```

## Intent karışım analizi (yanlış eşleşmeler)

| Gerçek → Tahmin | Adet |
|-----------------|------|
| (karışım yok) | — |

## Serving taksonomi uyumu

- Model sınıfları: `comparison, complaint, information, news, opinion, problem, purchase, recommendation`
- 8-intent hedefi: `information, recommendation, comparison, complaint, problem, purchase, opinion, news`
- Uyum: ✅ tam

