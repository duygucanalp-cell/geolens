package dev.geolens.benchmark;

import dev.geolens.testutil.JooqTestData;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Go benchmark/aggregator_test.go parity testleri — sektör toplulaştırma (NFR-13). */
class BenchmarkAggregatorTest {

    private static final DSLContext DSL = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);

    @Test
    void newAggregatorDefaultConfig() {
        BenchmarkAggregator a = new BenchmarkAggregator(DSL, null);
        assertEquals(1.0, a.minTenants() == 5 ? 1.0 : -1, 1e-9); // sanity: dpCfg defaults via merge
        assertEquals(5, a.minTenants());
    }

    @Test
    void newAggregatorCustomConfig() {
        DpConfig cfg = new DpConfig();
        cfg.epsilon = 2.0;
        cfg.minTenants = 3;
        BenchmarkAggregator a = new BenchmarkAggregator(DSL, cfg);
        assertEquals(3, a.minTenants());
    }

    @Test
    void aggregateInsufficientTenants() {
        // tenant_count=3 < 5 (NFR-13 eşiği) → hiçbir şey eklenmez
        stubFetchBySql(countRow(3, 5), null, null, null, null);

        BenchmarkAggregator a = new BenchmarkAggregator(DSL, null);
        String id = a.aggregate();
        assertEquals("", id, "yetersiz tenant ile id boş olmalı");
    }

    @Test
    void aggregateSufficientTenants() {
        stubFetchBySql(countRow(10, 20), statsRow(54.5, 12.0, 95.0, 14.2, 20),
                cols(52.0), percentilesRow(35.0, 68.0, 82.0), single("stats-001"));

        BenchmarkAggregator a = new BenchmarkAggregator(DSL, null);
        String id = a.aggregate();
        assertEquals("stats-001", id);
    }

    @Test
    void aggregateInsertError() {
        when(DSL.fetchOne(anyString(), any(Object[].class)))
                .thenAnswer(inv -> {
                    String sql = inv.getArgument(0);
                    if (sql.contains("COUNT(DISTINCT tenant_id)")) {
                        return JooqTestData.record(countRow(10, 20));
                    }
                    if (sql.contains("AVG(sub.latest)")) {
                        return JooqTestData.record(statsRow(54.5, 12.0, 95.0, 14.2, 20));
                    }
                    if (sql.contains("ROW_NUMBER()")) {
                        return JooqTestData.record(cols(52.0));
                    }
                    if (sql.contains("PERCENTILE_CONT")) {
                        return JooqTestData.record(percentilesRow(35.0, 68.0, 82.0));
                    }
                    if (sql.contains("INSERT INTO benchmark.industry_stats")) {
                        throw new RuntimeException("insert error");
                    }
                    return null;
                });

        BenchmarkAggregator a = new BenchmarkAggregator(DSL, null);
        String id = a.aggregate();
        assertEquals("", id, "hata durumunda id boş olmalı");
    }

    /** SQL içeriğine göre sıralı fetchOne sonuçlarını dağıtır (Go MockPool.QueryRowFunc switch deseni). */
    private static void stubFetchBySql(Map<String, Object> counts, Map<String, Object> stats,
                                       Map<String, Object> median, Map<String, Object> pct, Record insert) {
        when(DSL.fetchOne(anyString(), any(Object[].class)))
                .thenAnswer(inv -> {
                    String sql = inv.getArgument(0);
                    if (sql.contains("COUNT(DISTINCT tenant_id)")) {
                        return counts == null ? null : JooqTestData.record(counts);
                    }
                    if (sql.contains("AVG(sub.latest)")) {
                        return stats == null ? null : JooqTestData.record(stats);
                    }
                    if (sql.contains("ROW_NUMBER()")) {
                        return median == null ? null : JooqTestData.record(median);
                    }
                    if (sql.contains("PERCENTILE_CONT")) {
                        return pct == null ? null : JooqTestData.record(pct);
                    }
                    if (sql.contains("INSERT INTO benchmark.industry_stats")) {
                        return insert;
                    }
                    return null;
                });
    }

    @Test
    void getLatestSectorStatsSuccess() {
        when(DSL.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(latestRow()));

        BenchmarkAggregator a = new BenchmarkAggregator(DSL, null);
        AggregatedSectorStats stats = a.getLatestSectorStats();
        assertNotNull(stats);
        assertEquals(24, stats.tenantCount);
        assertEquals(54.5, stats.sectorAvg, 1e-9);
        assertTrue(stats.sufficientData, "24 tenant ile SufficientData true olmalı");
        assertEquals(0, stats.myScore, 1e-9);
    }

    @Test
    void getLatestSectorStatsNoData() {
        when(DSL.fetchOne(anyString(), any(Object[].class))).thenReturn(null);

        BenchmarkAggregator a = new BenchmarkAggregator(DSL, null);
        assertNull(a.getLatestSectorStats());
    }

    // ---------- yardımcılar ----------

    private static Map<String, Object> countRow(int tenants, int brands) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tenant_count", tenants);
        m.put("brand_count", brands);
        return m;
    }

    /** Çok sütunlu satır: sütunlar c0..cN anahtarlarıyla (get(0..N) sırası korunur). */
    private static Map<String, Object> cols(Object... vals) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < vals.length; i++) {
            m.put("c" + i, vals[i]);
        }
        return m;
    }

    private static Map<String, Object> statsRow(double avg, double min, double max, double stddev, int count) {
        return cols(avg, min, max, stddev, count);
    }

    private static Map<String, Object> percentilesRow(double p25, double p75, double p90) {
        return cols(p25, p75, p90);
    }

    private static Map<String, Object> latestRow() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tenant_count", 24);
        m.put("sector_avg", 54.5);
        m.put("sector_median", 52.0);
        m.put("sector_min", 12.0);
        m.put("sector_max", 95.0);
        m.put("sector_stddev", 14.2);
        m.put("percentile_25", 35.0);
        m.put("percentile_75", 68.0);
        m.put("percentile_90", 82.0);
        return m;
    }

    /** RETURNING id — INSERT sonucu tek sütunlu kayıt (Go MockRow{Values: []any{"stats-001"}} karşılığı). */
    private static Record single(Object v) {
        return JooqTestData.record(Map.of("id", v));
    }
}
