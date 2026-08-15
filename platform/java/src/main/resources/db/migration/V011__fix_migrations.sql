-- 011_fix_migrations.sql
-- Fix: Eksik trigger fonksiyonu, schema CREATE, ve RLS eksiklikleri
-- Bu migration öncesindeki migration'lardaki hataları düzeltir.

-- ============================================================================
-- Fix 1: Eksik trigger fonksiyonu (migration 004 ve 005'te referans edilir)
-- ============================================================================
CREATE OR REPLACE FUNCTION identity.update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- Fix 2: Eksik schema CREATE (migration 006 governance kullanır)
-- ============================================================================
CREATE SCHEMA IF NOT EXISTS governance;

-- ============================================================================
-- Fix 3: Eksik RLS — recommendation.results (migration 010)
-- ============================================================================
ALTER TABLE recommendation.results ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON recommendation.results
    USING (tenant_id = identity.get_tenant_id());

-- ============================================================================
-- Fix 4: Eksik RLS — recommendation.results için ek indeks
-- ============================================================================
CREATE INDEX IF NOT EXISTS idx_rec_results_tenant_brand
    ON recommendation.results (tenant_id, brand_id);
