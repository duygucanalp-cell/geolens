package dev.geolens.replay;

import dev.geolens.testutil.JooqTestData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Go replay/engine.go davranış testleri — snapshot + karşılaştırma. */
class ReplayEngineTest {

    @Test
    void captureSnapshotUsesLatestEngineResponse() {
        DSLContext dsl = org.mockito.Mockito.mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        ReplayEngine engine = new ReplayEngine(dsl);

        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(respRow("chatgpt", "Merhaba dünya", "raw-1"))));

        Snapshot s = engine.captureSnapshot("b-1", "selam", "ws-1", "T01");

        assertEquals("b-1", s.brandId());
        assertEquals("selam", s.promptText());
        assertEquals("chatgpt", s.engineName());
        assertEquals("Merhaba dünya", s.responsePreview());
        assertEquals("Merhaba dünya", s.responseFull());
        // SHA-256 hash doğrula
        assertEquals(64, s.contentHash().length());
        verify(dsl).execute(anyString(), any(Object[].class));
    }

    @Test
    void captureSnapshotTruncatesPreviewOver500() {
        DSLContext dsl = org.mockito.Mockito.mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        ReplayEngine engine = new ReplayEngine(dsl);

        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            longContent.append('x');
        }
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(respRow("gemini", longContent.toString(), "raw-2"))));

        Snapshot s = engine.captureSnapshot("b-1", "p", "ws-1", "T01");

        assertEquals(500, s.responsePreview().length());
        assertEquals(600, s.responseFull().length());
    }

    @Test
    void captureSnapshotNoResponsesThrows() {
        DSLContext dsl = org.mockito.Mockito.mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        ReplayEngine engine = new ReplayEngine(dsl);

        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of()));

        assertThrows(IllegalArgumentException.class,
                () -> engine.captureSnapshot("b-1", "p", "ws-1", "T01"));
    }

    @Test
    void compareIdenticalSnapshotsNoChange() {
        DSLContext dsl = org.mockito.Mockito.mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        ReplayEngine engine = new ReplayEngine(dsl);

        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(snapRow("b-1", "p", "chatgpt", "aynı içerik")));

        DiffResult d = engine.compare("s-a", "s-b", "ws-1", "T01");

        assertFalse(d.hasChanged());
        assertEquals("", d.changes());
        assertEquals("s-a", d.snapshotA());
        assertEquals("s-b", d.snapshotB());
    }

    @Test
    void compareDifferentSnapshotsDetectsChange() {
        DSLContext dsl = org.mockito.Mockito.mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        ReplayEngine engine = new ReplayEngine(dsl);

        // Sıralı: ilk çağrı snapshot A, ikinci çağrı snapshot B
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(snapRow("b-1", "p", "chatgpt", "aynı içerik")))
                .thenReturn(JooqTestData.record(snapRow("b-1", "p", "chatgpt", "farklı içerik")));

        DiffResult d = engine.compare("s-a", "s-b", "ws-1", "T01");

        assertTrue(d.hasChanged());
        assertTrue(d.changes().contains("değişmiş"));
    }

    @Test
    void compareMissingSnapshotThrows() {
        DSLContext dsl = org.mockito.Mockito.mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        ReplayEngine engine = new ReplayEngine(dsl);

        when(dsl.fetchOne(anyString(), any(Object[].class))).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> engine.compare("s-a", "s-b", "ws-1", "T01"));
    }

    // ---------- yardımcılar ----------

    private static Map<String, Object> respRow(String engine, String content, String id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("engine_name", engine);
        m.put("content_text", content);
        m.put("id", id);
        return m;
    }

    private static Map<String, Object> snapRow(String brandId, String prompt, String engine, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("brand_id", brandId);
        m.put("prompt_text", prompt);
        m.put("engine_name", engine);
        m.put("content", content);
        return m;
    }
}
