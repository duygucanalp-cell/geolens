-- GeoLens recommendation spike — entegrasyon test şeması (minimal).
-- Gerçek üretim şeması platform/migrations/*.sql dosyalarıdır; bu dosya yalnızca
-- DAO sorgularının doğrulanması için gereken tablo alt kümesini kurar.

CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS config;
CREATE SCHEMA IF NOT EXISTS measure;
CREATE SCHEMA IF NOT EXISTS governance;
CREATE SCHEMA IF NOT EXISTS recommendation;

CREATE TABLE identity.tenants (id TEXT PRIMARY KEY);

CREATE TABLE config.workspaces (
    id         TEXT PRIMARY KEY,
    tenant_id  TEXT NOT NULL REFERENCES identity.tenants(id)
);

CREATE TABLE config.brands (
    id           TEXT PRIMARY KEY,
    workspace_id TEXT NOT NULL REFERENCES config.workspaces(id),
    tenant_id    TEXT NOT NULL REFERENCES identity.tenants(id),
    name         TEXT NOT NULL,
    website_url  TEXT NOT NULL DEFAULT '',
    is_active    BOOLEAN NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE measure.scores (
    id               TEXT PRIMARY KEY,
    tenant_id        TEXT NOT NULL REFERENCES identity.tenants(id),
    workspace_id     TEXT NOT NULL REFERENCES config.workspaces(id),
    brand_id         TEXT NOT NULL REFERENCES config.brands(id),
    value            DOUBLE PRECISION NOT NULL,
    fidelity_label   TEXT NOT NULL DEFAULT 'full',
    engine_breakdown JSONB,
    freshness_at     TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE governance.audit_results (
    id            TEXT PRIMARY KEY,
    brand_id      TEXT NOT NULL REFERENCES config.brands(id),
    workspace_id  TEXT NOT NULL REFERENCES config.workspaces(id),
    tenant_id     TEXT NOT NULL REFERENCES identity.tenants(id),
    overall_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    robots_txt    JSONB,
    bot_access    JSONB,
    ssr           JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE recommendation.results (
    id           TEXT PRIMARY KEY,
    brand_id     TEXT NOT NULL REFERENCES config.brands(id),
    workspace_id TEXT NOT NULL,
    tenant_id    TEXT NOT NULL,
    category     TEXT NOT NULL,
    severity     TEXT NOT NULL,
    title        TEXT NOT NULL,
    detail       TEXT NOT NULL,
    action_url   TEXT,
    confidence   NUMERIC(5,2) NOT NULL DEFAULT 75.00,
    applied      BOOLEAN NOT NULL DEFAULT false,
    dismissed    BOOLEAN NOT NULL DEFAULT false,
    evidence     TEXT DEFAULT '',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ,
    applied_at   TIMESTAMPTZ,
    dismissed_at TIMESTAMPTZ
);

-- RLS (ADR-004): tenant bağlamı app.tenant_id session değişkeninden gelir.
CREATE OR REPLACE FUNCTION identity.get_tenant_id()
RETURNS TEXT AS $$
    SELECT current_setting('app.tenant_id', true);
$$ LANGUAGE SQL STABLE;

ALTER TABLE config.brands ENABLE ROW LEVEL SECURITY;
ALTER TABLE measure.scores ENABLE ROW LEVEL SECURITY;
ALTER TABLE governance.audit_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE recommendation.results ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON config.brands
    USING (tenant_id = identity.get_tenant_id());
CREATE POLICY tenant_isolation ON measure.scores
    USING (tenant_id = identity.get_tenant_id());
CREATE POLICY tenant_isolation ON governance.audit_results
    USING (tenant_id = identity.get_tenant_id());
CREATE POLICY tenant_isolation ON recommendation.results
    USING (tenant_id = identity.get_tenant_id());