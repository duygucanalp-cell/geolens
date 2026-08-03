# 0101 · GAVF Çekirdek Standardı

| Alan | Değer |
|---|---|
| Doküman ID | 0101 |
| Proje | GeoLens Specification |
| Versiyon | 1.1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 27 Temmuz 2026 |
| İlişkili | 0102–0109, 02-methodology/*, 03-compliance/* |

---

## 1. Amaç

Bu doküman **GeoLens AI Visibility Framework (GAVF)** standardının çekirdek tanımını yapar. GAVF, AI motorlarında marka görünürlüğünün ölçümü, skorlanması ve raporlanması için açık, tekrarlanabilir ve doğrulanabilir bir metodolojidir.

---

## 2. Standardın Yapısı

GAVF dört katmandan oluşur:

| Katman | Adı | Kapsam | Doküman |
|:------:|-----|--------|:-------:|
| **S1** | Ölçüm Standardı | Prompt tasarımı, motor çağrısı, örnekleme | 0102 |
| **S2** | Yanıt Standardı | Alıntı çıkarma, varlık tanıma, sınıflandırma, **sentiment analizi, hallüsinasyon tespiti** | 0103 |
| **S3** | Skor Standardı | Görünürlük, otorite, pay, bileşik skor, **competitive gap** | 0104 |
| **S4** | Aksiyon Standardı | Fırsat, öneri, trend, gözlem, **GEO önerileri** | 0105 |
| **S5** | GEO Standardı | Teknik GEO (bot izleme, schema), Content GEO (topic cluster, FAQ, entity) | 0105 |

---

## 3. Çekirdek İlkeler

| # | İlke | Açıklama |
|:-:|------|----------|
| **G1** | Açıklanabilirlik | Her skor, hangi girdilerden ve hangi algoritmayla üretildiği geriye doğru izlenebilir olmalıdır. |
| **G2** | Determinizm | Aynı girdilerle hesap tekrarlandığında birebir aynı sonuç üretilmelidir. |
| **G3** | Fidelite Dürüstlüğü | Her skor, ölçümün hangi kademeden yapıldığını gösteren etiketi taşımalıdır. |
| **G4** | İstatistiksel Dürüstlük | Her skor güven aralığıyla birlikte raporlanmalı; kesinlik iddiası taşımamalıdır. |
| **G5** | Versiyonlanmış Metodoloji | Ölçüm ve hesap yöntemindeki her değişiklik versiyonlanmalı ve eski skorlar korunmalıdır. |
| **G6** | Dürüst İddia | Hiçbir yüzey sıralama garantisi ima etmemeli; olasılıksal dil kullanılmalıdır. |

---

## 4. Uyumluluk Seviyeleri

| Seviye | Gereklilikler | Sertifika |
|:------:|:-------------:|:---------:|
| **Temel** | G1, G2, G3, G4 | Öz değerlendirme |
| **İleri** | Temel + G5, S3 bileşenlerinin tamamı | Öz değerlendirme |
| **Tam** | İleri + S4 bileşenleri | Bağımsız denetim |
| **Sertifikalı** | Tam + üçüncü taraf denetimi | GeoLens sertifikası |

---

## 5. Versiyon Bilgisi

| Sürüm | Tarih | Açıklama |
|-------|-------|----------|
| 1.0.0 | 22.07.2026 | İlk kararlı sürüm (S1-S4). |
| 1.1.0 | 27.07.2026 | Turkcell RFP kapsamında genişletme: S5 GEO Standardı eklendi. S2'ye sentiment/hallüsinasyon, S3'e competitive gap, S4'e GEO önerileri eklendi. |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.1.0 | 27.07.2026 | Turkcell RFP kapsamında genişletme: S5 GEO Standardı katmanı eklendi. S2 kapsamına sentiment/hallüsinasyon, S3 kapsamına competitive gap, S4 kapsamına GEO önerileri eklendi. Platform 0401-0419 ile senkronize edildi. |
| 1.0.0 | 22.07.2026 | İlk yayın: GAVF çekirdek standardı, 4 katman, 6 ilke, 4 uyumluluk seviyesi. |
