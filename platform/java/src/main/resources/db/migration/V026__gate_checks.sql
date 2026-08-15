-- 026_gate_checks.sql
-- R6: CI/CD Governance Gate — check history persistence

CREATE SCHEMA IF NOT EXISTS gate;

CREATE TABLE IF NOT EXISTS gate.checks (
    id              TEXT PRIMARY KEY,
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    entity_id       TEXT NOT NULL,
    entity_type     TEXT NOT NULL DEFAULT '',
    target_env      TEXT NOT NULL DEFAULT '',
    version         TEXT NOT NULL DEFAULT '',
    decision        TEXT NOT NULL DEFAULT 'blocked',
    passed_checks   INT NOT NULL DEFAULT 0,
    total_checks    INT NOT NULL DEFAULT 0,
    check_details   JSONB NOT NULL DEFAULT '[]',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_gate_checks_tenant ON gate.checks(tenant_id, entity_id);
CREATE INDEX IF NOT EXISTS idx_gate_checks_decision ON gate.checks(tenant_id, decision);
