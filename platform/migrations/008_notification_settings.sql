-- 008_notification_settings.sql
-- Bildirim Ayarları Tablosu: Workspace bazında e-posta/digest tercihlerini saklar

CREATE SCHEMA IF NOT EXISTS delivery;

CREATE TABLE delivery.notification_settings (
    workspace_id     TEXT NOT NULL REFERENCES config.workspaces(id),
    tenant_id        TEXT NOT NULL REFERENCES identity.tenants(id),
    email_address    TEXT NOT NULL DEFAULT '',
    digest_enabled   BOOLEAN NOT NULL DEFAULT true,
    digest_day       TEXT NOT NULL DEFAULT 'monday',
    digest_time      TEXT NOT NULL DEFAULT '09:00',
    digest_format    TEXT NOT NULL DEFAULT 'email',
    notify_on_drop   BOOLEAN NOT NULL DEFAULT true,
    drop_threshold   INTEGER NOT NULL DEFAULT 10,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, tenant_id)
);

CREATE INDEX idx_notif_settings_tenant ON delivery.notification_settings(tenant_id);

ALTER TABLE delivery.notification_settings ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON delivery.notification_settings
    USING (tenant_id = identity.get_tenant_id());
