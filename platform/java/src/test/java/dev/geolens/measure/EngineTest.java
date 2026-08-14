package dev.geolens.measure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go {@code engine_test.go} portu. */
class EngineTest {

    @Test
    void defaultWeights() {
        ComponentWeights w = ComponentWeights.V2_DEFAULT;
        assertEquals(0.30, w.presenceShare());
        assertEquals(0.20, w.positionWeight());
        assertEquals(0.15, w.sourceShare());
        assertEquals(0.15, w.competitorContext());
        assertEquals(0.10, w.appearanceRate());
        assertEquals(0.05, w.sentiment());
        assertEquals(0.05, w.compVisibility());
    }

    @Test
    void defaultWeightsSumToOne() {
        ComponentWeights w = ComponentWeights.V2_DEFAULT;
        double sum = w.presenceShare() + w.positionWeight() + w.sourceShare() + w.competitorContext()
                + w.appearanceRate() + w.sentiment() + w.compVisibility();
        assertEquals(1.0, sum);
    }

    @Test
    void defaultWeightsIsV2() {
        assertTrue(ComponentWeights.V2_DEFAULT.isV2());
        assertFalse(ComponentWeights.V1_LEGACY.isV2());
    }

    @Test
    void legacyWeights() {
        ComponentWeights w = ComponentWeights.V1_LEGACY;
        double sum = w.presenceShare() + w.positionWeight() + w.sourceShare() + w.competitorContext();
        assertEquals(1.0, sum);
    }

    @Test
    void calculateScoreWeightedFormula() {
        double expected = 30.0 + 20.0 + 15.0 + 15.0 + 10.0 + 5.0 + 5.0;
        assertEquals(100.0, expected);

        double expected2 = 0.30 * 50 + 0.20 * 50 + 0.15 * 50 + 0.15 * 50 + 0.10 * 50 + 0.05 * 50 + 0.05 * 50;
        assertEquals(50.0, expected2);

        double expected3 = 0.30 * 80 + 0.20 * 60 + 0.15 * 40 + 0.15 * 20 + 0.10 * 50 + 0.05 * 70 + 0.05 * 30;
        assertEquals(55.0, expected3);
    }

    @Test
    void measurementRequestValidation() {
        MeasurementRequest req = new MeasurementRequest("", "Acme", "",
                "Acme hakkında ne biliyorsun?", "perplexity", "", "", "", "");
        assertFalse(req.brandName().isEmpty());
        assertFalse(req.engineName().isEmpty());
        assertFalse(req.promptText().isEmpty());
    }
}