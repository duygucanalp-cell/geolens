package dev.geolens.policy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go policy/handler_test.go parity testleri — framework control tanımları. */
class PolicySeederTest {

    @Test
    void frameworkControlsMinCounts() {
        // Go TestFrameworkControls tablosu birebir
        assertMinControls("eu_ai_act", 7);
        assertMinControls("nist_ai_rmf", 7);
        assertMinControls("kvkk", 6);
        assertMinControls("iso_42001", 6);
        assertMinControls("custom", 1);
    }

    @Test
    void euAiActControls() {
        List<ControlDef> ctls = PolicySeeder.frameworkControls("eu_ai_act");
        assertEquals(7, ctls.size());
        assertEquals("Art.9", ctls.get(0).id());
        assertEquals("Risk Yönetim Sistemi", ctls.get(0).title());
        assertEquals("Risk Management", ctls.get(0).category());
        assertEquals("Art.15", ctls.get(6).id());
    }

    @Test
    void defaultFrameworksFourEntries() {
        assertEquals(4, PolicySeeder.defaultFrameworks().size());
        assertEquals("eu_ai_act", PolicySeeder.defaultFrameworks().get(0).framework());
        assertEquals("iso_42001", PolicySeeder.defaultFrameworks().get(3).framework());
    }

    private static void assertMinControls(String framework, int minCtls) {
        List<ControlDef> ctls = PolicySeeder.frameworkControls(framework);
        assertTrue(ctls.size() >= minCtls, framework + ": en az " + minCtls + " kontrol beklenir, gelen: " + ctls.size());
    }
}
