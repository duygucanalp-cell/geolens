# 0310 · Domain Güvenlik (RLS, IAM, Sır Yönetimi)

| Alan | Değer |
|---|---|
| Doküman ID | 0310 |
| Proje | GeoLens Platform |
| Versiyon | 1.1 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 04 Ağustos 2026 |
| İlişkili | 0507, 0508, ADR-004, ADR-010, 0302, 0305 |

---

## 1. Amaç

Bu doküman, GeoLens Platform'un domain katmanındaki güvenlik mekanizmalarını tanımlar. RLS politikaları, IAM yapısı ve sır yönetimini detaylandırır.

---

## 2. RLS Politikaları

Tüm tablolarda `tenant_id` kolonu bulunur. RLS politikaları `app.tenant_id` session değişkeni üzerinden çalışır:

```sql
-- Tüm tablolar için şablon
CREATE POLICY tenant_isolation ON {schema}.{table}
    USING (tenant_id = current_setting('app.tenant_id')::text);
```

### 2.1 RLS Kapsamı

`ENABLE ROW LEVEL SECURITY` + `tenant_isolation` politikası olan şemalar (migration gerçeği):

| Şema | Tablo Örnekleri | RLS |
|:----:|----------|:---:|
| identity | tenants, users, sessions, memberships, invitations, api_keys | ✅ |
| config | workspaces, brands, prompt_sets, panels, panel_brands, brand_competitors | ✅ |
| measure | measurement_jobs, raw_responses, scores, calculation_runs, citations, reports | ✅ |
| governance | audit_log, audit_results, rate_limit_buckets, usage_records, alert_rules | ✅ |
| delivery | notification_settings | ✅ |
| privacy | deletion_requests | ✅ |
| recommendation | results | ✅ |
| analysis | sentiment_scores, hallucination_flags | ✅ |
| replay / archive | conversation_snapshots, response_entries | ✅ |
| technical / content / competitive | bot_analyses, schema_analyses, gap_analyses, topic_clusters, gap_snapshots, gap_recommendations | ✅ |
| billing | invoices, stripe_customers | ✅ |

> **Faz 4 notu (v1.1):** BC13 (Billing) tabloları ADR-004 gereği RLS ile izole edilir. BC11/BC12 (AI Governance/Operations) şemaları — `registry`, `discovery`, `guardrail`, `policy`, `bias`, `gate`, `explain`, `agent`, `redteam`, `prompt`, `benchmark`, `cost`, `usage`, `optimize`, `version`, `incident`, `drift`, `seo` — RLS yerine **handler düzeyinde tenant WHERE** koşulu kullanır (bkz. 0305 §9.12). `public.event_outbox` platform seviyesindedir, RLS'e tabi değildir.

---

## 3. IAM (Identity & Access Management)

### 3.1 RBAC Rolleri

| Rol | Yetkiler |
|:---:|----------|
| `admin` | Tam erişim — tüm CRUD, kullanıcı yönetimi, ayarlar |
| `editor` | Marka/panel CRUD, ölçüm tetikleme, rapor indirme |
| `viewer` | Salt okunur — skorlar, öneriler, paneller |

Geçerli roller kodda `internal/auth` tarafından doğrulanır (`admin`, `editor`, `viewer`). Rol, her istekte DB'den taze okunur (`identity.users.role`) — JWT claim'i olsa dahi, rol değişikliği bir sonraki istekte geçerlidir (anında yetki iptali).

### 3.2 JWT Yapısı

```json
{
  "sub": "kullanici-ulid",
  "tenant_id": "tenant-ulid",
  "workspace_id": "ws-ulid",
  "role": "admin",
  "exp": 1700000000
}
```

---

## 4. Sır Yönetimi (SOPS+Age)

Gerçek env anahtarları `internal/config/config.go` içinden (`getEnv`): 

| Sır | Env Anahtarı | Şifreli mi? |
|-----|--------------|:-----------:|
| Perplexity API | `PERPLEXITY_API_KEY` | ✅ (Age) |
| ChatGPT API | `CHATGPT_API_KEY` | ✅ (Age) |
| Gemini API | `GEMINI_API_KEY` | ✅ (Age) |
| Claude API | `CLAUDE_API_KEY` | ✅ (Age) |
| Grok API | `GROK_API_KEY` | ✅ (Age) |
| Copilot API | `COPILOT_API_KEY` | ✅ (Age) |
| Mistral API | `MISTRAL_API_KEY` | ✅ (Age) |
| Stripe API + Webhook | `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET` | ✅ (Age) |
| e-Fatura modu | `EFATURA_MODE` (`mock`/`gib`) | ✅ (Age) |
| Elasticsearch API | `ELASTICSEARCH_API_KEY` | ✅ (Age) |
| SendGrid | `SENDGRID_API_KEY` | ✅ (Age) |
| JWT | `JWT_SECRET` | ✅ (Age) |
| MinIO/S3 | `S3_ACCESS_KEY`, `S3_SECRET_KEY` | ✅ (Age) |
| Google OAuth | `GOOGLE_OAUTH_CLIENT_SECRET` | ✅ (Age) |

Sırlar `docker/.env.secrets.enc` dosyasında Age ile şifrelenir; `docker/entrypoint.sh` container start'ında decrypt eder.

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 25.07.2026 | İlk yayın: RLS, IAM, RBAC roller, sır yönetimi |
| 1.1 | 04.08.2026 | **Faz 4 senkronu:** RLS kapsamı migration gerçeğiyle güncellendi (analysis, replay, archive, technical, content, competitive, billing şemaları eklendi). BC11/BC12 şemalarının RLS yerine handler WHERE kullandığı ve `public.event_outbox`'ın RLS'e tabi olmadığı not edildi (0305 §9.12 ile hizalı). RBAC bölümüne rolün her istekte DB'den taze okunduğu notu eklendi. Sır listesi `internal/config/config.go` gerçek env anahtarlarıyla yenilendi (7 motor, Stripe, e-Fatura, Elasticsearch, MinIO, Google OAuth). |
