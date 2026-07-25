# ADR-007 Ek · Dilim 2 Çıkış Kapısı Doğrulama Kontrol Listesi

| Alan | Değer |
|------|-------|
| Doküman ID | adr/0007-ek-checklist |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 24 Temmuz 2026 |

---

## Kriter: Kullanıcı panelinde üç motor (Perplexity + ChatGPT + Gemini) için ayrı ayrı skor görünür. Site denetimi çalıştırılır ve bulgular saniyeler içinde panoda listelenir. Korelasyon zinciri her motor için ayrı izlenebilir.

| # | Kriter | Durum | Not |
|:-:|--------|:-----:|-----|
| 1 | `engine/gemini/adapter.go` — Gemini API adaptörü (Google Search grounding) | ✅ | `engine/gemini/adapter.go` — Execute, parseResponse, groundingAttributions parsing |
| 2 | `engine/gemini/adapter_test.go` — Gemini adaptör testleri | ✅ | 9 test: Name, Tier, WithContext, parseResponse (success, grounding, empty, invalidJSON), mockResponse, mock Execute |
| 3 | Gemini engine registry'e kayıtlı (api/worker/scheduler) | ✅ | `cmd/api/main.go`, `cmd/worker/main.go`, `cmd/scheduler/main.go` |
| 4 | `GEMINI_API_KEY` config/env eklenmiş | ✅ | `internal/config/config.go` |
| 5 | `engine/chatgpt/adapter.go` — ChatGPT adaptörü | ✅ | Dilim 2'de oluşturuldu, mock mod, annotation citation parsing |
| 6 | `engine/chatgpt/adapter_test.go` — ChatGPT testleri | ✅ | 11 test: Name, Tier, WithContext, parseResponse (success, annotations, empty, invalidJSON), mockResponse, mock Execute |
| 7 | `CHATGPT_API_KEY` config/env eklenmiş | ✅ | `internal/config/config.go` |
| 8 | 3 motor da `engines.List()`'de dönüyor | ✅ | Scheduler log'u `[perplexity chatgpt gemini]` gösteriyor |
| 9 | `internal/audit/engine.go` — Audit domain tipleri | ✅ | AuditResult, RobotsTxtCheck, BotAccessCheck, SSRCheck, SSRFCheck, Issue, AICrawler |
| 10 | `internal/audit/service.go` — Audit servis implementasyonu | ✅ | checkRobotsTxt, checkBotAccess, checkSSR, checkSSRFProtection, computeOverallScore |
| 11 | `internal/audit/handler.go` — Audit HTTP handler | ✅ | `POST /v1/workspaces/{ws}/audit` — body'den brand_id + website_url alır |
| 12 | Audit API route kayıtlı | ✅ | `cmd/api/main.go` — `r.Post("/audit", auditHandler.RunAudit)` |
| 13 | `GET /v1/workspaces/{ws}/scores` — ci_low, ci_high, engine_breakdown döner | ✅ | `internal/measure/handler.go` — scoreRow 9 alan döndürüyor |
| 14 | Engine breakdown DB'ye kaydedilir | ✅ | `internal/measure/service.go` — `string(engineBreakdownJSON)` ile INSERT |
| 15 | `engine.RawSaver` ortak arayüz | ✅ | `engine/registry.go` — `type RawSaver interface` |
| 16 | RawSaver consolidation: api/worker tek değişken | ✅ | `var saver engine.RawSaver` — her iki main.go'da |
| 17 | Perplexity + ChatGPT + Gemini aynı RawSaver'ı kullanır | ✅ | `NewAdapter(cfg.Key, saver)` — tüm adapter'lar |
| 18 | `normalizeURL` hatası düzeltildi | ✅ | `parts[2]` kullanılıyor (eskiden `parts[1]`) |
| 19 | TypeScript derlemesi hatasız | ✅ | `npx tsc --noEmit` — 0 hata |
| 20 | TrendChart Recharts ile yenilendi | ✅ | `web/src/components/TrendChart.tsx` — ComposedChart + Area CI bandı |
| 21 | EngineComparison bileşeni | ✅ | `web/src/components/EngineComparison.tsx` — BarChart, renk kodlu motorlar |
| 22 | AuditPanel bileşeni | ✅ | `web/src/components/AuditPanel.tsx` — brand select, score, check cards, issues |
| 23 | ScoreDashboard tabs (scores/audit) + engine filter + panel select | ✅ | `web/src/components/ScoreDashboard.tsx` — `activeTab`, `filterEngine`, `selectedPanel` |
| 24 | Docker Compose demo güncellendi | ✅ | `deploy/docker-compose.demo.yml` — API key'ler eklendi |
| 25 | API üzerinden 3 motorlu ölçüm testi | ✅ | curl: `POST /v1/workspaces/WS01/measurements` — `["perplexity","chatgpt","gemini"]` |
| 26 | Skor API'den dönüyor | ✅ | curl: `GET /v1/workspaces/WS01/scores` — value, ci_low, ci_high, brand_id |
| 27 | Go build | ✅ | `go build ./...` |
| 28 | Go vet | ✅ | `go vet ./...` |
| 29 | Go test | ✅ | 8 paket, tüm testler geçiyor |
| 30 | Docker build (api + worker + web) | ✅ | `docker compose build` |

## Özet

| Kategori | Toplam | ✅ |
|----------|:------:|:--:|
| Gemini adaptörü | 4 | 4 |
| ChatGPT adaptörü | 3 | 3 |
| 3 motor entegrasyonu | 1 | 1 |
| Site Denetim (backend) | 4 | 4 |
| API uçları | 2 | 2 |
| RawSaver consolidation | 3 | 3 |
| Web UI — TrendChart | 1 | 1 |
| Web UI — EngineComparison | 1 | 1 |
| Web UI — AuditPanel | 1 | 1 |
| Web UI — ScoreDashboard | 2 | 2 |
| Demo ortamı | 1 | 1 |
| Doğrulama & Kalite | 7 | 7 |
| **Toplam** | **30** | **30** |

## Sonuç

✅ **Dilim 2 çıkış kapısı kriterleri sağlandı.** Tüm 30 madde yeşil. Üç motor (Perplexity + ChatGPT + Gemini) kayıtlı ve çalışıyor. Site denetim API'si ve web UI bileşenleri hazır. Docker imajları build edilmiş ve API testleri geçmiştir.
