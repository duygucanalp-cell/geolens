package dev.geolens.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch HTTP istemcisi — Go {@code search.Client} portu.
 * <p>Endpoint yapılandırılmamışsa (boş) {@link #index}/{@link #search} no-op'tur
 * (Go {@code endpoint == "" → atlanıyor} davranışı — spike ES'siz çalışır).
 * İndeksleme {@code PUT {endpoint}/{index}/_doc/{id}}; arama
 * {@code POST {endpoint}/{index}/_search} gövdesi {@code {"query": ...}}.
 * API key varsa {@code Authorization: ApiKey {key}} başlığı eklenir (30 sn timeout).
 */
public final class SearchClient {

    private static final Logger LOG = LoggerFactory.getLogger(SearchClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String endpoint;
    private final String apiKey;
    private final HttpClient httpClient;

    /** İndekslenecek belge — Go {@code search.IndexDoc} portu. */
    public record IndexDoc(String index, String id, Map<String, Object> body) {
    }

    public SearchClient(String endpoint, String apiKey) {
        this(endpoint, apiKey, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    SearchClient(String endpoint, String apiKey, HttpClient httpClient) {
        this.endpoint = endpoint == null ? "" : endpoint;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.httpClient = httpClient;
    }

    /** Endpoint yapılandırılmış mı — Go {@code c.endpoint == ""} kontrolü. */
    public boolean isConfigured() {
        return !endpoint.isBlank();
    }

    /**
     * Belgeyi indeksler — Go {@code Client.Index} portu.
     * Endpoint boşsa debug log ile atlanır; HTTP ≥400'de {@link SearchException}.
     */
    public void index(IndexDoc doc) {
        if (!isConfigured()) {
            LOG.debug("elasticsearch: endpoint yapılandırılmamış, atlanıyor — index {}", doc.index());
            return;
        }
        try {
            String body = MAPPER.writeValueAsString(doc.body());
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/" + doc.index() + "/_doc/" + doc.id()))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            send(req, false);
        } catch (SearchException e) {
            throw e;
        } catch (Exception e) {
            throw new SearchException("es çağrı hatası: " + e.getMessage(), e);
        }
    }

    /**
     * Sorgu çalıştırır — Go {@code Client.Search} portu.
     * Endpoint boşsa boş sonuç döner ({@code hits=0, documents=[]}).
     */
    public SearchResult search(String index, Map<String, Object> query) {
        if (!isConfigured()) {
            return new SearchResult(0, List.of());
        }
        try {
            String body = MAPPER.writeValueAsString(Map.of("query", query));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/" + index + "/_search"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            String raw = send(req, true);
            JsonNode resp = MAPPER.readTree(raw);
            int hits = resp.path("hits").path("total").path("value").asInt();
            List<JsonNode> documents = new ArrayList<>();
            for (JsonNode h : resp.path("hits").path("hits")) {
                documents.add(h.path("_source"));
            }
            return new SearchResult(hits, documents);
        } catch (SearchException e) {
            throw e;
        } catch (Exception e) {
            throw new SearchException("es arama çağrı hatası: " + e.getMessage(), e);
        }
    }

    private String send(HttpRequest req, boolean search) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(req, (n, v) -> true);
        if (!apiKey.isBlank()) {
            builder.header("Authorization", "ApiKey " + apiKey);
        }
        HttpResponse<String> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            String prefix = search ? "es arama hatası" : "es hatası";
            throw new SearchException(prefix + " (HTTP " + resp.statusCode() + "): " + resp.body());
        }
        return resp.body();
    }
}
