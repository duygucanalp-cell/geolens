-- 034_optimization.sql
-- R13: Optimization Recommendations — AI görünürlük optimizasyon önerileri

CREATE SCHEMA IF NOT EXISTS optimize;

CREATE TYPE optimize.impact_level AS ENUM ('high', 'medium', 'low');
CREATE TYPE optimize.recommendation_status AS ENUM ('pending', 'implemented', 'dismissed');

CREATE TABLE IF NOT EXISTS optimize.recommendations (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    category        TEXT NOT NULL DEFAULT '', -- prompt, engine, citation, brand, etc.
    title           TEXT NOT NULL DEFAULT '',
    description     TEXT NOT NULL DEFAULT '',
    impact          optimize.impact_level NOT NULL DEFAULT 'medium',
    effort          optimize.impact_level NOT NULL DEFAULT 'medium',
    status          optimize.recommendation_status NOT NULL DEFAULT 'pending',
    score_potential DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    metadata        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_optimize_recs_tenant ON optimize.recommendations(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_optimize_recs_category ON optimize.recommendations(tenant_id, category);
