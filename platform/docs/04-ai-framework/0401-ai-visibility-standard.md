# 0401 · AI Visibility Standard (GAVF)

| Alan | Değer |
|---|---|
| Doküman ID | 0401 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0006, 0301, 0302, 0402-0415, 0204 (GAVF S1-S4) |

---

## 1. Amaç

Bu doküman GeoLens AI Visibility Framework (GAVF) standardını tanımlar. GAVF, AI motorlarında marka görünürlüğünün ölçümü, skorlanması ve raporlanması için açık, tekrarlanabilir ve doğrulanabilir bir metodolojidir. Standardın iki amacı vardır:

1. **GeoLens Platform içinde** tüm skorlama ve raporlamanın tutarlı bir çerçeveye oturması
2. **GeoLens Specification reposunda** sektör standardı olarak yayınlanması (İ6)

> **Tasarım filtresi bağlantısı:** Bu doküman **F6** (kategori — GAVF standardı GeoLens'i bir üründen ekosisteme dönüştürür) ve **F5** (moat — açık standart rakip taklidini zorlaştırır) filtrelerine kanıt sağlar.

---

## 2. Standardın Yapısı

GAVF dört katmandan oluşur:

| Katman | Adı | Sorumluluk | Doküman(lar) |
|:------:|-----|-----------|:------------:|
| **S1** | Ölçüm Standardı | Prompt tasarımı, motor çağrısı, örnekleme | 0402, 0403, 0404 |
| **S2** | Yanıt Standardı | Alıntı çıkarma, varlık tanıma, sınıflandırma | 0405, 0406, 0407, 0408 |
| **S3** | Skor Standardı | Görünürlük, otorite, pay, bileşik skor | 0409, 0410, 0411 |
| **S4** | Aksiyon Standardı | Fırsat, öneri, trend, gözlem | 0412, 0413, 0414, 0415 |

---

## 3. Çekirdek İlkeler

| # | İlke | Açıklama |
|:-:|------|----------|
| G1 | **Açıklanabilirlik** | Her skor, hangi girdilerden ve hangi algoritmayla üretildiği geriye doğru izlenebilir olmalıdır. |
| G2 | **Determinizm** | Aynı girdilerle hesap tekrarlandığında birebir aynı sonuç üretilmelidir. |
| G3 | **Fidelite Dürüstlüğü** | Her skor, ölçümün hangi kademeden yapıldığını gösteren etiketi taşımalıdır. |
| G4 | **İstatistiksel Dürüstlük** | Her skor güven aralığıyla birlikte raporlanmalı; kesinlik iddiası taşımamalıdır. |
| G5 | **Versiyonlanmış Metodoloji** | Ölçüm ve hesap yöntemindeki her değişiklik versiyonlanmalı ve eski skorlar korunmalıdır. |
| G6 | **Dürüst İddia** | Hiçbir yüzey sıralama garantisi ima etmemeli; olasılıksal dil kullanılmalıdır. |

---

## 4. Uyumluluk Seviyeleri

| Seviye | Anlamı | Gereklilikler |
|:------:|--------|:------------:|
| **Temel** | GAVF uyumlu skor üretimi | G1, G2, G3, G4 |
| **İleri** | GAVF uyumlu skor + raporlama | Temel + G5, tüm S3 bileşenleri |
| **Tam** | GAVF uyumlu tüm katmanlar | İleri + S4 aksiyon bileşenleri |
| **Sertifikalı** | Bağımsız doğrulama | Tam + üçüncü taraf denetimi |

---

## 5. Methodology Versiyonlama

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: 4 katmanlı GAVF yapısı, 6 çekirdek ilke, 4 uyumluluk seviyesi |

## 6. GeoLens Platform-GAVF Bağı

| GAVF Bileşeni | Platform Karşılığı | FR/NFR |
|:-------------:|-------------------|:-------:|
| Açıklanabilirlik (G1) | calculation_run, panel versiyonu | İ3, NFR-7 |
| Determinizm (G2) | Hesap koşusu, yeniden üretim | NFR-7 |
| Fidelite (G3) | Fidelite etiketi (Kademe 1/2/3) | FR-C5, İ2 |
| İstatistiksel Dürüstlük (G4) | Güven aralığı, örnekleme | FR-C6 |
| Versiyonlama (G5) | Panel versiyonu, algoritma versiyonu | I4, §5 |
| Dürüst İddia (G6) | Olasılıksal dil, garanti yok | İ4, FR-E1 |

---

## Kaynaklar

- 0006 Glossary — GAVF, fidelite, calculation_run terimleri
- 0301 Core Concepts — görünürlük, panel, kademe kavramları
- 0204 PRD — İ1-İ6 ürün ilkeleri
- 0402-0415 — alt dokümanlar (framework bileşenleri)

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: GAVF standardı, 4 katman, 6 ilke, 4 uyumluluk seviyesi. |
