-- 020_retention_policies.sql
-- K3: Veri saklama politikaları ve yaşam döngüsü

CREATE SCHEMA IF NOT EXISTS retention;

CREATE TABLE IF NOT EXISTS retention.policies (
    id                      TEXT PRIMARY KEY DEFAULT gen_ulid(),
    tenant_id               TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    entity_type             TEXT NOT NULL, -- measurement, audit_log, report, alert
    retention_days          INTEGER NOT NULL DEFAULT 365,
    archival_strategy       TEXT NOT NULL DEFAULT 'delete', -- delete, anonymize, archive_s3
    enabled                 BOOLEAN NOT NULL DEFAULT true,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, entity_type)
);

CREATE TABLE IF NOT EXISTS retention.archives (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    entity_type     TEXT NOT NULL,
    entity_id       TEXT NOT NULL,
    archived_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL,
    s3_key          TEXT NOT NULL DEFAULT '',
    data_hash       TEXT NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_retention_archives_tenant ON retention.archives(tenant_id, entity_type);
CREATE INDEX IF NOT EXISTS idx_retention_archives_expires ON retention.archives(expires_at);
