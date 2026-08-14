package dev.geolens.engine.perplexity;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.engine.Adapter;
import dev.geolens.engine.Citation;
import dev.geolens.engine.EngineException;
import dev.geolens.engine.EngineHttp;
import dev.geolens.engine.RawResponse;
import dev.geolens.engine.RawSaver;
import dev.geolens.engine.Tier;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Perplexity Sonar API adaptörü — Go {@code perplexity} portu (Kademe 1 direct, H15 temp=0). */
public final class PerplexityAdapter implements Adapter {

    private static final Tier TIER = Tier.DIRECT;
    private static final String API_URL = "https://api.perplexity.ai/chat/completions";
    private static final String MODEL_NAME = "sonar-pro";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final EngineHttp http;
    private final RawSaver storage;
    private final String tenantId;
    private final String workspaceId;

    public PerplexityAdapter(String apiKey, RawSaver storage) {
        this(apiKey, storage, "", "");
    }

    private PerplexityAdapter(String apiKey, RawSaver storage, String tenantId, String workspaceId) {
        this.apiKey = apiKey;
        this.http = new EngineHttp(TIMEOUT);
        this.storage = storage;
        this.tenantId = tenantId;
        this.workspaceId = workspaceId;
    }

    @Override
    public String name() {
        return "perplexity";
    }

    @Override
    public Tier tier() {
        return TIER;
    }

    @Override
    public Adapter withContext(String tenantId, String workspaceId) {
        return new PerplexityAdapter(apiKey, storage, tenantId, workspaceId);
    }

    @Override
    public RawResponse execute(String prompt) throws EngineException {
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("mock")) {
            return mockResponse(prompt);
        }

        try {
            String body = mapper.writeValueAsString(new SonarRequest(MODEL_NAME,
                    List.of(new Message("user", prompt)), 0));
            EngineHttp.Result res = http.post(API_URL, apiKey, body);
            if (res.status() != 200) {
                throw new EngineException("perplexity api hatası (HTTP " + res.status() + "): " + res.body());
            }
            return parseResponse(res.body().getBytes(StandardCharsets.UTF_8), res.durationMs());
        } catch (EngineException e) {
            throw e;
        } catch (Exception e) {
            throw new EngineException("perplexity istek serileştirme: " + e.getMessage(), e);
        }
    }

    /** Mock modu: API anahtarı yoksa sahte yanıt döndürür. */
    public RawResponse mockResponse(String prompt) {
        String content = "Acme şirketi, sektöründe öncü bir konuma sahiptir ve yenilikçi ürünleriyle tanınmaktadır. "
                + "Pazar araştırmalarına göre Acme, rakiplerine kıyasla daha geniş bir müşteri tabanına hitap etmektedir. "
                + "Şirketin Ar-Ge yatırımları ve sürdürülebilirlik odaklı yaklaşımı, endüstri uzmanları tarafından "
                + "sıklıkla örnek gösterilmektedir. Özellikle dijital dönüşüm alanındaki çalışmaları, "
                + "sektör raporlarında dikkat çekmektedir.";

        return new RawResponse(
                "perplexity",
                "mock-req-" + System.currentTimeMillis(),
                content,
                List.of(
                        Citation.direct("https://example.com/acme-raporu", "", 1, "perplexity"),
                        Citation.direct("https://sector-news.com/industry-2026", "", 2, "perplexity"),
                        Citation.direct("https://tech-review.com/acme-innovation", "", 3, "perplexity")),
                true,
                Tier.DIRECT,
                "Kademe 1 · perplexity · sonar-pro (mock)",
                "");
    }

    /** Ham Perplexity API yanıtını {@link RawResponse}'a ayrıştırır; alıntı dizisini dönüştürür. */
    public RawResponse parseResponse(byte[] raw, long durationMs) throws EngineException {
        SonarResponse sr;
        try {
            sr = mapper.readValue(raw, SonarResponse.class);
        } catch (Exception e) {
            throw new EngineException("perplexity yanıt ayrıştırma: " + e.getMessage(), e);
        }

        if (sr.choices() == null || sr.choices().isEmpty()) {
            throw new EngineException("perplexity: boş choices dizisi");
        }

        String content = sr.choices().get(0).message().content();

        List<Citation> citations = new ArrayList<>();
        if (sr.citations() != null) {
            for (int i = 0; i < sr.citations().size(); i++) {
                citations.add(Citation.direct(sr.citations().get(i), "", i + 1, "perplexity"));
            }
        }

        RawResponse resp = new RawResponse(
                "perplexity",
                sr.id() == null ? "" : sr.id(),
                content,
                citations,
                sr.citations() != null && !sr.citations().isEmpty(),
                TIER,
                "Kademe 1 · perplexity · sonar-pro",
                "");

        if (storage != null && !tenantId.isEmpty()) {
            try {
                resp = new RawResponse(resp.engineName(), resp.requestId(), resp.content(), resp.citations(),
                        resp.hasSearch(), resp.tier(), resp.fidelityLabel(), storage.saveRawResponse(tenantId, workspaceId, "perplexity", raw));
            } catch (EngineException e) {
                // Non-fatal: S3 hatası skor hesaplamasını engellemez
                resp = new RawResponse(resp.engineName(), resp.requestId(), resp.content(), resp.citations(),
                        resp.hasSearch(), resp.tier(), resp.fidelityLabel(), "");
            }
        }
        return resp;
    }

    private record SonarRequest(String model, List<Message> messages, double temperature) {
    }

    private record Message(String role, String content) {
    }

    private record SonarResponse(String id, String model, List<Choice> choices, List<String> citations) {
    }

    private record Choice(int index, Message message) {
    }
}