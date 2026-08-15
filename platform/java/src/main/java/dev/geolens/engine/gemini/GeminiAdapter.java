package dev.geolens.engine.gemini;

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

/**
 * Google Gemini adaptörü — Go {@code gemini} portu. Standart sorgular Kademe 1 (direct);
 * Google AI Overview ve AI Mode yüzeyleri Kademe 3 (directional) olarak işaretlenir (Kademe 3 proxy).
 */
public final class GeminiAdapter implements Adapter {

    private static final Tier TIER = Tier.DIRECT;
    private static final Tier AI_OVERVIEW_TIER = Tier.DIRECTIONAL;
    private static final Tier AI_MODE_TIER = Tier.DIRECTIONAL;
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-pro:generateContent";
    private static final String MODEL_NAME = "gemini-3.5-pro";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final EngineHttp http;
    private final RawSaver storage;
    private final String tenantId;
    private final String workspaceId;

    public GeminiAdapter(String apiKey, RawSaver storage) {
        this(apiKey, storage, "", "");
    }

    private GeminiAdapter(String apiKey, RawSaver storage, String tenantId, String workspaceId) {
        this.apiKey = apiKey;
        this.http = new EngineHttp(TIMEOUT);
        this.storage = storage;
        this.tenantId = tenantId;
        this.workspaceId = workspaceId;
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public Tier tier() {
        return TIER;
    }

    @Override
    public Adapter withContext(String tenantId, String workspaceId) {
        return new GeminiAdapter(apiKey, storage, tenantId, workspaceId);
    }

    @Override
    public RawResponse execute(String prompt) throws EngineException {
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("mock")) {
            return mockResponse(prompt);
        }

        try {
            GeminiRequest req = new GeminiRequest(
                    List.of(new Content(List.of(new Part(prompt)))),
                    List.of(new Tool(new GoogleSearch())),
                    new GenerationConfig(0));
            String body = mapper.writeValueAsString(req);
            EngineHttp.Result res = http.post(API_URL + "?key=" + apiKey, (String) null, body);
            if (res.status() != 200) {
                throw new EngineException("gemini api hatası (HTTP " + res.status() + "): " + res.body());
            }
            return parseResponse(res.body().getBytes(StandardCharsets.UTF_8), res.durationMs());
        } catch (EngineException e) {
            throw e;
        } catch (Exception e) {
            throw new EngineException("gemini istek serileştirme: " + e.getMessage(), e);
        }
    }

    /** Mock modu: API anahtarı yoksa sahte yanıt döndürür. */
    public RawResponse mockResponse(String prompt) {
        String content = "Acme şirketi, Google Gemini modelleri ve yapay zeka altyapısı konusunda sektörde öncü bir konuma sahiptir. "
                + "Gemini'nin çok modlu yetenekleri ve Google Search grounding entegrasyonu sayesinde Acme, "
                + "doğru ve güncel bilgi sunma konusunda rakiplerinin önünde yer almaktadır. "
                + "Şirketin inovasyon odaklı yaklaşımı, birden çok sektör raporunda "
                + "örnek vaka olarak gösterilmektedir.";

        return new RawResponse(
                "gemini",
                "mock-req-gemini-" + System.currentTimeMillis(),
                content,
                List.of(
                        Citation.direct("https://deepmind.google/gemini/acme", "", 1, "gemini"),
                        Citation.direct("https://ai.googleblog.com/2026/acme-case", "", 2, "gemini"),
                        Citation.direct("https://cloud.google.com/gemini/acme", "", 3, "gemini")),
                true,
                Tier.DIRECT,
                "Kademe 1 · gemini · gemini-3.5-pro (mock)",
                "");
    }

    /** Ham Gemini API yanıtını {@link RawResponse}'a ayrıştırır; grounding attributions'dan alıntıları çıkarır. */
    public RawResponse parseResponse(byte[] raw, long durationMs) throws EngineException {
        GeminiResponse gr;
        try {
            gr = mapper.readValue(raw, GeminiResponse.class);
        } catch (Exception e) {
            throw new EngineException("gemini yanıt ayrıştırma: " + e.getMessage(), e);
        }

        if (gr.candidates() == null || gr.candidates().isEmpty()) {
            throw new EngineException("gemini: boş candidates dizisi");
        }

        Candidate cand = gr.candidates().get(0);

        StringBuilder contentText = new StringBuilder();
        if (cand.content() != null && cand.content().parts() != null) {
            for (Part p : cand.content().parts()) {
                contentText.append(p.text());
            }
        }

        List<Citation> citations = new ArrayList<>();
        if (cand.groundingAttributions() != null) {
            for (int i = 0; i < cand.groundingAttributions().size(); i++) {
                GroundingAttribution attr = cand.groundingAttributions().get(i);
                if (attr.sourceId() != null && attr.sourceId().webSource() != null
                        && attr.sourceId().webSource().uri() != null && !attr.sourceId().webSource().uri().isEmpty()) {
                    StringBuilder snippet = new StringBuilder();
                    if (attr.content() != null && attr.content().parts() != null) {
                        for (Part p : attr.content().parts()) {
                            snippet.append(p.text());
                        }
                    }
                    citations.add(Citation.direct(attr.sourceId().webSource().uri(), snippet.toString(), i + 1, "gemini"));
                }
            }
        }

        RawResponse resp = new RawResponse(
                "gemini",
                "gemini-" + System.currentTimeMillis(),
                contentText.toString(),
                citations,
                !citations.isEmpty(),
                TIER,
                "Kademe 1 · gemini · " + MODEL_NAME,
                "");

        if (storage != null && !tenantId.isEmpty()) {
            try {
                resp = new RawResponse(resp.engineName(), resp.requestId(), resp.content(), resp.citations(),
                        resp.hasSearch(), resp.tier(), resp.fidelityLabel(), storage.saveRawResponse(tenantId, workspaceId, "gemini", raw));
            } catch (EngineException e) {
                resp = new RawResponse(resp.engineName(), resp.requestId(), resp.content(), resp.citations(),
                        resp.hasSearch(), resp.tier(), resp.fidelityLabel(), "");
            }
        }
        return resp;
    }

    /** Google AI Overview yüzeyi — Kademe 3 (directional) fidelity etiketiyle işaretlenir. */
    public Adapter withAIOverview(String tenantId, String workspaceId) {
        return new AiOverviewAdapter(apiKey, storage, tenantId, workspaceId, this);
    }

    /** Google AI Mode yüzeyi — Kademe 3 (directional) fidelity etiketiyle işaretlenir (HT2 — FR-B6 genişletmesi). */
    public Adapter withAIMode(String tenantId, String workspaceId) {
        return new AiModeAdapter(apiKey, storage, tenantId, workspaceId, this);
    }

    /** AI Overview wrapper — Go {@code aiOverviewAdapter} portu. */
    static final class AiOverviewAdapter implements Adapter {

        private final String apiKey;
        private final RawSaver storage;
        private final String tenantId;
        private final String workspaceId;
        private final GeminiAdapter delegate;

        AiOverviewAdapter(String apiKey, RawSaver storage, String tenantId, String workspaceId, GeminiAdapter delegate) {
            this.apiKey = apiKey;
            this.storage = storage;
            this.tenantId = tenantId;
            this.workspaceId = workspaceId;
            this.delegate = delegate;
        }

        @Override
        public String name() {
            return "google_ai_overview";
        }

        @Override
        public Tier tier() {
            return AI_OVERVIEW_TIER;
        }

        @Override
        public Adapter withContext(String tenantId, String workspaceId) {
            return new AiOverviewAdapter(apiKey, storage, tenantId, workspaceId, delegate);
        }

        @Override
        public RawResponse execute(String prompt) throws EngineException {
            RawResponse resp = delegate.execute(prompt);
            return new RawResponse(
                    "google_ai_overview",
                    resp.requestId(),
                    resp.content(),
                    resp.citations(),
                    resp.hasSearch(),
                    AI_OVERVIEW_TIER,
                    "Kademe 3 · google_ai_overview · " + MODEL_NAME + " (official_proxy/directional)",
                    resp.s3Ref());
        }
    }

    /** AI Mode wrapper — Go {@code aiModeAdapter} portu. */
    static final class AiModeAdapter implements Adapter {

        private final String apiKey;
        private final RawSaver storage;
        private final String tenantId;
        private final String workspaceId;
        private final GeminiAdapter delegate;

        AiModeAdapter(String apiKey, RawSaver storage, String tenantId, String workspaceId, GeminiAdapter delegate) {
            this.apiKey = apiKey;
            this.storage = storage;
            this.tenantId = tenantId;
            this.workspaceId = workspaceId;
            this.delegate = delegate;
        }

        @Override
        public String name() {
            return "google_ai_mode";
        }

        @Override
        public Tier tier() {
            return AI_MODE_TIER;
        }

        @Override
        public Adapter withContext(String tenantId, String workspaceId) {
            return new AiModeAdapter(apiKey, storage, tenantId, workspaceId, delegate);
        }

        @Override
        public RawResponse execute(String prompt) throws EngineException {
            RawResponse resp = delegate.execute(prompt);
            return new RawResponse(
                    "google_ai_mode",
                    resp.requestId(),
                    resp.content(),
                    resp.citations(),
                    resp.hasSearch(),
                    AI_MODE_TIER,
                    "Kademe 3 · google_ai_mode · " + MODEL_NAME + " (official_proxy/directional)",
                    resp.s3Ref());
        }
    }

    private record GeminiRequest(List<Content> contents, List<Tool> tools, GenerationConfig generationConfig) {
    }

    private record Content(List<Part> parts) {
    }

    private record Part(String text) {
    }

    private record Tool(GoogleSearch google_search) {

        GoogleSearch googleSearch() {
            return google_search;
        }
    }

    private record GoogleSearch() {
    }

    private record GenerationConfig(double temperature) {
    }

    private record GeminiResponse(List<Candidate> candidates, Usage usageMetadata) {
    }

    private record Candidate(Content content, String finishReason, List<GroundingAttribution> groundingAttributions) {
    }

    private record GroundingAttribution(SourceId sourceId, Content content) {
    }

    private record SourceId(WebSource webSource) {
    }

    private record WebSource(String uri) {
    }

    private record Usage(int promptTokenCount, int candidatesTokenCount, int totalTokenCount) {
    }
}