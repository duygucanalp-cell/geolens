-- 002_measure.sql
-- Ölçüm şeması: measurement_jobs, raw_responses, scores

CREATE SCHEMA IF NOT EXISTS measure;

-- ============================================================================
-- BC3: Measure
-- ============================================================================

CREATE TABLE measure.measurement_jobs (
    id              TEXT PRIMARY KEY,          -- ULID
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id),
    workspace_id    TEXT NOT NULL REFERENCES config.workspaces(id),
    brand_id        TEXT NOT NULL REFERENCES config.brands(id),
    panel_id        TEXT,                      -- İzleme paneli (opsiyonel, manuel ölçümde boş)
    engine_name     TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'pending', -- pending, running, completed, failed
    prompt_text     TEXT NOT NULL,
    sample_count    INT NOT NULL DEFAULT 3,     -- n=3 (D-30)
    idempotency_key TEXT NOT NULL UNIQUE,
    error_message   TEXT,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE measure.raw_responses (
    id              TEXT PRIMARY KEY,          -- ULID
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id),
    job_id          TEXT NOT NULL REFERENCES measure.measurement_jobs(id),
    engine_name     TEXT NOT NULL,
    raw_body        TEXT NOT NULL,
    content_text    TEXT,
    s3_ref          TEXT,
    engine_meta     JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE measure.scores (
    id              TEXT PRIMARY KEY,          -- ULID
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id),
    workspace_id    TEXT NOT NULL REFERENCES config.workspaces(id),
    brand_id        TEXT NOT NULL REFERENCES config.brands(id),
    panel_id        TEXT,
    calculation_run_id TEXT NOT NULL,
    value           DOUBLE PRECISION NOT NULL,
    ci_low          DOUBLE PRECISION,
    ci_high         DOUBLE PRECISION,
    fidelity_label  TEXT NOT NULL,
    engine_breakdown JSONB,                   -- Motor bazında kırılım: {"chatgpt": 75.0, ...}
    panel_version   TEXT NOT NULL DEFAULT '1.0',
    freshness_at    TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE measure.calculation_runs (
    id              TEXT PRIMARY KEY,          -- ULID
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id),
    panel_id        TEXT,
    algorithm_version TEXT NOT NULL DEFAULT '1.0',
    component_values JSONB NOT NULL,           -- {"presence_share": 35.0, ...}
    input_snapshot  JSONB,                     -- Hesap girdilerinin anlık görüntüsü
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================================
-- Row-Level Security
-- ============================================================================

ALTER TABLE measure.measurement_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE measure.raw_responses ENABLE ROW LEVEL SECURITY;
ALTER TABLE measure.scores ENABLE ROW LEVEL SECURITY;
ALTER TABLE measure.calculation_runs ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON measure.measurement_jobs
    USING (tenant_id = identity.get_tenant_id());

CREATE POLICY tenant_isolation ON measure.raw_responses
    USING (tenant_id = identity.get_tenant_id());

CREATE POLICY tenant_isolation ON measure.scores
    USING (tenant_id = identity.get_tenant_id());

CREATE POLICY tenant_isolation ON measure.calculation_runs
    USING (tenant_id = identity.get_tenant_id());

-- ============================================================================
-- Indexes
-- ============================================================================

CREATE INDEX idx_mj_tenant_status ON measure.measurement_jobs(tenant_id, status);
CREATE INDEX idx_mj_idempotency ON measure.measurement_jobs(idempotency_key);
CREATE INDEX idx_mj_brand ON measure.measurement_jobs(brand_id);
CREATE INDEX idx_rr_job ON measure.raw_responses(job_id);
CREATE INDEX idx_scores_tenant_brand ON measure.scores(tenant_id, brand_id);
CREATE INDEX idx_scores_freshness ON measure.scores(tenant_id, freshness_at DESC);
CREATE INDEX idx_cr_panel ON measure.calculation_runs(tenant_id, panel_id);
