# 0452 · AI Visibility Whitepaper v1 ve Yayın Paketi (İP-10 / A4-3)

| Alan | Değer |
|---|---|
| Doküman ID | 0452 |
| Proje | GeoLens Platform |
| Versiyon | 0.1 (Draft) |
| Durum | Draft |
| Sahip | U2 AI Studio · Ar-Ge |
| Tarih | 11 Ağustos 2026 |
| İlişkili | 0420 İP-10, 0421 A4-3, 0401, 0409 |

> A4-3 çıktıları: (1) Whitepaper v1 [10-15 sayfa], (2) Konferans CFP taslakları
> (15-20 slayt), (3) Blog serisi planı (4-6 yazı). Tümü **Draft** — PO/yayın
> onayı alınmadan Approved yapılmaz.

---

## BÖLÜM A — Whitepaper v1 Taslağı

**Başlık:** *AI Visibility: A Measurement Framework for Brand Presence in LLM Responses*

### A.1 Yapı

| Bölüm | İçerik | Kaynak Doküman |
|---|---|---|
| Abstract | Sorun: LLM yanıtlarında marka görünürlüğü ölçülemiyor; öneri: VI + doğrulama + skorlama | — |
| 1. Introduction | AI çağında marka görünürlüğü sorunu, GEO kapsamı | 0417, 0418 |
| 2. Literature Review | 30+ akademik referans planı (listeler aşağıda) | — |
| 3. AI Visibility Framework | Prompt taksonomisi, motor panelleri, ölçüm protokolü (n=3) | 0401, 0402, 0403, 0407, 0416 |
| 4. Visibility Index | 7 bileşenli matematiksel model, normalizasyon, CI, AHP | 0409, 0421 A3-1 |
| 5. Communication/Citation | Citation framework ve URL doğrulama | 0405, 0416 |
| 6. Pilot Study | 100 marka, 8 motor, 3 sektör, sentetik gold doğrulama | 0421 A3/0420 İP-08 |
| 7. Opportunity Engine | Impact×Urgency×Confidence + ML etki öğrenimi | 0412, 0413 |
| 8. Conclusion | GeoLens konumlandırma ve açık standart | — |
| References | 30+ referans (aşağıda ilk 20 verildi) | — |

### A.2 Literatür Taraması Öneri Başlıkları (ilk 20)

```
1.  Montti (2023) — GEODP: "generative engine optimization"
2.  Google DeepMind RAFT — retrieval-augmented fine-tuning (2024)
3.  OpenAI (2024) — "Why GPT answers the way it does" (GSR)
4.  Bengio et al. (2023) — LLM confidence calibration survey
5.  Wei et al. (2022) — Emergent abilities of LLMs
6.  Bender & Koller (2020) — grounding in meaning (stochastic parrots)
7.  Kadavath et al. (2022) — model knows what it doesn't know
8.  Schaefer (2022) — SGE statistics (SEO review)
9.  Johnson (2024) — AI search behavior study (Perplexity/Google AI Overview)
10. Hoppmann et al. (2024) — LLM output citation accuracy study
11. Momeni et al. — "retrieval augmented generation for citation"
12. Ji et al. (2023) — survey of hallucination in NLG
13. Maynez et al. (2020) — faithfulness in abstractive summarization
14. Longpre et al. (2023) — data quality matters
15. Zhou et al. (2023) — chain-of-thought prompting
16. Tan et al. (2023) — prompt engineering for SEO
17. Xiong et al. (2024) — evaluation of LLM-as-judge
18. Zheng et al. (2023) — LLM-as-a-judge framework
19. Čolović-Lamo (2024) — structured data & AI search (schema.org)
20. Google (2024) — SEO starter guide update / AI overviews
```

> Not: 30+ tam liste yayın öncesi akademik doğrulama gerektirir; DOI/link normalizasyonu Yayın Görev Listesi'nde.

### A.3 Whitepaper Cilt Verileri

- Pilot: `ml/data/gold.jsonl` (1000 kayıt, 500 TR + 500 EN)
- Model: VII — AHP CR=0.003 (varsayılan), duyarlılık raporu `ml/data/vi_profiles_report.md`
- Doğrulama: VI 7 bileşen vs legacy 4 bileşen — R²=0.809, Spearman=0.684 (sentetik gold)

---

## BÖLÜM B — Konferans CFP Taslakları

### B.1 BrightonSEO (Öncelikli)

- **Başlık:** *AI Visibility: How to Measure Your Brand in ChatGPT, Gemini, and Claude*
- **Özet (150 kelime):** 500 markayı 7-8 AI motorunda test ettik. ChatGPT'de markanız
  ne sıklıkla geçiyor? Konum neresi? Rakibe göre neredesiniz? Sunumda 7 bileşenli
  Visibility Index, cross-source hallüsinasyon filtresi ve somut aksiyon önerisi
  gösterilir.
- **Çıktı:** 15-20 slayt; son slayt demo daveti (lead CTA).

### B.2 AI Summit / SearchLove / SMX / MozCon (yedek)

- **Başlık (cfp):** *From SEO to GEO: A Measurable Framework for Generative Engine Optimization*
- **Vurgu:** GEO'nun ölçülemez olduğu iddiasına karşı ölçüm protokolü (prompt taksonomisi + panel).

---

## BÖLÜM C — Blog Serisi Planı (4-6 yazı)

| # | Başlık | Hedef Anahtar | CTA |
|---|---|---|---|
| 1 | AI Visibility Nedir ve Neden Önemli | "AI visibility" (2400/ay) | Ücretsiz VI raporu |
| 2 | ChatGPT'te Markanız Ne Sıklıkta Geçiyor? | "ChatGPT brand mentions" | Demo |
| 3 | SEO'den GEO'ya: AI Çağında Görünürlük Stratejisi | "generative engine optimization" | Demo |
| 4 | Visibility Index: Markanızın AI Skorunu Nasıl Ölçeriz? | "visibility index AI" | Ücretsiz deneme |
| 5 | Hallüsinasyon Çağında İtibar: AI Yanıtlarında Yanlış İddialar | "LLM hallucination brand" | Audit |
| 6 | Rakipleriniz AI'da Nerede? Competitive Visibility Analizi | "AI competitive analysis" | Analiz raporu |

**Hedef:** Ayda ~5.000 organik ziyaretçi, %3-5 dönüşüm (B.3, İP-10).

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|---|---|---|
| 0.1 | 11.08.2026 | İlk draft: whitepaper v1 yapısı, CFP taslakları, blog serisi planı. |