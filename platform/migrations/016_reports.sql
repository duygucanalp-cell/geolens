-- 016_reports.sql
-- M12: FR-F5 — Async rapor tablosu (POST talep → status → download)

CREATE TABLE IF NOT EXISTS measure.reports (
    id              TEXT PRIMARY KEY,
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id),
    workspace_id    TEXT NOT NULL REFERENCES config.workspaces(id),
    report_type     TEXT NOT NULL,                  -- digest, score_card, audit
    brand_id        TEXT REFERENCES config.brands(id),
    status          TEXT NOT NULL DEFAULT 'pending',-- pending, generating, ready, failed
    file_path       TEXT,                           -- s3_ref veya local dosya yolu
    file_name       TEXT,
    file_size       BIGINT,
    error_message   TEXT,
    params          JSONB,                          -- Talep parametreleri
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_reports_status
    ON measure.reports (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_reports_workspace
    ON measure.reports (workspace_id, created_at DESC);

ALTER TABLE measure.reports ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON measure.reports
    USING (tenant_id = identity.get_tenant_id());
