-- 012_invitations.sql
-- Üye davet tablosu — FR-A2 kapsamında e-posta ile davet akışı

CREATE TABLE IF NOT EXISTS identity.invitations (
    id              TEXT PRIMARY KEY,                -- ULID
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id),
    workspace_id    TEXT NOT NULL REFERENCES config.workspaces(id),
    invited_by      TEXT NOT NULL REFERENCES identity.users(id),
    email           TEXT NOT NULL,
    role            TEXT NOT NULL DEFAULT 'member',  -- admin, editor, viewer
    token           TEXT NOT NULL UNIQUE,            -- davet kodu (JWT benzeri)
    expires_at      TIMESTAMPTZ NOT NULL,
    accepted_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_invitations_tenant
    ON identity.invitations (tenant_id);
CREATE INDEX IF NOT EXISTS idx_invitations_token
    ON identity.invitations (token);
CREATE INDEX IF NOT EXISTS idx_invitations_email
    ON identity.invitations (tenant_id, email);

ALTER TABLE identity.invitations ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON identity.invitations
    USING (tenant_id = identity.get_tenant_id());
