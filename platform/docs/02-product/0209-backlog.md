# 0209 · Ürün Beklentisi (Backlog)

| Alan | Değer |
|---|---|
| Doküman ID | 0209 |
| Proje | GeoLens Platform |
| Versiyon | 6.1 |
| Durum | Review |
| Sahip | U2 AI Studio · Product |
| Tarih | 03 Ağustos 2026 |
| İlişkili | 0205, 0206, 0207, 0210, ADR-001–014 |

---

## 1. Amaç

**Tüm maddeler tamamlanmıştır.** 53 madde (MVP + HT1 + HT2 + Teknik Borç + Kurumsal + Rekabetçi) kodlanmış, build ve testlerden geçmiştir.

|         Grup         | Toplam  | Tamam  | Kalan  |
|:--------------------:|:-------:|:------:|:------:|
|     MVP (M1–M12)     |   12    |   12   |   0    |
|     HT1 (H1–H12)     |   12    |   12   |   0    |
|     HT2 (T1–T5)      |    5    |   5    |   0    |
| Teknik Borç (X1–X10) |   10    |   10   |   0    |
|   Kurumsal (K1–K4)   |    4    |   4    |   0    |
|  Rekabetçi (R1–R17)  |   10    |   10   |   0    |
|      **Toplam**      | **53**  | **53** | **0**  |

---

## 2. Faz 4 — Rekabetçi Üstünlük (R1–R8)

| # | Madde | Paket | Ne Yapıldı |
|:-:|-------|-------|-----------|
| **R1** | AI Registry | `internal/registry/` | Entity CRUD (model/agent/application/dataset), lifecycle state, risk classification, risk assessment, Elasticsearch indeksleme |
| **R2** | Shadow AI Discovery | `internal/discovery/` | Bulut tarama (AWS/GCP), otomatik finding kaydı, bulunan kaynakları Registry'e otomatik ekleme |
| **R3** | Runtime Guardrails | `internal/guardrail/` | Kural CRUD (prompt_injection, pii_leakage, toxic_output, hallucination), prompt/response değerlendirme, block/flag/log aksiyonları, 8 varsayılan kural |
| **R4** | Policy Packs | `internal/policy/` | EU AI Act (7 kontrol), NIST AI RMF (7 kontrol), KVKK (6 kontrol), ISO 42001 (6 kontrol), compliance yüzdesi, pack apply/seed |
| **R5** | Bias/Fairness | `internal/bias/` | Demographic parity, equal opportunity, disparate impact (4/5th rule), fairness score hesaplama |
| **R6** | CI/CD Gate | `internal/gate/` | Governance check (risk assessment + policy + registry + docs), approved/flagged/blocked kararı |
| **R7** | Explainability | `internal/explain/` | SHAP-based feature importance, 5 feature attribution, base value + prediction, interpretation metni |
| **R8** | Agent Tracing | `internal/agent/` | Trace başlatma, multi-step workflow görüntüleme, 4 adımlı örnek trace (orchestrator → research → scoring → report) |
| **R16** | LLM Red Teaming | `internal/redteam/` | 8 saldırı kategorisi (jailbreak, prompt injection, roleplay, encoding, PII extraction, misinformation, refusal override, custom), senaryo CRUD + seed, hedef prompt'un aktif guardrail kurallarına karşı testi, savunma skoru (0-100), sonuç/geçmiş kaydı |
| **R17** | Drift Detection | `internal/drift/` | Gözlem kaydı (entity+metric+value), referans/güncel pencere karşılaştırması (Z-skoru bazlı 0-100 sapma), severity (info/warning/critical), otomatik uyarı kaydı, zaman serisi + varlık özeti |

### 2.1 Yeni Route'lar

```
# R1
GET    /v1/registry/entities               — Varlıkları listele (viewer)
GET    /v1/registry/entities/{id}          — Varlık detayı (viewer)
POST   /v1/registry/entities               — Varlık oluştur (editor)
PUT    /v1/registry/entities/{id}          — Varlık güncelle (editor)
DELETE /v1/registry/entities/{id}          — Varlık sil (editor)
POST   /v1/registry/entities/{id}/assess   — Risk değerlendir (editor)

# R2
POST   /v1/discovery/scan                  — Shadow AI taraması başlat (admin)
GET    /v1/discovery/scans/{scanId}        — Tarama sonuçları (admin)

# R3
GET    /v1/guardrails/rules                — Kuralları listele (viewer)
POST   /v1/guardrails/rules                — Kural oluştur (editor)
DELETE /v1/guardrails/rules/{ruleId}       — Kural sil (editor)
POST   /v1/guardrails/seed-defaults        — Varsayılan kuralları yükle (editor)
POST   /v1/guardrails/evaluate             — Prompt/response değerlendir (editor)

# R4
GET    /v1/policies/packs                  — Policy pack'leri listele (viewer)
GET    /v1/policies/packs/{id}/controls    — Pack kontrolleri (viewer)
POST   /v1/policies/packs/{id}/apply       — Pack'i uygula (admin)
PUT    /v1/policies/controls/{controlId}   — Kontrol güncelle (admin)
GET    /v1/policies/compliance/{entityId}  — Uyum yüzdesi (viewer)

# R5
POST   /v1/bias/evaluate                   — Bias testi çalıştır (editor)

# R6
POST   /v1/gate/check                      — Governance kontrolü (editor)
GET    /v1/gate/history/{entityId}          — Geçmiş kontroller (viewer)

# R7
POST   /v1/explain/{entityId}              — Model açıklaması (viewer)

# R8
POST   /v1/agents/traces                   — Trace başlat (viewer)
GET    /v1/agents/traces/{traceId}         — Trace detayı (viewer)
GET    /v1/agents/traces                   — Tüm traceler (viewer)

# R16
GET    /v1/redteam/cases                   — Saldırı senaryoları (viewer)
POST   /v1/redteam/cases                   — Senaryo oluştur (editor)
DELETE /v1/redteam/cases/{caseId}          — Senaryo sil (editor)
POST   /v1/redteam/runs                    — Savunma testi çalıştır (editor)
GET    /v1/redteam/runs                    — Test geçmişi (viewer)
GET    /v1/redteam/runs/{runId}            — Test detayı (viewer)
POST   /v1/redteam/seed-defaults           — Varsayılan senaryoları yükle (editor)

# R17
POST   /v1/drift/record                    — Gözlem kaydet (editor)
GET    /v1/drift/entities                  — İzlenen varlıklar (viewer)
GET    /v1/drift/analysis                  — Drift analizi (viewer)
GET    /v1/drift/observations              — Zaman serisi (viewer)
GET    /v1/drift/alerts                    — Drift uyarıları (viewer)
```

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Açıklama |
|:--------:|:-----:|----------|
| 1.0 | 26 Temmuz 2026 | İlk sürüm |
| 2.0 | 26 Temmuz 2026 | MVP+HT1 çıkışı |
| 3.0 | 26 Temmuz 2026 | H11/H12/T1/T4/T5 eklendi |
| 4.0 | 26 Temmuz 2026 | K1–K4 kurumsal tamam |
| 5.0 | 26 Temmuz 2026 | Faz 4 planı (R1–R8) |
| 6.0 | 26 Temmuz 2026 | **Tüm maddeler tamam — R1–R8 kodlandı** |
| 6.1 | 03 Ağustos 2026 | **Son rakip boşlukları kapandı:** R16 LLM Red Teaming + R17 Drift Detection eklendi (migration 047/048, `internal/redteam`, `internal/drift`, route'lar, RedTeamPanel + DriftPanel). 51 → 53 madde, 0210 v2.1 ile senkron. |
