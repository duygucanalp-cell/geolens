-- 031_model_benchmarks.sql
-- R10: Model Benchmark — AI model/engine performans karşılaştırmaları

CREATE SCHEMA IF NOT EXISTS benchmark;

CREATE TABLE IF NOT EXISTS benchmark.models (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    model_name      TEXT NOT NULL DEFAULT '',
    engine_name     TEXT NOT NULL DEFAULT '',
    category        TEXT NOT NULL DEFAULT 'llm', -- llm, embedding, vision, etc.
    accuracy_score  DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    latency_ms      INTEGER NOT NULL DEFAULT 0,
    cost_per_request DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    tokens_per_second DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    response_quality DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    citation_rate   DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    details         JSONB NOT NULL DEFAULT '{}',
    tested_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_benchmark_models_tenant ON benchmark.models(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_benchmark_models_engine ON benchmark.models(engine_name);
