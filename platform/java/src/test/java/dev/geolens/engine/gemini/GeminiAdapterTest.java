package dev.geolens.engine.gemini;

import dev.geolens.engine.Adapter;
import dev.geolens.engine.EngineException;
import dev.geolens.engine.RawResponse;
import dev.geolens.engine.Tier;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Go {@code gemini/adapter_test.go} portu. */
class GeminiAdapterTest {

    @Test
    void name() {
        GeminiAdapter a = new GeminiAdapter("test-key", null);
        assertEquals("gemini", a.name());
    }

    @Test
    void tier() {
        GeminiAdapter a = new GeminiAdapter("test-key", null);
        assertEquals(Tier.DIRECT, a.tier());
    }

    @Test
    void withContextKeepsName() {
        GeminiAdapter a = new GeminiAdapter("test-key", null);
        assertEquals("gemini", a.withContext("tenant-1", "ws-1").name());
    }

    @Test
    void aiOverviewWithContextPreservesWrapper() {
        GeminiAdapter a = new GeminiAdapter("test-key", null);
        Adapter overview = a.withAIOverview("", "");
        assertEquals("google_ai_overview", overview.name());
        assertEquals(Tier.DIRECTIONAL, overview.tier());

        Adapter ctxA = overview.withContext("tenant-1", "ws-1");
        assertEquals("google_ai_overview", ctxA.name());
        assertEquals(Tier.DIRECTIONAL, ctxA.tier());
    }

    @Test
    void aiOverviewExecuteMockMode() {
        GeminiAdapter a = new GeminiAdapter("", null);
        Adapter overview = a.withAIOverview("tenant-1", "ws-1");
        RawResponse resp = overview.execute("test prompt");
        assertEquals("google_ai_overview", resp.engineName());
        assertEquals(Tier.DIRECTIONAL, resp.tier());
        assertFalse(resp.fidelityLabel().isEmpty());
    }

    @Test
    void aiModeWithContextPreservesWrapper() {
        GeminiAdapter a = new GeminiAdapter("test-key", null);
        Adapter mode = a.withAIMode("", "");
        assertEquals("google_ai_mode", mode.name());
        assertEquals(Tier.DIRECTIONAL, mode.tier());

        Adapter ctxA = mode.withContext("tenant-1", "ws-1");
        assertEquals("google_ai_mode", ctxA.name());
        assertEquals(Tier.DIRECTIONAL, ctxA.tier());
    }

    @Test
    void aiModeExecuteMockMode() {
        GeminiAdapter a = new GeminiAdapter("", null);
        Adapter mode = a.withAIMode("tenant-1", "ws-1");
        RawResponse resp = mode.execute("test prompt");
        assertEquals("google_ai_mode", resp.engineName());
        assertEquals(Tier.DIRECTIONAL, resp.tier());
        assertFalse(resp.fidelityLabel().isEmpty());
    }

    @Test
    void parseResponseSuccess() {
        GeminiAdapter a = new GeminiAdapter("test-key", null);
        String raw = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Acme pazar lideridir.\"}]},"
                + "\"finishReason\":\"STOP\"}]}";
        RawResponse resp = a.parseResponse(raw.getBytes(StandardCharsets.UTF_8), 150);
        assertEquals("gemini", resp.engineName());
        assertEquals("Acme pazar lideridir.", resp.content());
    }

    @Test
    void parseResponseWithGrounding() {
        GeminiAdapter a = new GeminiAdapter("test-key", null);
        String raw = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Kaynaklara gore Acme sektor lideridir.\"}]},"
                + "\"finishReason\":\"STOP\","
                + "\"groundingAttributions\":[{\"sourceId\":{\"webSource\":{\"uri\":\"https://example.com/acme\"}},"
                + "\"content\":{\"parts\":[{\"text\":\"Acme Sektor Raporu 2026\"}]}}]}]}";
        RawResponse resp = a.parseResponse(raw.getBytes(StandardCharsets.UTF_8), 150);
        assertEquals(1, resp.citations().size());
        assertEquals("https://example.com/acme", resp.citations().get(0).url());
        assertEquals("Acme Sektor Raporu 2026", resp.citations().get(0).title());
    }

    @Test
    void parseResponseEmptyCandidates() {
        GeminiAdapter a = new GeminiAdapter("test-key", null);
        assertThrows(EngineException.class,
                () -> a.parseResponse("{\"candidates\":[]}".getBytes(StandardCharsets.UTF_8), 100));
    }

    @Test
    void parseResponseInvalidJson() {
        GeminiAdapter a = new GeminiAdapter("test-key", null);
        assertThrows(EngineException.class,
                () -> a.parseResponse("{invalid".getBytes(StandardCharsets.UTF_8), 100));
    }

    @Test
    void mockResponse() {
        GeminiAdapter a = new GeminiAdapter("test-key", null);
        RawResponse resp = a.mockResponse("test prompt");
        assertEquals("gemini", resp.engineName());
        assertFalse(resp.content().isEmpty());
        assertFalse(resp.citations().isEmpty());
        assertEquals(Tier.DIRECT, resp.tier());
    }

    @Test
    void executeMockMode() {
        GeminiAdapter a = new GeminiAdapter("", null);
        RawResponse resp = a.execute("test prompt");
        assertEquals("gemini", resp.engineName());
    }

    @Test
    void executeMockKey() {
        GeminiAdapter a = new GeminiAdapter("mock", null);
        RawResponse resp = a.execute("test prompt");
        assertEquals("gemini", resp.engineName());
    }
}