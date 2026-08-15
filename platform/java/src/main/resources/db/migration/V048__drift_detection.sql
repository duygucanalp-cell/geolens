-- 048_drift_detection.sql
-- R17: Drift Detection — metrik/model sapması izleme ve uyarılar

CREATE SCHEMA IF NOT EXISTS drift;

CREATE TABLE IF NOT EXISTS drift.observations (
    id            TEXT PRIMARY KEY DEFAULT gen_ulid(),
    tenant_id     TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    entity_id     TEXT NOT NULL DEFAULT '', -- model/brand/panel kimliği
    entity_name   TEXT NOT NULL DEFAULT '',
    metric        TEXT NOT NULL DEFAULT '', -- örn. visibility_score, refusal_rate
    value         NUMERIC(12,4) NOT NULL,
    window_start  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_drift_obs_tenant ON drift.observations(tenant_id, entity_id, metric, window_start DESC);

CREATE TABLE IF NOT EXISTS drift.alerts (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    entity_id       TEXT NOT NULL DEFAULT '',
    entity_name     TEXT NOT NULL DEFAULT '',
    metric          TEXT NOT NULL DEFAULT '',
    drift_score     NUMERIC(5,2) NOT NULL DEFAULT 0, -- 0-100
    severity        TEXT NOT NULL DEFAULT 'info',    -- info, warning, critical
    reference_mean  NUMERIC(12,4) NOT NULL DEFAULT 0,
    current_mean    NUMERIC(12,4) NOT NULL DEFAULT 0,
    delta           NUMERIC(12,4) NOT NULL DEFAULT 0,
    detail          TEXT NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_drift_alerts_tenant ON drift.alerts(tenant_id, created_at DESC);
