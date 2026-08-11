## A3-3 · Gold Doğrulama Raporu (İP-06 §Doğrulama)

- Numune: **1000** gold kayıt (sentetik, A1-2)
- Yeni VI (7 bileşen) dağılımı: min=37.5, max=85.08, mean=73.31, median=77.65
- Korelasyon (yeni vs legacy 4 bileşen): Pearson=0.942, Spearman=0.684
- MAE (legacy vs new): 4.763 · RMSE: 5.206 · R²: 0.809

| Metrik | Legacy (4 bileşen) | Yeni VI (7 bileşen) |
|:--|:--|:--|
| MAE | 4.763 | — (kendine karşı 0) |
| RMSE | 5.206 | — |
| R² | 0.809 | — |

Not: Rapor sentetik gold üzerinde metodolojik gösterimdir; gerçek uzman
etiketli veri (A0-3) geldiğinde aynı pipeline manuel skorlarla doğrulanır.
