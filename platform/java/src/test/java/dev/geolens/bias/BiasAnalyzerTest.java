package dev.geolens.bias;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go bias handler_test.go — demographicParity/equalOpportunity/disparateImpact/formatPct parity testleri. */
class BiasAnalyzerTest {

    @Test
    void demographicParity() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("group_a", 0.9);
        data.put("group_b", 0.5);
        Map<String, Object> r = BiasAnalyzer.demographicParity(data);
        assertEquals(0.6, (Double) r.get("fairness_score"), 1e-9);
        assertEquals(true, r.get("has_bias"));
    }

    @Test
    void demographicParityEmpty() {
        Map<String, Object> r = BiasAnalyzer.demographicParity(new LinkedHashMap<>());
        assertEquals(1.0, (Double) r.get("fairness_score"), 1e-9);
    }

    @Test
    void equalOpportunity() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("group_a", 0.95);
        data.put("group_b", 0.92);
        Map<String, Object> r = BiasAnalyzer.equalOpportunity(data);
        double fs = (Double) r.get("fairness_score");
        assertTrue(fs >= 0.96 && fs <= 0.98, "expected ~0.97, got " + fs);
    }

    @Test
    void disparateImpact() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("protected_group_rate", 0.6);
        data.put("non_protected_group_rate", 0.9);
        Map<String, Object> r = BiasAnalyzer.disparateImpact(data);
        assertEquals(0.6666666666666666, (Double) r.get("fairness_score"), 1e-9);
        assertEquals(false, r.get("four_fifths_rule"));
    }

    @Test
    void computeBiasUnknownMetric() {
        Map<String, Object> r = BiasAnalyzer.compute("unknown", null);
        assertNotNull(r.get("error"));
    }

    @Test
    void computeBiasKnownMetrics() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("group_a", 0.8);
        data.put("group_b", 0.6);
        Map<String, Object> dp = BiasAnalyzer.compute("demographic_parity", data);
        assertNull(dp.get("error"));
        assertEquals("demographic_parity", dp.get("metric_type"));
    }

    @Test
    void formatPct() {
        assertEquals("25.6%", BiasAnalyzer.formatPct(0.256));
    }
}
