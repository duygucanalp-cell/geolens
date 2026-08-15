package dev.geolens.engine.claude;

import dev.geolens.engine.EngineException;
import dev.geolens.engine.RawResponse;
import dev.geolens.engine.Tier;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go {@code claude/adapter_test.go} portu — Anthropic Messages API ayrıştırma. */
class ClaudeAdapterTest {

    @Test
    void name() {
        assertEquals("claude", new ClaudeAdapter("key", null).name());
    }

    @Test
    void tier() {
        assertEquals(Tier.OFFICIAL_PROXY, new ClaudeAdapter("key", null).tier());
    }

    @Test
    void withContextKeepsName() {
        assertEquals("claude", new ClaudeAdapter("key", null).withContext("t1", "w1").name());
    }

    @Test
    void parseResponseSuccess() {
        ClaudeAdapter a = new ClaudeAdapter("key", null);
        String raw = """
                {"id":"msg_01","type":"message","role":"assistant","model":"claude-sonnet-4-20260514",
                 "content":[{"type":"text","text":"Acme sektör lideridir."}],
                 "stop_reason":"end_turn"}
                """;
        RawResponse resp = a.parseResponse(raw.getBytes(StandardCharsets.UTF_8), 150);
        assertEquals("claude", resp.engineName());
        assertEquals("msg_01", resp.requestId());
        assertEquals("Acme sektör lideridir.", resp.content());
    }

    @Test
    void parseResponseWithCiteAndSource() {
        ClaudeAdapter a = new ClaudeAdapter("key", null);
        String raw = """
                {"id":"msg_02","model":"claude-sonnet-4-20260514",
                 "content":[
                   {"type":"text","text":"Kaynaklara göre Acme öne çıkıyor."},
                   {"type":"text","text":"Detay.", "cite":{"type":"char_location","title":"Rapor","uri":"https://x.com/r","start":0,"end":4}},
                   {"type":"text","text":"İkincil.", "source":{"type":"document","title":"Belge","url":"https://y.com/d"}}
                 ]}
                """;
        RawResponse resp = a.parseResponse(raw.getBytes(StandardCharsets.UTF_8), 150);
        assertEquals(2, resp.citations().size());
        assertEquals("https://x.com/r", resp.citations().get(0).url());
        assertEquals("https://y.com/d", resp.citations().get(1).url());
        assertEquals("attribution", resp.citations().get(1).type());
        assertTrue(resp.hasSearch());
    }

    @Test
    void parseResponseEmptyContent() {
        ClaudeAdapter a = new ClaudeAdapter("key", null);
        assertThrows(EngineException.class,
                () -> a.parseResponse("{\"id\":\"m\",\"content\":[]}".getBytes(StandardCharsets.UTF_8), 100));
    }

    @Test
    void parseResponseInvalidJson() {
        ClaudeAdapter a = new ClaudeAdapter("key", null);
        assertThrows(EngineException.class,
                () -> a.parseResponse("{invalid".getBytes(StandardCharsets.UTF_8), 100));
    }

    @Test
    void mockResponse() {
        RawResponse resp = new ClaudeAdapter("", null).mockResponse("test");
        assertEquals("claude", resp.engineName());
        assertFalse(resp.content().isEmpty());
        assertEquals(3, resp.citations().size());
        assertEquals(Tier.OFFICIAL_PROXY, resp.tier());
    }

    @Test
    void executeMockMode() {
        assertEquals("claude", new ClaudeAdapter("", null).execute("test").engineName());
        assertEquals("claude", new ClaudeAdapter("mock", null).execute("test").engineName());
    }
}
