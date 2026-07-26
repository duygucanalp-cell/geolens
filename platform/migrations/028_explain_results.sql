-- 028_explain_results.sql
-- R7: Explainability — SHAP-based model açıklama sonuçları

CREATE SCHEMA IF NOT EXISTS explain;

CREATE TABLE IF NOT EXISTS explain.results (
    id                TEXT PRIMARY KEY DEFAULT gen_ulid(),
    tenant_id         TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    entity_id         TEXT NOT NULL REFERENCES registry.entities(id) ON DELETE CASCADE,
    method            TEXT NOT NULL DEFAULT 'SHAP',
    base_value        DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    prediction        DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    feature_importance JSONB NOT NULL DEFAULT '{}',
    shap_values       JSONB NOT NULL DEFAULT '[]',
    interpretation    TEXT NOT NULL DEFAULT '',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_explain_results_tenant ON explain.results(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_explain_results_entity ON explain.results(entity_id);
