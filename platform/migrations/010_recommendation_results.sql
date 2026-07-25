-- 010: Recommendation Results Tablosu
-- Dilim 3 kapsamında: öneri sonuçlarının kalıcı olarak saklanması

CREATE SCHEMA IF NOT EXISTS recommendation;

-- recommendation.results: her bir öneri değerlendirme sonucunu tutar
CREATE TABLE IF NOT EXISTS recommendation.results (
    id              TEXT PRIMARY KEY,
    brand_id        TEXT NOT NULL REFERENCES config.brands(id),
    panel_id        TEXT,
    workspace_id    TEXT NOT NULL,
    tenant_id       TEXT NOT NULL,
    category        TEXT NOT NULL,          -- visibility, content, technical, competitor
    severity        TEXT NOT NULL,          -- critical, high, medium, low
    title           TEXT NOT NULL,
    detail          TEXT NOT NULL,
    action_url      TEXT,
    confidence      NUMERIC(5,2) NOT NULL DEFAULT 75.00, -- 0-100
    applied         BOOLEAN NOT NULL DEFAULT false,
    dismissed       BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    applied_at      TIMESTAMPTZ,
    dismissed_at    TIMESTAMPTZ
);

-- İndeksler
CREATE INDEX IF NOT EXISTS idx_rec_results_workspace
    ON recommendation.results (workspace_id, tenant_id);
CREATE INDEX IF NOT EXISTS idx_rec_results_brand
    ON recommendation.results (brand_id);
CREATE INDEX IF NOT EXISTS idx_rec_results_created
    ON recommendation.results (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_rec_results_active
    ON recommendation.results (workspace_id, tenant_id)
    WHERE applied = false AND dismissed = false;