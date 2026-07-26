-- 013_citations.sql
-- FR-D2: Alıntı/kaynak analizi — motor yanıtlarından çıkarılan kaynakça

CREATE TABLE IF NOT EXISTS measure.citations (
    id                  TEXT PRIMARY KEY,         -- ULID
    tenant_id           TEXT NOT NULL REFERENCES identity.tenants(id),
    raw_response_id     TEXT NOT NULL REFERENCES measure.raw_responses(id),
    job_id              TEXT NOT NULL REFERENCES measure.measurement_jobs(id),
    brand_id            TEXT,
    workspace_id        TEXT,
    source_url          TEXT NOT NULL,
    source_domain       TEXT NOT NULL,            -- example.com
    title               TEXT,
    position            INT DEFAULT 0,           -- Sıralama
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_citations_job
    ON measure.citations (job_id);
CREATE INDEX IF NOT EXISTS idx_citations_brand
    ON measure.citations (brand_id);
CREATE INDEX IF NOT EXISTS idx_citations_domain
    ON measure.citations (tenant_id, source_domain);

ALTER TABLE measure.citations ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON measure.citations
    USING (tenant_id = identity.get_tenant_id());
