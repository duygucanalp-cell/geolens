# 0606 · Veri Kalitesi (Data Quality)

| Alan | Değer |
|---|---|
| Doküman ID | 0606 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Approved |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0601, 0602, 0309, 0311, 0204, 0004 |

---

## 1. Amaç

Bu doküman GeoLens Platform veri kalitesi standartlarını tanımlar: doğruluk, bütünlük, tutarlılık, güncellik ve denetlenebilirlik boyutlarında veri kalitesi ölçütleri.

---

## 2. Veri Kalitesi Boyutları

| Boyut | Açıklama | Metrik |
|:-----:|----------|:------:|
| **Doğruluk** | Verinin gerçek dünyayı yansıtma derecesi | Skor determinizm testi |
| **Bütünlük** | Verinin eksiksiz olması | Zorunlu alan doluluk oranı |
| **Tutarlılık** | Veriler arası ilişkilerin geçerliliği | FK bütünlüğü, CHECK kısıtları |
| **Güncellik** | Verinin ne kadar güncel olduğu | Tazelik damgası (K3) |
| **Denetlenebilirlik** | Verinin kaynağına kadar izlenebilmesi | Korelasyon zinciri |

---

## 3. Veritabanı Düzeyinde Kalite Korumaları

| Mekanizma | Uygulama | İhlal Tepkisi |
|-----------|----------|:-------------:|
| CHECK kısıtları | Skor 0-100, GA sırası, status değerleri | INSERT/UPDATE reddi |
| NOT NULL | Fidelity label, CI alanları | INSERT reddi |
| UNIQUE | Idempotency key, tenant-period-counter | INSERT reddi |
| FK RESTRICT | Referans bütünlüğü | Silme reddi |
| Yalnız-ekleme trigger'ları | calculation_runs, audit_log | UPDATE/DELETE reddi |

---

## 4. Süreç Düzeyinde Kalite Korumaları

| Süreç | Kontrol | Sıklık |
|-------|---------|:------:|
| Ölçüm | Determinizm testi (aynı girdi → aynı skor) | Her calculation_run |
| Motor çağrısı | content_hash doğrulama | Her ham yanıt |
| Rapor üretimi | Yalnız etiketli skor kullanımı | Her rapor |
| Veritabanı | Bütünlük kontrolü (denetim zinciri) | Günlük |

---

## 5. Veri Kalitesi Metrikleri (0004 ile bağlantılı)

| Metrik | Hedef | Kaynak |
|:------:|:-----:|--------|
| Skor determinizm oranı | >%99.9 | 0309 — M6 |
| Ham yanıt bütünlüğü | %100 | 0303 — I5 |
| Audit zincir bütünlüğü | %100 | 0310 — I6 |
| Veri güncellik (K3) | <7 gün | 0004 — K3 |
| Zorunlu alan doluluk | %100 | CHECK + NOT NULL |

---

## 5.1 Annotation Guide (ML etiketleme kılavuzu) — 0421 A1-2

> Kaynak: 0420 İP-02, 0421 A1-2. Bu bölüm `ml/data/gold.jsonl` sentetik üretim
> ve manuel etiketleme kurallarını tanımlar. Şema: `ml/data/SCHEMA.md`.

Etiketleme kuralları (0420 İP-02 örnek kuralı temel alınmıştır):

1. **mention**: AI cevabında bir marka/rakip adı geçiyorsa `mention[].text` +
   `type` (brand|competitor) ile listelenir. Bir mention bir kez kaydedilir
   (tekrar eden geçişler birleştirilir).
2. **sentiment**: mention ile aynı cümcede olumlu sıfat ("güçlü", "takdir",
   "recommended") varsa `positive`; olumsuz/şikayet varsa `negative`; tarafsız
   ise `neutral`.
3. **citation**: cevapta kaynak gösterimi varsa `url` + `type`
   (direct|attribution; kaynak yoksa `none`).
4. **entity**: NER regex/model çıktısıyla doğrulanan kavramlar
   (brand, competitor, sector, technology, money, percent, date).
5. **hallucination**: 0421 A2-4 cross-source doğrulamayla tespit edilen tutarsızlık
   tipi (T1–T7; yoksa `none`).
6. **IAA**: yeni etiket eklendikçe iki etiketleyici ayrı dosyaya etiketler,
   `data/iaa.py` ile > %90 uyum zorunludur (0420 İP-02).

---

## 6. Veri Kalitesi Raporlama

| Rapor | Periyot | İçerik |
|:-----:|:-------:|--------|
| Kalite özeti | Haftalık | Determinizm, bütünlük, güncellik metrikleri |
| Anomali raporu | Günlük | Beklenmeyen NULL, FK ihlali, CHECK hatası |
| Trend kalitesi | Aylık | Panel versiyon sınırları, veri boşluğu analizi |

---

## Kaynaklar

- 0601 Data Model — veri kategorileri
- 0309 Scoring Engine — determinizm, GA, fidelite
- 0311 Observability — metrikler ve alarmlar
- 0004 Success Metrics — M6, M7, K3
- 0204 PRD — FR-C4..C7, NFR-6, NFR-7

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: 5 kalite boyutu, DB mekanizmaları, süreç kontrolleri, metrikler. |
| 1.1 | 11.08.2026 | Annotation Guide eklendi (§5.1, 0421 A1-2 çıktısı): mention/sentiment/citation/entity/hallucination etiketleme kuralları + IAA >%90 şartı. |
