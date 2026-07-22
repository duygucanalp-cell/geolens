-- 004_prompt_sets.sql
-- Prompt Set şablonları — tekrar kullanılabilir prompt metinleri

CREATE TABLE config.prompt_sets (
    id              TEXT PRIMARY KEY,           -- ULID
    workspace_id    TEXT NOT NULL REFERENCES config.workspaces(id),
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id),
    name            TEXT NOT NULL,
    description     TEXT,
    prompt_text     TEXT NOT NULL,              -- {brand_name}, {website_url} değişkenleri desteklenir
    is_active       BOOLEAN NOT NULL DEFAULT true,
    version         INT NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX idx_prompt_sets_tenant ON config.prompt_sets(tenant_id, workspace_id);
CREATE INDEX idx_prompt_sets_active ON config.prompt_sets(tenant_id, is_active) WHERE is_active = true;

-- RLS: config.prompt_sets için tenant izolasyonu
ALTER TABLE config.prompt_sets ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON config.prompt_sets
    USING (tenant_id = identity.get_tenant_id());

-- Audit trigger
CREATE TRIGGER trg_prompt_sets_updated_at
    BEFORE UPDATE ON config.prompt_sets
    FOR EACH ROW
    EXECUTE FUNCTION identity.update_updated_at();
