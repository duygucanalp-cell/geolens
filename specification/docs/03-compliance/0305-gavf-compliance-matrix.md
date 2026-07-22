# 0305 · GAVF Uyumluluk Matrisi — Platform <> Specification

| Alan | Değer |
|---|---|
| Doküman ID | 0305 |
| Proje | GeoLens Specification + Platform |
| Versiyon | 1.0.1 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 22 Temmuz 2026 |
| İlişkili | 0101, 0109, 0301, platform/docs/0401, platform/docs/0204 |

---

## 1. Amaç

Bu doküman, **GeoLens Platform**'un **GeoLens Specification (GAVF)** ile uyumluluğunu haritalandırır. Her GAVF ilkesi ve uyumluluk seviyesi için platformdaki karşılığını — gereksinim (FR/NFR/İ), doküman ve mimari bileşen düzeyinde — gösterir.

> Bu matris, platformun hangi GAVF seviyesinde olduğunu belirlemek ve sertifikasyon sürecini yönetmek için kullanılır.

---

## 2. GAVF İlkeleri → Platform Gereksinim Eşlemesi

| GAVF İlkesi | Açıklama | Platform FR/NFR/İ | Platform Dokümanı | Specification Karşılığı | Durum |
|:-----------:|----------|:------------------:|:------------------:|:-----------------------:|:-----:|
| **G1** | Açıklanabilirlik — Skor girdileri izlenebilir | İ3, NFR-7 | 0401 §3, 0409 §3 | 0101 §3 (G1) | 🟢 MVP |
| **G2** | Determinizm — Aynı girdi → aynı sonuç | NFR-7 | 0401 §3, 0409 §3 | 0101 §3 (G2) | 🟢 MVP |
| **G3** | Fidelite Dürüstlüğü — Etiket zorunlu | FR-C5, İ2 | 0401 §3, 0108 | 0101 §3 (G3), 0108 | 🟢 MVP |
| **G4** | İstatistiksel Dürüstlük — GA ile raporlama | FR-C6 | 0401 §3, 0409 §5 | 0101 §3 (G4), 0104 §4 | 🟢 MVP |
| **G5** | Versiyonlanmış Metodoloji | İ4, (FR-C4) | 0401 §3, §5 | 0101 §3 (G5), 0000 §5 | 🟢 MVP |
| **G6** | Dürüst İddia — Olasılıksal dil | İ4, FR-E1 | 0401 §3, 0204 §3 | 0101 §3 (G6) | 🟢 MVP |

> **MVP durumu:** Tüm GAVF ilkeleri (G1–G6) GeoLens Platform V1'de karşılanmaktadır. Bu, platformun **Temel** uyumluluk seviyesini ilk günden sağladığı anlamına gelir.

---

## 3. GAVF Katmanları → Platform Doküman Eşlemesi

| Katman | Adı | Specification Doküman(lar)ı | Platform Doküman(lar)ı | Karşılama |
|:------:|-----|:---------------------------:|:-----------------------:|:---------:|
| **S1** | Ölçüm Standardı | 0102, 0106, 0201 | 0402, 0403, 0404, 0505 | 🟢 MVP |
| **S2** | Yanıt Standardı | 0103, 0107, 0209 | 0405, 0406, 0407, 0408 | 🟢 MVP |
| **S3** | Skor Standardı | 0104, 0202, 0203, 0204, 0205 | 0409, 0410, 0411, 0309 | 🟢 MVP |
| **S4** | Aksiyon Standardı | 0105, 0206, 0207, 0208 | 0412, 0413, 0414, 0415 | 🟡 Kısmi (MVP daraltılmış) |

### S4 Detaylı Durum

| S4 Bileşeni | Platform FR | MVP Durumu | Hızlı Takip |
|-------------|:-----------:|:-----------:|:-----------:|
| Fırsat Tespiti | FR-E1 (daraltılmış) | 🟡 Kural tabanlı | HT1 |
| Öneri Üretimi | FR-E1 (daraltılmış) | 🟡 Kural tabanlı | HT1 |
| Trend Analizi | FR-D4 | 🟢 Tam | — |
| Uyarı/Aksiyon | FR-F1, FR-F2 (daraltılmış) | 🟡 Varsayılan eşikler | HT1 |

> **Not:** S4 bileşenlerinde kısmi uyumluluk vardır. "İleri" seviye için S3 tam olarak karşılanır. "Tam" seviye için S4'ün tamamlanması gerekir—bu HT1 penceresinde hedeflenmiştir.

---

## 4. Uyumluluk Seviyeleri → Platform Karşılama Matrisi

### 4.1 Temel (Basic) — ✅ MVP'de Tam

| Gereklilik | İlke | Platform Karşılama | FR/NFR |
|------------|:----:|--------------------|:-------:|
| Skor girdileri kaydedilir ve izlenebilir | G1 | calculation_run kaydı, anlık görüntü | İ3, NFR-7 |
| Aynı girdilerle aynı sonuç üretilir | G2 | Deterministik hesap katmanı, n=3, temp=0 | NFR-7, FR-C3 |
| Her skor fidelite etiketi taşır | G3 | Kademe 1/2/3 etiketi, tüm yüzeylerde zorunlu | FR-C5, İ2 |
| Her skor güven aralığıyla sunulur | G4 | %95 GA, örneklemeli hesaplama | FR-C6 |

### 4.2 İleri (Advanced) — ✅ MVP'de Tam

| Gereklilik | İlke | Platform Karşılama | FR/NFR |
|------------|:----:|--------------------|:-------:|
| Tüm S3 skor bileşenleri hesaplanır | G5 | Varlık + Konum + Kaynak + Rakip (4 bileşen) | FR-C4, 0409 |
| Metodoloji versiyonlanır, eski skorlar korunur | G5 | Panel versiyonu, algoritma versiyonu, tarihçe | İ4, NFR-11 |

### 4.3 Tam (Full) — 🟡 HT1 Hedefi

| Gereklilik | İlke | Platform Karşılama | FR/NFR | Durum |
|------------|:----:|--------------------|:-------:|:-----:|
| Tüm S4 bileşenleri sağlanır | G5 | Fırsat + Öneri + Trend + Uyarı | FR-E1, FR-E4, FR-F1 | 🟡 HT1 |
| Bağımsız denetim | — | Planlanmış | — | 🔴 Plan |

### 4.4 Sertifikalı (Certified) — 🔴 Kurumsal Kapı

| Gereklilik | Platform Karşılama | Durum |
|------------|--------------------|:-----:|
| Üçüncü taraf bağımsız doğrulama | Henüz başlamadı | 🔴 Plan |
| GeoLens sertifikası | Specification lansmanı ile | 🔴 Plan |
| Yıllık yenileme denetimi | — | 🔴 Plan |

---

## 5. Platform FR/NFR → GAVF Uyumluluk Haritası

Aşağıdaki tablo, platformdaki **her Çekirdek FR'yi** hangi GAVF gereksinimini karşıladığını gösterir.

### 5.1 Doğrudan GAVF Bağlantılı FR'ler

| FR ID | Adı | GAVF İlkesi | GAVF Katmanı | Uyumluluk Seviyesi |
|:-----:|-----|:-----------:|:------------:|:------------------:|
| FR-C3 | Örneklemeli çalıştırma | G2, G4 | S1 | Temel |
| FR-C4 | Deterministik skor | G1, G2 | S3 | Temel |
| **FR-C5** | **Fidelite etiketi** | G3 | S1, S3 | **Temel** (kritik) |
| FR-C6 | Güven aralığı | G4 | S3 | Temel |
| FR-C7 | Tazelik damgası | G5 | S3 | İleri |
| FR-D1 | Motor kırılımı | G1 | S3 | Temel |
| FR-D2 | Alıntı/kaynak analizi | G1 | S2 | Temel |
| FR-D4 | Zaman serisi | G4 | S4 | İleri |
| FR-E1 | Öneri üretimi | G6 | S4 | Tam |
| FR-E2 | Politika filtresi | G6 | S4 | Tam |
| FR-E3 | Öneri işaretleme | G5 | S4 | Tam |
| FR-F1 | Anlamlı uyarı | G4 | S4 | Tam |
| FR-B2 | Prompt seti kurulumu | — | S1 | Temel (altyapı) |
| FR-B4 | Site erişim denetimi | — | S2 | Temel (altyapı) |

### 5.2 GAVF Bağlantılı NFR'ler

| NFR ID | Adı | GAVF İlkesi | Uyumluluk Seviyesi |
|:------:|-----|:-----------:|:------------------:|
| NFR-6 | Değişmez denetim izi | G1 | Temel |
| NFR-7 | Deterministik yeniden hesap | G1, G2 | Temel |
| NFR-11 | Tarihçe saklama | G5 | İleri |

### 5.3 GAVF Bağlantılı Ürün İlkeleri

| İ ID | Adı | GAVF İlkesi | Uyumluluk Seviyesi |
|:----:|-----|:-----------:|:------------------:|
| İ2 | Fidelite istisnasızlığı | G3 | Temel |
| İ3 | Açıklanabilirlik | G1, G2 | Temel |
| İ4 | Dürüst iddia | G6 | Temel |
| İ6 | GAVF uyumluluğu | Tümü | Tüm seviyeler |

---

## 6. Mimari Bileşen → GAVF Karşılığı

| Platform Mimarisi | GAVF Karşılığı | Specification Dokümanı |
|-------------------|:--------------:|:----------------------:|
| EngineAdapter (registry) | Kademe etiketleme (direct/official_proxy/directional) | 0108, 0201 |
| ProbeResult (ham yanıt) | Alıntı çıkarma + varlık tanıma | 0107, 0209 |
| CalculationRun | Skor girdi anlık görüntüsü | 0104, 0202 |
| Scoring Engine (GA) | Bileşik skor hesaplama | 0202, 0203 |
| FidelityLabel (Kademe 1/2/3) | Fidelite etiketi | 0108 |
| Recommendations | Kanıt dereceli öneri sınıflandırması | 0207 |
| Trend Analysis | Trend sınıflandırması | 0208 |
| Opportunity Engine | Fırsat puanı hesaplama | 0206 |

---

## 7. ADR Eşlemesi

| Platform ADR | Konu | Specification ADR | Uyum |
|:------------:|------|:-----------------:|:----:|
| 0001 — DDD | Domain model | — | Referans |
| 0002 — Event-Driven | Redis Streams | — | Altyapı |
| 0003 — PostgreSQL | ULID, sqlc | — | Altyapı |
| 0004 — Kafka (red) | Redis Streams seçimi | — | Altyapı |
| 0005 — Plugin | EngineAdapter | 0003 (fidelite) | ✅ |
| — | Standart lisansı | 0001 | ✅ |
| — | Skor bileşenleri | 0002 | ✅ |
| — | Örnekleme parametreleri | 0004 | ✅ |
| — | Versiyonlama şeması | 0005 | ✅ |

---

## 8. Geçiş Yolu: MVP'den Sertifikalı'ya

| Aşama | GAVF Seviyesi | Zamanlama | Kritik Kilometre Taşları |
|:-----:|:-------------:|:---------:|--------------------------|
| **V1.0 MVP** | Temel + İleri | MVP lansmanı | G1–G6 tam, S1–S3 tam, S4 kısmi |
| **HT1** | Tam | MVP+1 çeyrek | S4 tamamı (FR-E4, FR-F2 derinleşmesi) |
| **HT2** | Tam (denetimli) | MVP+2 çeyrek | Bağımsız denetim süreci başlangıcı |
| **Kurumsal Kapı** | Sertifikalı | P1 satışı ile | SOC 2 + GAVF sertifikası |

---

## 9. Özet Tablo

| GAVF Seviyesi | Platform Durumu | Karşılama Oranı | Eksikler |
|:-------------:|:---------------:|:---------------:|----------|
| 🟢 **Temel** | MVP'de tam | %100 | — |
| 🟢 **İleri** | MVP'de tam | %100 | — |
| 🟡 **Tam** | HT1 hedefi | ~%70 | S4 bileşenlerinin derinleşmesi |
| 🔴 **Sertifikalı** | Kurumsal kapı | %0 | Bağımsız denetim, sertifika süreci |

---

## 10. Açık Sorular

| ID | Soru | Durum |
|----|------|-------|
| O-1 | S4 bileşenleri HT1'de Tam'a ulaştığında bağımsız denetim ne zaman başlamalı? | ⏳ HT2 başı. AVIP D-45 (SOC 2 ilk kurumsal müşteriyle) ile uyumlu. |
| O-2 | İleri seviye için bağımsız doğrulama gerekli mi? | ⏳ Pilot sonrası netleşecek. AVIP D-47 (sızma testi Dilim 4) referans. |

### Devralınan AVIP Kararları

| ID | Karar | Kaynak |
|----|-------|--------|
| **D-45** | **SOC 2:** İlk kurumsal müşteriyle başlar. MVP'de güvenlik-ilk yeterli. PO 21.07.2026. | AVIP 0104 O-2 |
| **D-47** | **Sızma testi:** Dilim 4'te yapılır (pilot kapısından bağımsız). PO 21.07.2026. | AVIP 0310 O-4 |
| **D-90** | **Sızma testi kapsamı:** Dış yüzeyler + izolasyon. Tedarik Dilim 3, uygulama Dilim 4. PO+TL 21.07.2026. | AVIP 0405 O-3 |
| **D-82** | **1.0.0 = GA:** Pilot çıkış kapısı sonrası. PO 21.07.2026. | AVIP 0406 O-1 |

---

## Kaynaklar

- GeoLens Platform PRD: `platform/docs/02-product/0204-prd.md`
- Platform GAVF Standardı: `platform/docs/04-ai-framework/0401-ai-visibility-standard.md`
- Specification GAVF Çekirdek: `specification/docs/01-standard/0101-gavf-core.md`
- Specification Uyumluluk Seviyeleri: `specification/docs/01-standard/0109-compliance-levels.md`
- Specification Öz Değerlendirme: `specification/docs/03-compliance/0301-self-assessment.md`
- Platform Mimari: `platform/docs/05-architecture/0505-plugin-system.md`

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0.0 | 22.07.2026 | İlk yayın: Platform-Specification uyumluluk matrisi. GAVF ilkeleri → FR/NFR eşlemesi, katman-doküman haritası, 4 seviyeli karşılama durumu, geçiş yolu. |
| 1.0.1 | 22.07.2026 | AVIP kapalı kararları taşındı: D-45 (SOC 2), D-47 (sızma testi), D-90 (sızma kapsamı), D-82 (GA tanımı). Devralınan Kararlar eklendi. |
