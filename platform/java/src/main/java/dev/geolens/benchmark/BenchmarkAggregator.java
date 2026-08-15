package dev.geolens.benchmark;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Sektör düzeyinde benchmark istatistikleri toplayıcı — Go {@code benchmark.Aggregator} portu (FR-D5).
 * <p>{@code measure.scores}'dan marka başına en son skorları toplar, ham istatistikleri hesaplar
 * ve {@code benchmark.industry_stats}'a differansiyel gizlilik korumasıyla (Laplace noise, NFR-13) yazar.
 */
public class BenchmarkAggregator {

    private static final Logger LOG = LoggerFactory.getLogger(BenchmarkAggregator.class);

    private final DSLContext dsl;
    private final DpConfig dpCfg;

    public BenchmarkAggregator(DSLContext dsl, DpConfig dpCfg) {
        this.dsl = dsl;
        this.dpCfg = DpConfig.merge(dpCfg);
    }

    /**
     * Sektör istatistiklerini hesaplayıp {@code benchmark.industry_stats}'a yazar.
     * Yetersiz veri (NFR-13) veya hata durumunda boş döner — Go {@code Aggregate} portu.
     */
    public String aggregate() {
        // Adım 1: Toplam kiracı ve marka sayısı (ham)
        Record counts = fetchOne("""
                SELECT COUNT(DISTINCT tenant_id) AS tenant_count,
                       COUNT(DISTINCT brand_id) AS brand_count
                FROM measure.scores
                """);
        if (counts == null) {
            return "";
        }
        int tenantCount = num(counts.get("tenant_count"));
        int brandCount = num(counts.get("brand_count"));

        // Adım 2: Yeterli veri yoksa hiçbir şey ekleme (NFR-13)
        if (tenantCount < dpCfg.minTenants) {
            LOG.info("aggregator: yetersiz veri, sektör istatistikleri yayınlanmıyor (NFR-13) tenant_count={} min_tenants={}",
                    tenantCount, dpCfg.minTenants);
            return "";
        }

        // Adım 3: Ham istatistikler (marka başına en son skor)
        Record stats = fetchOne("""
                SELECT AVG(sub.latest)::numeric(10,2)::double precision,
                       MIN(sub.latest)::numeric(10,2)::double precision,
                       MAX(sub.latest)::numeric(10,2)::double precision,
                       COALESCE(STDDEV(sub.latest)::numeric(10,2)::double precision, 0),
                       COUNT(*)::int
                FROM (
                    SELECT DISTINCT ON (brand_id) value AS latest
                    FROM measure.scores
                    ORDER BY brand_id, freshness_at DESC
                ) sub
                """);
        if (stats == null) {
            return "";
        }
        double rawAvg = dbl(stats.get(0));
        double rawMin = dbl(stats.get(1));
        double rawMax = dbl(stats.get(2));
        double rawStddev = dbl(stats.get(3));
        int scoreCount = num(stats.get(4));

        // Medyan + yüzdelikler — Go'da hata yok sayılır (varsayılan 0)
        double rawMedian = valueOf("""
                WITH ranked AS (
                    SELECT value, ROW_NUMBER() OVER (ORDER BY value) AS rn,
                           COUNT(*) OVER () AS cnt
                    FROM (
                        SELECT DISTINCT ON (brand_id) value
                        FROM measure.scores
                        ORDER BY brand_id, freshness_at DESC
                    ) sub
                )
                SELECT AVG(value)::numeric(10,2)::double precision
                FROM ranked
                WHERE rn IN ((cnt + 1) / 2, (cnt + 2) / 2)
                """);
        Record pct = fetchOne("""
                WITH distinct_scores AS (
                    SELECT DISTINCT ON (brand_id) value AS latest
                    FROM measure.scores
                    ORDER BY brand_id, freshness_at DESC
                )
                SELECT
                    PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY latest)::numeric(10,2)::double precision,
                    PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY latest)::numeric(10,2)::double precision,
                    PERCENTILE_CONT(0.90) WITHIN GROUP (ORDER BY latest)::numeric(10,2)::double precision
                FROM distinct_scores
                """);
        double rawP25 = pct == null ? 0 : dbl(pct.get(0));
        double rawP75 = pct == null ? 0 : dbl(pct.get(1));
        double rawP90 = pct == null ? 0 : dbl(pct.get(2));

        // Adım 4: Differansiyel gizlilik uygula
        RawSectorStats raw = new RawSectorStats(
                0, rawAvg, rawMedian, rawMin, rawMax, rawStddev,
                rawP25, rawP75, rawP90, tenantCount);
        AggregatedSectorStats dp = AggregatedSectorStats.of(raw, dpCfg);
        if (!dp.sufficientData) {
            LOG.debug("aggregator: DP sonrası yetersiz veri, atlanıyor");
            return "";
        }

        // Adım 5: benchmark.industry_stats'a yaz
        String id;
        try {
            Record r = dsl.fetchOne("""
                    INSERT INTO benchmark.industry_stats
                        (computed_at, tenant_count, brand_count,
                         sector_avg, sector_median, sector_min, sector_max, sector_stddev,
                         percentile_25, percentile_75, percentile_90, score_count)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """, Instant.now(), dp.tenantCount, brandCount,
                    dp.sectorAvg, dp.sectorMedian, dp.sectorMin, dp.sectorMax, dp.sectorStdDev,
                    dp.percentile25, dp.percentile75, dp.percentile90, scoreCount);
            id = r == null ? "" : String.valueOf(r.get("id"));
        } catch (RuntimeException e) {
            LOG.warn("aggregator: benchmark.industry_stats yazma hatası", e);
            return "";
        }

        LOG.info("aggregator: sektör istatistikleri hesaplandı ve kaydedildi stats_id={} tenant_count={}",
                id, dp.tenantCount);
        return id;
    }

    /** En güncel sektör istatistiklerini döndürür; veri yoksa null — Go {@code GetLatestSectorStats} portu. */
    public AggregatedSectorStats getLatestSectorStats() {
        Record r = fetchOne("""
                SELECT tenant_count, sector_avg, sector_median, sector_min, sector_max,
                       sector_stddev, percentile_25, percentile_75, percentile_90
                FROM benchmark.industry_stats
                ORDER BY computed_at DESC
                LIMIT 1
                """);
        if (r == null) {
            return null;
        }
        AggregatedSectorStats stats = new AggregatedSectorStats();
        stats.tenantCount = num(r.get("tenant_count"));
        stats.sectorAvg = dbl(r.get("sector_avg"));
        stats.sectorMedian = dbl(r.get("sector_median"));
        stats.sectorMin = dbl(r.get("sector_min"));
        stats.sectorMax = dbl(r.get("sector_max"));
        stats.sectorStdDev = dbl(r.get("sector_stddev"));
        stats.percentile25 = dbl(r.get("percentile_25"));
        stats.percentile75 = dbl(r.get("percentile_75"));
        stats.percentile90 = dbl(r.get("percentile_90"));
        stats.myScore = 0;
        stats.difference = 0;
        stats.trend = "";
        stats.sufficientData = stats.tenantCount >= dpCfg.minTenants;
        return stats;
    }

    /**
     * Canlı sektör bağlamı istatistikleri — Go {@code GetBenchmarkContext} portu.
     * <p>Kullanıcının skorunu tüm kiracıların ortalamasıyla karşılaştırır (T2 anonim kıyas).
     * İsteğe bağlı sektör filtresi (FR-D5): skorlar {@code config.brands.sector} ile kısıtlanır.
     * NFR-13 eşiği (minTenants) altında istatistik yayınlanmaz; DP (Laplace, ε) uygulanır.
     */
    public AggregatedSectorStats sectorContext(double myScore, String sector) {
        int tenantCount;
        if (sector != null && !sector.isBlank()) {
            Record r = fetchOne("""
                    SELECT COUNT(DISTINCT s.tenant_id)
                    FROM measure.scores s
                    JOIN config.brands b ON b.id = s.brand_id
                    WHERE b.sector = ?
                    """, sector);
            tenantCount = r == null ? 0 : num(r.get(0));
        } else {
            Record r = fetchOne("SELECT COUNT(DISTINCT tenant_id) FROM measure.scores");
            tenantCount = r == null ? 0 : num(r.get(0));
        }

        double avg = 0, min = 0, max = 0, median = 0, stddev = 0, p25 = 0, p75 = 0, p90 = 0;
        if (tenantCount >= dpCfg.minTenants) {
            String join = "";
            if (sector != null && !sector.isBlank()) {
                join = "JOIN config.brands b ON b.id = sub.brand_id WHERE b.sector = ?";
            }
            Record stats = fetchOne("""
                    SELECT AVG(sub.latest)::numeric(10,2)::double precision,
                           MIN(sub.latest)::numeric(10,2)::double precision,
                           MAX(sub.latest)::numeric(10,2)::double precision
                    FROM (
                        SELECT DISTINCT ON (brand_id) brand_id, value AS latest
                        FROM measure.scores
                        ORDER BY brand_id, freshness_at DESC
                    ) sub
                    """ + join, sector);
            if (stats != null) {
                avg = dbl(stats.get(0));
                min = dbl(stats.get(1));
                max = dbl(stats.get(2));
            }
            median = valueOf("""
                    WITH ranked AS (
                        SELECT value, ROW_NUMBER() OVER (ORDER BY value) AS rn,
                               COUNT(*) OVER () AS cnt
                        FROM (
                            SELECT DISTINCT ON (brand_id) brand_id, value
                            FROM measure.scores
                            ORDER BY brand_id, freshness_at DESC
                        ) sub
                        """ + join + """
                    )
                    SELECT AVG(value)::numeric(10,2)::double precision
                    FROM ranked
                    WHERE rn IN ((cnt + 1) / 2, (cnt + 2) / 2)
                    """, sector);
            stddev = valueOf("""
                    SELECT COALESCE(STDDEV(sub.latest)::numeric(10,2)::double precision, 0)
                    FROM (
                        SELECT DISTINCT ON (brand_id) brand_id, value AS latest
                        FROM measure.scores
                        ORDER BY brand_id, freshness_at DESC
                    ) sub
                    """ + join, sector);
            Record pct = fetchOne("""
                    WITH distinct_scores AS (
                        SELECT DISTINCT ON (brand_id) brand_id, value AS latest
                        FROM measure.scores
                        ORDER BY brand_id, freshness_at DESC
                    )
                    SELECT
                        PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY latest)::numeric(10,2)::double precision,
                        PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY latest)::numeric(10,2)::double precision,
                        PERCENTILE_CONT(0.90) WITHIN GROUP (ORDER BY latest)::numeric(10,2)::double precision
                    FROM distinct_scores
                    """ + join, sector);
            if (pct != null) {
                p25 = dbl(pct.get(0));
                p75 = dbl(pct.get(1));
                p90 = dbl(pct.get(2));
            }
        }

        RawSectorStats raw = new RawSectorStats(
                myScore, avg, median, min, max, stddev, p25, p75, p90, tenantCount);
        return AggregatedSectorStats.of(raw, dpCfg);
    }

    public int minTenants() {
        return dpCfg.minTenants;
    }

    private Record fetchOne(String sql, Object... args) {
        try {
            return dsl.fetchOne(sql, args);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private double valueOf(String sql, Object... args) {
        Record r = fetchOne(sql, args);
        return r == null ? 0 : dbl(r.get(0));
    }

    private static int num(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }

    private static double dbl(Object o) {
        return o == null ? 0 : ((Number) o).doubleValue();
    }
}
