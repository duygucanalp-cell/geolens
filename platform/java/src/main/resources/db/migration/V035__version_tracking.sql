-- 035_version_tracking.sql
-- R14: Version Tracking — model/engine versiyon değişiklik takibi

CREATE SCHEMA IF NOT EXISTS version;

CREATE TABLE IF NOT EXISTS version.entries (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    entity_type     TEXT NOT NULL DEFAULT '', -- engine, model, prompt_set, guardrail, registry
    entity_id       TEXT NOT NULL DEFAULT '',
    entity_name     TEXT NOT NULL DEFAULT '',
    old_version     TEXT NOT NULL DEFAULT '',
    new_version     TEXT NOT NULL DEFAULT '',
    change_notes    TEXT NOT NULL DEFAULT '',
    changed_by      TEXT NOT NULL DEFAULT '',
    metadata        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_version_entries_tenant ON version.entries(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_version_entries_entity ON version.entries(entity_type, entity_id);
