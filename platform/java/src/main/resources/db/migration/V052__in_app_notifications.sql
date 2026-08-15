-- 052_in_app_notifications.sql
-- In-app bildirim kanalı (internal/delivery ChannelInApp) için kalıcı depolama.
-- Önceden pasifti (sadece debug log) — artık bildirimler DB'ye yazılır ve
-- web UI/API üzerinden okunabilir (FR-D10 in-app kanalı).

CREATE TABLE IF NOT EXISTS delivery.notifications (
    id            TEXT PRIMARY KEY,           -- ULID
    tenant_id     TEXT NOT NULL,
    workspace_id  TEXT NOT NULL,
    user_id       TEXT NOT NULL DEFAULT '',
    type          TEXT NOT NULL,              -- score_drop | weekly_digest | new_suggestion | audit_complete
    title         TEXT NOT NULL,
    body          TEXT NOT NULL,
    data          JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_read       BOOLEAN NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE delivery.notifications ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON delivery.notifications
    USING (tenant_id = current_setting('app.tenant_id')::text);

CREATE INDEX idx_notifications_tenant_created
    ON delivery.notifications (tenant_id, created_at DESC);
CREATE INDEX idx_notifications_unread
    ON delivery.notifications (tenant_id, workspace_id, is_read);
