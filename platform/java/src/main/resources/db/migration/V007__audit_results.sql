-- 007_audit_results.sql
-- Audit Sonuçları Tablosu: Site denetim sonuçlarını JSONB olarak saklar

CREATE TABLE governance.audit_results (
    id              TEXT PRIMARY KEY,                          -- ULID
    brand_id        TEXT NOT NULL REFERENCES config.brands(id),
    workspace_id    TEXT NOT NULL REFERENCES config.workspaces(id),
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id),
    brand_name      TEXT NOT NULL DEFAULT '',
    website_url     TEXT NOT NULL DEFAULT '',
    overall_score   NUMERIC(5,2) NOT NULL DEFAULT 0,
    robots_txt      JSONB,                                    -- RobotsTxtCheck
    bot_access      JSONB,                                    -- BotAccessCheck
    ssr             JSONB,                                    -- SSRCheck
    ssrf            JSONB,                                    -- SSRFCheck
    issues          JSONB DEFAULT '[]'::jsonb,                -- Issue[]
    raw_result      JSONB,                                    -- Complete AuditResult snapshot
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX idx_audit_results_brand ON governance.audit_results(brand_id, created_at DESC);
CREATE INDEX idx_audit_results_tenant ON governance.audit_results(tenant_id, created_at DESC);
CREATE INDEX idx_audit_results_workspace ON governance.audit_results(workspace_id, created_at DESC);

-- RLS
ALTER TABLE governance.audit_results ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON governance.audit_results
    USING (tenant_id = identity.get_tenant_id());
