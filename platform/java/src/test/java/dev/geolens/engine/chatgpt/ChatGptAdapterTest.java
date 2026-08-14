package dev.geolens.engine.chatgpt;

import dev.geolens.engine.EngineException;
import dev.geolens.engine.RawResponse;
import dev.geolens.engine.Tier;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go {@code chatgpt/adapter_test.go} portu. */
class ChatGptAdapterTest {

    @Test
    void name() {
        ChatGptAdapter a = new ChatGptAdapter("test-key", null);
        assertEquals("chatgpt", a.name());
    }

    @Test
    void tier() {
        ChatGptAdapter a = new ChatGptAdapter("test-key", null);
        assertEquals(Tier.DIRECT, a.tier());
    }

    @Test
    void withContextKeepsName() {
        ChatGptAdapter a = new ChatGptAdapter("test-key", null);
        assertEquals("chatgpt", a.withContext("tenant-1", "ws-1").name());
    }

    @Test
    void parseResponseSuccess() {
        ChatGptAdapter a = new ChatGptAdapter("test-key", null);
        String raw = "{\"id\":\"chatcmpl-abc123\",\"object\":\"chat.completion\",\"model\":\"gpt-4o\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"Acme pazar lideridir.\"},"
                + "\"finish_reason\":\"stop\"}]}";
        RawResponse resp = a.parseResponse(raw.getBytes(StandardCharsets.UTF_8), 150);
        assertEquals("chatgpt", resp.engineName());
        assertEquals("chatcmpl-abc123", resp.requestId());
        assertEquals("Acme pazar lideridir.", resp.content());
    }

    @Test
    void parseResponseWithAnnotations() {
        ChatGptAdapter a = new ChatGptAdapter("test-key", null);
        String raw = "{\"id\":\"chatcmpl-def456\",\"object\":\"chat.completion\",\"model\":\"gpt-4o\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\","
                + "\"content\":\"Kaynaklara göre [Acme](https://example.com/acme) sektör lideridir.\","
                + "\"annotations\":[{\"type\":\"url_citation\",\"url_citation\":{\"url\":\"https://example.com/acme\","
                + "\"title\":\"Acme Sektör Raporu\",\"start_index\":0,\"end_index\":10}}]},"
                + "\"finish_reason\":\"stop\"}]}";
        RawResponse resp = a.parseResponse(raw.getBytes(StandardCharsets.UTF_8), 150);
        assertEquals(1, resp.citations().size());
        assertEquals("https://example.com/acme", resp.citations().get(0).url());
        assertEquals("Acme Sektör Raporu", resp.citations().get(0).title());
    }

    @Test
    void parseResponseEmptyChoices() {
        ChatGptAdapter a = new ChatGptAdapter("test-key", null);
        String raw = "{\"id\":\"r-1\",\"object\":\"chat.completion\",\"model\":\"gpt-4o\",\"choices\":[]}";
        assertThrows(EngineException.class,
                () -> a.parseResponse(raw.getBytes(StandardCharsets.UTF_8), 100));
    }

    @Test
    void parseResponseInvalidJson() {
        ChatGptAdapter a = new ChatGptAdapter("test-key", null);
        assertThrows(EngineException.class,
                () -> a.parseResponse("{invalid".getBytes(StandardCharsets.UTF_8), 100));
    }

    @Test
    void mockResponse() {
        ChatGptAdapter a = new ChatGptAdapter("test-key", null);
        RawResponse resp = a.mockResponse("test prompt");
        assertEquals("chatgpt", resp.engineName());
        assertFalse(resp.content().isEmpty());
        assertFalse(resp.citations().isEmpty());
        assertEquals(Tier.DIRECT, resp.tier());
    }

    @Test
    void executeMockMode() {
        ChatGptAdapter a = new ChatGptAdapter("", null);
        RawResponse resp = a.execute("test prompt");
        assertEquals("chatgpt", resp.engineName());
    }

    @Test
    void executeMockKey() {
        ChatGptAdapter a = new ChatGptAdapter("mock", null);
        RawResponse resp = a.execute("test prompt");
        assertEquals("chatgpt", resp.engineName());
    }

    @Test
    void executeMockHasSearch() {
        ChatGptAdapter a = new ChatGptAdapter("", null);
        assertTrue(a.execute("test prompt").hasSearch());
    }
}