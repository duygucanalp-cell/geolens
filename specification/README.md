# GeoLens Specification

**AI görünürlüğü ölçümü için açık standart.**

GeoLens AI Visibility Framework (GAVF), AI motorlarında marka görünürlüğünün ölçümü, skorlanması ve raporlanması için **açık, tekrarlanabilir ve doğrulanabilir bir metodolojidir.**

> 🎯 **Hedef:** AI görünürlüğü denildiğinde akla gelen ilk standart olmak.
> Başka şirketlerin "GAVF 2.0 uyumluyuz" dediği bir ekosistem inşa etmek.

---

## Neden GAVF?

| Geleneksel SEO | GAVF ile GEO |
|----------------|--------------|
| Google sıralamaları | AI yanıt motorları (ChatGPT, Gemini, Perplexity...) |
| Anahtar kelime yoğunluğu | Alıntı, bağlam, kaynak otoritesi |
| Siyah kutu algoritmalar | **Açıklanabilir, izlenebilir, deterministik** |
| Garanti vaadi | **İstatistiksel dürüstlük + fidelite etiketi** |

---

## Hızlı Başlangıç

```bash
# Repoyu klonla
git clone https://github.com/u2ai/geolens-specification.git
cd geolens-specification

# Dokümanlara göz at
docs/00-overview/0000-master-plan.md    # Master plan
docs/01-standard/0101-gavf-core.md      # GAVF çekirdek standardı
docs/01-standard/0108-fidelity-tiers.md # Fidelite kademeleri
docs/05-integration/0501-getting-started.md  # Uygulamaya başlama
```

---

## Standart Yapısı

GAVF dört katmandan oluşur:

| Katman | Adı | Kapsam |
|:------:|-----|--------|
| **S1** | Ölçüm Standardı | Prompt tasarımı, motor çağrısı, örnekleme (n=3, temp=0) |
| **S2** | Yanıt Standardı | Alıntı çıkarma, varlık tanıma, sınıflandırma |
| **S3** | Skor Standardı | Görünürlük, otorite, ses payı, bileşik skor |
| **S4** | Aksiyon Standardı | Fırsat, öneri, trend, gözlem |

### 6 Çekirdek İlke

| G1 | G2 | G3 | G4 | G5 | G6 |
|:--:|:--:|:--:|:--:|:--:|:--:|
| Açıklanabilirlik | Determinizm | Fidelite | İstatistiksel Dürüstlük | Versiyonlama | Dürüst İddia |

### 4 Uyumluluk Seviyesi

| Temel | İleri | Tam | Sertifikalı |
|:-----:|:-----:|:---:|:-----------:|
| G1–G4 | +G5 +S3 | +S4 + Denetim | +3. Taraf Sertifikası |

---

## Doküman Ağacı

```
docs/
├── 00-overview/      # Meta — standart hakkında (6 doküman)
├── 01-standard/      # GAVF Standardı (9 doküman)
├── 02-methodology/   # Detaylı metodoloji (9 doküman)
├── 03-compliance/    # Uyumluluk ve sertifikasyon (5 doküman)
├── 04-whitepapers/   # Akademik ve sektör yayınları (4 doküman)
├── 05-integration/   # Üçüncü taraf entegrasyon (5 doküman)
└── adr/              # Standart karar kayıtları (5 doküman)
```

**Toplam: 43 doküman**

---

## GeoLens Platform ile İlişki

```
GeoLens Specification (açık) — bu repo
    ↑ besler / doğrular
    ↓ uygular
GeoLens Platform (ticari)
```

GeoLens Platform, Specification'ın **referans uygulamasıdır**. Platformdaki her GAVF bileşeni bu standartta tanımlanmıştır.

➡️ [GeoLens Platform](https://github.com/u2ai/geolens-platform)

---

## Katkı

Standarda katkı yapmak için lütfen [Katkı Rehberi](docs/00-overview/0004-contributing.md)'ni inceleyin.

1. Fork'layın
2. Değişikliğinizi yapın
3. PR açın (patch/minor/major olarak etiketleyin)
4. İnceleme sonrası birleştirin

---

## Lisans

- **Dokümantasyon ve şartname:** [CC BY-SA 4.0](LICENSE)
- **Referans kod ve şemalar:** [Apache License 2.0](LICENSE)

© 2026 U2 AI Studio
