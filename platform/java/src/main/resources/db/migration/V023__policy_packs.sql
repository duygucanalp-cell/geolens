-- 023_policy_packs.sql
-- R4: Policy Packs — EU AI Act, NIST AI RMF, ISO 42001, KVKK hazır politikalar

CREATE SCHEMA IF NOT EXISTS policy;

CREATE TYPE policy.framework AS ENUM ('eu_ai_act', 'nist_ai_rmf', 'iso_42001', 'kvkk', 'custom');

CREATE TABLE IF NOT EXISTS policy.packs (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    name            TEXT NOT NULL,
    framework       policy.framework NOT NULL,
    description     TEXT NOT NULL DEFAULT '',
    version         TEXT NOT NULL DEFAULT '1.0.0',
    enabled         BOOLEAN NOT NULL DEFAULT true,
    applied_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, framework)
);

CREATE TABLE IF NOT EXISTS policy.controls (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),
    pack_id         TEXT NOT NULL REFERENCES policy.packs(id) ON DELETE CASCADE,
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    control_id      TEXT NOT NULL, -- CC1, CC2, A.8.1, etc.
    title           TEXT NOT NULL,
    description     TEXT NOT NULL DEFAULT '',
    category        TEXT NOT NULL DEFAULT '',
    status          TEXT NOT NULL DEFAULT 'pending', -- pending, passed, failed, not_applicable
    evidence        TEXT NOT NULL DEFAULT '',
    due_date        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (pack_id, control_id)
);

CREATE INDEX IF NOT EXISTS idx_policy_packs_tenant ON policy.packs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_policy_controls_pack ON policy.controls(pack_id);
CREATE INDEX IF NOT EXISTS idx_policy_controls_status ON policy.controls(tenant_id, status);
