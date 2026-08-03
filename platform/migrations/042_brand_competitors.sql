-- 042_brand_competitors.sql
-- Bu migration, kullanıcı tanımlı rakip marka ilişkilerini saklar.
-- Eklenen FR: FR-B1 (marka/rakip tanımı)

-- brand_competitors: her marka için kullanıcının tanımladığı rakip markalar
CREATE TABLE config.brand_competitors (
    id            TEXT PRIMARY KEY,        -- ULID
    brand_id      TEXT NOT NULL REFERENCES config.brands(id) ON DELETE CASCADE,
    competitor_id TEXT NOT NULL REFERENCES config.brands(id) ON DELETE CASCADE,
    tenant_id     TEXT NOT NULL REFERENCES identity.tenants(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(brand_id, competitor_id),
    CHECK(brand_id != competitor_id)       -- Kendi kendine rakip olamaz
);

ALTER TABLE config.brand_competitors ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON config.brand_competitors
    USING (tenant_id = current_setting('app.tenant_id')::text);

CREATE INDEX idx_bcomp_brand ON config.brand_competitors(brand_id);
CREATE INDEX idx_bcomp_competitor ON config.brand_competitors(competitor_id);
CREATE INDEX idx_bcomp_tenant ON config.brand_competitors(tenant_id);
