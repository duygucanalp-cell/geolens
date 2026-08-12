CREATE OR REPLACE FUNCTION identity.update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- gen_ulid — migration'lar DEFAULT gen_ulid() kullanır ama fonksiyon tanımı
-- hiçbir migration'da yok; Docker initdb yalnızca boş volume'de çalıştığından
-- mevcut DB'lerde eksik kalır ve bu tabloları kullanan INSERT'ler (örn. 044
-- benchmark.industry_stats) 'function gen_ulid() does not exist' hatası verir.
-- UUID tabanlı pratik karşılık (kronolojik sıralama gerektirmeyen id'ler için).
-- ============================================================================
CREATE OR REPLACE FUNCTION gen_ulid() RETURNS TEXT AS $$
BEGIN
    RETURN gen_random_uuid()::text;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- 044_benchmark_stats.sql — Eksik sektör istatistikleri önbelleği (FR-D5/C)
-- benchmark.industry_stats: NFR-13 eşiği + DP Laplace noise korumalı anonim
-- toplulaştırma tablosu. Worker'daki aggregator yazmadan önce tablonun var
-- olması gerekir (42P01 hatasını önler).
-- ============================================================================
CREATE SCHEMA IF NOT EXISTS benchmark;

CREATE TABLE IF NOT EXISTS benchmark.industry_stats (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),
    computed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    tenant_count    INTEGER NOT NULL DEFAULT 0,
    brand_count     INTEGER NOT NULL DEFAULT 0,
    sector_avg      DOUBLE PRECISION NOT NULL DEFAULT 0,
    sector_median   DOUBLE PRECISION NOT NULL DEFAULT 0,
    sector_min      DOUBLE PRECISION NOT NULL DEFAULT 0,
    sector_max      DOUBLE PRECISION NOT NULL DEFAULT 0,
    sector_stddev   DOUBLE PRECISION NOT NULL DEFAULT 0,
    percentile_25   DOUBLE PRECISION NOT NULL DEFAULT 0,
    percentile_75   DOUBLE PRECISION NOT NULL DEFAULT 0,
    percentile_90   DOUBLE PRECISION NOT NULL DEFAULT 0,
    score_count     INTEGER NOT NULL DEFAULT 0,
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_industry_stats_computed
    ON benchmark.industry_stats(computed_at DESC);

CREATE SCHEMA IF NOT EXISTS governance;

-- 005_panels.sql
CREATE TABLE IF NOT EXISTS config.panels (
    id               TEXT PRIMARY KEY,
    workspace_id     TEXT NOT NULL REFERENCES config.workspaces(id),
    tenant_id        TEXT NOT NULL REFERENCES identity.tenants(id),
    name             TEXT NOT NULL,
    description      TEXT,
    prompt_set_id    TEXT REFERENCES config.prompt_sets(id),
    schedule_cron    TEXT,
    is_active        BOOLEAN NOT NULL DEFAULT true,
    last_measured_at TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS config.panel_brands (
    panel_id     TEXT NOT NULL REFERENCES config.panels(id) ON DELETE CASCADE,
    brand_id     TEXT NOT NULL REFERENCES config.brands(id) ON DELETE CASCADE,
    workspace_id TEXT NOT NULL REFERENCES config.workspaces(id),
    tenant_id    TEXT NOT NULL REFERENCES identity.tenants(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (panel_id, brand_id)
);

CREATE INDEX IF NOT EXISTS idx_panels_tenant ON config.panels(tenant_id, workspace_id);
CREATE INDEX IF NOT EXISTS idx_panels_active ON config.panels(tenant_id, is_active) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS idx_panels_schedule ON config.panels(tenant_id, schedule_cron) WHERE schedule_cron IS NOT NULL AND schedule_cron != '';
CREATE INDEX IF NOT EXISTS idx_panel_brands_panel ON config.panel_brands(panel_id);
CREATE INDEX IF NOT EXISTS idx_panel_brands_brand ON config.panel_brands(brand_id);

ALTER TABLE config.panels ENABLE ROW LEVEL SECURITY;
DO $$ BEGIN
    CREATE POLICY tenant_isolation ON config.panels USING (tenant_id = identity.get_tenant_id());
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

ALTER TABLE config.panel_brands ENABLE ROW LEVEL SECURITY;
DO $$ BEGIN
    CREATE POLICY tenant_isolation ON config.panel_brands USING (tenant_id = identity.get_tenant_id());
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DROP TRIGGER IF EXISTS trg_panels_updated_at ON config.panels;
CREATE TRIGGER trg_panels_updated_at
    BEFORE UPDATE ON config.panels
    FOR EACH ROW
    EXECUTE FUNCTION identity.update_updated_at();

-- 006_governance.sql
CREATE TABLE IF NOT EXISTS governance.audit_log (
    id              TEXT PRIMARY KEY,
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id),
    user_id         TEXT,
    event_type      TEXT NOT NULL,
    resource_type   TEXT NOT NULL,
    resource_id     TEXT,
    action          TEXT NOT NULL,
    metadata        JSONB,
    ip_address      TEXT,
    user_agent      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS governance.usage_records (
    id              TEXT PRIMARY KEY,
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id),
    metric_name     TEXT NOT NULL,
    metric_value    BIGINT NOT NULL DEFAULT 1,
    resource_type   TEXT,
    resource_id     TEXT,
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS governance.rate_limit_buckets (
    id              TEXT PRIMARY KEY,
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id),
    bucket_name     TEXT NOT NULL,
    max_tokens      BIGINT NOT NULL,
    tokens_used     BIGINT NOT NULL DEFAULT 0,
    window_start    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, bucket_name, window_start)
);

CREATE INDEX IF NOT EXISTS idx_audit_tenant_event ON governance.audit_log(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_resource ON governance.audit_log(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_event_type ON governance.audit_log(tenant_id, event_type);
CREATE INDEX IF NOT EXISTS idx_usage_tenant_metric ON governance.usage_records(tenant_id, metric_name, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_usage_recorded_at ON governance.usage_records(recorded_at);
CREATE INDEX IF NOT EXISTS idx_rlb_tenant_bucket ON governance.rate_limit_buckets(tenant_id, bucket_name);

ALTER TABLE governance.audit_log ENABLE ROW LEVEL SECURITY;
DO $$ BEGIN
    CREATE POLICY tenant_isolation ON governance.audit_log USING (tenant_id = identity.get_tenant_id());
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

ALTER TABLE governance.usage_records ENABLE ROW LEVEL SECURITY;
DO $$ BEGIN
    CREATE POLICY tenant_isolation ON governance.usage_records USING (tenant_id = identity.get_tenant_id());
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

ALTER TABLE governance.rate_limit_buckets ENABLE ROW LEVEL SECURITY;
DO $$ BEGIN
    CREATE POLICY tenant_isolation ON governance.rate_limit_buckets USING (tenant_id = identity.get_tenant_id());
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- ============================================================================
-- 016_reports.sql — Eksik async rapor tablosu (FR-F5)
-- Docker initdb yalnızca boş volume'de çalıştığından, 016'dan önce init edilmiş
-- mevcut DB'lerde measure.reports oluşmaz; pdf worker 'bekleyen rapor sorgu
-- hatası' (42P01) üretir. Bu blok tabloyu idempotent şekilde tamamlar.
-- ============================================================================
CREATE SCHEMA IF NOT EXISTS measure;

CREATE TABLE IF NOT EXISTS measure.reports (
    id              TEXT PRIMARY KEY,
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id),
    workspace_id    TEXT NOT NULL REFERENCES config.workspaces(id),
    report_type     TEXT NOT NULL,                  -- digest, score_card, audit
    brand_id        TEXT REFERENCES config.brands(id),
    status          TEXT NOT NULL DEFAULT 'pending',-- pending, generating, ready, failed
    file_path       TEXT,                           -- s3_ref veya local dosya yolu
    file_name       TEXT,
    file_size       BIGINT,
    error_message   TEXT,
    params          JSONB,                          -- Talep parametreleri
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_reports_status
    ON measure.reports (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_reports_workspace
    ON measure.reports (workspace_id, created_at DESC);

ALTER TABLE measure.reports ENABLE ROW LEVEL SECURITY;
DO $$ BEGIN
    CREATE POLICY tenant_isolation ON measure.reports
        USING (tenant_id = identity.get_tenant_id());
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
