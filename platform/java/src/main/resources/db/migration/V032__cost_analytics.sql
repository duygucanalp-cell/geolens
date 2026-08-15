-- 032_cost_analytics.sql
-- R11: Cost Analytics — AI engine/model maliyet takibi

CREATE SCHEMA IF NOT EXISTS cost;

CREATE TYPE cost.operation_type AS ENUM ('measurement', 'evaluation', 'embedding', 'generation', 'other');

CREATE TABLE IF NOT EXISTS cost.entries (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    engine_name     TEXT NOT NULL DEFAULT '',
    model_name      TEXT NOT NULL DEFAULT '',
    operation       cost.operation_type NOT NULL DEFAULT 'other',
    token_count     INTEGER NOT NULL DEFAULT 0,
    input_tokens    INTEGER NOT NULL DEFAULT 0,
    output_tokens   INTEGER NOT NULL DEFAULT 0,
    cost_usd        DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    currency        TEXT NOT NULL DEFAULT 'USD',
    metadata        JSONB NOT NULL DEFAULT '{}',
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cost_entries_tenant ON cost.entries(tenant_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_cost_entries_engine ON cost.entries(engine_name, recorded_at DESC);
