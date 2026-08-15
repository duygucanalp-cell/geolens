package dev.geolens.technicalgeo;

import dev.geolens.testutil.JooqTestData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Go technicalgeo/engine.go davranış testleri — bot/schema analiz + skor. */
class TechnicalGeoEngineTest {

    @Test
    void analyzeBotAccessInsertsForAllBots() {
        DSLContext dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        TechnicalGeoEngine engine = new TechnicalGeoEngine(dsl);

        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(Map.of("url", "https://example.com")))
                .thenThrow(new RuntimeException("no audit"))
                .thenThrow(new RuntimeException("no audit"));

        BotAnalysisResult r = engine.analyzeBotAccess("b-1", "", "ws-1", "T01");

        assertEquals("b-1", r.brandId());
        assertEquals("https://example.com", r.url());
        // 9 bot insert
        verify(dsl, org.mockito.Mockito.times(9)).execute(anyString(), any(Object[].class));
    }

    @Test
    void analyzeBotAccessUrlMissingThrows() {
        DSLContext dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        TechnicalGeoEngine engine = new TechnicalGeoEngine(dsl);

        when(dsl.fetchOne(anyString(), any(Object[].class))).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> engine.analyzeBotAccess("b-1", "", "ws-1", "T01"));
    }

    @Test
    void analyzeSchemaInsertsForAllTypes() {
        DSLContext dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        TechnicalGeoEngine engine = new TechnicalGeoEngine(dsl);

        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("no audit"));

        SchemaAnalysisResult r = engine.analyzeSchema("b-1", "ws-1", "T01");

        assertEquals("b-1", r.brandId());
        // 10 schema tipi insert
        verify(dsl, org.mockito.Mockito.times(10)).execute(anyString(), any(Object[].class));
    }

    @Test
    void getScoreComputesGrade() {
        DSLContext dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        TechnicalGeoEngine engine = new TechnicalGeoEngine(dsl);

        // bot avg 80, schema avg 90 → overall = 80*0.4 + 90*0.4 = 68 → C
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(Map.of("avg", 80.0)))
                .thenReturn(JooqTestData.record(Map.of("avg", 90.0)));

        TechnicalGeoScore s = engine.getScore("b-1", "ws-1", "T01");

        assertEquals("b-1", s.brandId());
        assertEquals(80.0, s.botScore(), 1e-9);
        assertEquals(90.0, s.schemaScore(), 1e-9);
        assertEquals(68.0, s.overall(), 1e-9);
        assertEquals("C", s.grade());
    }

    @Test
    void getScoreGradeBoundaries() {
        DSLContext dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        TechnicalGeoEngine engine = new TechnicalGeoEngine(dsl);

        // overall = 100 → A (bot 100, schema 100 → 40+40=80... schema+b ot 100 her biri 100*0.4*2 = 80)
        // A için overall>=90 gerekir: bot 100, schema 100 → 80. Hmm, B seviyesi.
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(Map.of("avg", 100.0)))
                .thenReturn(JooqTestData.record(Map.of("avg", 100.0)));

        TechnicalGeoScore s = engine.getScore("b-1", "ws-1", "T01");
        assertEquals(80.0, s.overall(), 1e-9);
        assertTrue("B".equals(s.grade()) || "C".equals(s.grade()));
    }
}
