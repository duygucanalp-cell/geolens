package dev.geolens.benchmark;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go benchmark/privacy_test.go parity testleri — differansiyel gizlilik. */
class PrivacyTest {

    @Test
    void defaultDpConfigValues() {
        DpConfig cfg = DpConfig.defaults();
        assertEquals(1.0, cfg.epsilon, 1e-9);
        assertEquals(100.0, cfg.sensitivity, 1e-9);
        assertEquals(0.0, cfg.clampMin, 1e-9);
        assertEquals(100.0, cfg.clampMax, 1e-9);
        assertEquals(5, cfg.minTenants);
    }

    @Test
    void clampLowerBound() {
        assertEquals(0.0, Privacy.clamp(-5.0, 0.0, 100.0), 1e-9);
    }

    @Test
    void clampUpperBound() {
        assertEquals(100.0, Privacy.clamp(150.0, 0.0, 100.0), 1e-9);
    }

    @Test
    void clampWithinRange() {
        assertEquals(50.0, Privacy.clamp(50.0, 0.0, 100.0), 1e-9);
    }

    @Test
    void addLaplaceNoiseZeroEpsilon() {
        DpConfig cfg = new DpConfig();
        cfg.epsilon = 0;
        cfg.clampMin = 0;
        cfg.clampMax = 100;
        assertEquals(50.0, Privacy.addLaplaceNoise(50.0, cfg), 1e-9);
    }

    @Test
    void addLaplaceNoiseNegativeEpsilon() {
        DpConfig cfg = new DpConfig();
        cfg.epsilon = -1;
        cfg.clampMin = 0;
        cfg.clampMax = 100;
        assertEquals(50.0, Privacy.addLaplaceNoise(50.0, cfg), 1e-9);
    }

    @Test
    void addLaplaceNoiseClampsToMin() {
        DpConfig cfg = new DpConfig();
        cfg.epsilon = 0.01;
        cfg.sensitivity = 100;
        cfg.clampMin = 0;
        cfg.clampMax = 100;
        assertTrue(Privacy.addLaplaceNoise(-50.0, cfg) >= 0.0);
    }

    @Test
    void addLaplaceNoiseClampsToMax() {
        DpConfig cfg = new DpConfig();
        cfg.epsilon = 0.01;
        cfg.sensitivity = 100;
        cfg.clampMin = 0;
        cfg.clampMax = 100;
        assertTrue(Privacy.addLaplaceNoise(200.0, cfg) <= 100.0);
    }

    @Test
    void laplaceRandomFinite() {
        for (int i = 0; i < 1000; i++) {
            double v = Privacy.laplaceRandom(1.0);
            assertTrue(!Double.isInfinite(v) && !Double.isNaN(v), "geçersiz değer: " + v);
        }
    }

    @Test
    void laplaceRandomMeanNearZero() {
        final int samples = 10000;
        double sum = 0;
        for (int i = 0; i < samples; i++) {
            sum += Privacy.laplaceRandom(1.0);
        }
        double mean = sum / samples;
        assertTrue(Math.abs(mean) < 0.3, "Laplace mean 0'a yakın olmalı, gerçek " + mean);
    }

    @Test
    void laplaceRandomScale() {
        final int samples = 10000;
        int outOfRange = 0;
        for (int i = 0; i < samples; i++) {
            double v = Privacy.laplaceRandom(1.0);
            if (v < -5 || v > 5) {
                outOfRange++;
            }
        }
        double ratio = (double) outOfRange / samples;
        assertTrue(ratio < 0.05, "%5'ten azı [-5,5] dışında olmalı: " + ratio);
    }

    @Test
    void addLaplaceNoiseStaysInRange() {
        DpConfig cfg = DpConfig.defaults();
        for (int i = 0; i < 1000; i++) {
            double result = Privacy.addLaplaceNoise(50.0, cfg);
            assertTrue(result >= 0 && result <= 100, "clamp başarısız: " + result);
        }
    }

    @Test
    void addLaplaceNoiseEpsilonEffect() {
        DpConfig high = new DpConfig();
        high.epsilon = 10.0;
        high.sensitivity = 100;
        high.clampMin = 0;
        high.clampMax = 100;
        DpConfig low = new DpConfig();
        low.epsilon = 0.1;
        low.sensitivity = 100;
        low.clampMin = 0;
        low.clampMax = 100;

        final int samples = 5000;
        double highSum = 0, lowSum = 0;
        for (int i = 0; i < samples; i++) {
            highSum += Math.abs(Privacy.addLaplaceNoise(50.0, high) - 50.0);
            lowSum += Math.abs(Privacy.addLaplaceNoise(50.0, low) - 50.0);
        }
        double highAvgDev = highSum / samples;
        double lowAvgDev = lowSum / samples;
        assertTrue(lowAvgDev > highAvgDev,
                "düşük epsilon daha fazla gürültü eklemeli: low=" + lowAvgDev + " high=" + highAvgDev);
    }

    // ---------- AnonymizeSectorStats ----------

    @Test
    void anonymizeInsufficientData() {
        DpConfig cfg = DpConfig.defaults();
        RawSectorStats raw = new RawSectorStats(72.0, 54.0, 52.0, 12.0, 95.0, 0, 0, 0, 0, 3);
        AggregatedSectorStats stats = AggregatedSectorStats.of(raw, cfg);
        assertFalse(stats.sufficientData, "yetersiz veri için SufficientData false olmalı");
        assertEquals(72.0, stats.myScore, 1e-9);
        assertEquals(3, stats.tenantCount);
        assertEquals(0, stats.sectorAvg, 1e-9);
        assertEquals(0, stats.sectorMedian, 1e-9);
    }

    @Test
    void anonymizeAtThreshold() {
        DpConfig cfg = DpConfig.defaults();
        RawSectorStats raw = new RawSectorStats(
                72.0, 54.0, 52.0, 12.0, 95.0, 14.2, 35.0, 68.0, 82.0, 5);
        AggregatedSectorStats stats = AggregatedSectorStats.of(raw, cfg);
        assertTrue(stats.sufficientData, "5 kiracı ile SufficientData true olmalı");
        assertEquals(72.0, stats.myScore, 1e-9);
        assertEquals(5, stats.tenantCount);
        assertTrue(stats.sectorAvg >= 0 && stats.sectorAvg <= 100);
        assertTrue(stats.sectorMin >= 0 && stats.sectorMax <= 100);
        assertTrue(stats.percentile25 >= 0 && stats.percentile90 <= 100);
        assertEquals(stats.myScore - stats.sectorAvg, stats.difference, 1e-9);
    }

    @Test
    void anonymizeMyScoreUnchanged() {
        DpConfig cfg = DpConfig.defaults();
        RawSectorStats raw = new RawSectorStats(42.5, 50.0, 48.0, 10.0, 90.0, 0, 0, 0, 0, 50);
        AggregatedSectorStats stats = AggregatedSectorStats.of(raw, cfg);
        assertEquals(42.5, stats.myScore, 1e-9);
    }

    @Test
    void anonymizeTrendDetermination() {
        DpConfig cfg = new DpConfig();
        cfg.epsilon = 100.0;
        cfg.sensitivity = 100;
        cfg.clampMin = 0;
        cfg.clampMax = 100;
        cfg.minTenants = 5;

        final int iterations = 50;

        RawSectorStats above = new RawSectorStats(80.0, 20.0, 0, 0, 0, 0, 0, 0, 0, 10);
        int upCount = 0;
        for (int i = 0; i < iterations; i++) {
            if ("up".equals(AggregatedSectorStats.of(above, cfg).trend)) {
                upCount++;
            }
        }
        assertTrue((double) upCount / iterations >= 0.90, "trend 'up' oranı düşük: " + upCount + "/" + iterations);

        RawSectorStats below = new RawSectorStats(20.0, 80.0, 0, 0, 0, 0, 0, 0, 0, 10);
        int downCount = 0;
        for (int i = 0; i < iterations; i++) {
            if ("down".equals(AggregatedSectorStats.of(below, cfg).trend)) {
                downCount++;
            }
        }
        assertTrue((double) downCount / iterations >= 0.90, "trend 'down' oranı düşük: " + downCount + "/" + iterations);

        RawSectorStats stable = new RawSectorStats(50.1, 50.0, 0, 0, 0, 0, 0, 0, 0, 10);
        int stableCount = 0;
        for (int i = 0; i < iterations; i++) {
            if ("stable".equals(AggregatedSectorStats.of(stable, cfg).trend)) {
                stableCount++;
            }
        }
        assertTrue((double) stableCount / iterations >= 0.85, "trend 'stable' oranı düşük: " + stableCount + "/" + iterations);
    }

    @Test
    void anonymizeEdgeCaseZeroTenants() {
        DpConfig cfg = DpConfig.defaults();
        RawSectorStats raw = new RawSectorStats(50.0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        AggregatedSectorStats stats = AggregatedSectorStats.of(raw, cfg);
        assertFalse(stats.sufficientData, "0 kiracı ile SufficientData false olmalı");
    }
}
