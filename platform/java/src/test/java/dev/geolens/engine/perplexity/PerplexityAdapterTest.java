package dev.geolens.engine.perplexity;

import dev.geolens.engine.EngineException;
import dev.geolens.engine.RawResponse;
import dev.geolens.engine.Tier;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Go {@code perplexity/adapter_test.go} portu. */
class PerplexityAdapterTest {

    @Test
    void name() {
        PerplexityAdapter a = new PerplexityAdapter("test-key", null);
        assertEquals("perplexity", a.name());
    }

    @Test
    void tier() {
        PerplexityAdapter a = new PerplexityAdapter("test-key", null);
        assertEquals(Tier.DIRECT, a.tier());
    }

    @Test
    void withContextKeepsName() {
        PerplexityAdapter a = new PerplexityAdapter("test-key", null);
        assertEquals("perplexity", a.withContext("tenant-1", "ws-1").name());
    }

    @Test
    void parseResponseSuccess() {
        PerplexityAdapter a = new PerplexityAdapter("test-key", null);
        String raw = "{\"id\":\"req-123\",\"model\":\"sonar-pro\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"Acme pazar lideridir.\"}}],"
                + "\"citations\":[\"https://example.com\"]}";
        RawResponse resp = a.parseResponse(raw.getBytes(StandardCharsets.UTF_8), 150);
        assertEquals("perplexity", resp.engineName());
        assertEquals("req-123", resp.requestId());
        assertEquals(1, resp.citations().size());
        assertEquals("https://example.com", resp.citations().get(0).url());
    }

    @Test
    void parseResponseEmptyChoices() {
        PerplexityAdapter a = new PerplexityAdapter("test-key", null);
        String raw = "{\"id\":\"req-1\",\"model\":\"sonar-pro\",\"choices\":[],\"citations\":[]}";
        assertThrows(EngineException.class,
                () -> a.parseResponse(raw.getBytes(StandardCharsets.UTF_8), 100));
    }

    @Test
    void parseResponseInvalidJson() {
        PerplexityAdapter a = new PerplexityAdapter("test-key", null);
        assertThrows(EngineException.class,
                () -> a.parseResponse("{invalid".getBytes(StandardCharsets.UTF_8), 100));
    }

    @Test
    void executeMockMode() {
        PerplexityAdapter a = new PerplexityAdapter("", null);
        RawResponse resp = a.execute("test prompt");
        assertEquals("perplexity", resp.engineName());
    }
}