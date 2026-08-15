-- 001_initial.sql
-- İlk migration: Kiracı, kullanıcı, çalışma alanı ve marka tabloları

-- Extension
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Schema
CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS config;

-- ============================================================================
-- BC1: Identity
-- ============================================================================

CREATE TABLE identity.tenants (
    id          TEXT PRIMARY KEY,          -- ULID (26 karakter)
    name        TEXT NOT NULL,
    slug        TEXT NOT NULL UNIQUE,
    tier        TEXT NOT NULL DEFAULT 'free', -- free, pro, business, enterprise
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE identity.users (
    id              TEXT PRIMARY KEY,      -- ULID
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id),
    email           TEXT NOT NULL,
    password_hash   TEXT NOT NULL,
    role            TEXT NOT NULL DEFAULT 'member', -- admin, member
    full_name       TEXT NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, email)
);

CREATE TABLE identity.sessions (
    id          TEXT PRIMARY KEY,          -- ULID
    user_id     TEXT NOT NULL REFERENCES identity.users(id),
    tenant_id   TEXT NOT NULL REFERENCES identity.tenants(id),
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================================
-- BC2: Config
-- ============================================================================

CREATE TABLE config.workspaces (
    id          TEXT PRIMARY KEY,          -- ULID
    tenant_id   TEXT NOT NULL REFERENCES identity.tenants(id),
    name        TEXT NOT NULL,
    slug        TEXT NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, slug)
);

CREATE TABLE config.memberships (
    id            TEXT PRIMARY KEY,        -- ULID
    workspace_id  TEXT NOT NULL REFERENCES config.workspaces(id),
    user_id       TEXT NOT NULL REFERENCES identity.users(id),
    tenant_id     TEXT NOT NULL REFERENCES identity.tenants(id),  -- ADR-004: her katta kiracı bağlamı
    role          TEXT NOT NULL DEFAULT 'member', -- admin, editor, viewer
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(workspace_id, user_id)
);

CREATE TABLE config.brands (
    id            TEXT PRIMARY KEY,        -- ULID
    workspace_id  TEXT NOT NULL REFERENCES config.workspaces(id),
    tenant_id     TEXT NOT NULL REFERENCES identity.tenants(id),
    name          TEXT NOT NULL,
    website_url   TEXT NOT NULL,
    is_active     BOOLEAN NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE config.prompt_sets (
    id            TEXT PRIMARY KEY,        -- ULID
    workspace_id  TEXT NOT NULL REFERENCES config.workspaces(id),
    tenant_id     TEXT NOT NULL REFERENCES identity.tenants(id),
    name          TEXT NOT NULL,
    prompt_text   TEXT NOT NULL,
    category      TEXT,                    -- sektör kategorisi
    is_active     BOOLEAN NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================================
-- Row-Level Security (ADR-004)
-- ============================================================================

-- Tenant isolation function
CREATE OR REPLACE FUNCTION identity.get_tenant_id()
RETURNS TEXT AS $$
    SELECT current_setting('app.tenant_id', true);
$$ LANGUAGE SQL STABLE;

-- RLS policies
ALTER TABLE identity.tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE identity.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE identity.sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE config.workspaces ENABLE ROW LEVEL SECURITY;
ALTER TABLE config.memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE config.brands ENABLE ROW LEVEL SECURITY;
ALTER TABLE config.prompt_sets ENABLE ROW LEVEL SECURITY;

-- Tenant isolation policies
CREATE POLICY tenant_isolation ON identity.tenants
    USING (id = identity.get_tenant_id());

CREATE POLICY tenant_isolation ON identity.users
    USING (tenant_id = identity.get_tenant_id());

CREATE POLICY tenant_isolation ON identity.sessions
    USING (tenant_id = identity.get_tenant_id());

CREATE POLICY tenant_isolation ON config.workspaces
    USING (tenant_id = identity.get_tenant_id());

CREATE POLICY tenant_isolation ON config.brands
    USING (tenant_id = identity.get_tenant_id());

CREATE POLICY tenant_isolation ON config.prompt_sets
    USING (tenant_id = identity.get_tenant_id());

-- Tenant isolation for memberships (doğrudan tenant_id, alt sorgu yok)
CREATE POLICY tenant_isolation ON config.memberships
    USING (tenant_id = identity.get_tenant_id());

-- ============================================================================
-- Indexes
-- ============================================================================

CREATE INDEX idx_users_tenant ON identity.users(tenant_id);
CREATE INDEX idx_sessions_user ON identity.sessions(user_id);
CREATE INDEX idx_sessions_expires ON identity.sessions(expires_at);
CREATE INDEX idx_workspaces_tenant ON config.workspaces(tenant_id);
CREATE INDEX idx_brands_workspace ON config.brands(workspace_id);
CREATE INDEX idx_memberships_workspace ON config.memberships(workspace_id);
CREATE INDEX idx_memberships_tenant ON config.memberships(tenant_id);
CREATE INDEX idx_prompt_sets_workspace ON config.prompt_sets(workspace_id);
