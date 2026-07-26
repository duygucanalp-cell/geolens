-- 019_sso_config.sql
-- K1: SSO/SAML yapılandırması

CREATE SCHEMA IF NOT EXISTS sso;

CREATE TABLE IF NOT EXISTS sso.configs (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    idp_entity_id   TEXT NOT NULL DEFAULT '',
    idp_sso_url     TEXT NOT NULL DEFAULT '',
    idp_cert        TEXT NOT NULL DEFAULT '',
    sp_entity_id    TEXT NOT NULL DEFAULT '',
    sp_acs_url      TEXT NOT NULL DEFAULT '',
    enabled         BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_sso_configs_tenant ON sso.configs(tenant_id);
