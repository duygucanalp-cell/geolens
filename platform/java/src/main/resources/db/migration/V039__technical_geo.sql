-- 039_technical_geo.sql
-- Bu migration, 0417 Technical GEO dokümanı için technical schema'sını oluşturur.
-- Eklenen FR: FR-B6 (bot izleme), FR-B7 (schema analizi), FR-E7 (teknik GEO önerileri)

CREATE SCHEMA IF NOT EXISTS technical;

-- LLM Bot erişim analizleri (FR-B6)
CREATE TABLE technical.bot_analyses (
    id                TEXT PRIMARY KEY,  -- ULID
    brand_id          TEXT NOT NULL REFERENCES config.brands(id),
    bot_name          TEXT NOT NULL,     -- GPTBot, Google-Extended, PerplexityBot, vb.
    url               TEXT NOT NULL,
    is_blocked        BOOLEAN NOT NULL DEFAULT false,
    robots_txt_rule   TEXT NOT NULL DEFAULT 'Allow',  -- Allow / Disallow / NoRule
    ges_score         REAL NOT NULL DEFAULT 0.0,      -- Genel Erişim Skoru (0-100)
    tenant_id         TEXT NOT NULL,
    workspace_id      TEXT NOT NULL,
    analyzed_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE technical.bot_analyses ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON technical.bot_analyses
    USING (tenant_id = current_setting('app.tenant_id')::text);

CREATE INDEX idx_bot_brand ON technical.bot_analyses(brand_id, bot_name);
CREATE INDEX idx_bot_tenant ON technical.bot_analyses(tenant_id);

-- Schema.org kullanım analizleri (FR-B7)
CREATE TABLE technical.schema_analyses (
    id                TEXT PRIMARY KEY,  -- ULID
    brand_id          TEXT NOT NULL REFERENCES config.brands(id),
    schema_type       TEXT NOT NULL,     -- Product, FAQ, Organization, Article, vb.
    is_present        BOOLEAN NOT NULL DEFAULT false,
    schema_score      REAL NOT NULL DEFAULT 0.0,      -- 0-100
    recommendation    TEXT NOT NULL DEFAULT '',
    tenant_id         TEXT NOT NULL,
    workspace_id      TEXT NOT NULL,
    analyzed_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE technical.schema_analyses ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON technical.schema_analyses
    USING (tenant_id = current_setting('app.tenant_id')::text);

CREATE INDEX idx_schema_brand ON technical.schema_analyses(brand_id, schema_type);
CREATE INDEX idx_schema_tenant ON technical.schema_analyses(tenant_id);
