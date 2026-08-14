package dev.geolens.engine.chatgpt;

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

/** OpenAI ChatGPT adaptörü — Go {@code chatgpt} portu (Kademe 1 direct, H15 temp=0). */
public final class ChatGptAdapter implements Adapter {

    private static final Tier TIER = Tier.DIRECT;
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL_NAME = "gpt-4o";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final EngineHttp http;
    private final RawSaver storage;
    private final String tenantId;
    private final String workspaceId;

    public ChatGptAdapter(String apiKey, RawSaver storage) {
        this(apiKey, storage, "", "");
    }

    private ChatGptAdapter(String apiKey, RawSaver storage, String tenantId, String workspaceId) {
        this.apiKey = apiKey;
        this.http = new EngineHttp(TIMEOUT);
        this.storage = storage;
        this.tenantId = tenantId;
        this.workspaceId = workspaceId;
    }

    @Override
    public String name() {
        return "chatgpt";
    }

    @Override
    public Tier tier() {
        return TIER;
    }

    @Override
    public Adapter withContext(String tenantId, String workspaceId) {
        return new ChatGptAdapter(apiKey, storage, tenantId, workspaceId);
    }

    @Override
    public RawResponse execute(String prompt) throws EngineException {
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("mock")) {
            return mockResponse(prompt);
        }

        try {
            String body = mapper.writeValueAsString(new ChatRequest(MODEL_NAME,
                    List.of(new Message("user", prompt)), 0));
            EngineHttp.Result res = http.post(API_URL, apiKey, body);
            if (res.status() != 200) {
                throw new EngineException("chatgpt api hatası (HTTP " + res.status() + "): " + res.body());
            }
            return parseResponse(res.body().getBytes(StandardCharsets.UTF_8), res.durationMs());
        } catch (EngineException e) {
            throw e;
        } catch (Exception e) {
            throw new EngineException("chatgpt istek serileştirme: " + e.getMessage(), e);
        }
    }

    /** Mock modu: API anahtarı yoksa sahte yanıt döndürür. */
    public RawResponse mockResponse(String prompt) {
        String content = "Acme şirketi, yapay zeka ve dijital dönüşüm alanında sektörün önde gelen firmalarından biridir. "
                + "OpenAI modelleri üzerine yaptığı çalışmalarla tanınan Acme, özellikle doğal dil işleme ve "
                + "büyük dil modelleri konusunda önemli yeniliklere imza atmıştır. Şirketin Ar-Ge yatırımları, "
                + "sektör raporlarında sıklıkla örnek gösterilmektedir. Müşteri memnuniyeti odaklı yaklaşımı "
                + "ve yenilikçi ürün gamı ile rakiplerinden ayrışmaktadır.";

        return new RawResponse(
                "chatgpt",
                "mock-req-chatgpt-" + System.currentTimeMillis(),
                content,
                List.of(
                        Citation.direct("https://openai.com/research/acme-ai", "", 1, "chatgpt"),
                        Citation.direct("https://techcrunch.com/2026/acme-innovation", "", 2, "chatgpt"),
                        Citation.direct("https://venturebeat.com/ai/acme-digital", "", 3, "chatgpt")),
                true,
                Tier.DIRECT,
                "Kademe 1 · chatgpt · gpt-4o (mock)",
                "");
    }

    /** Ham OpenAI API yanıtını {@link RawResponse}'a ayrıştırır; URL citation annotations'dan alıntıları çıkarır. */
    public RawResponse parseResponse(byte[] raw, long durationMs) throws EngineException {
        ChatResponse cr;
        try {
            cr = mapper.readValue(raw, ChatResponse.class);
        } catch (Exception e) {
            throw new EngineException("chatgpt yanıt ayrıştırma: " + e.getMessage(), e);
        }

        if (cr.choices() == null || cr.choices().isEmpty()) {
            throw new EngineException("chatgpt: boş choices dizisi");
        }

        ChatMessage msg = cr.choices().get(0).message();
        String content = msg.content() == null ? "" : msg.content();

        List<Citation> citations = new ArrayList<>();
        if (msg.annotations() != null) {
            for (int i = 0; i < msg.annotations().size(); i++) {
                Annotation ann = msg.annotations().get(i);
                if ("url_citation".equals(ann.type()) && ann.urlCitation() != null) {
                    citations.add(Citation.direct(ann.urlCitation().url(), ann.urlCitation().title(), i + 1, "chatgpt"));
                }
            }
        }

        RawResponse resp = new RawResponse(
                "chatgpt",
                cr.id() == null ? "" : cr.id(),
                content,
                citations,
                !citations.isEmpty(),
                TIER,
                "Kademe 1 · chatgpt · " + (cr.model() == null ? "" : cr.model()),
                "");

        if (storage != null && !tenantId.isEmpty()) {
            try {
                resp = new RawResponse(resp.engineName(), resp.requestId(), resp.content(), resp.citations(),
                        resp.hasSearch(), resp.tier(), resp.fidelityLabel(), storage.saveRawResponse(tenantId, workspaceId, "chatgpt", raw));
            } catch (EngineException e) {
                resp = new RawResponse(resp.engineName(), resp.requestId(), resp.content(), resp.citations(),
                        resp.hasSearch(), resp.tier(), resp.fidelityLabel(), "");
            }
        }
        return resp;
    }

    private record ChatRequest(String model, List<Message> messages, double temperature) {
    }

    private record Message(String role, String content) {
    }

    private record ChatResponse(String id, String object, String model, List<Choice> choices, Usage usage) {
    }

    private record Choice(int index, ChatMessage message, String finish_reason) {
    }

    private record ChatMessage(String role, String content, List<Annotation> annotations) {
    }

    private record Annotation(String type, UrlCitation url_citation) {

        UrlCitation urlCitation() {
            return url_citation;
        }
    }

    private record UrlCitation(String url, String title, int start_index, int end_index) {
    }

    private record Usage(int prompt_tokens, int completion_tokens, int total_tokens) {
    }
}