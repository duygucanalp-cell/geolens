-- 043_seo_connections.sql
-- SEO platform entegrasyonları (FR-B8)
-- Google Search Console ve GA4 bağlantılarını OAuth2 token'ları ile yönetir.

CREATE SCHEMA IF NOT EXISTS seo;

-- OAuth2 token ve bağlantı bilgilerini saklar
CREATE TABLE seo.connections (
    id          TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    tenant_id   TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    workspace_id TEXT NOT NULL REFERENCES config.workspaces(id) ON DELETE CASCADE,
    platform    TEXT NOT NULL CHECK (platform IN ('search_console', 'ga4')),  -- Hizmet türü
    email       TEXT NOT NULL,              -- Bağlanan Google hesabı
    access_token TEXT NOT NULL,             -- Şifrelenmiş OAuth2 access token
    refresh_token TEXT NOT NULL,            -- Şifrelenmiş OAuth2 refresh token
    token_expires_at TIMESTAMPTZ NOT NULL,  -- Access token geçerlilik süresi
    is_active   BOOLEAN NOT NULL DEFAULT true,
    last_synced_at TIMESTAMPTZ,             -- Son veri çekme zamanı
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, platform)
);

-- Search Console veri önbelleği
CREATE TABLE seo.search_console_data (
    id              TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    connection_id   TEXT NOT NULL REFERENCES seo.connections(id) ON DELETE CASCADE,
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    brand_id        TEXT NOT NULL REFERENCES config.brands(id) ON DELETE CASCADE,
    query           TEXT NOT NULL,           -- Arama sorgusu
    clicks          BIGINT NOT NULL DEFAULT 0,
    impressions     BIGINT NOT NULL DEFAULT 0,
    ctr             FLOAT NOT NULL DEFAULT 0,
    avg_position    FLOAT NOT NULL DEFAULT 0,
    measured_at     DATE NOT NULL,           -- Hangi güne ait veri
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(connection_id, brand_id, query, measured_at)
);

-- GA4 veri önbelleği
CREATE TABLE seo.ga4_data (
    id              TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
    connection_id   TEXT NOT NULL REFERENCES seo.connections(id) ON DELETE CASCADE,
    tenant_id       TEXT NOT NULL REFERENCES identity.tenants(id) ON DELETE CASCADE,
    brand_id        TEXT NOT NULL REFERENCES config.brands(id) ON DELETE CASCADE,
    page_views      BIGINT NOT NULL DEFAULT 0,
    sessions        BIGINT NOT NULL DEFAULT 0,
    bounce_rate     FLOAT NOT NULL DEFAULT 0,
    avg_session_duration FLOAT NOT NULL DEFAULT 0,
    measured_at     DATE NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(connection_id, brand_id, measured_at)
);

CREATE INDEX idx_seo_connections_tenant ON seo.connections(tenant_id);
CREATE INDEX idx_seo_sc_data_brand ON seo.search_console_data(brand_id, measured_at DESC);
CREATE INDEX idx_seo_ga4_data_brand ON seo.ga4_data(brand_id, measured_at DESC);
