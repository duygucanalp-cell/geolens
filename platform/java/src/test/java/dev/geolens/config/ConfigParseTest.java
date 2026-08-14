package dev.geolens.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go config/intent_scale_test.go parity testleri. */
class ConfigParseTest {

    @Test
    void parseIntentWeightScaleValid() {
        String raw = "information=1.25,1.00,0.90,0.90,1.10,0.90,0.90;opinion=1.00,1.00,1.00,1.00,1.25,1.00,1.00";
        Map<String, double[]> scale = Config.parseIntentWeightScaleRaw(raw);
        assertTrue(scale != null, "geçerli girdi null olmamalı");
        assertEquals(2, scale.size(), "beklenen 2 intent");
        assertEquals(1.25, scale.get("information")[0], 1e-9);
        assertEquals(0.90, scale.get("information")[6], 1e-9);
        assertEquals(1.25, scale.get("opinion")[4], 1e-9, "opinion appearance çarpanı 1.25 olmalı");
    }

    @Test
    void parseIntentWeightScaleEmpty() {
        assertNull(Config.parseIntentWeightScaleRaw(""), "boş girdi null olmalı");
        assertNull(Config.parseIntentWeightScaleRaw("   "), "boşluklu girdi null olmalı");
    }

    @Test
    void parseIntentWeightScaleInvalid() {
        String[] cases = {
                "presence=1.25",
                "presence=1.25,1,0.9,0.9,1.1,0.9",
                "presence=abc,1,0.9,0.9,1.1,0.9,1",
                "presence=-1,1,0.9,0.9,1.1,0.9,1",
                "presence=NaN,1,0.9,0.9,1.1,0.9,1",
                "presence=1,1,0.9,0.9,1.1,0.9,Inf",
                "presence=1,1,0.9,0.9,1.1,0.9,1;=",
                "sadece=1,1,1,1,1,1,1;sadece",
        };
        for (String c : cases) {
            assertNull(Config.parseIntentWeightScaleRaw(c), "geçersiz girdi null olmamalı: " + c);
        }
    }

    @Test
    void parseIntentWeightScaleConfigMethod() {
        Config c = new Config();
        c.intentWeightScaleRaw = "problem=1.00,1.15,1.10,1.00,0.90,1.00,1.00";
        Map<String, double[]> scale = c.parseIntentWeightScale();
        assertTrue(scale != null, "Config metodu geçerli girdiyi kabul etmeli");
        assertEquals(1.15, scale.get("problem")[1], 1e-9, "problem konum çarpanı 1.15 olmalı");
    }

    @Test
    void parseStripePriceIdsValid() {
        Config c = new Config();
        c.stripePriceIdsRaw = "pro=price_1xxx,business=price_2xxx,enterprise=price_3xxx";
        Map<String, String> map = c.parseStripePriceIds();
        assertEquals(3, map.size());
        assertEquals("price_1xxx", map.get("pro"));
        assertEquals("price_3xxx", map.get("enterprise"));
    }

    @Test
    void parseStripePriceIdsEmpty() {
        Config c = new Config();
        assertNull(c.parseStripePriceIds(), "boş girdi null olmalı");
    }

    @Test
    void parseStripePriceIdsInvalidPairsSkipped() {
        Config c = new Config();
        c.stripePriceIdsRaw = "pro=price_1xxx,bozuk-satir,enterprise=price_3xxx";
        Map<String, String> map = c.parseStripePriceIds();
        assertEquals(2, map.size());
        assertEquals("price_1xxx", map.get("pro"));
        assertEquals("price_3xxx", map.get("enterprise"));
        assertFalse(map.containsKey("bozuk-satir"));
    }
}
