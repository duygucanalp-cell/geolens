-- 006_governance.sql
-- Governance: audit_log, usage_records, rate_limit_buckets

-- ============================================================================
-- Denetim Günlüğü (Audit Log)
-- ============================================================================
CREATE TABLE governance.audit_log (
    id              TEXT PRIMARY KEY,           -- ULID
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id),
    user_id         TEXT,                       -- İşlemi yapan kullanıcı (nullable: system events)
    event_type      TEXT NOT NULL,              -- brand.created, measurement.started, score.computed
    resource_type   TEXT NOT NULL,              -- brand, panel, measurement, score, user
    resource_id     TEXT,                       -- İlgili kaynağın ID'si
    action          TEXT NOT NULL,              -- create, read, update, delete, execute
    metadata        JSONB,                      -- Ek bağlam bilgisi
    ip_address      TEXT,
    user_agent      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================================
-- Kullanım Kayıtları (Usage Records)
-- ============================================================================
CREATE TABLE governance.usage_records (
    id              TEXT PRIMARY KEY,           -- ULID
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id),
    metric_name     TEXT NOT NULL,              -- engine_calls, api_requests, storage_bytes
    metric_value    BIGINT NOT NULL DEFAULT 1,
    resource_type   TEXT,
    resource_id     TEXT,
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================================
-- Hız Sınırı Kovaları (Rate Limit Buckets) — token bucket modeli
-- ============================================================================
CREATE TABLE governance.rate_limit_buckets (
    id              TEXT PRIMARY KEY,           -- ULID
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id),
    bucket_name     TEXT NOT NULL,              -- engine_calls_per_min, api_requests_per_hour
    max_tokens      BIGINT NOT NULL,            -- Maksimum token sayısı
    tokens_used     BIGINT NOT NULL DEFAULT 0,
    window_start    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, bucket_name, window_start)
);

-- ============================================================================
-- Indexes
-- ============================================================================
CREATE INDEX idx_audit_tenant_event ON governance.audit_log(tenant_id, created_at DESC);
CREATE INDEX idx_audit_resource ON governance.audit_log(resource_type, resource_id);
CREATE INDEX idx_audit_event_type ON governance.audit_log(tenant_id, event_type);
CREATE INDEX idx_usage_tenant_metric ON governance.usage_records(tenant_id, metric_name, recorded_at DESC);
CREATE INDEX idx_usage_recorded_at ON governance.usage_records(recorded_at);
CREATE INDEX idx_rlb_tenant_bucket ON governance.rate_limit_buckets(tenant_id, bucket_name);

-- ============================================================================
-- RLS
-- ============================================================================
ALTER TABLE governance.audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE governance.usage_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE governance.rate_limit_buckets ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON governance.audit_log
    USING (tenant_id = identity.get_tenant_id());

CREATE POLICY tenant_isolation ON governance.usage_records
    USING (tenant_id = identity.get_tenant_id());

CREATE POLICY tenant_isolation ON governance.rate_limit_buckets
    USING (tenant_id = identity.get_tenant_id());
