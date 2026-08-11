## A4-1 PoC Raporu (İP-08)

| PoC | Durum | Süre (ms) |
|:--|:--:|:--:|
| poc_citation | PASS | 475.14 |
| poc_entity | PASS | 4.22 |
| poc_recommendation | PASS | 0.78 |
| poc_prompt | PASS | 0.3 |
| poc_visibility | PASS | 23.98 |

Toplam: 5/5 PoC geçti (hedef geçildi)

### Detaylı metrikler

```
[PASS] citation-type: n=8 acc=1.000 prec=1.000 recall=1.000 F1=1.000 (0.04 ms)
örnek extraction: {'url': 'https://example.com/acme', 'type': 'direct'}
URL doğrulama (çevrimdışı): {'url': 'https://example.com/acme', 'type': 'direct', 'reachable': 'False'}


[PASS] entity-type: n=8 acc=1.000 prec=1.000 recall=1.000 F1=1.000 (0.42 ms)
örnek summarize: {'brand': ['Turkcell', 'Vodafone']}


[PASS] recommendation: n=8 acc=1.000 prec=1.000 recall=1.000 F1=1.000 (0.05 ms)
güç(Kesinlikle MobiTel'in premium planını ta…): (True, 9.0)
güç(Bu konuda VekoCom alternatif olarak değe…): (True, 6.0)
güç(Turkcell en iyi seçim olarak öne çıkıyor…): (True, 9.0)


[PASS] prompt-intent: n=5 acc=0.800 prec=0.875 recall=0.875 F1=0.875 (0.01 ms)
mode: fallback


[PASS] vi-score-consistency: n=3 acc=1.000 prec=1.000 recall=1.000 F1=1.000 (0.02 ms)
AHP CR=0.003, toplam ağırlık=1.000
ilk gold VI: 85.08
duyarlılık (ilk gold): {'presence': '+1.06', 'position': '+0.93', 'citation': '+0.87', 'competitor': '-4.98', 'appearance': '+0.82', 'sentiment': '+0.78', 'compvis': '+0.78'}

```
