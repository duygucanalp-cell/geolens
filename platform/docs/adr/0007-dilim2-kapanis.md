# ADR-007 · Dilim 2 (Ölçüm Tam) Kapanış Kaydı

| Alan | Değer |
|------|-------|
| ADR ID | ADR-007 |
| Durum | Kabul |
| Tarih | 24.07.2026 |
| Karar veren | TL |
| İlişkili | ADR-006, project-plan, engine/gemini, internal/audit |

---

## Bağlam

Dilim 2 (Ölçüm Tam, H5–H8) tamamlanmıştır. Bu ADR, Dilim 2 boyunca alınan kararları, gerçekleşen mimari sapmaları ve kapanış kriterlerini belgelemektedir. Dilim 1'den devralınan açık öğelerin durumu da bu kayıtta değerlendirilmiştir.

---

## Kapsam

Dilim 2 şu hipotezleri (H5–H8) kapsar:

| Hipotez | Açıklama | Durum |
|---------|----------|-------|
| H5 | ChatGPT adaptörü (OpenAI Responses API + web araması) + GA mekaniği iyileştirmeleri + pano güncellemeleri | ✓ Tamam |
| H6 | Gemini adaptörü (Gemini API + Google Search grounding) + 3 motor kaydı + Recharts trend + motor karşılaştırma | ✓ Tamam |
| H7 | Site denetim bileşeni (robots.txt, bot listesi, SSR, SSRF) + API uçları + denetim bulguları ekranı | ✓ Tamam |
| H8 | Uçtan uca test, hata ayıklama, demo senaryosu | ✓ Tamam |

---

## Kararlar

### K1: RawSaver ortak arayüzü (engine.RawSaver)

| Öngörü | Gerçekleşen |
|--------|-------------|
| Her adapter kendi RawSaver'ını tanımlar (perplexity.RawSaver, chatgpt.RawSaver) | `engine.RawSaver` ortak arayüze taşındı, tüm adapter'lar aynı tipi kullanır |

**Gerekçe:** Üçüncü adapter (gemini) eklenirken arayüz çoğaltması sürdürülemez hale gelmişti. `engine` paketinde tek bir `RawSaver` tanımı yapıldı, api/worker/scheduler `main.go`'da artık tek `var saver engine.RawSaver` değişkeni yeterli.

### K2: Gemini API seçimi

| Öngörü | Gerçekleşen |
|--------|-------------|
| Gemini 2.5 Flash | Gemini 3.5 Pro (`google_search` tool ile) |

**Gerekçe:** Google'ın standart `generateContent` endpoint'i kullanıldı. `v1beta/interactions` endpoint'i deneysel olduğu için tercih edilmedi. API key query parameter (`?key=`) ile iletilir.

### K3: Site denetim API'si ayrı servis

| Öngörü | Gerçekleşen |
|--------|-------------|
| Audit mantığı mevcut servislere gömülü | `internal/audit/` ayrı bir Go paketi + kendi Service interface'i |

**Gerekçe:** Site denetim, measure pipeline'ından bağımsız bir sorumluluktur. Ayrı bir paket olması test edilebilirliği ve bağımsız çalışmayı kolaylaştırır.

### K4: Engine breakdown DB'ye kaydediliyor (Dilim 1 hatası düzeltildi)

| Öngörü | Gerçekleşen |
|--------|-------------|
| `computeEngineBreakdown` hesaplanır ama DB'ye `"{}"` yazılırdı | Artık gerçek breakdown JSON'ı `engine_breakdown` kolonuna kaydediliyor |

**Gerekçe:** Dilim 1'de `CalculateScore` breakdown'ı `Score` struct'a atar ama INSERT'te `"{}"` geçerdi. Bu, web UI'da motor kırılımının görünmemesine yol açıyordu.

### K5: ListScores API genişletmesi

| Öngörü | Gerçekleşen |
|--------|-------------|
| `ListScores` sadece `id, brand_name, value, fidelity_label, freshness_at` döndürürdü | Artık `ci_low, ci_high, engine_breakdown, brand_id` de dönüyor |

**Gerekçe:** Web UI'da CI bandı ve engine breakdown göstermek için bu alanlar gerekliydi. API değişikliği geriye dönük uyumludur (yeni alanlar eklenmiştir, mevcut alanlar değişmemiştir).

### K6: Recharts'a geçiş (SVG'den)

| Öngörü | Gerçekleşen |
|--------|-------------|
| El yapımı SVG TrendChart | Recharts `ComposedChart` (Line + Area CI bandı) |

**Gerekçe:** Recharts daha iyi responsive davranış, tooltip, hover efektleri ve daha az bakım gerektirir. Zaten `package.json`'da bağımlılık olarak mevcuttu.

---

## Dilim 1'den Devralınan Açık Öğelerin Durumu

| # | Açık Öğe (Dilim 1 → Dilim 2) | Dilim 2'de Yapılan | Durum |
|:-:|-------------------------------|--------------------|:-----:|
| 1 | Gerçek ULID kütüphanesi (`oklog/ulid`) | `generateULID()` → `ulid.Make().String()` ile değiştirildi, `randomString()` kaldırıldı | ✅ |
| 2 | Kafka entegrasyonu | Redis Streams + outbox yeterli görüldü, ertelendi | ⏳ Dilim 3 |
| 3 | Perplexity API canlı test | Mock engine yeterli, gerçek API testi için API anahtarı gerekli | ⏳ Pilot |
| 4 | Kapsamlı entegrasyon testleri | Gözden geçirildi, mevcut testler yeterli | ⏳ Dilim 3 |
| 5 | oapi-codegen | OpenAPI'den kod üretimi için henüz ihtiyaç yok | ⏳ Dilim 3 |
| 6 | Multi-node deployment | Tek node demo yeterli | ⏳ Dilim 4 |
| 7 | Canlı monitoring (Prometheus + Grafana) | OTel temel altyapısı var | ⏳ Dilim 4 |
| 8 | Kapsamlı RBAC | Admin/member ayrımı yeterli | ⏳ Dilim 3 |

---

## Açık Öğeler (Dilim 3'e devreden)

1. **Kafka entegrasyonu** — ADR-004'te planlandı, Redis Streams yeterli
2. **Perplexity API canlı test** — Gerçek API anahtarı gerekli
3. **Kapsamlı RBAC** — Şu an sadece admin/member ayrımı
4. **oapi-codegen** — OpenAPI spesifikasyonundan Go kod üretimi
5. **Gemini groundingConfig dead code** — `adapter.go`'daki kullanılmayan `groundingConfig` struct'ı
6. **Audit birim testleri** — `computeOverallScore`, `normalizeURL`, `checkRobotsTxt` için test yok

---

## Mimari Bileşenler (Dilim 2 eklemeleri)

| Bileşen | Teknoloji | LOC (yaklaşık) |
|---------|-----------|----------------|
| ChatGPT adaptörü | Go + OpenAI Chat Completions API | 150+ |
| Gemini adaptörü | Go + Gemini generateContent API | 150+ |
| Site Denetim | Go (internal/audit/) | 250+ |
| Engine breakdown DB fix | Go (internal/measure/service.go) | ~5 satır değişiklik |
| ListScores API genişletme | Go (internal/measure/handler.go) | ~15 satır değişiklik |
| RawSaver consolidation | Go (engine/registry.go + adapters) | ~10 satır değişiklik |
| TrendChart → Recharts | TypeScript + Recharts | 80+ |
| EngineComparison | TypeScript + Recharts (BarChart) | 60+ |
| AuditPanel | TypeScript + CSS | 120+ |
| ScoreDashboard (tabs + filters) | TypeScript + CSS | 100+ |

---

## Çıkış Kapısı Kriterleri

| Kriter | Durum |
|--------|-------|
| 3 motor (Perplexity + ChatGPT + Gemini) engine registry'de kayıtlı | ✓ |
| ChatGPT adaptörü çalışıyor (mock mod) | ✓ |
| Gemini adaptörü çalışıyor (mock mod) | ✓ |
| Ölçüm pipeline'ı 3 motor için de job üretir | ✓ |
| Skorlar 3 motor verisiyle hesaplanır | ✓ |
| Engine breakdown DB'ye kaydedilir ve API'den döner | ✓ |
| Web UI'da motor sekmeleri ve engine karşılaştırma görünür | ✓ |
| TrendChart Recharts ile gösterilir | ✓ |
| Site denetim API'si çalışır (POST /v1/workspaces/{ws}/audit) | ✓ |
| Denetim bulguları web UI'da görünür | ✓ |
| ListScores ci_low, ci_high, engine_breakdown döndürür | ✓ |
| tüm Go birim testleri geçer | ✓ |
| TypeScript derlemesi hatasız | ✓ |
| Docker imajları build edilir ve çalışır | ✓ |

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 24.07.2026 | İlk yayın: Dilim 2 kapanış kaydı, kararlar, açık öğeler, devralınan öğelerin durumu. |
