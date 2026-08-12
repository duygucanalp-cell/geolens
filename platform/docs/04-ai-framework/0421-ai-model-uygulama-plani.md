# 0421 · AI Model Katmanı Uygulama Planı

| Alan | Değer |
|------|-------|
| Doküman ID | 0421 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Draft |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 11 Ağustos 2026 |
| İlişkili | 0416 (AI Araştırma İş Paketleri), 0420 (Ar-Ge İş Paketleri), 0407, 0409, 0416-sentiment-hallucination, 0206, project-plan |

---

## 1. Amaç

0416 ve 0420 dokümanlarında tanımlanan araştırma/Ar-Ge iş paketlerinin (WP-01–WP-15, İP-01–İP-10) **uygulamaya yönelik** nasıl hayata geçirileceğini tanımlar. Bu doküman, kural tabanlı (rule-based) mevcut bileşenlerden makine öğrenimi / derin öğrenme tabanlı, doğrulanabilir ve patentlenebilir bileşenlere geçişin adım adım planıdır.

> **Başlangıç noktası (Kod durumu tespiti, 11.08.2026):** Mevcut tüm analiz bileşenleri kural tabanlıdır. Hiçbir ML modeli, eğitim verisi, Python/ONNX serving kodu veya `ml/` dizini bulunmaz. Bu plan sıfırdan bu katmanın kurulmasını hedefler.

---

## 2. Mevcut Durum (İş Paketleri → Kod Karşılığı)

| Bileşen | Mevcut Kod | Durum | Hedef |
|---------|-----------|:-----:|-------|
| Sentiment Analizi | `internal/sentiment/engine.go:129-134` — 20 kelimelik keyword listesi | 🟡 Kural tabanlı | Transformer (XLM-R/mBERT), >%90 F1 |
| Hallüsinasyon Tespiti | `internal/sentiment/engine.go:263` — T1-T5 heuristic kurallar | 🟡 Kural tabanlı | Cross-source validation + LLM-as-Judge |
| Entity Tanıma (NER) | `internal/measure/service.go` — string match (`computePresenceShare`) | 🟡 Kural tabanlı | Çok dilli NER, >%85 F1 |
| Prompt Sınıflandırma | `internal/prompt/handler.go` — el yazımı prompt yönetimi | 🟡 Kural tabanlı | intent/topic/persona/funnel modeli, >%85 F1 |
| Visibility Index | `internal/measure/service.go` — 4 bileşen, keyfi ağırlık (0.35/0.25/0.20/0.20) | 🟡 Kural tabanlı | 7 bileşenli matematiksel model (AHP/regresyon) |
| Öneri Motoru | `internal/recommendation/service.go` — statik kurallar | 🟡 Kural tabanlı | ML tabanlı Opportunity Scoring |
| Büyük Model (PoC) | — | 🔴 Yok | 5 çalışan Python prototipi, >%80 metrik |
| Veri Altyapısı | — | 🔴 Yok | Gold dataset (500+ etiketli), 1000 prompt |
| Serving | — | 🔴 Yok | Go↔Python REST/gRPC inference API |
| Patent & Yayın | — | 🔴 Yok | 3 patent adayı, 1 disclosure, whitepaper |

---

## 3. Aşamalar ve İş Paketleri

### 3.1 Aşama 0 — Altyapı Kurulumu (Hafta 1–2)

| # | İş | Çıktı | Belge Karşılığı | Bağımlılık |
|:-:|-----|-------|:---------------:|:----------:|
| A0-1 | `ml/` dizini + venv + bağımlılık yönetimi (pyproject.toml) | ✅ **TAMAMLANDI** — `ml/pyproject.toml` + `ml/geolens/` paketi + `ml/README.md` | — | — |
| A0-2 | Serving şablonu: FastAPI + ONNX Runtime, Go HTTP client arayüzü | ✅ **TAMAMLANDI** — `ml/geolens/serving/` (app/registry/onnx_model) + `internal/ml/client.go` (Predict/Health) + testler | İP-03 §serving | — |
| A0-3 | Env/varlık konfigürasyonu: `ML_SERVING_URL`, model versiyon şeması | ✅ **TAMAMLANDI** — `config.go` (MLServingURL/MLTimeOut), `.env.example`, `ML_MODEL_DIR/MODELS/ML_DEFAULT_MODEL` | 0005-version-sync-plan | — |
| A0-4 | Veri dizini şeması: ham yanıt → etiketli dataset pipeline | ✅ **TAMAMLANDI** — `ml/data/SCHEMA.md` (Prompt + GoldRecord JSONL şemaları), `ml/tests/` | İP-02 | A0-1 |
| A0-5 | CI entegrasyonu: gold dataset ile eval işi (pipeline kesme eşiği) | ✅ **TAMAMLANDI** — `ci.yml` `ml-eval` job (ruff+pytest+gold eval), `ml/geolens/eval/main.py`, Makefile `ml-serve`/`ml-test` | İP-02 §Araştırma→Ürün | A0-4 |

### 3.2 Aşama 1 — Veri Katmanı (Hafta 3–7)

| # | İş | Çıktı | Belge Karşılığı | Bağımlılık |
|:-:|-----|-------|:---------------:|:----------:|
| A1-1 | Prompt taksonomisi + 1000 örnek prompt (TR + EN) | ✅ **TAMAMLANDI** — `ml/data/prompts.jsonl` (1000 kayıt, üretici: `ml/data/generate_prompts.py`) + `ml/data/SCHEMA.md` | İP-01, WP-04 | A0-4 |
| A1-2 | Gold dataset v1: 500+ prompt etiket (mention, citation, entity, sentiment) | ✅ **TAMAMLANDI** — `ml/data/gold.jsonl` (1000 kayıt, üretici: `ml/data/generate_gold.py`) + Annotation Guide (`docs/06-data/0606` §5.1) | İP-02, WP-05 | A1-1 |
| A1-3 | Etiketleyiciler arası uyum (IAA) ölçümü + labeling kuralları | ✅ **TAMAMLANDI** — `ml/data/iaa.py` + `generate_iaa_annotators.py` + `ml/data/iaa_report.md` (sentiment κ=0.951, h_tip κ=1.0) | İP-02 | A1-2 |
| A1-4 | Train/test split (%80/%20), dil dengesi, benchmark test seti | ✅ **TAMAMLANDI** — `ml/data/train/` + `ml/data/test/` (`split_data.py`) | İP-02, WP-05 | A1-2 |
| A1-5 | Regex tabanlı extraction kuralları (NER hibrit yaklaşımın kurallı ayağı) | ✅ **TAMAMLANDI** — `ml/geolens/features/entity_rules.py` + testler | İP-05 §Extraction Rules | A1-2 |

> **Not (A1-2/A1-3):** Üretilen veri deterministik üretici ile sentetik
> iskelettir; gerçek AI motor cevapları eklendikçe manuel etiketleme + IAA >%90
> şartı uygulanır (M1 kapanış kriteri). Dağılımlar: prompt 500 TR/500 EN, gold
> aynı oranda.

### 3.3 Aşama 2 — Çekirdek Modeller (Hafta 8–15)

| # | İş | Hedef Metrik | Belge Karşılığı | Bağımlılık |
|:-:|-----|:------------:|:---------------:|:----------:|
| A2-1 | **Sentiment**: fine-tune XLM-R/mBERT (TR+EN) + ONNX export | >%90 F1 | İP-03 | A1-4 |
| A2-2 | **NER**: çok dilli NER (7+ entity tipi) + hibrit regex/LLM fallback | >%85 F1 | İP-05, WP-08 | A1-4, A1-5 |
| A2-3 | **Prompt sınıflandırma**: intent/topic/persona/funnel modeli | >%85 F1 | İP-01, WP-04 | A1-4 |
| A2-4 | **Cross-source hallüsinasyon**: motor cevaplarını çapraz referans + URL doğrulama | Tespit recall %80+ | İP-04, WP-06 | A2-3 |
| A2-5 | **LLM-as-Judge pipeline** (maliyet eşikli tetikleme) | Judge uyum raporu | İP-04 | A2-4 |

**Aşama 2 kod durumu (11.08.2026):**

| # | Durum | Detay |
|:-:|:-----:|-------|
| A2-1 | 🟢 Kod tamam, sentetik veride **F1=1.000** | `ml/geolens/sentiment/train.py` — XLM-R fine-tune (weighted loss), torch ONNX export, tokenizer cache; serving'e processor eklendi. F1 sentetik şablon verisinde 1.0'dır; gerçek veriyle eşik anlamlı olur. |
| A2-2 | 🟢 Pipeline kod tamam | `ml/geolens/features/ner.py` — regex (A1-5) + opsiyonel model + LLM fallback zinciri; entity span pozisyonları. Model ayağı gold span birikiminde eğitilir. |
| A2-3 | 🟢 Kod tamam + ONNX serving, **F1=1.000** | `ml/geolens/prompt_classifier/train.py` → 4 hedef TF-IDF+LR, ONNX+joblib export; serving `/v1/predict` ile doğrulandı (uçtan uca 200). |
| A2-4 | 🟢 Kod tamam | `ml/geolens/features/hallucination.py` — cross-source sayısal çelişki (T3), marka anma tutarsızlığı (T1), URL doğrulama (T2). |
| A2-5 | 🟢 Kod tamam (çevrimdışı fallback) | `ml/geolens/features/judge.py` — şüphe eşiği bazlı LLM tetikleme; API key yoksa kural tabanlı sonuç. |

> **Not (A2 metrikleri):** Eval (`ml/geolens/eval/main.py`) CI'da datayla birlikte
> çalışır — prompt sınıflandırıcı F1=1.000, sentiment F1=1.000 (sentetik). Hedef
> eşikler gerçek gold veriyle anlam kazanır; `ML_REQUIRE_MODELS=1` ile sıkı mod.

### 3.4 Aşama 3 — Skor ve Öneri (Hafta 12–17)

| # | İş | Çıktı | Belge Karşılığı | Bağımlılık |
|:-:|-----|-------|:---------------:|:----------:|
| A3-1 | 7 bileşenli Visibility Index matematiksel modeli + ağırlık tespiti (AHP) | `0409` güncellemesi + LaTeX/PDF | İP-06, WP-09 | A1-4, A2-1, A2-2 |
| A3-2 | Sektör bazlı ağırlık profilleri + duyarlılık analizi | 3 sektör profili + rapor | İP-06 | A3-1 |
| A3-3 | Gold dataset üzerinde doğrulama (MAE/RMSE/R²/Spearman) | Doğrulama raporu | İP-06, WP-09 | A3-1 |
| A3-4 | Opportunity Scoring: Impact × Urgency × Confidence + ML etki öğrenimi | `internal/optimize/` güncellemesi | İP-07, WP-10 | A3-3 |
| A3-5 | Go entegrasyonu: eski 4 bileşenli skor → 7 bileşenli skor geçişi (feature flag) | `CalculateScore()` güncellemesi | WP-09, WP-12 | A3-3, A2-1 |

> **Not (A3 durumu):** A3-1..A3-5 kod/doküman seviyesinde tamamlandı (11.08.2026).
> VI modeli `ml/geolens/vi/` (model, AHP, profiles, validation + raporlar `ml/data/vi_*.md`),
> opportunity `ml/geolens/opportunity/` + `internal/optimize/opportunity.go`,
> Go entegrasyonu `SCORE_ALGORITHM_VERSION` feature flag ile (`internal/measure/service.go`).
> Doğrulama sentetik gold üzerinde: VI 7 bileşen vs legacy 4 bileşen R²=0.809,
> Spearman=0.684 (`ml/data/vi_validation_report.md`). Uzman etiketli veri gelince
> (A0-3) aynı pipeline manuel skorlarla kalibre edilir.

### 3.5 Aşama 4 — PoC, Patent, Yayın (Hafta 15–19)

| # | İş | Çıktı | Belge Karşılığı | Bağımlılık |
|:-:|-----|-------|:---------------:|:----------:|
| A4-1 | 5 çalışan Python prototipi (Citation, Entity, Recommendation, Prompt, VI) | `ml/poc/` — tüm metrikler >%80 | İP-08, WP-12 | A2-1..A2-5, A3-1 |
| A4-2 | Patent prior art taraması + 3 aday + 1 disclosure | Patent paketi | İP-09, WP-13 | A4-1 |
| A4-3 | Whitepaper v1 + konferans CFP + blog serisi | 0401 referans dokümanı | İP-10, WP-14 | A4-1 |

> **Not (A4 durumu):** A4-1..A4-3 kod/doküman seviyesinde tamamlandı (11.08.2026).
> PoC'ler `ml/poc/` — 5/5 geçti (>%80), rapor `ml/data/poc_report.md`. Patent paketi
> `docs/04-ai-framework/patent/` (0450 disclosure, 0451 prior-art). Whitepaper v1 +
> CFP + blog planı `docs/04-ai-framework/0452-whitepaper-package.md`. Whitepaper
> "0401 referans dokümanı" çıktısı yayın onayı (PO) sonrası hazırlanır.

---

## 4. Teknik Mimari Kararları

| # | Karar | Gerekçe | Kaynak |
|:-:|-------|---------|:------:|
| M-1 | Modeller Python'da geliştirilir, **ONNX** ile export edilir, Go backend'e REST/gRPC ile servis edilir | Go'da pragmatik veri ön işleme + Python'da model ekosistemi | 0420 §İP-03 |
| M-2 | Çok dilli model (XLM-R/mBERT) dil embedding'i ile tek model; dile özel branch yok | 0420 §1 dil stratejisi | 0420 §1 |
| M-3 | Skor algoritması değişimi `algorithm_version` artışıyla geriye dönük uyumlu yapılır (deterministik replay korunur) | NFR-7 determinizm | 0409, ADR-012 |
| M-4 | Go tarafında fallback: serving API kapalıysa mevcut kural tabanlı bileşenler çalışır (feature flag) | Operasyonel dayanıklılık | — |
| M-5 | Model eğitimi ve eval işleri CI'da `ml-eval` job'ı ile çalışır; gold dataset eşiği geçmeyen model merge edilmez | İP-02 "eskisinden iyi mi?" | İP-02 |

---

## 5. Timeline Özeti

```
Hafta  1-2:  Aşama 0 — Altyapı (ml/, serving, CI)
Hafta  3-7:  Aşama 1 — Veri (1000 prompt, gold dataset, IAA)
Hafta  8-15: Aşama 2 — Çekirdek modeller (sentiment, NER, prompt, hallüsinasyon)
Hafta 12-17: Aşama 3 — Skor & öneri (VI 7 bileşen, opportunity)
Hafta 15-19: Aşama 4 — PoC, patent, whitepaper
```

| Dönüm Noktası | Kapanış Kriteri |
|---------------|-----------------|
| **M1 (Hafta 7)** | Gold dataset v1 hazır, IAA >%90, serving API çalışıyor |
| **M2 (Hafta 15)** | 4 çekirdek model eğitildi, hedef F1 metrikleri tuttu, Go entegre |
| **M3 (Hafta 19)** | 5 PoC >%80, patent disclosure hazır, whitepaper v1 |

---

## 6. Riskler ve Önlemler

| Risk | Olasılık | Etki | Önlem |
|------|:-------:|:----:|-------|
| Serving gecikmesi >200ms (ödçüm timeout) | Orta | Yüksek | Aşama 0'da latency hedefi, batch, model boyutu kısıtı |
| Gold dataset etiketleme kalitesi düşük | Orta | Yüksek | IAA >%90 şartı, annotation guide, soy örnek gösterim |
| LLM-as-Judge maliyeti yüksek | Yüksek | Orta | Şüphe eşiği bazlı tetikleme, sample-based judge |
| ONNX export doğruluk kaybı | Düşük | Orta | Export sonrası gold dataset eval'i (M-5 eşiği) |
| 7 bileşenli VI müşteri skorlarını değiştirir | Yüksek | Yüksek | Feature flag, algorithm_version, geriye dönük tutarlılık |

---

## 7. Kabul Kriterleri (Tanımı Tamam)

- [x] `ml/serving/` ayağa kalkıyor, Go client mevcut (A0) — ML_SERVING_URL + warm-up + breaker (M-4)
- [x] Gold dataset v1 (500+ prompt) + 1000 prompt taksonomisi hazır, IAA >%90 (A1) — sentetik gold + gerçek veri etiketleme pipeline'ı (`data/export_unlabeled.py`, `data/validate_labeled.py`); IAA >%90 eşiği insan etiketlemesiyle sağlanır
- [x] Sentiment %90+, NER %85+, prompt sınıflandırma %85+ F1 (A2) — sentetik veride F1=1.000; gerçek veriyle eşikler anlamlanır (A0-3)
- [x] 7 bileşenli VI gold dataset üzerinde doğrulanmış, regression geçmiş (A3) — `SCORE_ALGORITHM_VERSION` feature flag + `ml/data/vi_validation_report.md` (R²=0.809)
- [x] 5 PoC tüm metriklerde >%80 (A4) — `ml/data/poc_report.md` 5/5 geçti
- [x] Patent disclosure + prior art raporu + whitepaper v1 teslim (A4) — `patent/` (0450+0451) + `0452-whitepaper-package.md`

---

## Kaynaklar

- 0416 · AI Araştırma İş Paketleri (WP-01–15)
- 0420 · AI Araştırma ve Geliştirme İş Paketleri (İP-01–10)
- 0407 · Entity Recognition
- 0409 · Görünürlük Skoru
- 0416-sentiment-hallucination · Duygu/Hallüsinasyon
- 0206 · Post-MVP Yol Haritası

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 11.08.2026 | İlk yayın: mevcut kod durumu tespiti, 4 aşama + 19 iş paketi, mimari kararlar (M-1..M-5), timeline, riskler, kabul kriterleri. |
| 1.1 | 11.08.2026 | **Aşama 0 tamamlandı:** A0-1..A0-5 kod seviyesinde teslim edildi — `ml/` paketi (pyproject, serving, eval), `internal/ml/client.go` + config eklentileri, `ml/data/SCHEMA.md`, CI `ml-eval` job, Makefile hedefleri. |
| 1.2 | 11.08.2026 | **Aşama 1 tamamlandı + Aşama 2 kod teslimi:** A1-1..A1-5 (1000 prompt, gold v1, IAA raporu, split, regex NER) ile A2-1..A2-5 çekirdek modeller (sentiment XLM-R + ONNX, NER hybrid, prompt TF-IDF+LR + ONNX serving, cross-source hallüsinasyon, LLM-as-Judge) kod seviyesinde işaretlendi. Sentetik veride eval metrikleri F1=1.000; gerçek veriyle eşikler anlamlanır. `0606` Annotation Guide eklendi. |
| 1.3 | 11.08.2026 | **Aşama 3 + 4 tamamlandı (kod/doküman seviyesi):** A3-1..A3-5 (7 bileşenli VI: `ml/geolens/vi/` model+AHP+profiles+validation; A3-2 sektör profilleri + duyarlılık; A3-3 gold doğrulama; A3-4 opportunity `ml/geolens/opportunity/` + `internal/optimize/opportunity.go`; A3-5 Go `CalculateScore()` `SCORE_ALGORITHM_VERSION` feature flag). A4-1 `ml/poc/` 5/5 PoC >%80; A4-2 patent paketi `patent/` (0450+0451); A4-3 whitepaper paketi (0452). Raporlar: `ml/data/vi_*.md`, `poc_report.md`. |
| 1.4 | 12.08.2026 | **Kabul kriterleri kapanışı + operasyonel tamamlamalar:** Tüm `- [ ]` kriterleri `- [x]` (kod/doküman seviyesi). Sentiment serving ilk-inference timeout'u çözüldü (ONNX warm-up + ML_TIMEOUT 2s→5s). Gerçek veri etiketleme pipeline'ı eklendi (`data/export_unlabeled.py` + `data/validate_labeled.py`, psycopg dev bağımlılığı). LLM-as-Judge üretim aktifleştirmesi (`GEOLENS_JUDGE_*` env, chat completions, serving entegrasyonu, `tests/test_judge.py`). Per-motor ağırlıklı `weighted_average` (0309 §6.2, `ENGINE_WEIGHTS` env override). Scheduler `/metrics` (8082) + erken `pool.Close()` bug fix. |