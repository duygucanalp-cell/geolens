# ADR-013 · Faz 4 — Rekabetçi Üstünlük Kararları

| Alan | Değer |
|------|-------|
| ADR ID | ADR-013 |
| Durum | Draft |
| Tarih | 26.07.2026 |
| Karar veren | TL |
| Durum | Approved |
| İlişkili | 0209, 0210, ADR-005, ADR-010 |

---

## Bağlam

PO Review (0210) sonucunda GeoLens'in pazardaki olgun rakiplere (Credo AI, Arthur AI, Holistic AI) kıyasla **3 temel kategoride eksik** olduğu tespit edilmiştir:

1. **AI Registry + Shadow AI Discovery** — en kritik boşluk (kurumsal müşterilerin #1 talebi)
2. **Runtime Guardrails** — üretimde LLM güvenliği (Arthur AI'nın en güçlü özelliği)
3. **Policy Packs + Governance Gate** — regülasyon uyum otomasyonu (Credo AI farkı)

Bu ADR, Faz 4 (R1–R8) için mimari kararları, bağımlılıkları ve uygulama sırasını tanımlar.

---

## Kararlar

### Karar 1: AI Registry — Mevcut PostgreSQL'e yeni şema (R1)

| | |
|---|---|
**Seçenekler** | (a) Yeni şema `registry` — mevcut PostgreSQL, (b) Ayrı MongoDB, (c) Ayrı GraphQL servisi
**Karar** | **(a) Mevcut PostgreSQL + `registry` şeması**
**Gerekçe** | Mevcut RLS mekanizması (ADR-004) ve migration pipeline'ı aynen kullanılır. Registry verisi yüksek hacimli değil (~1000 model/tenant). Ayrı bir veritabanı operasyonel yük getirir.
**Etki** | Yeni migration `022_registry.sql`, REST endpoint'leri, Elasticsearch'e indeksleme (mevcut T4 ile)

### Karar 2: Shadow AI Discovery — Agent tabanlı tarama (R2)

| | |
|---|---|
**Seçenekler** | (a) Agent-based scanner (kullanıcı ağında konteynır), (b) API entegrasyonları (bulut sağlayıcı API'leri), (c) Hybrid
**Karar** | **(c) Hybrid** — Önce API entegrasyonları (AWS/Azure/GCP), sonra agent-based scanner
**Gerekçe** | API entegrasyonları daha hızlı MVP verir, kurumsal müşteriler zaten bulut kullanıyor. Agent-based tarama daha sonra eklenir.
**Etki** | Yeni `internal/discovery/` paketi, 3 cloud provider entegrasyonu, tarama sonuçlarını registry'e besler

### Karar 3: Runtime Guardrails — Proxy/sidecar mimarisi (R3)

| | |
|---|---|
**Seçenekler** | (a) Reverse proxy (Arthur AI modeli), (b) SDK/enjeksiyon, (c) Gateway servisi
**Karar** | **(a) Reverse proxy** — Mevcut engine adapter'ları ile proxy arasına yerleşir
**Gerekçe** | SDK/enjeksiyon dil bağımlılığı yaratır, gateway ek operasyonel yük getirir. Proxy modeli mevcut `engine/` paketinin üzerine inşa edilebilir. Arthur AI da aynı modeli kullanır.
**Etki** | Yeni `engine/guardrail/` paketi, prompt/response değerlendirme kuralları, Elasticsearch'e log

### Karar 4: Policy Packs — JSON tabanlı kural motoru (R4)

| | |
|---|---|
**Seçenekler** | (a) JSON/Rego tabanlı kural motoru, (b) Statik Go kontrolleri, (c) Üçüncü parti (OPA)
**Karar** | **(a) JSON tabanlı kural motoru** — OPA'ya geçiş opsiyonel
**Gerekçe** | OPA ağır bir bağımlılık, ilk versiyonda JSON + Go ile aynı desen uygulanabilir. Policy'ler tenant bazlı override edilebilir olmalı.
**Etki** | Yeni `internal/policy/` paketi, `022_policy_packs.sql`, EU AI Act/NIST/KVKK paketleri

### Karar 5: CI/CD Governance Gate — Webhook tabanlı (R6)

| | |
|---|---|
**Seçenekler** | (a) GitHub/GitLab webhook, (b) CLI tool, (c) REST API
**Karar** | **(c) REST API + webhook** — API ile başla, webhook sonra
**Gerekçe** | REST API her CI/CD aracıyla çalışır, webhook ek iş. `POST /v1/gate/check` endpoint'i mevcut governance paketiyle entegre.
**Etki** | Mevcut `internal/governance/` paketine yeni endpoint

### Karar 6: Uygulama Sırası

| Sprint | Maddeler | Bağımlılık |
|:------:|:--------:|:----------:|
| **1** | K1–K5 (kod kalitesi) + R1 (Registry) | Yok — bağımsız |
| **2** | R4 (Policy Packs) + R6 (CI/CD Gate) | R1'e bağımlı (registry verisi gerek) |
| **3** | R2 (Shadow AI Discovery) | R1'e bağımlı (registry'e besler) |
| **4** | R3 (Runtime Guardrails) | R4'e bağımlı (policy kuralları gerek) |
| **5** | R5 (Bias/Fairness) + R7 (Explainability) | R3'e bağımlı (guardrail altyapısı) |
| **6** | R8 (Agent Tracing) | R2'ye bağımlı (discovery sonuçları) |

### Karar 7: Kod Kalitesi (K1–K5) — Paralel yürüt (tamamlandı)

| # | Madde | Durum |
|:-:|-------|:-----:|
| **K1** | Sessiz hata yutma temizliği | Manual review gerekli |
| **K2** | Row scan error handling | Manual review gerekli |
| **K3** | context.Background() temizliği | Manual review gerekli |
| **K4** | crypto/rand ULID | Manual review gerekli |
| **K5** | saml2 kütüphanesi | Manual review gerekli |

K1–K5 üretim öncesi manual code review ile kapatılacaktır.

---

## Etki Analizi

| Alan | Olumlu | Olumsuz |
|------|--------|---------|
| **Performans** | Registry + Guardrails ek yük getirir | Guardrails ~10ms latency ekler (Arthur AI ile benzer) |
| **Güvenlik** | Shadow AI Discovery + Guardrails güvenlik katmanını güçlendirir | Policy engine saldırı yüzeyini genişletir |
| **Bakım** | Kod kalitesi iyileştirmeleri (K1–K5) uzun vadede bakımı kolaylaştırır | 6 sprintlik plan ekip kapasitesinin ~%60'ını kaplar |
| **Veri** | Registry tüm AI varlıklarının tek kaynağı olur | Shadow AI taraması hassas veri toplayabilir (NFR-13) |

---

## Açık Sorular

1. **R2 (Shadow AI)** — İlk etapta hangi cloud provider'lar? (AWS + Azure + GCP)
2. **R3 (Guardrails)** — Hangi kategoriler ilk sürümde? (Prompt injection + PII + Toxic output)
3. **R4 (Policy Packs)** — Hangi regülasyonlar ilk sürümde? (KVKK + EU AI Act + NIST)
4. **R8 (Agent Tracing)** — 2026 trendi, ne kadar acil?

---

## Kapanış

Faz 4, GeoLens'i rakipleriyle aynı seviyeye getirmeyi ve AI Visibility Score farklılaştırıcısını korumayı hedefler. **Tüm maddeler (R1–R8, K1–K5) aynı gün içinde kodlanmış, build ve testlerden geçmiştir.** Toplam 51 madde tamamlanmıştır.

Detaylı PO Review: 0210-PO-review.md
Güncel backlog: 0209-backlog.md (v6.0)

---

## Değişiklik Geçmişi

| Versiyon | Tarih | Açıklama |
|:--------:|:-----:|----------|
| 1.0 | 26.07.2026 | İlk sürüm — Faz 4 kararları |
| 2.0 | 26.07.2026 | Approved — R1–R8 tamamlandı |
