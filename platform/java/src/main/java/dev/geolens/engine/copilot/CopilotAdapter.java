package dev.geolens.engine.copilot;

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
 * Microsoft Copilot adaptörü — Go {@code copilot} portu (Kademe 3 directional).
 * <p>Chat completions API; alıntılar {@code choices[0].message.citations[]} üzerinden.
 */
public final class CopilotAdapter implements Adapter {

    private static final Tier TIER = Tier.DIRECTIONAL;
    private static final String API_URL = "https://copilot.microsoft.com/api/chat/completions";
    private static final String MODEL_NAME = "copilot-gpt-4o";
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final EngineHttp http;
    private final RawSaver storage;
    private final String tenantId;
    private final String workspaceId;

    public CopilotAdapter(String apiKey, RawSaver storage) {
        this(apiKey, storage, "", "");
    }

    private CopilotAdapter(String apiKey, RawSaver storage, String tenantId, String workspaceId) {
        this.apiKey = apiKey;
        this.http = new EngineHttp(TIMEOUT);
        this.storage = storage;
        this.tenantId = tenantId;
        this.workspaceId = workspaceId;
    }

    @Override
    public String name() {
        return "copilot";
    }

    @Override
    public Tier tier() {
        return TIER;
    }

    @Override
    public Adapter withContext(String tenantId, String workspaceId) {
        return new CopilotAdapter(apiKey, storage, tenantId, workspaceId);
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
                throw new EngineException("copilot api hatası (HTTP " + res.status() + "): " + res.body());
            }
            return parseResponse(res.body().getBytes(StandardCharsets.UTF_8), res.durationMs());
        } catch (EngineException e) {
            throw e;
        } catch (Exception e) {
            throw new EngineException("copilot istek serileştirme: " + e.getMessage(), e);
        }
    }

    /** Mock modu — Go {@code mockResponse} birebir. */
    public RawResponse mockResponse(String prompt) {
        String content = "Acme, dijital pazarlama ve yapay zeka görünürlüğü alanında faaliyet gösteren bir teknoloji şirketidir. "
                + "Microsoft Copilot entegrasyonu sayesinde, markaların AI motorlarındaki "
                + "görünürlüğünü analiz eder ve iyileştirme önerileri sunar. "
                + "Şirketin yenilikçi yaklaşımı, sektör raporlarında dikkat çekmektedir.";

        return new RawResponse(
                "copilot",
                "mock-req-copilot-" + System.currentTimeMillis(),
                content,
                List.of(
                        new Citation("https://copilot.microsoft.com", "Microsoft Copilot", 1, "copilot", "", "direct"),
                        new Citation("https://learn.microsoft.com/en-us/copilot/", "Copilot Documentation", 2, "copilot", "", "direct")),
                false,
                TIER,
                "Kademe 3 · copilot · " + MODEL_NAME + " (mock)",
                "");
    }

    /** Ham Copilot yanıtını ayrıştırır — Go {@code parseResponse} portu. */
    public RawResponse parseResponse(byte[] raw, long durationMs) throws EngineException {
        ChatResponse cr;
        try {
            cr = mapper.readValue(raw, ChatResponse.class);
        } catch (Exception e) {
            throw new EngineException("copilot yanıt ayrıştırma: " + e.getMessage(), e);
        }
        if (cr.choices() == null || cr.choices().isEmpty()) {
            throw new EngineException("copilot: boş choices dizisi");
        }

        ChatMessage msg = cr.choices().get(0).message();
        String content = msg == null ? "" : msg.content();

        List<Citation> citations = new ArrayList<>();
        if (msg != null && msg.citations() != null) {
            for (MessageCitation c : msg.citations()) {
                if (c.url() != null && !c.url().isEmpty()) {
                    citations.add(Citation.direct(c.url(), c.title() == null ? "" : c.title(),
                            citations.size() + 1, "copilot"));
                }
            }
        }

        RawResponse resp = new RawResponse(
                "copilot",
                cr.id() == null ? "" : cr.id(),
                content,
                citations,
                false,
                TIER,
                "Kademe 3 · copilot · " + (cr.model() == null ? "" : cr.model()),
                "");

        if (storage != null && !tenantId.isEmpty()) {
            try {
                resp = new RawResponse(resp.engineName(), resp.requestId(), resp.content(), resp.citations(),
                        resp.hasSearch(), resp.tier(), resp.fidelityLabel(),
                        storage.saveRawResponse(tenantId, workspaceId, "copilot", raw));
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

    private record ChatMessage(String role, String content, List<MessageCitation> citations) {
    }

    private record MessageCitation(String url, String title) {
    }
}
