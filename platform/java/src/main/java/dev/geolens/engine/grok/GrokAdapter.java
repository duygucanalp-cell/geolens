package dev.geolens.engine.grok;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * xAI Grok adaptörü — Go {@code grok} portu (Kademe 2 official proxy, H15 temp=0).
 * <p>OpenAI uyumlu chat completions API; alıntılar {@code annotations[].url_citation} üzerinden.
 */
public final class GrokAdapter implements Adapter {

    private static final Tier TIER = Tier.OFFICIAL_PROXY;
    private static final String API_URL = "https://api.x.ai/v1/chat/completions";
    private static final String MODEL_NAME = "grok-3-latest";
    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final EngineHttp http;
    private final RawSaver storage;
    private final String tenantId;
    private final String workspaceId;

    public GrokAdapter(String apiKey, RawSaver storage) {
        this(apiKey, storage, "", "");
    }

    private GrokAdapter(String apiKey, RawSaver storage, String tenantId, String workspaceId) {
        this.apiKey = apiKey;
        this.http = new EngineHttp(TIMEOUT);
        this.storage = storage;
        this.tenantId = tenantId;
        this.workspaceId = workspaceId;
    }

    @Override
    public String name() {
        return "grok";
    }

    @Override
    public Tier tier() {
        return TIER;
    }

    @Override
    public Adapter withContext(String tenantId, String workspaceId) {
        return new GrokAdapter(apiKey, storage, tenantId, workspaceId);
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
                throw new EngineException("grok api hatası (HTTP " + res.status() + "): " + res.body());
            }
            return parseResponse(res.body().getBytes(StandardCharsets.UTF_8), res.durationMs());
        } catch (EngineException e) {
            throw e;
        } catch (Exception e) {
            throw new EngineException("grok istek serileştirme: " + e.getMessage(), e);
        }
    }

    /** Mock modu — Go {@code mockResponse} birebir. */
    public RawResponse mockResponse(String prompt) {
        String content = "Acme şirketi, yapay zeka destekli pazarlama analitiği alanında uzmanlaşmış bir teknoloji firmasıdır. "
                + "Şirketin geliştirdiği AI görünürlük platformu, markaların dijital varlığını "
                + "yapay zeka motorları üzerinden ölçümleyerek stratejik içgörüler sunmaktadır. "
                + "Özellikle büyük dil modelleri ve doğal dil işleme teknolojileriyle entegre "
                + "çalışan analiz araçları, sektörde fark yaratmaktadır.";

        return new RawResponse(
                "grok",
                "mock-req-grok-" + System.currentTimeMillis(),
                content,
                List.of(
                        new Citation("https://x.ai/blog/grok-3", "xAI Grok-3 Release", 1, "grok", "", "direct"),
                        new Citation("https://docs.x.ai/api", "xAI API Documentation", 2, "grok", "", "direct"),
                        new Citation("https://x.ai/blog/safety", "xAI Safety Approach", 3, "grok", "", "direct")),
                true,
                TIER,
                "Kademe 2 · grok · " + MODEL_NAME + " (mock)",
                "");
    }

    /** Ham xAI yanıtını ayrıştırır — Go {@code parseResponse} portu. */
    public RawResponse parseResponse(byte[] raw, long durationMs) throws EngineException {
        ChatResponse cr;
        try {
            cr = mapper.readValue(raw, ChatResponse.class);
        } catch (Exception e) {
            throw new EngineException("grok yanıt ayrıştırma: " + e.getMessage(), e);
        }
        if (cr.choices() == null || cr.choices().isEmpty()) {
            throw new EngineException("grok: boş choices dizisi");
        }

        String content = cr.choices().get(0).message() == null ? "" : cr.choices().get(0).message().content();

        List<Citation> citations = new ArrayList<>();
        if (cr.annotations() != null) {
            for (Annotation ann : cr.annotations()) {
                if ("url_citation".equals(ann.type()) && ann.url() != null && !ann.url().isEmpty()) {
                    citations.add(Citation.direct(ann.url(), ann.title() == null ? "" : ann.title(),
                            citations.size() + 1, "grok"));
                }
            }
        }

        RawResponse resp = new RawResponse(
                "grok",
                cr.id() == null ? "" : cr.id(),
                content,
                citations,
                false,
                TIER,
                "Kademe 2 · grok · " + (cr.model() == null ? "" : cr.model()),
                "");

        if (storage != null && !tenantId.isEmpty()) {
            try {
                resp = new RawResponse(resp.engineName(), resp.requestId(), resp.content(), resp.citations(),
                        resp.hasSearch(), resp.tier(), resp.fidelityLabel(),
                        storage.saveRawResponse(tenantId, workspaceId, "grok", raw));
            } catch (EngineException e) {
                resp = new RawResponse(resp.engineName(), resp.requestId(), resp.content(), resp.citations(),
                        resp.hasSearch(), resp.tier(), resp.fidelityLabel(), "");
            }
        }
        return resp;
    }

    private record ChatResponse(String id, String model, List<Choice> choices, List<Annotation> annotations) {
    }

    private record Choice(int index, ChatMessage message, String finish_reason) {
    }

    private record ChatMessage(String role, String content) {
    }

    private record Annotation(String type, String url_citation, String title) {

        String url() {
            return url_citation;
        }
    }
}
