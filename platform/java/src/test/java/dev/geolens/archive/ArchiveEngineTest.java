package dev.geolens.archive;

import dev.geolens.testutil.JooqTestData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Go archive/engine.go davranış testleri — yanıt arşivleme + versiyonlama. */
class ArchiveEngineTest {

    @Test
    void archiveComputesHashAndNextVersion() {
        DSLContext dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        ArchiveEngine engine = new ArchiveEngine(dsl);

        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(Map.of("max", 2)));

        Entry e = engine.archive("b-1", "chatgpt", "selam", "yanıt içeriği", "ws-1", "T01");

        assertEquals("b-1", e.brandId());
        assertEquals("chatgpt", e.engineName());
        assertEquals(3, e.version(), "MAX(version)=2 → next=3");
        assertEquals(64, e.contentHash().length(), "SHA-256 hex 64 karakter");
        verify(dsl).execute(anyString(), any(Object[].class));
    }

    @Test
    void archiveVersionQueryErrorFallsBackTo1() {
        DSLContext dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        ArchiveEngine engine = new ArchiveEngine(dsl);

        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        Entry e = engine.archive("b-1", "gemini", "", "uzun yanıt", "ws-1", "T01");

        assertEquals(1, e.version(), "versiyon sorgusu hatasında 0'dan devam → next=1");
    }

    @Test
    void archiveTruncatesPreviewOver1000() {
        DSLContext dsl = mock(DSLContext.class, Answers.RETURNS_DEEP_STUBS);
        ArchiveEngine engine = new ArchiveEngine(dsl);

        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(Map.of("max", 0)));

        StringBuilder longResp = new StringBuilder();
        for (int i = 0; i < 1500; i++) {
            longResp.append('x');
        }
        Entry e = engine.archive("b-1", "perplexity", "", longResp.toString(), "ws-1", "T01");

        assertEquals(1000, e.responsePreview().length());
        assertEquals(1500, e.responseFull().length());
    }
}
