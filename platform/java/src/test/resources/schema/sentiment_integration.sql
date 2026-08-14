-- Sentiment DAO integration test şeması (Testcontainers init).
-- Go migrasyonlarının (037_sentiment_hallucination.sql + bağımlı tablolar) minimal karşılığı.

CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS config;
CREATE SCHEMA IF NOT EXISTS measure;
CREATE SCHEMA IF NOT EXISTS analysis;

CREATE TABLE IF NOT EXISTS identity.tenants (id TEXT PRIMARY KEY);

CREATE TABLE IF NOT EXISTS config.workspaces (
    id TEXT PRIMARY KEY,
    tenant_id TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS config.brands (
    id TEXT PRIMARY KEY,
    workspace_id TEXT NOT NULL,
    tenant_id TEXT NOT NULL,
    name TEXT NOT NULL,
    website_url TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS measure.raw_responses (
    id TEXT PRIMARY KEY,
    tenant_id TEXT NOT NULL,
    brand_id TEXT NOT NULL REFERENCES config.brands(id),
    engine_name TEXT NOT NULL,
    content_text TEXT NOT NULL,
    prompt_text TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS analysis.sentiment_scores (
    id TEXT PRIMARY KEY,
    brand_id TEXT NOT NULL REFERENCES config.brands(id),
    engine_name TEXT NOT NULL,
    overall_sentiment REAL NOT NULL DEFAULT 0.5,
    positive_score REAL NOT NULL DEFAULT 0.0,
    neutral_score REAL NOT NULL DEFAULT 0.0,
    negative_score REAL NOT NULL DEFAULT 0.0,
    mention_count INT NOT NULL DEFAULT 0,
    tenant_id TEXT NOT NULL,
    workspace_id TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    analyzed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE analysis.sentiment_scores ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON analysis.sentiment_scores
    USING (tenant_id = current_setting('app.tenant_id')::text);

CREATE TABLE IF NOT EXISTS analysis.hallucination_flags (
    id TEXT PRIMARY KEY,
    brand_id TEXT NOT NULL REFERENCES config.brands(id),
    engine_name TEXT NOT NULL,
    hallucination_type TEXT NOT NULL,
    severity TEXT NOT NULL,
    description TEXT NOT NULL,
    confidence REAL NOT NULL DEFAULT 0.0,
    verified BOOLEAN,
    replay_id TEXT,
    tenant_id TEXT NOT NULL,
    workspace_id TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE analysis.hallucination_flags ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON analysis.hallucination_flags
    USING (tenant_id = current_setting('app.tenant_id')::text);