package dev.geolens.engine.mistral;

import dev.geolens.engine.EngineException;
import dev.geolens.engine.RawResponse;
import dev.geolens.engine.Tier;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go {@code mistral/adapter_test.go} portu — Mistral chat completions ayrıştırma. */
class MistralAdapterTest {

    @Test
    void name() {
        assertEquals("mistral", new MistralAdapter("key", null).name());
    }

    @Test
    void tier() {
        assertEquals(Tier.OFFICIAL_PROXY, new MistralAdapter("key", null).tier());
    }

    @Test
    void withContextKeepsName() {
        assertEquals("mistral", new MistralAdapter("key", null).withContext("t1", "w1").name());
    }

    @Test
    void parseResponseSuccess() {
        MistralAdapter a = new MistralAdapter("key", null);
        String raw = "{\"id\":\"ms-1\",\"model\":\"mistral-large-latest\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"Acme lider.\"},\"finish_reason\":\"stop\"}]}";
        RawResponse resp = a.parseResponse(raw.getBytes(StandardCharsets.UTF_8), 150);
        assertEquals("mistral", resp.engineName());
        assertEquals("ms-1", resp.requestId());
        assertEquals("Acme lider.", resp.content());
        // Mistral standard chat citation döndürmez (Go birebir)
        assertTrue(resp.citations().isEmpty());
        assertFalse(resp.hasSearch());
    }

    @Test
    void parseResponseEmptyChoices() {
        MistralAdapter a = new MistralAdapter("key", null);
        assertThrows(EngineException.class,
                () -> a.parseResponse("{\"id\":\"m\",\"choices\":[]}".getBytes(StandardCharsets.UTF_8), 100));
    }

    @Test
    void parseResponseInvalidJson() {
        MistralAdapter a = new MistralAdapter("key", null);
        assertThrows(EngineException.class,
                () -> a.parseResponse("{invalid".getBytes(StandardCharsets.UTF_8), 100));
    }

    @Test
    void mockResponse() {
        RawResponse resp = new MistralAdapter("", null).mockResponse("test");
        assertEquals("mistral", resp.engineName());
        assertFalse(resp.content().isEmpty());
        assertTrue(resp.citations().isEmpty());
        assertEquals(Tier.OFFICIAL_PROXY, resp.tier());
    }

    @Test
    void executeMockMode() {
        assertEquals("mistral", new MistralAdapter("", null).execute("test").engineName());
        assertEquals("mistral", new MistralAdapter("mock", null).execute("test").engineName());
    }
}
