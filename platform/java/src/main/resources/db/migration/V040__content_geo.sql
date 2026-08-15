-- 040_content_geo.sql
-- Bu migration, 0418 Content GEO dokümanı için content schema'sını oluşturur.
-- Eklenen FR: FR-E5 (content gap), FR-E6 (GEO içerik önerileri)

CREATE SCHEMA IF NOT EXISTS content;

-- Content gap analizleri (FR-E5)
CREATE TABLE content.gap_analyses (
    id                TEXT PRIMARY KEY,  -- ULID
    brand_id          TEXT NOT NULL REFERENCES config.brands(id),
    gap_type          TEXT NOT NULL,     -- blog / product / faq / news / category / general
    gap_score         REAL NOT NULL DEFAULT 0.0,      -- 0.0 (kapalı) .. 1.0 (açık)
    description       TEXT NOT NULL,
    recommendation    TEXT NOT NULL,
    priority          TEXT NOT NULL DEFAULT 'medium',  -- high / medium / low
    tenant_id         TEXT NOT NULL,
    workspace_id      TEXT NOT NULL,
    analyzed_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE content.gap_analyses ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON content.gap_analyses
    USING (tenant_id = current_setting('app.tenant_id')::text);

CREATE INDEX idx_content_gap_brand ON content.gap_analyses(brand_id, gap_type);
CREATE INDEX idx_content_gap_tenant ON content.gap_analyses(tenant_id);

-- Topic cluster önerileri (FR-E6)
CREATE TABLE content.topic_clusters (
    id                TEXT PRIMARY KEY,  -- ULID
    brand_id          TEXT NOT NULL REFERENCES config.brands(id),
    topic_name        TEXT NOT NULL,
    opportunity_score REAL NOT NULL DEFAULT 0.0,      -- 0-100
    relevance         TEXT NOT NULL,     -- high / medium / low
    recommendation    TEXT NOT NULL,
    tenant_id         TEXT NOT NULL,
    workspace_id      TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE content.topic_clusters ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON content.topic_clusters
    USING (tenant_id = current_setting('app.tenant_id')::text);

CREATE INDEX idx_topic_brand ON content.topic_clusters(brand_id, opportunity_score DESC);
CREATE INDEX idx_topic_tenant ON content.topic_clusters(tenant_id);
