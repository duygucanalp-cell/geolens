# 0421-8INTENT · Prompt Intent Taksonomisi Genişletme Planı (5 → 8)

| Alan       | Değer                                                                                   |
|------------|-----------------------------------------------------------------------------------------|
| Doküman ID | 0421-8INTENT                                                                             |
| Proje      | GeoLens Platform                                                                         |
| Versiyon   | 1.0                                                                                     |
| Durum      | Draft                                                                                   |
| Sahip      | U2 AI Studio · Engineering                                                              |
| Tarih      | 12 Ağustos 2026                                                                          |
| İlişkili   | 0421 (AI Model Uygulama Planı), 0420, `ml/data/SCHEMA.md`, ÖDEV-01/ÖDEV-02 teslimleri    |

---

## 1. Amaç

Ödev-01 teslimindeki **9.000 etiketli soru** 8 intent kategorisi kullanıyor
(information, recommendation, comparison, complaint, problem, purchase, opinion,
news), platformun serving taksonomisi ise 5 intent (presence, comparison,
recommendation, category, problem). Bu doküman, prompt sınıflandırıcıyı **8-intent
taksonomisine** taşıyacak geçişi tanımlar; veri, model, Go kodu, doğrulama ve
doküman adımlarını kapsar. Mevcut durumda `opinion/news/purchase/complaint`
ayrımı kayıplı haritalamayla eriyor ve `measure` bu intent'ler için ölçek
uygulayamıyor.

## 2. Mevcut Durum ve Hedef Taksonomi

| Boyut | Mevcut (SCHEMA.md) | Hedef | Veri kaynağı |
|-------|--------------------|-------|--------------|
| intent | presence, comparison, recommendation, category, problem (5) | information, recommendation, comparison, complaint, problem, purchase, opinion, news (8) | `ml/data/odev01/prompts_v1.jsonl` |
| persona | consumer, expert, journalist, investor (4) | end_user, technical_expert, executive, journalist, investor (5) | `prompts_v1.jsonl` |
| funnel | awareness, decision (2) | awareness, consideration, decision, purchase, loyalty (5) | `prompts_v1.jsonl` |
| topic | product, service, brand, sector, technology (5) | **korunur** (5) | `data/full/` (mevcut sentetik + türetilmiş) |

Hedef persona/funnel, Ödev-01 verisinin zengin etiketleriyle birebirdir; topic
taksonomisi korunur (Ödev-01 topic türetimi `brand/service/product` zaten bu
kümeye düşer).

> Genişletme notu: Ödev-02 dokümanı persona için 7 değer (student, developer dahil)
> öngörür; şu an bu etiketlerde veri olmadığından bu planda kapsam dışıdır (ileride
> `prompts_v2` verisiyle eklenebilir).

## 3. Kod Etki Analizi

| Bileşen | Etki |
|---------|------|
| `internal/measure/service.go` `defaultIntentComponentScale` | 🔴 **Tek fonksiyonel Go değişikliği.** 5 anahtarlı harita 8 anahtara genişletilir; yoksa 8-intent etikette `intentWeights` (satır 183) bilinmeyen key'de fallback'e düşer ve ölçek hiç uygulanmaz |
| `internal/ml/client.go` | 🟢 Etki yok — `PromptClassification.Label` serbest string, enum yok |
| `internal/config/config.go` `ParseIntentWeightScaleRaw` | 🟢 Etki yok — parser genel (`intent=v1,...,v7;...`), key agnostik |
| persona/funnel/topic çıktıları | 🟢 Go'da hiçbir yerde tüketilmiyor (yalnız `client_test.go:97` mock kontrolü); model+data tarafında serbestçe zenginleştirilebilir |
| `internal/measure/intent_test.go`, `internal/config/intent_scale_test.go` | 🟡 Test beklentileri güncellenir (yeni key'ler / kaldırılan key'ler) |

## 4. Uygulama Fazları

### Faz A — Veri (Go kodu gerektirmez)

1. `prompts_v1.jsonl` kanonik eğitim kaynağı ilan edilir; `prompts_v1.mapped.jsonl`
   deprecate edilir (geri dönüş için tutulur).
2. İsteğe bağlı: mevcut 1000 sentetik prompt 8-intent'e ters haritalanır
   (presence→information, category→opinion, comparison→comparison,
   recommendation→recommendation, problem→problem). Öneri: intent modeli için
   **kullanma** (ters haritalama kayıplı; zengin 9000 yeterli), topic modeli için
   **kullan** (sector/technology varyasyonu verir).

### Faz B — Model (Python)

> ✅ **Uygulandı (13.08.2026)** — `split_odev01.py` ile kayıpsız veri split'i
> (7200/1800, intent×sector, çekirdek 42); intent/persona/funnel modelleri
> `prompts_v1.jsonl` üzerinden 8/5/5 sınıfla `class_weight='balanced'` ile yeniden
> eğitildi ve ONNX export edildi (`ml/models/prompt_classifier/`). Sürüm: 2.0.0
> (`registry.py`); topic 1.1.0 olarak korundu. Eski 1.1.0 artefaktları
> `backup_v1.1.0_20260813/`'te.

1. `intent` modeli `prompts_v1.jsonl` üzerinden 8 sınıfla yeniden eğitilir.
2. `persona` (5 sınıf) ve `funnel` (5 sınıf) da `prompts_v1.jsonl`'dan yeniden
   eğitilir (veride doğrudan etiketli).
3. `topic` modeli `data/full/` (mevcut + Ödev-01) üzerinde kalır.
4. Sınıf dengesizliği için `LogisticRegression(class_weight='balanced')` önerilir
   (opinion 2400 vs complaint 600).
5. Sürüm: intent/persona/funnel → **2.0.0**, topic → 1.1.0
   (`ml/geolens/serving/registry.py`).

### Faz C — Go (onay gerekir)

> ✅ **Uygulandı (13.08.2026)** — Aşağıdaki 8 anahtarlı tablo
> `internal/measure/service.go`'daki `defaultIntentComponentScale`'a işlendi;
> `intent_test.go` (yeni intent testleri + eski presence/category kaldırma
> kontrolü), `config/intent_scale_test.go` ve `ml/client_test.go` (opinion
> etiketi örneği) güncellendi. Tüm Go testleri geçiyor.

1. `defaultIntentComponentScale` 8 anahtara genişletildi — aşağıdaki taslak
   çarpanlar kullanıldı (7 bileşen sırası: varlık, konum, kaynak, rakip,
   appearance, sentiment, compvis).

| intent | varlık | konum | kaynak | rakip | appearance | sentiment | compvis | kaynak |
|--------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|--------|
| information | 1.25 | 1.00 | 0.90 | 0.90 | 1.10 | 0.90 | 0.90 | eski presence |
| recommendation | 1.00 | 1.00 | 1.15 | 1.00 | 0.95 | 1.10 | 0.95 | aynen |
| comparison | 0.90 | 1.00 | 0.90 | 1.40 | 0.90 | 0.90 | 1.30 | aynen |
| opinion | 1.00 | 1.00 | 1.00 | 1.00 | 1.25 | 1.00 | 1.00 | eski category |
| problem | 1.00 | 1.15 | 1.10 | 1.00 | 0.90 | 1.00 | 1.00 | aynen |
| complaint | 1.00 | 1.10 | 1.15 | 1.00 | 0.90 | 1.20 | 1.00 | problem + sentiment ağırlıklı |
| purchase | 1.00 | 1.00 | 1.10 | 1.10 | 1.00 | 1.10 | 1.00 | karar yönlendirmesi |
| news | 1.10 | 1.00 | 1.20 | 1.00 | 0.95 | 1.00 | 0.95 | kaynak/güncellik |

> **Uyarı:** Taslak çarpanlar kalibrasyon gerektirir — mevcut tablo "pilot verisiyle
> kalibre edilir" notu taşıyor (0421 A3-3). complaint/purchase/news satırları
> üretim öncesi pilot verisiyle doğrulanmalıdır.

2. Test güncellemeleri: `intent_test.go`, `intent_scale_test.go`,
   `client_test.go` (yeni etiket örnekleri, ör. `opinion`).

### Faz D — Doğrulama

> ✅ **Uygulandı (13.08.2026)** — Rapor: `ml/data/8intent_f1_report.md`.

1. 8-intent modelde per-sınıf F1 (özellikle comparison ↔ opinion karışımı —
   "Apple mı Samsung mu?" testiyle gözlemlenmişti) — **per-sınıf F1=1.000**
   (1800 test kaydında karışım yok; veri şablon kaynaklı, sözcüksel ayraçlar güçlü).
2. Uçtan uca: serving `/v1/prompt/classify` → `measure.intentWeights`'ın artık
   fallback'e düşmediği (yeni etiketlerde scale uygulandığı) Go entegrasyon testi
   — **`TestIntentWeights_NewIntentScale`** (opinion → appearance yükselir, ok=true).
3. ONNX + joblib artefaktlarında model sürümünün 2.0.0 döndüğü — **`/v1/predict`
   model_version=2.0.0** doğrulandı; serving 8 intent örneğinde 8/8 doğru etiket.
4. **CI bağlantısı:** `ml-eval` job'ındaki `geolens.eval.main` commit edilmiş
   intent/persona/funnel artefaktlarını odev01 split'i üzerinde **per-sınıf F1**
   ile değerlendirir (eşik `ML_PROMPT_PER_CLASS_F1`, varsayılan 0.85) — tek
   sınıf eşiğin altına inerse veya model sınıf kümesi taksonomiyle uyuşmazsa CI
   kırılır. `ML_REQUIRE_MODELS=1` ile sıkı mod aktif.

### Faz E — Doküman

1. `ml/data/SCHEMA.md` — intent (8), persona (5), funnel (5) sözlükleri ve çapraz
   dağılım notu güncellenir (5×5×5×4×2 → 8×…).
2. `docs/04-ai-framework/0421-ai-model-uygulama-plani.md` — prompt sınıflandırma
   hedefi 8-intent olarak güncellenir.
3. `docs/04-ai-framework/patent/0450-patent-disclosure.md` — "5×5×5×4×2" referansı.
4. `ml/data/odev01/README.md` — mapped dosya deprecate notu.

## 5. Riskler

| Risk | Önlem |
|------|-------|
| complaint/purchase/news çarpanları kalibre değil → skor sapması | Faz C çarpanları pilot verisiyle doğrulanmadan 2.0.0 üretime alınmaz |
| opinion ↔ comparison sınıf karışımı (kurgu veri deseni) | Gerçek veri (Ödev-02) geldikçe iyileşir; per-sınıf F1 takibi |
| Sınıf dengesizliği (opinion 2400, complaint 600) | `class_weight='balanced'` |
| Tüm veri Türkçe — EN sınıflandırma zayıf | EN veri Ödev-02 ile ayrı iş kalemi |

## 6. Açık Kararlar (onay bekliyor)

1. **8 intent çarpan tablosu** (Faz C taslak değerleri) — ✅ **karara bağlandı**
   (13.08.2026): taslak değerlerle uygulandı; complaint/purchase/news çarpanları
   pilot verisiyle doğrulanmadan üretime alınmayacak (bkz. Riskler).
2. persona/funnel de 5'er sınıfa zenginleştirilsin mi? — ✅ **karara bağlandı**
   (13.08.2026): Faz B'de `prompts_v1.jsonl` üzerinden 5'er sınıfla eğitildi (2.0.0).
3. Mevcut 1000 sentetik intent modelinde ters haritalanıp eklenmesin mi?
   — ✅ **karara bağlandı** (13.08.2026): intent için **hayır** (kayıplı, 9000
   zengin veri yeterli); topic için de gerekmedi — `data/full` zaten
   mevcut sentetik + Ödev-01 içeriyor.
4. Sürüm: intent/persona/funnel 2.0.0, topic 1.1.0 — ✅ **onaylandı ve uygulandı**
   (13.08.2026, `ml/geolens/serving/registry.py`).

---

## Changelog

| Versiyon | Tarih     | Değişiklik | Kişi |
|----------|-----------|------------|------|
| 1.0      | 2026-08-12| 8-intent taksonomi geçiş planı taslak olarak oluşturuldu | U2 AI Studio · Engineering |
| 1.1      | 2026-08-13| **Faz C uygulandı:** `defaultIntentComponentScale` 8 anahtara genişletildi (presence→information, category→opinion eşlemesiyle); Go testleri güncellendi. **Faz E kısmen uygulandı:** `SCHEMA.md` 8/5/5 şemasına güncellendi, `odev01/prompts_v1.jsonl` kanonik kaynak ilan edildi, mapped dosyalar deprecate edildi, 0421 plan + patent 0450 referansları güncellendi. Kalan: Faz B (model yeniden eğitimi → 2.0.0) ve Faz D (doğrulama). | U2 AI Studio · Engineering |
| 1.2      | 2026-08-13| **Faz B + Faz D uygulandı:** intent/persona/funnel modelleri `prompts_v1.jsonl` üzerinden 8/5/5 sınıfla `class_weight='balanced'` ile yeniden eğitildi → sürüm 2.0.0 (topic 1.1.0). Doğrulama: per-sınıf F1=1.000, serving uçtan uca 8/8 uyumlu, `measure.intentWeights` yeni etiketlerde scale uyguluyor (`TestIntentWeights_NewIntentScale`), `/v1/predict` model_version=2.0.0. Rapor: `ml/data/8intent_f1_report.md`. | U2 AI Studio · Engineering |
| 1.3      | 2026-08-13| **CI bağlantısı (Faz D ek):** `ml-eval` job'ında `geolens.eval.main` artık 8-intent modeli per-sınıf F1 eşiğiyle değerlendiriyor (`ML_PROMPT_PER_CLASS_F1`, varsayılan 0.85) — tek sınıf çökmesi veya taksonomi uyuşmazlığı CI'yı kırar; `ML_REQUIRE_MODELS=1` ile sıkı mod. | U2 AI Studio · Engineering |
| 1.4      | 2026-08-13| Açık kararlar kapatıldı: #2 (persona/funnel 5 sınıf) Faz B'de uygulandı, #3 (sentetik ters haritalama) kapsam dışı bırakıldı, #4 (sürüm 2.0.0/1.1.0) onaylandı. | U2 AI Studio · Engineering |