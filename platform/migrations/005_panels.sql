-- 005_panels.sql
-- Panel tanımları — tekrar kullanılabilir ölçüm yapılandırmaları

-- Panel tablosu: bir ölçüm grubu (markalar + prompt seti + zamanlama)
CREATE TABLE config.panels (
    id               TEXT PRIMARY KEY,           -- ULID
    workspace_id     TEXT NOT NULL REFERENCES config.workspaces(id),
    tenant_id        TEXT NOT NULL REFERENCES identity.tenants(id),
    name             TEXT NOT NULL,
    description      TEXT,
    prompt_set_id    TEXT REFERENCES config.prompt_sets(id),
    schedule_cron    TEXT,                        -- cron expression (boş = manuel)
    is_active        BOOLEAN NOT NULL DEFAULT true,
    last_measured_at TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Panel-Marka ilişki tablosu
CREATE TABLE config.panel_brands (
    panel_id     TEXT NOT NULL REFERENCES config.panels(id) ON DELETE CASCADE,
    brand_id     TEXT NOT NULL REFERENCES config.brands(id) ON DELETE CASCADE,
    workspace_id TEXT NOT NULL REFERENCES config.workspaces(id),
    tenant_id    TEXT NOT NULL REFERENCES identity.tenants(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (panel_id, brand_id)
);

-- Indexes
CREATE INDEX idx_panels_tenant ON config.panels(tenant_id, workspace_id);
CREATE INDEX idx_panels_active ON config.panels(tenant_id, is_active) WHERE is_active = true;
CREATE INDEX idx_panels_schedule ON config.panels(tenant_id, schedule_cron) WHERE schedule_cron IS NOT NULL AND schedule_cron != '';
CREATE INDEX idx_panel_brands_panel ON config.panel_brands(panel_id);
CREATE INDEX idx_panel_brands_brand ON config.panel_brands(brand_id);

-- RLS: config.panels için tenant izolasyonu
ALTER TABLE config.panels ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON config.panels
    USING (tenant_id = identity.get_tenant_id());

ALTER TABLE config.panel_brands ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON config.panel_brands
    USING (tenant_id = identity.get_tenant_id());

-- Audit trigger (config.panels)
CREATE TRIGGER trg_panels_updated_at
    BEFORE UPDATE ON config.panels
    FOR EACH ROW
    EXECUTE FUNCTION identity.update_updated_at();
