# 0000 · Master Plan — GeoLens Specification

| Alan | Değer |
|---|---|
| Doküman ID | 0000 |
| Proje | GeoLens Specification |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 22 Temmuz 2026 |
| İlişkili | platform/docs/0000, platform/docs/0401, platform/docs/adr/0001–0005 |

---

## 1. Amaç

Bu doküman **GeoLens Specification** reposunun anayasasıdır. GeoLens Specification, AI görünürlüğü ölçümü ve raporlaması için **açık, şeffaf, tekrarlanabilir bir standart** tanımlar. Platform reposundaki ticari üründen bağımsız, herkesin erişimine açık bir referanstır.

> **Stratejik hedef:** Başka şirketlerin "GAVF 2.0 uyumluyuz" dediği bir ekosistem inşa etmek.

---

## 2. Repo Yapısı ve İlişki

| Varlık | Repo | Lisans | Hedef |
|--------|------|--------|-------|
| **GeoLens Platform** | `geolens-platform` (private) | Ticari | SaaS ürün |
| **GeoLens Specification** | `geolens-specification` (public) | CC BY-SA 4.0 / Apache 2.0 | Açık standart |

### İki Repo Arasındaki İlişki

```
GeoLens Specification (açık)
    │
    ├── GAVF Standardı (çekirdek)
    ├── Metodoloji dokümanları
    ├── Whitepaper'lar
    └── Uyumluluk testleri
            │
            ▼
GeoLens Platform (ticari)
    ├── GAVF'ı uygular
    ├── Standarttan türetilen ek ticari know-how içerir
    └── Specification'daki yenilikleri ürüne taşır
```

Platform, Specification'ın referans uygulamasıdır. Specification'daki her karar, platformda karşılığını bulur.

---

## 3. Specification Felsefesi

| # | İlke | Anlamı |
|---|------|--------|
| S1 | **Açıklık** | Metodoloji, algoritma ve ağırlıklar kamuya açıktır. |
| S2 | **Tekrarlanabilirlik** | Aynı girdilerle herkes aynı sonucu üretebilmelidir. |
| S3 | **Sürüm Bağımsızlığı** | Yeni sürüm eski skorları geçersiz kılmaz; her skor hangi versiyonla üretildiğini taşır. |
| S4 | **Dürüstlük** | Standardın kendisi asla sıralama garantisi ima etmez. |
| S5 | **Katılımcılık** | Sektör oyuncuları geri bildirim ve katkı yapabilmelidir. |
| S6 | **Platform-Nötr** | Hiçbir AI motoru veya ticari ürün lehine tasarlanmaz. |

---

## 4. Doküman Ağacı

```
specification/docs/
│
├── 00-overview/            # Meta — standart hakkında
│   ├── 0000-master-plan.md
│   ├── 0001-vision.md
│   ├── 0002-glossary.md
│   ├── 0003-governance.md
│   ├── 0004-contributing.md
│   └── 0005-version-sync-plan.md   # Platform-Specification versiyon senkronizasyon planı
│
├── 01-standard/            # GAVF Standardı (çekirdek)
│   ├── 0101-gavf-core.md           # GAVF: 4 katman, 6 ilke, uyumluluk
│   ├── 0102-measurement-standard.md    # S1: Ölçüm Standardı
│   ├── 0103-response-standard.md       # S2: Yanıt Standardı
│   ├── 0104-scoring-standard.md        # S3: Skor Standardı
│   ├── 0105-action-standard.md         # S4: Aksiyon Standardı
│   ├── 0106-prompt-taxonomy.md         # Prompt sınıflandırma şeması
│   ├── 0107-citation-framework.md      # Alıntı çıkarma ve doğrulama
│   ├── 0108-fidelity-tiers.md          # Fidelite kademeleri
│   └── 0109-compliance-levels.md       # Uyumluluk seviyeleri (Temel/İleri/Tam/Sertifikalı)
│
├── 02-methodology/         # Detaylı metodoloji
│   ├── 0201-sampling-methodology.md        # Örnekleme: n=3, temp=0, bayraklı oran
│   ├── 0202-scoring-algorithm.md            # Bileşik skor hesaplama (GA)
│   ├── 0203-visibility-score.md             # Görünürlük skoru — varlık+konum+kaynak
│   ├── 0204-authority-score.md              # Otorite skoru
│   ├── 0205-share-of-voice.md               # Ses payı
│   ├── 0206-opportunity-detection.md         # Fırsat tespiti
│   ├── 0207-recommendation-classification.md # Öneri sınıflandırma
│   ├── 0208-trend-analysis.md               # Trend analizi
│   └── 0209-citation-markup.md              # Alıntı işaretleme şeması
│
├── 03-compliance/          # Uyumluluk ve sertifikasyon
│   ├── 0301-self-assessment.md        # Öz değerlendirme kontrol listesi
│   ├── 0302-certification-process.md  # Sertifikasyon süreci
│   ├── 0303-audit-requirements.md     # Bağımsız denetim gereklilikleri
│   ├── 0304-compliance-test-suite.md  # Uyumluluk test senaryoları
│   └── 0305-gavf-compliance-matrix.md # Platform-Specification uyumluluk matrisi
│
├── 04-whitepapers/         # Akademik ve sektör yayınları
│   ├── 0401-ai-visibility-whitepaper.md    # "AI Visibility: A Measurement Framework"
│   ├── 0402-seo-vs-geo-whitepaper.md       # "SEO'den GEO'ya: Paradigma Değişimi"
│   ├── 0403-citation-ethics.md             # "Alıntı Etiği ve Yapay Zeka"
│   └── 0404-fidelity-trust.md              # "Fidelite: AI Ölçümünde Güven İlkesi"
│
├── 05-integration/         # Üçüncü taraf entegrasyon
│   ├── 0501-getting-started.md         # GAVF'a başlarken
│   ├── 0502-api-reference.md           # API referansı (girdi/çıktı formatları)
│   ├── 0503-data-format.md             # Veri formatları (JSON Schema)
│   ├── 0504-version-migration.md       # Versiyon geçiş rehberi
│   └── 0505-test-implementation.md     # Test uygulaması referansı
│
└── adr/                    # Standart karar kayıtları
    ├── 0001-standard-license.md          # CC BY-SA 4.0 + Apache 2.0
    ├── 0002-scoring-dimensions.md        # Skor bileşenleri: varlık+konum+kaynak+rakip
    ├── 0003-fidelity-tier-definition.md  # 3 kademeli fidelite modeli
    ├── 0004-sampling-parameters.md       # n=3, temp=0 kararı
    └── 0005-versioning-scheme.md         # SemVer benzeri versiyonlama
```

---

## 5. GAVF Versiyonlama

GAVF, SemVer benzeri bir şema kullanır:

| Bileşen | Anlamı | Örnek |
|---------|--------|-------|
| **Major** | Kırıcı değişiklik (skor hesaplama yöntemi değişir) | 1.0.0 → 2.0.0 |
| **Minor** | Geriye uyumlu ekleme (yeni skor bileşeni) | 1.0.0 → 1.1.0 |
| **Patch** | Düzeltme / açıklama (skor değişmez) | 1.0.0 → 1.0.1 |

Her skor, üretildiği GAVF versiyonunu taşır. Versiyon değişiklikleri adr/ dokümanlarıyla gerekçelendirilir.

---

## 6. Yayın Döngüsü

| Aşama | Açıklama |
|-------|----------|
| **Taslak (Draft)** | İç değerlendirme, henüz kamuya açık değil |
| **Yorum (RFC)** | Sektör oyuncularına açık, 30 gün geri bildirim süresi |
| **Kararlı (Stable)** | Resmi yayın, değişiklikler minör/patch ile |
| **Eski (Deprecated)** | Yerini yeni major versiyon almış, 12 ay geçiş süresi |

---

## 7. Riskler

| ID | Risk | Olasılık | Etki | Önlem |
|:--:|------|----------|------|-------|
| SR-01 | Sektör adopte etmez, standart atıl kalır | Orta | Yüksek | Platform referans uygulaması + pilot müşteriler |
| SR-02 | Rakip kendi standardını piyasaya sürer | Orta | Orta | İlk hareket avantajı + açık katkı modeli |
| SR-03 | AI motorları standardı kendi lehlerine manipüle eder | Düşük | Orta | Platform-nötr ilkesi (S6), çoklu motor dengesi |
| SR-04 | Patent/metodoloji taklit edilir | Orta | Düşük | Açık standart olmanın doğal riski; moat platformda |

---

## Kaynaklar

- GeoLens Platform: `platform/docs/00-overview/0000-master-plan.md`
- GAVF Standardı (Platform uygulaması): `platform/docs/04-ai-framework/0401-ai-visibility-standard.md`
- GeoLens Vizyon: `platform/docs/00-overview/0001-vision.md`

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 22.07.2026 | İlk yayın: Specification reposu master planı, doküman ağacı, GAVF versiyonlama, yayın döngüsü. |
| 1.1 | 22.07.2026 | Doküman ağacı güncellendi: 00-overview'e 0005-version-sync-plan, 03-compliance'a 0305-gavf-compliance-matrix eklendi. Toplam: 43 doküman. |
