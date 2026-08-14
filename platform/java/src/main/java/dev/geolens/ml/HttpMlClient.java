package dev.geolens.ml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP serving prompt sınıflandırıcısı — Go {@code ml.Client.ClassifyPrompt} portu (0421 A0-2).
 * /v1/prompt/classify uç noktasına JSON POST yapar. HTTP 200 dışı veya ağ hatası
 * {@link ServingException} fırlatır — çağıran kural tabanlı ağırlıklara düşer (0421 M-4).
 */
public final class HttpMlClient implements MlClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpMlClient(String baseUrl, Duration timeout) {
        this.baseUrl = baseUrl;
        this.requestTimeout = timeout == null || timeout.isZero() || timeout.isNegative() ? DEFAULT_TIMEOUT : timeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @Override
    public PromptClassification classifyPrompt(String text) {
        JsonNode res = postJson("/v1/prompt/classify", Map.of("text", text));
        return new PromptClassification(
                label(res, "intent"),
                label(res, "topic"),
                label(res, "persona"),
                label(res, "funnel"));
    }

    private static PromptLabel label(JsonNode res, String field) {
        JsonNode node = res.path(field);
        return new PromptLabel(node.path("label").asText(), node.path("confidence").asDouble());
    }

    private JsonNode postJson(String path, Object body) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new ServingException("serving HTTP " + resp.statusCode() + ": " + resp.body());
            }
            return mapper.readTree(resp.body());
        } catch (IOException e) {
            throw new ServingException("serving çağrı hatası: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServingException("serving çağrı iptal", e);
        }
    }
}