-- 027_bias_tests.sql
-- R5: Bias/Fairness — bias test sonuçları ve geçmiş

CREATE SCHEMA IF NOT EXISTS bias;

CREATE TYPE bias.metric_type AS ENUM ('demographic_parity', 'equal_opportunity', 'disparate_impact');

CREATE TABLE IF NOT EXISTS bias.tests (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    model_id        TEXT NOT NULL DEFAULT '',
    metric_type     bias.metric_type NOT NULL,
    fairness_score  DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    has_bias        BOOLEAN NOT NULL DEFAULT false,
    max_gap         DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    details         JSONB NOT NULL DEFAULT '{}',
    recommendations JSONB NOT NULL DEFAULT '[]',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_bias_tests_tenant ON bias.tests(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_bias_tests_model ON bias.tests(tenant_id, model_id);
