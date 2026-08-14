package dev.geolens.drift;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go drift handler_test.go — computeDriftScore/severityFor/driftIdempotencyKey parity testleri. */
class DriftAnalyzerTest {

    @Test
    void computeDriftScoreMaxShift() {
        DriftAnalyzer.DriftResult res = DriftAnalyzer.computeDriftScore(
                List.of(10.0, 10.0, 10.0), List.of(50.0, 50.0, 50.0));
        assertTrue(res.score() > 0, "expected positive score");
        assertEquals(40, res.delta(), 1e-9);
        assertEquals(10, res.refMean(), 1e-9);
        assertEquals(50, res.curMean(), 1e-9);
    }

    @Test
    void computeDriftScoreNoDrift() {
        DriftAnalyzer.DriftResult res = DriftAnalyzer.computeDriftScore(
                List.of(10.0, 11.0, 12.0), List.of(10.0, 11.0, 12.0));
        assertEquals(0, res.score(), 1e-9);
        assertEquals(0, res.delta(), 1e-9);
    }

    @Test
    void computeDriftScoreEmptyInput() {
        DriftAnalyzer.DriftResult res = DriftAnalyzer.computeDriftScore(List.of(), List.of(1.0, 2.0));
        assertEquals(0, res.score(), 1e-9);
    }

    @Test
    void severityFor() {
        assertEquals("info", DriftAnalyzer.severityFor(10));
        assertEquals("warning", DriftAnalyzer.severityFor(30));
        assertEquals("critical", DriftAnalyzer.severityFor(70));
    }

    @Test
    void driftIdempotencyKeyDeterministic() {
        String k1 = DriftAnalyzer.driftIdempotencyKey("brand-1", "visibility_score", 42.5, 3.25);
        String k2 = DriftAnalyzer.driftIdempotencyKey("brand-1", "visibility_score", 42.5, 3.25);
        assertEquals(k1, k2);

        String k3 = DriftAnalyzer.driftIdempotencyKey("brand-2", "visibility_score", 42.5, 3.25);
        String k4 = DriftAnalyzer.driftIdempotencyKey("brand-1", "refusal_rate", 42.5, 3.25);
        String k5 = DriftAnalyzer.driftIdempotencyKey("brand-1", "visibility_score", 60.0, 3.25);
        assertNotEquals(k1, k3);
        assertNotEquals(k1, k4);
        assertNotEquals(k1, k5);

        assertTrue(k1.startsWith("drift:"), "unexpected key format: " + k1);
    }
}
