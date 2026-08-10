-- 049_benchmark_sector.sql
-- FR-D5 kırılımı: sektör bazlı benchmark
-- config.brands'e sektör kolonu eklenir; ölçümler marka bazında sektöre bağlanır.
-- GetBenchmarkContext (measure/handler.go) sektör filtresiyle anonim sektör kıyası yapar.

ALTER TABLE config.brands
    ADD COLUMN IF NOT EXISTS sector TEXT NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS idx_brands_sector
    ON config.brands(tenant_id, sector);