package dev.geolens.search;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Go {@code internal/search/client.go} portu — gerçek ES yerine JDK HttpServer
 * stub'ıyla HTTP davranışı doğrulanır (PUT/POST yolu, gövde, ApiKey başlığı, hata kodu).
 */
class SearchClientTest {

    private record Exchange(String method, String path, String auth, String body) {
    }

    private HttpServer server;
    private final List<Exchange> exchanges = new ArrayList<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = "{}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", ex -> {
            byte[] body = ex.getRequestBody().readAllBytes();
            exchanges.add(new Exchange(
                    ex.getRequestMethod(),
                    ex.getRequestURI().toString(),
                    ex.getRequestHeaders().getFirst("Authorization"),
                    new String(body, StandardCharsets.UTF_8)));
            byte[] resp = responseBody.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(responseStatus, resp.length);
            ex.getResponseBody().write(resp);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String endpoint() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Test
    void index_endpointEmpty_isNoop() {
        SearchClient client = new SearchClient("", "key");
        client.index(new SearchClient.IndexDoc("geolens-audit", "a1", Map.of("a", "b")));
        // istisna fırlatmaz, HTTP çağrısı yapılmaz (server'a istek düşmedi)
        assertEquals(0, exchanges.size());
    }

    @Test
    void index_sendsPutWithBodyAndApiKey() {
        SearchClient client = new SearchClient(endpoint(), "es-key-123");
        client.index(new SearchClient.IndexDoc("geolens-audit", "a1",
                Map.of("tenant_id", "T01", "event_type", "measure")));

        assertEquals(1, exchanges.size());
        Exchange ex = exchanges.get(0);
        assertEquals("PUT", ex.method());
        assertEquals("/geolens-audit/_doc/a1", ex.path());
        assertEquals("ApiKey es-key-123", ex.auth());
        assertTrue(ex.body().contains("\"tenant_id\":\"T01\""), ex.body());
        assertTrue(ex.body().contains("\"event_type\":\"measure\""), ex.body());
    }

    @Test
    void index_httpError_throwsSearchException() {
        responseStatus = 500;
        responseBody = "boom";
        SearchClient client = new SearchClient(endpoint(), "");

        SearchException e = assertThrows(SearchException.class,
                () -> client.index(new SearchClient.IndexDoc("i", "1", Map.of("a", "b"))));
        assertTrue(e.getMessage().contains("es hatası (HTTP 500)"), e.getMessage());
        assertTrue(e.getMessage().contains("boom"), e.getMessage());
    }

    @Test
    void search_parsesHitsAndSources() {
        responseBody = """
                {"hits":{"total":{"value":2},"hits":[
                  {"_source":{"id":"a1","action":"run"}},
                  {"_source":{"id":"a2","action":"delete"}}]}}
                """;
        SearchClient client = new SearchClient(endpoint(), "");
        SearchResult result = client.search("geolens-audit", Map.of("bool", Map.of()));

        assertEquals(2, result.hits());
        assertEquals(2, result.documents().size());
        assertEquals("a1", result.documents().get(0).path("id").asText());
        assertEquals("delete", result.documents().get(1).path("action").asText());

        assertEquals(1, exchanges.size());
        Exchange ex = exchanges.get(0);
        assertEquals("POST", ex.method());
        assertEquals("/geolens-audit/_search", ex.path());
        assertTrue(ex.body().startsWith("{\"query\":{"), ex.body());
    }

    @Test
    void search_endpointEmpty_returnsEmptyResult() {
        SearchClient client = new SearchClient("", "");
        SearchResult result = client.search("geolens-audit", Map.of("bool", Map.of()));

        assertEquals(0, result.hits());
        assertTrue(result.documents().isEmpty());
    }

    @Test
    void search_httpError_throwsSearchException() {
        responseStatus = 502;
        responseBody = "bad gateway";
        SearchClient client = new SearchClient(endpoint(), "");

        SearchException e = assertThrows(SearchException.class,
                () -> client.search("geolens-audit", Map.of("bool", Map.of())));
        assertTrue(e.getMessage().contains("es arama hatası (HTTP 502)"), e.getMessage());
    }

    @Test
    void index_noApiKey_sendsNoAuthHeader() {
        SearchClient client = new SearchClient(endpoint(), "");
        client.index(new SearchClient.IndexDoc("i", "1", Map.of("a", "b")));

        // Go: apiKey == "" → Authorization başlığı eklenmez
        assertNull(exchanges.get(0).auth());
    }
}
