-- 024_guardrails.sql
-- R3: Runtime Guardrails — prompt/response değerlendirme kuralları

CREATE SCHEMA IF NOT EXISTS guardrail;

CREATE TYPE guardrail.rule_category AS ENUM ('prompt_injection', 'pii_leakage', 'toxic_output', 'hallucination', 'custom');

CREATE TABLE IF NOT EXISTS guardrail.rules (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    name            TEXT NOT NULL,
    category        guardrail.rule_category NOT NULL,
    pattern         TEXT NOT NULL DEFAULT '', -- regex or keyword pattern
    action          TEXT NOT NULL DEFAULT 'block', -- block, flag, log
    severity        TEXT NOT NULL DEFAULT 'high', -- low, medium, high, critical
    enabled         BOOLEAN NOT NULL DEFAULT true,
    metadata        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_guardrail_rules_tenant ON guardrail.rules(tenant_id, category);

CREATE TABLE IF NOT EXISTS guardrail.evaluations (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    rule_id         TEXT NOT NULL REFERENCES guardrail.rules(id) ON DELETE CASCADE,
    prompt          TEXT NOT NULL DEFAULT '',
    response        TEXT NOT NULL DEFAULT '',
    matched         BOOLEAN NOT NULL DEFAULT false,
    action_taken    TEXT NOT NULL DEFAULT 'none', -- none, block, flag, log
    duration_ms     INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_guardrail_eval_tenant ON guardrail.evaluations(tenant_id, created_at DESC);
