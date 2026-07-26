-- 025_discovery.sql
-- R2: Shadow AI Discovery — kaçak AI tespit taraması

CREATE SCHEMA IF NOT EXISTS discovery;

CREATE TYPE discovery.scan_status AS ENUM ('pending', 'running', 'completed', 'failed');

CREATE TABLE IF NOT EXISTS discovery.scans (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    scan_type       TEXT NOT NULL DEFAULT 'api', -- api, agent, manual
    status          discovery.scan_status NOT NULL DEFAULT 'pending',
    provider        TEXT NOT NULL DEFAULT '', -- aws, azure, gcp, all
    total_found     INTEGER NOT NULL DEFAULT 0,
    error_message   TEXT NOT NULL DEFAULT '',
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_discovery_scans_tenant ON discovery.scans(tenant_id, created_at DESC);

CREATE TABLE IF NOT EXISTS discovery.findings (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),
    scan_id         TEXT NOT NULL REFERENCES discovery.scans(id) ON DELETE CASCADE,
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    resource_type   TEXT NOT NULL DEFAULT '', -- lambda, sagemaker, aks, vertex_ai, etc.
    resource_name   TEXT NOT NULL DEFAULT '',
    resource_id     TEXT NOT NULL DEFAULT '',
    provider        TEXT NOT NULL DEFAULT '',
    region          TEXT NOT NULL DEFAULT '',
    risk_level      TEXT NOT NULL DEFAULT 'medium',
    details         JSONB NOT NULL DEFAULT '{}',
    discovered_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_discovery_findings_scan ON discovery.findings(scan_id);
CREATE INDEX IF NOT EXISTS idx_discovery_findings_tenant ON discovery.findings(tenant_id);
