# 0451 · Patent Prior Art Analizi (İP-09)

| Alan | Değer |
|---|---|
| Doküman ID | 0451 |
| Proje | GeoLens Platform |
| Versiyon | 0.1 (Draft) |
| Durum | Draft |
| Sahip | U2 AI Studio · Ar-Ge |
| Tarih | 11 Ağustos 2026 |
| İlişkili | 0420 İP-09, 0450, 0421 A4-2 |

> Tarama: Google Patents, USPTO, WIPO, Espacenet (2026-08-11). Bu döküman
> patent vekilinin doğrulamasına sunulur; vekil onayı olmadan **Draft** durumu
> korunur.

---

## 1. Tarama Kapsamı

Amaç: 0450 disclosure'ındaki 4 adayın yenilik iddiasını risk değerlendirmesiyle
sınamak. Aranan kavramlar:
- multi-engine AI / LLM brand measurement
- branded search visibility in LLM responses
- cross-source hallucination detection
- AI-specific entity disambiguation
- opportunity scoring with impact prediction

---

## 2. Aday 1 — Cross-Source Hallucination Detection (AI-Generated Brand Mentions)

### 2.1 Prior Art Bulguları

| Kaynak | Özet | İlgililik |
|---|---|---|
| US 2023xxxxxxxx (LLM hallucination detection) | Tek model çıktısında iç tutarlılık kontrolü | Düşük-Orta |
| arXiv: cross-checking LLM responses via n-model aggregation | Aynı girdiye çoklu model çıktısı toplayıp oylama | Orta |
| WIPO PCT LLM factual verification | Doğrulama modeli eğitme (NLI tabanlı) | Düşük |

### 2.2 Farklılaşma

Literatürdeki cross-model doğrulama **sadece doğruluk oylaması** yapar; **citation
URL'lerine canlı HEAD isteği** , **marka bağlamlı çelişki (T3) kuralları** ve
**skor temizliği + opportunity motoru bağlantısı** birleşimi bulunmadı.

### 2.3 Patent Alabilirlik

| İddia | Risk | Gerekçe |
|---|---|---|
| Çok motorlu çelişki yakalama | Orta | Benzeri mevcut |
| URL canlı doğrulama + T3 kuralları | Düşük | Kombinasyon yeni |
| Skora bağlama (VI kirliliği önleme) | Düşük | Ölçüm+doğrulama pipeline'ı yeni |

**Sonuç:** Faydalı model / utility patent güçlü. Kombinasyon-odaklı claim önerilir.

---

## 3. Aday 2 — Multi-Engine Visibility Scoring Algorithm

### 3.1 Prior Art Bulguları

| Kaynak | Özet | İlgililik |
|---|---|---|
| US G06Q30/02 (marketing scoring) | Tek kanal skorlama | Düşük |
| Ahrefs/SEMrush patentleri | SERP bazlı visibility | Düşük (LLM yok) |
| Search Engine visibility score (ref) | Anahtar kelime ağırlıklı | Orta (kanal farkı) |

### 3.2 Farklılaşma

- Prior art tek arama motoru SERP skoruyor; **biz 8 AI motorunun yanıtını aynı
  prompt taksonomisi üzerinden paralel ölçüyoruz**.
- Motor kademesi (tier) bazlı güvenilirlik etiketleme mevcut sistemlerde yok.
- AHP ile veriyle belirlenen ağırlıklar + sektör profilleri + duyarlılık raporu
  açıklanabilirliği farklılaştırıcı.

### 3.3 Patent Alabilirlik

| İddia | Risk | Gerekçe |
|---|---|---|
| Multi-engine LLM measurement | Orta | Kanal bazlı ölçüm ilkesi bilinen |
| Tier etiketleme + CI + replay determinizmi | Düşük | Teknik kombinasyon yeni |
| AHP kalibrasyonlu ağırlıklar | Düşük | Skor kalibrasyonu özel |

**Sonuç:** Patent uygun; claim'lerde "LLM yanıt kanallarının paralel ölçümü ve
tier-bazlı güvenilirlik" ana çekirdek olmalı.

---

## 4. Aday 3 — AI-Specific Entity Disambiguation (Marka Bağlamında)

### 4.1 Prior Art Bulguları

| Kaynak | Özet | İlgililik |
|---|---|---|
| US G06F40/295 (name entity disambiguation) | Genel NED | Orta |
| Co-reference resolution paper | Zamir çözümleme | Düşük |

### 4.2 Farklılaşma

Genel NED marka bağlamını bilmez; bizim hibrit pipeline (regex → ONNX model →
LLM fallback) + Türkçe marka allowlist + kanonik ad normalizasyonu (ı→i) +
sektör terimleri + para/yüzde/tarih tipleri marka ölçümüne özelleşmiş.

### 4.3 Patent Alabilirlik

| İddia | Risk | Gerekçe |
|---|---|---|
| Marka-bağlam NED | Orta | Genel NED yakın |
| Hibrit zincir + fallback | Düşük | Pipeline mimarisi özel |

**Sonuç:** Faydalı model adayı; kimlik çözme yöntemi yerine **hibrit pipeline
gerektiğinde LLM fallback** claim'i öne çıkarılmalı.

---

## 5. Aday 4 — Opportunity Scoring with ML-Based Impact Prediction

### 5.1 Prior Art Bulguları

| Kaynak | Özet | İlgililik |
|---|---|---|
| US recommendation engine patents | Kişiselleştirilmiş öneri sıralama | Orta |
| SEO audit action planning | Statik kural önerisi | Düşük |

### 5.2 Farklılaşma

- **Impact × Urgency × Confidence** formülü İP-07 ile tanımlı; literatürde
  "visibility önerisi" için birebir yok.
- ML, uygulanan/dismissed önerilerden kategori etki katsayısını öğrenir
  (`learn_impact`, `internal/optimize`).

### 5.3 Patent Alabilirlik

| İddia | Risk | Gerekçe |
|---|---|---|
| Impact×Urgency×Confidence skorlama | Düşük-Orta | Formül yeni ama temel matematik bilinen |
| Öneri sonuçlarından etki öğrenme | Düşük | ML geri besleme döngüsü yeni |

**Sonuç:** En güçlü "uygulanan öneri geri beslemesiyle etki öğrenen önceliklendirme"
açısına odaklanmalı.

---

## 6. Toplam ve Önerilen Sıralama

| Sıra | Aday | Öncelik | Tahmini Maliyet | Önerilen Başvuru |
|---|---|---|---|---|
| 1 | Cross-source hallucination + URL + opportunity bağı | Yüksek | €5.000-6.000 | 2026-Q4 |
| 2 | Multi-engine visibility scoring + tier + AHP | Yüksek | €5.000-6.000 | 2027-Q1 |
| 3 | AI-specific entity disambiguation (hibrit) | Orta | €4.000-5.000 | 2027-Q2 |
| 4 | Opportunity scoring + ML etki öğrenimi | Orta | €4.000-5.000 | 2027-Q2 (ek) |

**Backlog:** `2026-Q4 → Patent #1`, `2027-Q1 → Patent #2`, `2027-Q2 → Patent #3-4`.

---

## 7. Not

Bu tarama otomatik araç + manuel literatür okumasıyla hazırlanmıştır; resmi
patent vekili doğrulaması **zorunludur** ve başvuru öncesi farklılaşma
analizlerinin güncellenmesi gerekir.

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 0.1 | 11.08.2026 | İlk draft: 4 aday için prior art taraması ve farklılaşma analizi. |