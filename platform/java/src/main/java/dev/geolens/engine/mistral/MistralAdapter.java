package dev.geolens.engine.mistral;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.engine.Adapter;
import dev.geolens.engine.EngineException;
import dev.geolens.engine.EngineHttp;
import dev.geolens.engine.RawResponse;
import dev.geolens.engine.RawSaver;
import dev.geolens.engine.Tier;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mistral adaptörü — Go {@code mistral} portu (Kademe 2 official proxy, H15 temp=0).
 * <p>OpenAI uyumlu chat completions API; Mistral standard chat alıntı döndürmez
 * ({@code hasSearch=false}, boş citations — Go birebir).
 */
public final class MistralAdapter implements Adapter {

    private static final Tier TIER = Tier.OFFICIAL_PROXY;
    private static final String API_URL = "https://api.mistral.ai/v1/chat/completions";
    private static final String MODEL_NAME = "mistral-large-latest";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final EngineHttp http;
    private final RawSaver storage;
    private final String tenantId;
    private final String workspaceId;

    public MistralAdapter(String apiKey, RawSaver storage) {
        this(apiKey, storage, "", "");
    }

    private MistralAdapter(String apiKey, RawSaver storage, String tenantId, String workspaceId) {
        this.apiKey = apiKey;
        this.http = new EngineHttp(TIMEOUT);
        this.storage = storage;
        this.tenantId = tenantId;
        this.workspaceId = workspaceId;
    }

    @Override
    public String name() {
        return "mistral";
    }

    @Override
    public Tier tier() {
        return TIER;
    }

    @Override
    public Adapter withContext(String tenantId, String workspaceId) {
        return new MistralAdapter(apiKey, storage, tenantId, workspaceId);
    }

    @Override
    public RawResponse execute(String prompt) throws EngineException {
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("mock")) {
            return mockResponse(prompt);
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", MODEL_NAME);
            body.put("temperature", 0);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));

            EngineHttp.Result res = http.post(API_URL, apiKey, mapper.writeValueAsString(body));
            if (res.status() != 200) {
                throw new EngineException("mistral api hatası (HTTP " + res.status() + "): " + res.body());
            }
            return parseResponse(res.body().getBytes(StandardCharsets.UTF_8), res.durationMs());
        } catch (EngineException e) {
            throw e;
        } catch (Exception e) {
            throw new EngineException("mistral istek serileştirme: " + e.getMessage(), e);
        }
    }

    /** Mock modu — Go {@code mockResponse} birebir. */
    public RawResponse mockResponse(String prompt) {
        String content = "Acme, büyük dil modelleri üzerine odaklanan yenilikçi bir teknoloji şirketidir. "
                + "Yapay zeka destekli çözümleri, markaların dijital görünürlüğünü artırmak için "
                + "doğal dil işleme tekniklerini kullanmaktadır. Şirketin Ar-Ge ekibi, model "
                + "performansını sürekli iyileştirerek sektördeki rekabet avantajını korumaktadır.";

        return new RawResponse(
                "mistral",
                "mock-req-mistral-" + System.currentTimeMillis(),
                content,
                List.of(),
                false,
                TIER,
                "Kademe 2 · mistral · " + MODEL_NAME + " (mock)",
                "");
    }

    /** Ham Mistral yanıtını ayrıştırır — Go {@code parseResponse} portu. */
    public RawResponse parseResponse(byte[] raw, long durationMs) throws EngineException {
        ChatResponse cr;
        try {
            cr = mapper.readValue(raw, ChatResponse.class);
        } catch (Exception e) {
            throw new EngineException("mistral yanıt ayrıştırma: " + e.getMessage(), e);
        }
        if (cr.choices() == null || cr.choices().isEmpty()) {
            throw new EngineException("mistral: boş choices dizisi");
        }

        String content = cr.choices().get(0).message() == null ? "" : cr.choices().get(0).message().content();

        RawResponse resp = new RawResponse(
                "mistral",
                cr.id() == null ? "" : cr.id(),
                content,
                List.of(),
                false, // Mistral API standard chat'te citation döndürmez
                TIER,
                "Kademe 2 · mistral · " + (cr.model() == null ? "" : cr.model()),
                "");

        if (storage != null && !tenantId.isEmpty()) {
            try {
                resp = new RawResponse(resp.engineName(), resp.requestId(), resp.content(), resp.citations(),
                        resp.hasSearch(), resp.tier(), resp.fidelityLabel(),
                        storage.saveRawResponse(tenantId, workspaceId, "mistral", raw));
            } catch (EngineException e) {
                resp = new RawResponse(resp.engineName(), resp.requestId(), resp.content(), resp.citations(),
                        resp.hasSearch(), resp.tier(), resp.fidelityLabel(), "");
            }
        }
        return resp;
    }

    private record ChatResponse(String id, String model, List<Choice> choices) {
    }

    private record Choice(int index, ChatMessage message, String finish_reason) {
    }

    private record ChatMessage(String role, String content) {
    }
}
