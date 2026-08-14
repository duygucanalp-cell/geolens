package dev.geolens.compliance;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go compliance boolToStatus/boolToEvidence/generateRecommendations testleri. */
class ComplianceEvaluatorTest {

    @Test
    void boolToStatusMaps() {
        assertEquals("passed", ComplianceEvaluator.boolToStatus(true));
        assertEquals("failed", ComplianceEvaluator.boolToStatus(false));
    }

    @Test
    void boolToEvidenceSelectsMessage() {
        assertEquals("ok", ComplianceEvaluator.boolToEvidence(true, "ok", "kötü"));
        assertEquals("kötü", ComplianceEvaluator.boolToEvidence(false, "ok", "kötü"));
    }

    @Test
    void recommendationsFromFailedControls() {
        List<Control> controls = List.of(
                new Control("CC1", "Control Environment", "Kiracı yönetimi", "Açıklama", "failed", "yok"),
                new Control("CC2", "Communication", "Bildirimler", "Açıklama", "passed", "var"));

        List<String> recs = ComplianceEvaluator.generateRecommendations(controls);
        assertEquals(1, recs.size());
        assertTrue(recs.get(0).contains("Kiracı yönetimi"));
    }

    @Test
    void recommendationsEmptyWhenAllPassed() {
        List<Control> controls = List.of(
                new Control("CC1", "C", "T", "D", "passed", "e"));

        List<String> recs = ComplianceEvaluator.generateRecommendations(controls);
        assertEquals(1, recs.size());
        assertTrue(recs.get(0).contains("hazırsınız"));
    }
}
