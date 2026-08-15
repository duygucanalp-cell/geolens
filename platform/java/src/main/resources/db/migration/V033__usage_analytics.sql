-- 033_usage_analytics.sql
-- R12: Usage Analytics — API kullanım metrikleri

CREATE SCHEMA IF NOT EXISTS usage;

CREATE TABLE IF NOT EXISTS usage.metrics (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    endpoint        TEXT NOT NULL DEFAULT '',
    method          TEXT NOT NULL DEFAULT 'GET',
    status_code     INTEGER NOT NULL DEFAULT 200,
    latency_ms      INTEGER NOT NULL DEFAULT 0,
    user_id         TEXT NOT NULL DEFAULT '',
    ip_address      TEXT NOT NULL DEFAULT '',
    user_agent      TEXT NOT NULL DEFAULT '',
    request_size    INTEGER NOT NULL DEFAULT 0,
    response_size   INTEGER NOT NULL DEFAULT 0,
    metadata        JSONB NOT NULL DEFAULT '{}',
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_usage_metrics_tenant ON usage.metrics(tenant_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_usage_metrics_endpoint ON usage.metrics(endpoint, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_usage_metrics_status ON usage.metrics(status_code);
