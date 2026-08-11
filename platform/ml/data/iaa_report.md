# IAA Raporu — A1-3

| Alan | Değer |
|------|-------|
| Doküman ID | IAA-001 (A1-3 çıktısı) |
| Proje | GeoLens Platform |
| Tarih | 11 Ağustos 2026 |
| Durum | Draft (sentetik demo) |
| İlişkili | 0421 A1-3, 0420 İP-02, 0606 |

---

## 1. Amaç

Altın veri kümesindeki etiketlerin etiketleyiciler arası uyumunu ölçmek. 0420 İP-02 hedefi: **inter-annotator agreement > %90**.

Ölçüm, `ml/data/iaa.py` script'i ile yapılır. `ml/data/generate_iaa_annotators.py` sentetik iki etiketleyici dosyası üretir (gerçek insan etiketlemesi M1 öncesi insan gücü ile yapılacaktır). Bu demo, ölçüm pipeline'ının kurulu ve eşik kontrolünün çalıştığını doğrular.

## 2. Kurulum

```bash
python data/generate_iaa_annotators.py          # 2×2 etiketleyici dosyası
python data/iaa.py --label value data/annotators/annotator1_sentiment.jsonl data/annotators/annotator2_sentiment.jsonl
```

## 3. Sonuç (demo, 1000 kayıt)

| Etiket | Tam anlaşma | Cohen's Kappa | Eşik (≥%90) |
|:-------|:-----------:|:-------------:|:-----------:|
| sentiment | %97.0 (970/1000) | 0.951 | ✅ |
| hallucination.type | %100.0 (1000/1000) | 1.000 | ✅ |

Bir sonraki adım (manuel etiketleme) gerçek veriyle gösterilecektir; bu rapor pipeline'ın doğrulanmasıdır.

## 4. Yorum

- Kappa ≥ 0.8 güçlü anlaşma kabul edilir; 0.951/1.000 hedefin üzerindedir.
- Sentiment sınıfları sınır vakalarında (nötr ↔ olumlu) tutarsızlık simüle edildi; manuel etiketlemede bu sınırlar Annotation Guide'da netleştirilmelidir.