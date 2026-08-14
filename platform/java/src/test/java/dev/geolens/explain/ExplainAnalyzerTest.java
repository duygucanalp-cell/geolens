package dev.geolens.explain;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go explain/handler_test.go parity testleri — feature importance hesapları. */
class ExplainAnalyzerTest {

    @Test
    void computeFeatureImportanceHighRisk() {
        Map<String, Double> w = ExplainAnalyzer.computeFeatureImportance("high", 0.75);
        assertEquals(0.30, w.get("citation_accuracy"), 1e-9);
        assertEquals(0.25, w.get("ai_visibility_score"), 1e-9);
        assertEquals(0.20, w.get("response_quality"), 1e-9);
        assertEquals(0.15, w.get("brand_consistency"), 1e-9);
        assertEquals(0.10, w.get("sentiment_score"), 1e-9);
    }

    @Test
    void computeFeatureImportanceLowRisk() {
        Map<String, Double> w = ExplainAnalyzer.computeFeatureImportance("low", 0.75);
        assertEquals(0.20, w.get("citation_accuracy"), 1e-9);
        assertEquals(0.35, w.get("ai_visibility_score"), 1e-9);
        assertEquals(0.25, w.get("response_quality"), 1e-9);
        assertEquals(0.12, w.get("brand_consistency"), 1e-9);
        assertEquals(0.08, w.get("sentiment_score"), 1e-9);
    }

    @Test
    void computeFeatureImportanceCriticalTreatedAsHigh() {
        Map<String, Double> w = ExplainAnalyzer.computeFeatureImportance("critical", 0.9);
        assertEquals(0.30, w.get("citation_accuracy"), 1e-9);
    }

    @Test
    void computeFeatureImportanceLowConfidence() {
        Map<String, Double> w = ExplainAnalyzer.computeFeatureImportance("low", 0.3);
        assertTrue(w.get("brand_consistency") < 0.12, "low confidence: brand_consistency < 0.12 beklenir");
        // ai_visibility_score, brand_consistency'den aktarılan payla artar
        assertEquals(0.35 + (0.12 - 0.12 * 0.3), w.get("ai_visibility_score"), 1e-9);
    }

    @Test
    void computeFeatureImportanceZeroConfidenceUnchanged() {
        Map<String, Double> w = ExplainAnalyzer.computeFeatureImportance("low", 0.0);
        assertEquals(0.12, w.get("brand_consistency"), 1e-9);
    }
}
