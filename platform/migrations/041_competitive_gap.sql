-- 041_competitive_gap.sql
-- Bu migration, 0419 Competitive Gap Analysis dokümanı için competitive schema'sını oluşturur.
-- Eklenen FR: FR-D11 (competitive gap analysis)

CREATE SCHEMA IF NOT EXISTS competitive;

-- Gap snapshot'ları
CREATE TABLE competitive.gap_snapshots (
    gap_id             TEXT PRIMARY KEY,   -- ULID
    brand_id           TEXT NOT NULL REFERENCES config.brands(id),
    competitor_id      TEXT NOT NULL REFERENCES config.brands(id),
    period_start       DATE NOT NULL,
    period_end         DATE NOT NULL,
    visibility_gap     REAL,               -- -100 .. +100
    citation_gap       REAL,
    content_gap        REAL,
    topic_gap          REAL,
    prompt_gap         REAL,
    competitive_score  REAL,               -- 0-100 normalized
    breakdown          JSONB,              -- { visibility: {...}, citation: {...}, ... }
    tenant_id          TEXT NOT NULL,
    workspace_id       TEXT NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(brand_id, competitor_id, period_start, period_end)
);

ALTER TABLE competitive.gap_snapshots ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON competitive.gap_snapshots
    USING (tenant_id = current_setting('app.tenant_id')::text);

CREATE INDEX idx_gap_brand_period ON competitive.gap_snapshots(brand_id, period_start DESC);
CREATE INDEX idx_gap_competitor ON competitive.gap_snapshots(competitor_id);
CREATE INDEX idx_gap_tenant ON competitive.gap_snapshots(tenant_id);

-- Gap bazlı öneriler
CREATE TABLE competitive.gap_recommendations (
    recommendation_id  TEXT PRIMARY KEY,   -- ULID
    gap_id             TEXT NOT NULL REFERENCES competitive.gap_snapshots(gap_id),
    gap_type           TEXT NOT NULL,      -- visibility / citation / content / topic / prompt
    priority           TEXT NOT NULL,      -- critical / high / medium / low
    description        TEXT NOT NULL,
    impact             TEXT,
    kanit_derecesi     TEXT,               -- deneysel / korelasyonel / denenebilir
    related_fr         TEXT,               -- ilgili FR kodu
    tenant_id          TEXT NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE competitive.gap_recommendations ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON competitive.gap_recommendations
    USING (tenant_id = current_setting('app.tenant_id')::text);

CREATE INDEX idx_gap_rec_gap ON competitive.gap_recommendations(gap_id);
CREATE INDEX idx_gap_rec_priority ON competitive.gap_recommendations(priority);
CREATE INDEX idx_gap_rec_tenant ON competitive.gap_recommendations(tenant_id);
