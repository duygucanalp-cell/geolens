-- 017_api_keys.sql
-- H1: FR-F6 — API anahtarları (/public/v1 erişimi için)

CREATE TABLE IF NOT EXISTS identity.api_keys (
    id              TEXT PRIMARY KEY,
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id),
    name            TEXT NOT NULL,
    key_hash        TEXT NOT NULL,               -- bcrypt hash of the actual key
    key_prefix      TEXT NOT NULL,               -- gls_abc... (ilk 8 karakter, loglama için)
    role            TEXT NOT NULL DEFAULT 'viewer',
    allowed_ips     TEXT[],                      -- optional IP restriction
    expires_at      TIMESTAMPTZ,
    last_used_at    TIMESTAMPTZ,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_api_keys_prefix
    ON identity.api_keys (key_prefix);
CREATE INDEX IF NOT EXISTS idx_api_keys_tenant
    ON identity.api_keys (tenant_id);

ALTER TABLE identity.api_keys ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON identity.api_keys
    USING (tenant_id = identity.get_tenant_id());
