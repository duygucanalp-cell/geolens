# 0004 · Başarı Metrikleri

| Alan | Değer |
|---|---|
| Doküman ID | 0004 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Approved |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0003, 0205, 0307, 0309 |

---

## 1. Amaç

0003'teki G1-G9 hedeflerini ölçülebilir kılan metrik setini tanımlar. [K] işaretli eşikler başlangıç kalibrasyonudur; pilot verisiyle revize edilir.

---

## 2. North Star Metriği

> **WAT% (Haftalık Aktif Kiracı Oranı):** Son 7 günde en az bir görünürlük raporunu inceleyen ve en az bir skor/kaynak detayına inen kiracıların toplam aktif sözleşmeli kiracıya oranı.

**Pilot eşiği:** ≥ %60 [K]

---

## 3. Metrik Kataloğu

### Benimseme (G4, G8)

| ID | Metrik | Eşik | Bağ |
|----|--------|:----:|:---:|
| **M1** | WAT% (North Star) | ≥ %60 [K] | G8 |
| **M2** | Pilot tamamlama oranı | ≥ %80 [K] | G8 |
| **M3** | Haftalık rapor görüntüleme (medyan) | ≥ 3 [K] | G8 |
| **M4** | Öneri işaretleme oranı | ≥ %40 [K] | G4 |

### Ölçüm Güvenilirliği (G1, G2)

| ID | Metrik | Eşik | Bağ |
|----|--------|:----:|:---:|
| **M5** | Güven aralığı genişliği | ≤ ±5 @ %95 GA [K] | G1 |
| **M6** | Hesap tekrarlanabilirliği | %100 (sert) | G2 |
| **M7** | İzlenebilirlik kapsaması | %100 (sert) | G2 |
| **M8** | Motor ölçüm başarısı | ≥ %97 [K] | G1 |

### Atıf ve Kaynak (G3)

| ID | Metrik | Eşik | Bağ |
|----|--------|:----:|:---:|
| **M9** | Alıntı çözümleme oranı | ≥ %90 [K] | G3 |
| **M15** | Sentiment tespit doğruluğu | ≥ %80 [K] | G10 |
| **M16** | Hallüsinasyon tespit oranı | ≥ %85 [K] | G11 |
| **M17** | Response Archive kapsama oranı | %100 (sert) | G12 |
| **M18** | Conversation replay erişilebilirlik oranı | ≥ %98 [K] | G12 |
| **M19** | LLM bot tespit başarısı | ≥ %90 [K] | G13 |
| **M20** | Content gap tesbit doğruluğu | ≥ %75 [K] | G14 |

### İzleme Sürekliliği (G5, G6)

| ID | Metrik | Eşik | Bağ |
|----|--------|:----:|:---:|
| **M10** | Zamanlanmış ölçüm zamanındalığı | ≥ %99 [K] | G6 |
| **M11** | Uyarı isabeti (yanlış alarm oranı) | ≤ %20 [K] | G6 |

### Kurumsal (G7)

| ID | Metrik | Eşik | Bağ |
|----|--------|:----:|:---:|
| **M12** | Tenant izolasyon ihlali | 0 (sert) | G7 |
| **M13** | Aylık uptime | ≥ %99.5 [K] | G7 |
| **M14** | Denetim izi kapsaması | %100 (sert) | G7 |

---

## 4. Koruma Metrikleri

| ID | Koruma | Aksiyon |
|----|--------|---------|
| **K1** | Birim ölçüm maliyeti artış eğilimi | Örnekleme ve motor kapsamı gözden geçirilir |
| **K2** | Motor politika uyumu — yetkisiz erişim sıfır | İlgili motor entegrasyonu durdurulur |
| **K3** | Veri tazeliği — skor yaşı pencereyi aşmaz | Bayatlık uyarısı, öncelikli yeniden ölçüm |

---

## 5. Metrik-Hedef Eşlemesi

| Hedef | Birincil Metrik(ler) | Not |
|:-----:|:--------------------|-----|
| G1 | M5, M6, M7, M8 | Ölçüm güvenilirliği |
| G2 | M6, M7 | Sert kurallar |
| G3 | M9 | Alıntı çözümleme |
| G4 | M4 | Öneri etkileşimi |
| G5 | — | Doğrudan metrik atanmamıştır. Rakip kıyası kullanımı M1 (WAT%) üzerinden ölçülür. |
| G6 | M10, M11 | İzleme sürekliliği |
| G7 | M12, M13, M14 | Kurumsal operasyon |
| G8 | M1, M2, M3 | Benimseme |
| G9 | — | Platform ufku. Metrik 0206 ile birlikte tanımlanır. |
| G10 | M15 | Sentiment tespiti |
| G11 | M16 | Hallüsinasyon tespiti |
| G12 | M17, M18 | Conversation replay + arşiv |
| G13 | M19 | LLM bot izleme |
| G14 | M20 | Content gap analizi |

---

## 6. GeoLens İçin Çıkarımlar

1. **M6, M7, M12, M14** sert kurallardır — eşik pazarlığına kapalı. Mimari yükümlülüktür.
2. **[K] işaretli eşikler** pilot verisiyle kalibre edilir. Pilot öncesi taahhüt değil tasarım hedefidir.
3. **Specification bağlantısı:** M5 (güven aralığı) ve M6 (tekrarlanabilirlik), GAVF standardının temel gereksinimleridir.

---

## 7. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | Aday metrikler (TTFV, aktivasyon) ne zaman resmileşecek? | ⏳ AVIP §4 aday metrikler listesi devralındı. Pilot verisiyle resmileşecek. |

### Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|----|-------|--------|
| **D-79** | **Pilot kiracı profili:** 6-8 kiracı (3 P3 + 2-3 P2 + 1-2 P4). PO 21.07.2026. | AVIP 0004 O-1 |
| **D-88** | **Örnekleme n=3, sıklık haftalık/günlük** (Free/Pro haftalık, Business/Enterprise günlük). TL 21.07.2026. | AVIP 0004 O-2 |
| **D-68** | **Uptime SLO:** İlk kurumsal müşteriyle sözleşmesel SLO. MVP'de %99.5 [K] tasarım hedefi. PO 21.07.2026. | AVIP 0004 O-3 |

---

## Kaynaklar

- 0003 Goals — G1-G9 hedef bağları
- 0205 MVP — pilot tanımı
- 0309 Measurement Engine — örnekleme tasarımı
- archive/avip-v1/0004-success-metrics.md

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: GeoLens başarı metrikleri. North Star (WAT%), M1-M14 kataloğu, K1-K3 korumaları. |
| 1.1 | 22.07.2026 | AVIP kapalı kararları taşındı: D-79 (pilot profili), D-88 (örnekleme n=3), D-68 (uptime SLO). Devralınan Kararlar eklendi. |
| 1.2 | 27.07.2026 | Turkcell RFP kapsamında yeni metrikler eklendi: M15 (sentiment), M16 (hallüsinasyon), M17 (response archive), M18 (conversation replay), M19 (LLM bot), M20 (content gap). Hedef-metrik eşlemesi güncellendi. |
