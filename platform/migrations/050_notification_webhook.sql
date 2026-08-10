-- 050_notification_webhook.sql
-- HT2 Webhook Çeşitlendirme: Workspace bildirim ayarlarına webhook kanalı
-- (Slack, Microsoft Teams, Discord, PagerDuty, generic custom webhook) eklenir.

ALTER TABLE delivery.notification_settings
    ADD COLUMN IF NOT EXISTS webhook_url    TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS webhook_kind   TEXT NOT NULL DEFAULT 'generic',
    ADD COLUMN IF NOT EXISTS webhook_active BOOLEAN NOT NULL DEFAULT false;