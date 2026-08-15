-- 047_red_team.sql
-- R16: LLM Red Teaming — adversarial saldırı senaryolarıyla savunma testi

CREATE SCHEMA IF NOT EXISTS redteam;

CREATE TYPE redteam.attack_category AS ENUM (
    'prompt_injection', 'jailbreak', 'roleplay', 'encoding',
    'pii_extraction', 'misinformation', 'refusal_override', 'custom'
);

CREATE TABLE IF NOT EXISTS redteam.test_cases (
    id               TEXT PRIMARY KEY DEFAULT gen_ulid(),
    tenant_id        TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    name             TEXT NOT NULL,
    category         redteam.attack_category NOT NULL,
    payload          TEXT NOT NULL, -- saldırı prompt'u
    attack_vector    TEXT NOT NULL DEFAULT '',
    severity         TEXT NOT NULL DEFAULT 'high', -- low, medium, high, critical
    enabled          BOOLEAN NOT NULL DEFAULT true,
    metadata         JSONB NOT NULL DEFAULT '{}',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_redteam_cases_tenant ON redteam.test_cases(tenant_id, category);

CREATE TABLE IF NOT EXISTS redteam.runs (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    target_name     TEXT NOT NULL DEFAULT '', -- test edilen prompt/model adı
    total_cases     INTEGER NOT NULL DEFAULT 0,
    passed          INTEGER NOT NULL DEFAULT 0,
    failed          INTEGER NOT NULL DEFAULT 0,
    defense_score   NUMERIC(5,2) NOT NULL DEFAULT 0, -- 0-100
    status          TEXT NOT NULL DEFAULT 'completed',
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_redteam_runs_tenant ON redteam.runs(tenant_id, created_at DESC);

CREATE TABLE IF NOT EXISTS redteam.results (
    id          TEXT PRIMARY KEY DEFAULT gen_ulid(),
    run_id      TEXT NOT NULL REFERENCES redteam.runs(id) ON DELETE CASCADE,
    tenant_id   TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    case_id     TEXT NOT NULL REFERENCES redteam.test_cases(id) ON DELETE CASCADE,
    category    redteam.attack_category NOT NULL,
    payload     TEXT NOT NULL DEFAULT '',
    outcome     TEXT NOT NULL DEFAULT 'passed', -- passed, failed, inconclusive
    risk_level  TEXT NOT NULL DEFAULT 'low',    -- low, medium, high, critical
    matched_rule TEXT NOT NULL DEFAULT '',      -- saldırıyı yakalayan guardrail kuralı
    detail      TEXT NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_redteam_results_run ON redteam.results(run_id, created_at);
