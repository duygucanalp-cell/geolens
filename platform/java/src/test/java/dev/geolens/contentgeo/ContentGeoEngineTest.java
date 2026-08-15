package dev.geolens.contentgeo;

import dev.geolens.testutil.JooqTestData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Go contentgeo/engine.go davranış testleri — content gap + hub skoru. */
class ContentGeoEngineTest {

    @Test
    void analyzeContentGapIdentifiesBlogGapAndSaves() {
        DSLContext dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        ContentGeoEngine engine = new ContentGeoEngine(dsl);

        // Yalnızca 10 blog alıntısı → blog gap = 1 - 10/100 = 0.9,
        // diğer 4 türde alıntı yok → gap = 1.0 (Go birebir: hepsi > 0.5)
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(citationRow("example.com/blog", 10))));

        List<ContentGapResult> gaps = engine.analyzeContentGap("b-1", "ws-1", "T01");

        assertEquals(5, gaps.size());
        ContentGapResult g = gaps.stream().filter(x -> "blog".equals(x.gapType())).findFirst().orElseThrow();
        assertEquals(0.9, g.gapScore(), 1e-9);
        assertTrue(g.description().contains("Blog/Makale"));
        assertTrue(g.recommendation().contains("Blog/Makale"));
        // Kayıt — priority high (>0.7)
        verify(dsl, times(5)).execute(anyString(), any(Object[].class));
    }

    @Test
    void analyzeContentGapNoGapsReturnsGeneral() {
        DSLContext dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        ContentGeoEngine engine = new ContentGeoEngine(dsl);

        // Tüm türler iyi temsil ediliyor (60 > eşik) → gap yok → general
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(
                        citationRow("example.com/blog", 60),
                        citationRow("example.com/product", 60),
                        citationRow("example.com/faq", 60),
                        citationRow("example.com/news", 60),
                        citationRow("example.com/category", 60))));

        List<ContentGapResult> gaps = engine.analyzeContentGap("b-1", "ws-1", "T01");

        assertEquals(1, gaps.size());
        ContentGapResult g = gaps.get(0);
        assertEquals("general", g.gapType());
        assertEquals(0.3, g.gapScore(), 1e-9);
        assertTrue(g.description().contains("Genel içerik kapsamı yeterli"));
    }

    @Test
    void analyzeContentGapCitationQueryErrorThrows() {
        DSLContext dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        ContentGeoEngine engine = new ContentGeoEngine(dsl);

        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db down"));

        assertThrows(IllegalArgumentException.class,
                () -> engine.analyzeContentGap("b-1", "ws-1", "T01"));
    }

    @Test
    void getContentHubScoreComputesGrade() {
        DSLContext dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        ContentGeoEngine engine = new ContentGeoEngine(dsl);

        // 6 gap türü, avg 0.2, 5 kaynak → topicCoverage = 6/7*100 = 85.71,
        // sourceDiversity = 50, authority = 20 (index okuma — sıralı map)
        Map<String, Object> agg = new LinkedHashMap<>();
        agg.put("topic_count", 6);
        agg.put("avg", 0.2);
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(agg))
                .thenReturn(JooqTestData.record(Map.of("source_count", 5)));

        ContentHubScore s = engine.getContentHubScore("b-1", "ws-1", "T01");

        assertEquals("b-1", s.brandId());
        assertEquals(85.714, s.topicCoverage(), 1e-3);
        assertEquals(50.0, s.sourceDiversity(), 1e-9);
        assertEquals(20.0, s.authorityScore(), 1e-9);
        // oppGap = 100 - (85.714*.4 + 50*.3 + .2*.3) = 100 - (34.29+15+0.06) = 50.66
        // overall = 49.34 → D (>=40)
        assertEquals(49.34, s.overall(), 1e-2);
        assertEquals("D", s.grade());
    }

    @Test
    void getContentHubScoreQueryErrorsTolerated() {
        DSLContext dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        ContentGeoEngine engine = new ContentGeoEngine(dsl);

        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db down"));

        // Hata yutulur → tüm değerler 0 → overall 0 → F
        ContentHubScore s = engine.getContentHubScore("b-1", "ws-1", "T01");

        assertEquals(0.0, s.overall(), 1e-9);
        assertEquals("F", s.grade());
    }

    private static Map<String, Object> citationRow(String domain, int count) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source_domain", domain);
        m.put("citation_count", count);
        m.put("last_cited_at", "2026-08-15T10:00:00Z");
        return m;
    }
}
