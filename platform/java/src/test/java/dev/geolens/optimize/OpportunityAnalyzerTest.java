package dev.geolens.optimize;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go optimize/opportunity_test.go parity testleri — Opportunity Scoring. */
class OpportunityAnalyzerTest {

    @Test
    void opportunityScoreFormula() {
        // 9 × 8 × 0.95 = 68.4
        assertEquals(68.4, OpportunityAnalyzer.opportunityScore(9, 8, 0.95), 1e-9);
    }

    @Test
    void opportunityScoreRange() {
        double lo = OpportunityAnalyzer.opportunityScore(1, 1, 0.0);
        double hi = OpportunityAnalyzer.opportunityScore(10, 10, 1.0);
        assertTrue(lo >= 0 && hi <= 100, "score out of range: lo=" + lo + " hi=" + hi);
        assertEquals(100.0, hi, 1e-9);
    }

    @Test
    void impactIntMapping() {
        assertEquals(10, OpportunityAnalyzer.impactInt("critical"));
        assertEquals(9, OpportunityAnalyzer.impactInt("high"));
        assertEquals(6, OpportunityAnalyzer.impactInt("medium"));
        assertEquals(3, OpportunityAnalyzer.impactInt("low"));
        assertEquals(5, OpportunityAnalyzer.impactInt("unknown"));
    }

    @Test
    void urgencyFromEffortMapping() {
        assertEquals(9, OpportunityAnalyzer.urgencyFromEffort("high"));
        assertEquals(7, OpportunityAnalyzer.urgencyFromEffort("medium"));
        assertEquals(4, OpportunityAnalyzer.urgencyFromEffort("low"));
        assertEquals(5, OpportunityAnalyzer.urgencyFromEffort("unknown"));
    }

    @Test
    void analyzeWithFewScoresIncludesMeasurement() {
        List<Map<String, Object>> recs = OpportunityAnalyzer.analyze(0);
        assertEquals(4, recs.size(), "scoreCount=0 → 4 öneri");
    }

    @Test
    void analyzeWithManyScoresSkipsMeasurement() {
        List<Map<String, Object>> recs = OpportunityAnalyzer.analyze(10);
        assertEquals(3, recs.size(), "scoreCount>=5 → 3 öneri");
    }

    @Test
    void analyzeUsesOpportunityScore() {
        List<Map<String, Object>> recs = OpportunityAnalyzer.analyze(0);
        for (Map<String, Object> rec : recs) {
            Object v = rec.get("score_potential");
            assertTrue(v instanceof Double, "score_potential Double olmalı, gelen: " + (v == null ? null : v.getClass()));
            double d = (Double) v;
            assertTrue(d > 0 && d <= 100, "score_potential 0-100 aralığında olmalı: " + d);
        }
    }
}
