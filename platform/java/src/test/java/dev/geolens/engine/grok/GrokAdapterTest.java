package dev.geolens.engine.grok;

import dev.geolens.engine.EngineException;
import dev.geolens.engine.RawResponse;
import dev.geolens.engine.Tier;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go {@code grok/adapter_test.go} portu — xAI chat completions ayrıştırma. */
class GrokAdapterTest {

    @Test
    void name() {
        assertEquals("grok", new GrokAdapter("key", null).name());
    }

    @Test
    void tier() {
        assertEquals(Tier.OFFICIAL_PROXY, new GrokAdapter("key", null).tier());
    }

    @Test
    void withContextKeepsName() {
        assertEquals("grok", new GrokAdapter("key", null).withContext("t1", "w1").name());
    }

    @Test
    void parseResponseSuccess() {
        GrokAdapter a = new GrokAdapter("key", null);
        String raw = "{\"id\":\"grok-1\",\"model\":\"grok-3-latest\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"Acme lider.\"},\"finish_reason\":\"stop\"}]}";
        RawResponse resp = a.parseResponse(raw.getBytes(StandardCharsets.UTF_8), 150);
        assertEquals("grok", resp.engineName());
        assertEquals("grok-1", resp.requestId());
        assertEquals("Acme lider.", resp.content());
    }

    @Test
    void parseResponseWithAnnotations() {
        GrokAdapter a = new GrokAdapter("key", null);
        String raw = "{\"id\":\"grok-2\",\"model\":\"grok-3-latest\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"metin\"},\"finish_reason\":\"stop\"}],"
                + "\"annotations\":[{\"type\":\"url_citation\",\"url_citation\":\"https://x.ai/blog/grok-3\",\"title\":\"Grok-3\"}]}";
        RawResponse resp = a.parseResponse(raw.getBytes(StandardCharsets.UTF_8), 150);
        assertEquals(1, resp.citations().size());
        assertEquals("https://x.ai/blog/grok-3", resp.citations().get(0).url());
    }

    @Test
    void parseResponseEmptyChoices() {
        GrokAdapter a = new GrokAdapter("key", null);
        assertThrows(EngineException.class,
                () -> a.parseResponse("{\"id\":\"g\",\"choices\":[]}".getBytes(StandardCharsets.UTF_8), 100));
    }

    @Test
    void parseResponseInvalidJson() {
        GrokAdapter a = new GrokAdapter("key", null);
        assertThrows(EngineException.class,
                () -> a.parseResponse("{invalid".getBytes(StandardCharsets.UTF_8), 100));
    }

    @Test
    void mockResponse() {
        RawResponse resp = new GrokAdapter("", null).mockResponse("test");
        assertEquals("grok", resp.engineName());
        assertFalse(resp.content().isEmpty());
        assertTrue(resp.hasSearch());
        assertEquals(Tier.OFFICIAL_PROXY, resp.tier());
    }

    @Test
    void executeMockMode() {
        assertEquals("grok", new GrokAdapter("", null).execute("test").engineName());
        assertEquals("grok", new GrokAdapter("mock", null).execute("test").engineName());
    }
}
