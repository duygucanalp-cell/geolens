package dev.geolens.engine.claude;

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
 * Anthropic Claude adaptörü — Go {@code claude} portu (Kademe 2 official proxy, H15 temp=0).
 * <p>Anthropic Messages API ({@code x-api-key} + {@code anthropic-version} başlıkları);
 * content bloklarından text + cite/source alıntıları çıkarılır.
 */
public final class ClaudeAdapter implements Adapter {

    private static final Tier TIER = Tier.OFFICIAL_PROXY;
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL_NAME = "claude-sonnet-4-20260514";
    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final EngineHttp http;
    private final RawSaver storage;
    private final String tenantId;
    private final String workspaceId;

    public ClaudeAdapter(String apiKey, RawSaver storage) {
        this(apiKey, storage, "", "");
    }

    private ClaudeAdapter(String apiKey, RawSaver storage, String tenantId, String workspaceId) {
        this.apiKey = apiKey;
        this.http = new EngineHttp(TIMEOUT);
        this.storage = storage;
        this.tenantId = tenantId;
        this.workspaceId = workspaceId;
    }

    @Override
    public String name() {
        return "claude";
    }

    @Override
    public Tier tier() {
        return TIER;
    }

    @Override
    public Adapter withContext(String tenantId, String workspaceId) {
        return new ClaudeAdapter(apiKey, storage, tenantId, workspaceId);
    }

    @Override
    public RawResponse execute(String prompt) throws EngineException {
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("mock")) {
            return mockResponse(prompt);
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", MODEL_NAME);
            body.put("max_tokens", 1024);
            body.put("temperature", 0);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));

            String json = mapper.writeValueAsString(body);
            Map<String, String> headers = Map.of(
                    "x-api-key", apiKey,
                    "anthropic-version", "2023-06-01");
            EngineHttp.Result res = http.post(API_URL, headers, json);
            if (res.status() != 200) {
                throw new EngineException("claude api hatası (HTTP " + res.status() + "): " + res.body());
            }
            return parseResponse(res.body().getBytes(StandardCharsets.UTF_8), res.durationMs());
        } catch (EngineException e) {
            throw e;
        } catch (Exception e) {
            throw new EngineException("claude istek serileştirme: " + e.getMessage(), e);
        }
    }

    /** Mock modu: API anahtarı yoksa sahte yanıt (Go {@code mockResponse} birebir). */
    public RawResponse mockResponse(String prompt) {
        String content = "Acme şirketi kurumsal yapay zeka çözümleri konusunda sektörün önde gelen firmalarından biridir. "
                + "Özellikle doğal dil işleme ve büyük dil modelleri alanındaki Ar-Ge çalışmaları ile tanınmaktadır. "
                + "Şirketin son dönemde yayınladığı teknik raporlar, AI güvenliği ve etik yapay zeka konularında "
                + "sektöre yön vermektedir. Müşteri portföyünde Fortune 500 şirketlerinin yer alması, "
                + "güvenilirliğinin önemli bir göstergesidir.";

        return new RawResponse(
                "claude",
                "mock-req-claude-" + System.currentTimeMillis(),
                content,
                List.of(
                        Citation.direct("https://anthropic.com/research/acme", "", 1, "claude"),
                        Citation.direct("https://techcrunch.com/2026/acme-claude", "", 2, "claude"),
                        Citation.direct("https://venturebeat.com/ai/acme-enterprise", "", 3, "claude")),
                true,
                TIER,
                "Kademe 2 · claude · " + MODEL_NAME + " (mock)",
                "");
    }

    /** Ham Anthropic yanıtını {@link RawResponse}'a ayrıştırır — Go {@code parseResponse} portu. */
    public RawResponse parseResponse(byte[] raw, long durationMs) throws EngineException {
        MessageResponse mr;
        try {
            mr = mapper.readValue(raw, MessageResponse.class);
        } catch (Exception e) {
            throw new EngineException("claude yanıt ayrıştırma: " + e.getMessage(), e);
        }

        if (mr.content() == null || mr.content().isEmpty()) {
            throw new EngineException("claude: boş content dizisi");
        }

        StringBuilder fullContent = new StringBuilder();
        List<Citation> citations = new ArrayList<>();
        for (ContentBlock block : mr.content()) {
            if ("text".equals(block.type()) && block.text() != null && !block.text().isEmpty()) {
                fullContent.append(block.text()).append(' ');
            }
            if (block.cite() != null) {
                citations.add(Citation.direct(
                        block.cite().uri(), block.cite().title(), block.cite().start(), "claude"));
            }
            if (block.source() != null && block.source().url() != null && !block.source().url().isEmpty()) {
                citations.add(new Citation(block.source().url(), block.source().title(),
                        citations.size() + 1, "claude", "", "attribution"));
            }
        }

        RawResponse resp = new RawResponse(
                "claude",
                mr.id() == null ? "" : mr.id(),
                fullContent.toString().trim(),
                citations,
                !citations.isEmpty(),
                TIER,
                "Kademe 2 · claude · " + (mr.model() == null ? "" : mr.model()),
                "");

        if (storage != null && !tenantId.isEmpty()) {
            try {
                resp = new RawResponse(resp.engineName(), resp.requestId(), resp.content(), resp.citations(),
                        resp.hasSearch(), resp.tier(), resp.fidelityLabel(),
                        storage.saveRawResponse(tenantId, workspaceId, "claude", raw));
            } catch (EngineException e) {
                resp = new RawResponse(resp.engineName(), resp.requestId(), resp.content(), resp.citations(),
                        resp.hasSearch(), resp.tier(), resp.fidelityLabel(), "");
            }
        }
        return resp;
    }

    private record MessageResponse(String id, String type, String role, String model,
                                   List<ContentBlock> content, String stop_reason) {
    }

    private record ContentBlock(String type, String text, Cite cite, CiteSource source) {
    }

    private record Cite(String type, String title, String uri, int start, int end) {
    }

    private record CiteSource(String type, String title, String url) {
    }
}
