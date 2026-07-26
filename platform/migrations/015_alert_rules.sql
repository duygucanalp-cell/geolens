-- 015_alert_rules.sql
-- M11: FR-F2 — Uyarı ayarları (alert_rules tablosu + CRUD)

CREATE TABLE IF NOT EXISTS governance.alert_rules (
    id              TEXT PRIMARY KEY,
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id),
    brand_id        TEXT NOT NULL REFERENCES config.brands(id),
    name            TEXT NOT NULL,
    metric          TEXT NOT NULL,
    condition       TEXT NOT NULL,
    threshold       DOUBLE PRECISION NOT NULL,
    channel         TEXT NOT NULL DEFAULT 'email',
    channel_config  JSONB,
    enabled         BOOLEAN NOT NULL DEFAULT true,
    cooldown_min    INT NOT NULL DEFAULT 60,
    last_fired_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_alert_rules_brand
    ON governance.alert_rules (tenant_id, brand_id);
CREATE INDEX IF NOT EXISTS idx_alert_rules_enabled
    ON governance.alert_rules (tenant_id, enabled);

ALTER TABLE governance.alert_rules ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON governance.alert_rules
    USING (tenant_id = identity.get_tenant_id());
