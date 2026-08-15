-- 044_benchmark_stats.sql
-- FR-D5/C: Benchmark sektör istatistikleri önbelleği
-- Periyodik olarak tüm kiracıların skorlarından anonim toplulaştırma yapar.
--
-- Bu tablo, NFR-13 (gizlilik eşiği ≥5) koruması ile çalışır:
-- - Eğer tenant_count < 5 ise satır eklenmez (sufficient_data = false)
-- - Tüm istatistik değerleri DP Laplace noise (ε=1.0) ile korunur
-- - Tekil brand_id'ler bu tabloda saklanmaz

CREATE SCHEMA IF NOT EXISTS benchmark;

CREATE TABLE IF NOT EXISTS benchmark.industry_stats (
    id              TEXT PRIMARY KEY DEFAULT gen_ulid(),

    -- Zaman damgası — her periyodik aggregation yeni bir satır ekler
    computed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Toplam katkıda bulunan kiracı sayısı (ham, DP yok)
    tenant_count    INTEGER NOT NULL DEFAULT 0,

    -- Toplam katkıda bulunan brand sayısı (ham, DP yok)
    brand_count     INTEGER NOT NULL DEFAULT 0,

    -- Sektör istatistikleri (DP noisy, ε=1.0, sensitivity=100)
    sector_avg      DOUBLE PRECISION NOT NULL DEFAULT 0,
    sector_median   DOUBLE PRECISION NOT NULL DEFAULT 0,
    sector_min      DOUBLE PRECISION NOT NULL DEFAULT 0,
    sector_max      DOUBLE PRECISION NOT NULL DEFAULT 0,
    sector_stddev   DOUBLE PRECISION NOT NULL DEFAULT 0,

    -- Yüzdelik dilimler (DP noisy)
    percentile_25   DOUBLE PRECISION NOT NULL DEFAULT 0,
    percentile_75   DOUBLE PRECISION NOT NULL DEFAULT 0,
    percentile_90   DOUBLE PRECISION NOT NULL DEFAULT 0,

    -- Meta: aggregation sırasında kaç skor değerlendirildi
    score_count     INTEGER NOT NULL DEFAULT 0,

    -- Hata payı / eksik veri durumu
    error_message   TEXT,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Son aggregation'ı hızlı bulmak için
CREATE INDEX IF NOT EXISTS idx_industry_stats_computed
    ON benchmark.industry_stats(computed_at DESC);

-- RLS gerekli değil — bu tablo herkese açık anonim veri içerir
-- (tenant_id yok, brand_id yok, tüm değerler DP-noisy)
