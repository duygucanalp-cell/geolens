-- 021_pilot_tenants.sql
-- K4: Kurumsal pilot programı

CREATE TABLE IF NOT EXISTS identity.pilot_tenants (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),
    tenant_id       TEXT NOT NULL UNIQUE REFERENCES identity.tenants(id) ON DELETE CASCADE,
    program_name    TEXT NOT NULL DEFAULT 'Kurumsal Pilot Programı',
    trial_ends_at   TIMESTAMPTZ NOT NULL DEFAULT now() + INTERVAL '90 days',
    max_workspaces  INTEGER NOT NULL DEFAULT 10,
    max_engines     INTEGER NOT NULL DEFAULT 5,
    support_level   TEXT NOT NULL DEFAULT 'standard',
    contact_email   TEXT NOT NULL DEFAULT '',
    notes           TEXT NOT NULL DEFAULT '',
    auto_convert    BOOLEAN NOT NULL DEFAULT true,
    status          TEXT NOT NULL DEFAULT 'active',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_pilot_tenants_status ON identity.pilot_tenants(status);
