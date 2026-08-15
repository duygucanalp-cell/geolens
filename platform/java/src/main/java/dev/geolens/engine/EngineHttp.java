package dev.geolens.engine;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Adaptörlerin ortak HTTP çağrı altyapısı — Go {@code http.Client} + {@code io.ReadAll} portu. */
public final class EngineHttp {

    /** HTTP çağrısı sonucu: durum kodu, ham gövde ve süre (ms). */
    public record Result(int status, String body, long durationMs) {
    }

    private final HttpClient client;

    public EngineHttp(Duration timeout) {
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    /** JSON POST yapar. {@code authBearer} boşsa Authorization başlığı eklenmez (Gemini URL'de anahtar taşır). */
    public Result post(String url, String authBearer, String jsonBody) throws EngineException {
        Map<String, String> headers = new LinkedHashMap<>();
        if (authBearer != null && !authBearer.isEmpty()) {
            headers.put("Authorization", "Bearer " + authBearer);
        }
        return post(url, headers, jsonBody);
    }

    /** JSON POST yapar — başlıklar harita olarak verilir (Claude {@code x-api-key} vb.). */
    public Result post(String url, Map<String, String> headers, String jsonBody) throws EngineException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        if (headers != null) {
            headers.forEach(builder::header);
        }
        long start = System.nanoTime();
        try {
            HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            return new Result(resp.statusCode(), resp.body(), durationMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EngineException("http çağrısı iptal: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new EngineException("http çağrısı başarısız: " + e.getMessage(), e);
        }
    }
}