package dev.geolens.sentiment.ml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HTTP serving istemcisi — Go {@code ml.Client} portu (0421 A0-2). Serving API'ye
 * JSON POST yapar. HTTP 200 dışı veya ağ hatası {@link ServingException} fırlatır —
 * çağıran kural tabanlıya düşer (0421 M-4).
 */
public final class HttpServingMlClient implements MlClient {

    private static final Logger log = LoggerFactory.getLogger(HttpServingMlClient.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

    private static final String[] SENTIMENT_LABELS = {"negative", "neutral", "positive"};

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpServingMlClient(String baseUrl, Duration timeout) {
        this.baseUrl = baseUrl;
        this.requestTimeout = timeout == null || timeout.isZero() || timeout.isNegative() ? DEFAULT_TIMEOUT : timeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @Override
    public SentimentPrediction predictSentiment(String text) {
        JsonNode res = postJson("/v1/predict", Map.of("model", "sentiment", "lang", "", "text", text));
        JsonNode outputs = res.get("outputs");
        if (outputs == null || !outputs.has("logits")) {
            throw new ServingException("sentiment yanıtında 'logits' çıktısı yok");
        }
        double[] probs = softmaxRow(outputs.get("logits"));
        int labelIdx = 0;
        for (int i = 1; i < probs.length; i++) {
            if (probs[i] > probs[labelIdx]) {
                labelIdx = i;
            }
        }
        String modelVersion = res.has("model_version") ? res.get("model_version").asText() : "";
        return new SentimentPrediction(modelVersion, SENTIMENT_LABELS[labelIdx], probs[labelIdx], probs);
    }

    @Override
    public List<HallucinationFinding> detectHallucinations(List<HallucinationResponse> responses) {
        if (responses == null || responses.size() < 2) {
            return List.of();
        }
        var payload = new ArrayList<Map<String, String>>();
        for (HallucinationResponse r : responses) {
            payload.add(Map.of("id", r.id(), "engine", r.engine(), "text", r.text()));
        }
        JsonNode res = postJson("/v1/hallucination/detect", Map.of("responses", payload));
        var findings = new ArrayList<HallucinationFinding>();
        for (JsonNode f : res.path("findings")) {
            findings.add(new HallucinationFinding(
                    f.path("type").asText(),
                    f.path("severity").asText(),
                    f.path("description").asText(),
                    f.path("confidence").asDouble(),
                    f.path("engine").asText()));
        }
        return findings;
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

    /** Logits satırını ([batch,3]) 3 sınıflı softmax olasılıklarına çevirir (sayısal kararlılık: max çıkarma). */
    static double[] softmaxRow(JsonNode logits) {
        if (!logits.isArray() || logits.isEmpty()) {
            throw new ServingException("logits beklenen dizi değil");
        }
        JsonNode row = logits.get(0);
        if (!row.isArray() || row.size() != 3) {
            throw new ServingException("logits 3 sınıf olmalı");
        }
        double[] values = new double[3];
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 3; i++) {
            values[i] = row.get(i).asDouble();
            max = Math.max(max, values[i]);
        }
        double[] exp = new double[3];
        double sum = 0;
        for (int i = 0; i < 3; i++) {
            exp[i] = Math.exp(values[i] - max);
            sum += exp[i];
        }
        for (int i = 0; i < 3; i++) {
            exp[i] /= sum;
        }
        return exp;
    }
}