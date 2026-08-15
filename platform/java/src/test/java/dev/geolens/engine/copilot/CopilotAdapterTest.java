package dev.geolens.engine.copilot;

import dev.geolens.engine.EngineException;
import dev.geolens.engine.RawResponse;
import dev.geolens.engine.Tier;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Go {@code copilot/adapter_test.go} portu — Copilot chat completions ayrıştırma. */
class CopilotAdapterTest {

    @Test
    void name() {
        assertEquals("copilot", new CopilotAdapter("key", null).name());
    }

    @Test
    void tier() {
        assertEquals(Tier.DIRECTIONAL, new CopilotAdapter("key", null).tier());
    }

    @Test
    void withContextKeepsName() {
        assertEquals("copilot", new CopilotAdapter("key", null).withContext("t1", "w1").name());
    }

    @Test
    void parseResponseSuccess() {
        CopilotAdapter a = new CopilotAdapter("key", null);
        String raw = "{\"id\":\"cp-1\",\"model\":\"copilot-gpt-4o\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"Acme lider.\","
                + "\"citations\":[{\"url\":\"https://copilot.microsoft.com\",\"title\":\"Copilot\"}]},\"finish_reason\":\"stop\"}]}";
        RawResponse resp = a.parseResponse(raw.getBytes(StandardCharsets.UTF_8), 150);
        assertEquals("copilot", resp.engineName());
        assertEquals("cp-1", resp.requestId());
        assertEquals(1, resp.citations().size());
        assertEquals("https://copilot.microsoft.com", resp.citations().get(0).url());
        assertFalse(resp.hasSearch());
    }

    @Test
    void parseResponseEmptyChoices() {
        CopilotAdapter a = new CopilotAdapter("key", null);
        assertThrows(EngineException.class,
                () -> a.parseResponse("{\"id\":\"c\",\"choices\":[]}".getBytes(StandardCharsets.UTF_8), 100));
    }

    @Test
    void parseResponseInvalidJson() {
        CopilotAdapter a = new CopilotAdapter("key", null);
        assertThrows(EngineException.class,
                () -> a.parseResponse("{invalid".getBytes(StandardCharsets.UTF_8), 100));
    }

    @Test
    void mockResponse() {
        RawResponse resp = new CopilotAdapter("", null).mockResponse("test");
        assertEquals("copilot", resp.engineName());
        assertFalse(resp.content().isEmpty());
        assertEquals(2, resp.citations().size());
        assertEquals(Tier.DIRECTIONAL, resp.tier());
    }

    @Test
    void executeMockMode() {
        assertEquals("copilot", new CopilotAdapter("", null).execute("test").engineName());
        assertEquals("copilot", new CopilotAdapter("mock", null).execute("test").engineName());
    }
}
