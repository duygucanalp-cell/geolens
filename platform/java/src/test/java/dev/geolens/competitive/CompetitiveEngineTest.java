package dev.geolens.competitive;

import dev.geolens.testutil.JooqTestData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Go competitive/engine.go davranış testleri — 5 gap türü + skor + kayıt. */
class CompetitiveEngineTest {

    @Test
    void analyzeAllGapsComputesGapsAndScore() {
        DSLContext dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        CompetitiveEngine engine = new CompetitiveEngine(dsl);

        // Marka adı + rakip sorgusu
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(Map.of("name", "Marka A")));
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(Map.of("id", "c-1", "name", "Rakip A"))));

        // gap hesaplama sorguları (10 adet: 5 gap × 2 taraf)
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(Map.of("name", "Marka A")), // marka adı
                        JooqTestData.record(Map.of("value", 80.0)), // visibility brand SOV
                        JooqTestData.record(Map.of("value", 60.0)), // visibility comp SOV
                        JooqTestData.record(Map.of("value", 30)),   // citation brand
                        JooqTestData.record(Map.of("value", 20)),   // citation comp
                        JooqTestData.record(Map.of("value", 50)),   // citation total
                        JooqTestData.record(Map.of("value", 5)),    // content brand domains
                        JooqTestData.record(Map.of("value", 2)),    // content comp domains
                        JooqTestData.record(Map.of("value", 80.0)), // topic brand
                        JooqTestData.record(Map.of("value", 60.0)), // topic comp
                        JooqTestData.record(Map.of("value", 8)),    // prompt brand jobs
                        JooqTestData.record(Map.of("value", 2)));   // prompt comp jobs

        List<GapSnapshot> snapshots = engine.analyzeAllGaps("b-1", "ws-1", "T01");

        assertEquals(1, snapshots.size());
        GapSnapshot s = snapshots.get(0);
        assertEquals("b-1", s.brandId());
        assertEquals("Marka A", s.brandName());
        assertEquals("c-1", s.competitorId());
        assertEquals("Rakip A", s.competitorName());

        // Visibility: gap = 80-60 = 20 → norm = 50 + 10 = 60, brand_ahead
        GapDetail vis = s.visibilityGap();
        assertEquals(20.0, vis.gapValue(), 1e-9);
        assertEquals(60.0, vis.normalized(), 1e-9);
        assertEquals("brand_ahead", vis.direction());

        // Citation: rates 30/50=60, 20/50=40 → gap 20 → norm 60
        GapDetail cit = s.citationGap();
        assertEquals(60.0, cit.brandValue(), 1e-9);
        assertEquals(40.0, cit.competitorValue(), 1e-9);
        assertEquals(60.0, cit.normalized(), 1e-9);

        // Content: gap = 5-2 = 3 → norm = 50 + 3/20*50 = 57.5, brand_ahead (>2)
        GapDetail con = s.contentGap();
        assertEquals(3.0, con.gapValue(), 1e-9);
        assertEquals(57.5, con.normalized(), 1e-9);
        assertEquals("brand_ahead", con.direction());

        // Topic: gap 20 → norm 60
        assertEquals(60.0, s.topicGap().normalized(), 1e-9);

        // Prompt: 8/10=80, 2/10=20 → gap 60 → norm 80
        GapDetail prm = s.promptGap();
        assertEquals(80.0, prm.brandValue(), 1e-9);
        assertEquals(20.0, prm.competitorValue(), 1e-9);
        assertEquals(80.0, prm.normalized(), 1e-9);

        // Skor: 60*.30 + 60*.25 + 57.5*.20 + 60*.15 + 80*.10 = 18+15+11.5+9+8 = 61.5
        assertEquals(61.5, s.competitiveScore(), 1e-9);

        // 1 snapshot insert + 5 öneri insert
        verify(dsl, times(6)).execute(anyString(), any(Object[].class));
    }

    @Test
    void analyzeAllGapsNoCompetitorsReturnsNull() {
        DSLContext dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        CompetitiveEngine engine = new CompetitiveEngine(dsl);

        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(Map.of("name", "Marka A")));
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of()));

        List<GapSnapshot> snapshots = engine.analyzeAllGaps("b-1", "ws-1", "T01");

        assertNull(snapshots);
        verify(dsl, times(0)).execute(anyString(), any(Object[].class));
    }

    @Test
    void analyzeAllGapsBrandNotFoundThrows() {
        DSLContext dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        CompetitiveEngine engine = new CompetitiveEngine(dsl);

        when(dsl.fetchOne(anyString(), any(Object[].class))).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> engine.analyzeAllGaps("b-1", "ws-1", "T01"));
    }

    @Test
    void getGapDetailComputesNormalizedAndDirection() {
        DSLContext dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        CompetitiveEngine engine = new CompetitiveEngine(dsl);

        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(Map.of("visibility_gap", 20.0)));

        GapDetail d = engine.getGapDetail("b-1", "c-1", "visibility", "T01");

        assertEquals(20.0, d.gapValue(), 1e-9);
        assertEquals(60.0, d.normalized(), 1e-9);
        assertEquals("brand_ahead", d.direction());
    }

    @Test
    void getGapDetailNullGapReturnsNull() {
        DSLContext dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        CompetitiveEngine engine = new CompetitiveEngine(dsl);

        when(dsl.fetchOne(anyString(), any(Object[].class))).thenReturn(null);

        assertNull(engine.getGapDetail("b-1", "c-1", "visibility", "T01"));
    }

    @Test
    void getGapDetailQueryErrorThrows() {
        DSLContext dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        CompetitiveEngine engine = new CompetitiveEngine(dsl);

        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db down"));

        assertThrows(IllegalArgumentException.class,
                () -> engine.getGapDetail("b-1", "c-1", "visibility", "T01"));
    }
}
