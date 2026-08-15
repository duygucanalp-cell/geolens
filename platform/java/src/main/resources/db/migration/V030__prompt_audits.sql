-- 030_prompt_audits.sql
-- R9: Prompt Audit — prompt kalite ve uygunluk denetimleri

CREATE SCHEMA IF NOT EXISTS prompt;

CREATE TYPE prompt.audit_status AS ENUM ('passed', 'flagged', 'failed');

CREATE TABLE IF NOT EXISTS prompt.audits (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    prompt_id       TEXT NOT NULL DEFAULT '',
    prompt_text     TEXT NOT NULL DEFAULT '',
    engine_name     TEXT NOT NULL DEFAULT '',
    status          prompt.audit_status NOT NULL DEFAULT 'passed',
    score           DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    token_count     INTEGER NOT NULL DEFAULT 0,
    latency_ms      INTEGER NOT NULL DEFAULT 0,
    issues          JSONB NOT NULL DEFAULT '[]',
    metadata        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_prompt_audits_tenant ON prompt.audits(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_prompt_audits_status ON prompt.audits(tenant_id, status);
