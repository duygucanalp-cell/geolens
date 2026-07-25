# 0310 · Domain Güvenlik (RLS, IAM, Sır Yönetimi)

| Alan | Değer |
|---|---|
| Doküman ID | 0310 |
| Proje | GeoLens Platform |
| Versiyon | 1.0 |
| Durum | Review |
| Sahip | U2 AI Studio · Engineering |
| Tarih | 25 Temmuz 2026 |
| İlişkili | 0507, 0508, ADR-004, ADR-010 |

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

| Şema | Tablolar | RLS |
|:----:|----------|:---:|
| identity | users, tenants, workspaces | ✅ |
| config | brands, panels, prompt_sets | ✅ |
| measure | scores, calculation_runs, event_outbox | ✅ |
| governance | audit_log, audit_results, rate_limit_buckets | ✅ |
| delivery | notification_settings | ✅ |

---

## 3. IAM (Identity & Access Management)

### 3.1 RBAC Rolleri

| Rol | Yetkiler |
|:---:|----------|
| `admin` | Tam erişim — tüm CRUD, kullanıcı yönetimi, ayarlar |
| `editor` | Marka/panel CRUD, ölçüm tetikleme, rapor indirme |
| `viewer` | Salt okunur — skorlar, öneriler, paneller |

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

| Sır | Kaynak | Şifreli mi? |
|-----|--------|:-----------:|
| PERPLEXITY_API_KEY | `.env.secrets.enc` | ✅ (Age) |
| OPENAI_API_KEY | `.env.secrets.enc` | ✅ (Age) |
| GEMINI_API_KEY | `.env.secrets.enc` | ✅ (Age) |
| SENDGRID_API_KEY | `.env.secrets.enc` | ✅ (Age) |
| JWT_SECRET | `.env.secrets.enc` | ✅ (Age) |
| ENCRYPTION_KEY | SOPS Age key | ✅ (Dosya sistemi) |

Sırlar `docker/entrypoint.sh` ile container start'ında decrypt edilir.

---

## Changelog

| Versiyon | Tarih | Değişiklik |
|----------|-------|------------|
| 1.0 | 25.07.2026 | İlk yayın: RLS, IAM, RBAC roller, sır yönetimi |
