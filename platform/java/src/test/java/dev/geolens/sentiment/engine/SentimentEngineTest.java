package dev.geolens.sentiment.engine;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.geolens.sentiment.domain.CheckTarget;
import dev.geolens.sentiment.domain.HallucinationResult;
import dev.geolens.sentiment.domain.RawResponse;
import dev.geolens.sentiment.domain.SentimentResult;
import dev.geolens.sentiment.ml.HttpServingMlClient;
import dev.geolens.sentiment.persistence.NoopSentimentDao;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go sentiment/engine_test.go portu — httptest karşılığı com.sun.net.httpserver fake serving. */
class SentimentEngineTest {

    @FunctionalInterface
    interface Responder {
        /** istek gövdesi → "status|body" (ör. "200|{...}"). */
        String respond(String requestBody);
    }

    private static final class FakeServing implements AutoCloseable {
        final HttpServer server;
        final AtomicInteger calls = new AtomicInteger();
        Responder responder = body -> "500|{}";

        FakeServing() {
            try {
                server = HttpServer.create(new InetSocketAddress(0), 0);
                server.createContext("/", exchange -> {
                    calls.incrementAndGet();
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    String resp = responder.respond(body);
                    int status = Integer.parseInt(resp.substring(0, 3));
                    byte[] out = resp.substring(4).getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(status, out.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(out);
                    }
                    exchange.close();
                });
                server.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        HttpServingMlClient client() {
            return new HttpServingMlClient("http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(2));
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static String ok(String json) {
        return "200|" + json;
    }

    @Test
    void mlNilIsRuleBased() {
        SentimentEngine e = new SentimentEngine(new NoopSentimentDao());
        SentimentResult res = e.analyzeWithML("chatgpt", "brand-1", raw("Acme harika bir ürün"));
        assertEquals(1.0, res.overallSentiment());
        assertEquals(1.0, res.positiveScore());
        assertEquals("chatgpt", res.engineName());
        assertEquals("brand-1", res.brandId());
        assertNotNull(res.analyzedAt());
    }

    @Test
    void mlFirstPositive() {
        try (FakeServing s = new FakeServing()) {
            s.responder = body -> ok("{\"model\":\"sentiment\",\"model_version\":\"1.0.0\",\"outputs\":{\"logits\":[[0.1,0.2,2.1]]}}");
            SentimentEngine e = new SentimentEngine(new NoopSentimentDao(), s.client());
            SentimentResult res = e.analyzeWithML("gemini", "brand-1", raw("bu metin serving ile analiz edilir"));
            assertTrue(res.positiveScore() >= 0.7);
            assertEquals(1, res.mentionCount());
            double sum = res.negativeScore() + res.neutralScore() + res.positiveScore();
            assertTrue(sum > 0.999 && sum < 1.001);
        }
    }

    @Test
    void perResponseAggregation() {
        try (FakeServing s = new FakeServing()) {
            s.responder = body -> body.contains("kötü")
                    ? ok("{\"model\":\"sentiment\",\"model_version\":\"1.0.0\",\"outputs\":{\"logits\":[[2.0,0.1,0.1]]}}")
                    : ok("{\"model\":\"sentiment\",\"model_version\":\"1.0.0\",\"outputs\":{\"logits\":[[0.1,0.1,2.0]]}}");
            SentimentEngine e = new SentimentEngine(new NoopSentimentDao(), s.client());
            SentimentResult res = e.analyzeWithML("chatgpt", "brand-1",
                    raw("harika ürün tavsiye ederim", "kötü kalitesiz pahalı"));
            assertEquals(2, res.mentionCount());
            assertTrue(res.positiveScore() > res.negativeScore());
            assertTrue(res.positiveScore() < 1.0 && res.negativeScore() > 0.0);
            double sum = res.negativeScore() + res.neutralScore() + res.positiveScore();
            assertTrue(sum > 0.999 && sum < 1.001);
        }
    }

    @Test
    void partialFailureMergesSuccessful() {
        try (FakeServing s = new FakeServing()) {
            s.responder = body -> body.contains("patla")
                    ? "500|{}"
                    : ok("{\"model\":\"sentiment\",\"model_version\":\"1.0.0\",\"outputs\":{\"logits\":[[0.1,0.1,2.0]]}}");
            SentimentEngine e = new SentimentEngine(new NoopSentimentDao(), s.client());
            SentimentResult res = e.analyzeWithML("chatgpt", "brand-1", raw("harika ürün", "patla", "mükemmel servis"));
            assertEquals(2, res.mentionCount());
            assertTrue(res.positiveScore() >= 0.7);
        }
    }

    @Test
    void skipsEmptyText() {
        try (FakeServing s = new FakeServing()) {
            s.responder = body -> ok("{\"model\":\"sentiment\",\"model_version\":\"1.0.0\",\"outputs\":{\"logits\":[[0.1,0.2,2.1]]}}");
            SentimentEngine e = new SentimentEngine(new NoopSentimentDao(), s.client());
            SentimentResult res = e.analyzeWithML("chatgpt", "brand-1", raw("   ", "", "harika ürün"));
            assertEquals(1, res.mentionCount());
            assertEquals(1, s.calls.get());
        }
    }

    @Test
    void earlyAbortOnTotalFailure() {
        try (FakeServing s = new FakeServing()) {
            s.responder = body -> {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                return "500|{}";
            };
            SentimentEngine e = new SentimentEngine(new NoopSentimentDao(), s.client());
            List<RawResponse> many = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                many.add(new RawResponse("r" + i, "chatgpt", "Acme harika ürün", java.time.Instant.now()));
            }
            SentimentResult res = e.analyzeWithML("chatgpt", "brand-1", many);
            assertEquals(1.0, res.overallSentiment());
            assertEquals(1, s.calls.get(), "ilk hata sonrası kalan yanıtlar için ML denenmemeli");
        }
    }

    @Test
    void fallbackOnError() {
        try (FakeServing s = new FakeServing()) {
            s.responder = body -> "500|{\"detail\":\"inference hatası\"}";
            SentimentEngine e = new SentimentEngine(new NoopSentimentDao(), s.client());
            SentimentResult res = e.analyzeWithML("perplexity", "brand-1", raw("Acme harika bir ürün"));
            assertEquals(1.0, res.overallSentiment());
            assertEquals("perplexity", res.engineName());
            assertEquals("brand-1", res.brandId());
        }
    }

    @Test
    void cooldownSkipsMl() {
        try (FakeServing s = new FakeServing()) {
            s.responder = body -> "500|{}";
            SentimentEngine e = new SentimentEngine(new NoopSentimentDao(), s.client());
            List<RawResponse> one = raw("Acme harika bir ürün");
            SentimentResult res1 = e.analyzeWithML("chatgpt", "brand-1", one);
            assertEquals(1.0, res1.overallSentiment());
            assertTrue(e.breaker.inCooldown(), "serving hatası sonrası cooldown başlamalı");
            SentimentResult res2 = e.analyzeWithML("chatgpt", "brand-1", one);
            assertEquals(1.0, res2.overallSentiment());
            assertEquals(1, s.calls.get(), "cooldown'da ikinci ML çağrısı beklenmez");
        }
    }

    @Test
    void aggregateWeighted() {
        double[] avg = SentimentEngine.aggregateWeighted(
                List.of(new double[]{0.5, 0.25, 0.25}, new double[]{0.2, 0.3, 0.5}), List.of(3.0, 1.0));
        double[] want = {0.425, 0.2625, 0.3125};
        for (int i = 0; i < 3; i++) {
            assertEquals(want[i], avg[i], 1e-9);
        }
        double[] eq = SentimentEngine.aggregateWeighted(
                List.of(new double[]{0.2, 0.3, 0.5}, new double[]{0.4, 0.2, 0.4}), List.of(1.0, 1.0));
        assertEquals(0.3, eq[0], 1e-9);
        assertEquals(0.25, eq[1], 1e-9);
        assertEquals(0.45, eq[2], 1e-9);
        double[] z = SentimentEngine.aggregateWeighted(List.of(), List.of());
        assertEquals(0.0, z[0]);
        assertEquals(0.0, z[1]);
        assertEquals(0.0, z[2]);
    }

    @Test
    void sentimentFromProbabilities() {
        var res = SentimentEngine.sentimentFromProbabilities("chatgpt", "brand-1", new double[]{0.2, 0.6, 0.2}, 3);
        assertEquals(0.6, res.neutralScore());
        assertEquals(0.2 + 0.6 * 0.5, res.overallSentiment());
        assertEquals(3, res.mentionCount());
    }

    @Test
    void groupByPrompt() {
        List<CheckTarget> targets = List.of(
                new CheckTarget("r1", "chatgpt", "a", "P1", "Acme", ""),
                new CheckTarget("r2", "gemini", "b", "P2", "Acme", ""),
                new CheckTarget("r3", "perplexity", "c", "P1", "Acme", ""),
                new CheckTarget("r4", "grok", "d", "", "Acme", ""),
                new CheckTarget("r5", "claude", "e", "P2", "Acme", ""));
        var groups = SentimentEngine.groupByPrompt(targets);
        assertEquals(3, groups.size());
        assertEquals("r1", groups.get(0).get(0).id());
        assertEquals(2, groups.get(0).size());
        assertEquals("r2", groups.get(1).get(0).id());
        assertEquals(2, groups.get(1).size());
        assertEquals("r4", groups.get(2).get(0).id());
        assertEquals(1, groups.get(2).size());
    }

    @Test
    void applyMLCrossSourceSamePromptOnly() {
        List<String> sent = new ArrayList<>();
        try (FakeServing s = new FakeServing()) {
            s.responder = body -> {
                sent.add(body);
                return ok("{\"findings\":[{\"type\":\"T3\",\"severity\":\"high\",\"description\":\"Çelişik sayısal claim\",\"confidence\":0.7,\"engine\":\"chatgpt\"}]}");
            };
            SentimentEngine e = new SentimentEngine(new NoopSentimentDao(), s.client());
            List<CheckTarget> targets = List.of(
                    new CheckTarget("r1", "chatgpt", "MobiTel %30 büyüme", "P1", "", ""),
                    new CheckTarget("r2", "gemini", "MobiTel %60 büyüme", "P1", "", ""),
                    new CheckTarget("r3", "perplexity", "farklı prompt yanıtı", "P2", "", ""));
            var res = e.applyMLCrossSource(List.of(), targets, "brand-1");
            assertEquals(1, res.size());
            assertEquals(1, sent.size(), "tek çağrı beklenir");
            var body = sent.get(0);
            assertTrue(body.contains("r1") && body.contains("r2") && !body.contains("r3"),
                    "prompt karışımı: " + body);
        }
    }

    @Test
    void applyMLCrossSourceSingleGroupNoCall() {
        try (FakeServing s = new FakeServing()) {
            s.responder = body -> ok("{\"findings\":[]}");
            SentimentEngine e = new SentimentEngine(new NoopSentimentDao(), s.client());
            List<CheckTarget> targets = List.of(
                    new CheckTarget("r1", "chatgpt", "a", "P1", "", ""),
                    new CheckTarget("r2", "gemini", "b", "P2", "", ""));
            assertEquals(0, e.applyMLCrossSource(List.of(), targets, "brand-1").size());
            assertEquals(0, s.calls.get(), "tek yanıtlı gruplarda serving çağrısı beklenmez");
        }
    }

    @Test
    void applyMLCrossSourceNoMlClient() {
        SentimentEngine e = new SentimentEngine(new NoopSentimentDao());
        List<CheckTarget> targets = List.of(
                new CheckTarget("r1", "chatgpt", "a", "P1", "", ""),
                new CheckTarget("r2", "gemini", "b", "P1", "", ""));
        var base = List.of(hall("T2", "kural"));
        assertEquals(1, e.applyMLCrossSource(base, targets, "brand-1").size());
    }

    @Test
    void applyMLCrossSourceError() {
        try (FakeServing s = new FakeServing()) {
            s.responder = body -> "500|{}";
            SentimentEngine e = new SentimentEngine(new NoopSentimentDao(), s.client());
            List<CheckTarget> targets = List.of(
                    new CheckTarget("r1", "chatgpt", "a", "P1", "", ""),
                    new CheckTarget("r2", "gemini", "b", "P1", "", ""));
            var base = List.of(hall("T1", "kural"));
            var res = e.applyMLCrossSource(base, targets, "brand-1");
            assertEquals(1, res.size());
            assertTrue(e.breaker.inCooldown(), "serving hatası sonrası cooldown başlamalı");
            assertEquals(1, e.applyMLCrossSource(base, targets, "brand-1").size());
            assertEquals(1, s.calls.get(), "cooldown'da ikinci ML çağrısı beklenmez");
        }
    }

    @Test
    void ruleBasedHallucinations() {
        SentimentEngine e = new SentimentEngine(new NoopSentimentDao());
        List<CheckTarget> targets = List.of(
                new CheckTarget("r1", "chatgpt", "Acme %30 pazar payına sahip", "", "", ""),
                new CheckTarget("r2", "gemini", "rakip ürünler hakkında bilgi", "", "Acme", ""));
        var res = e.ruleBasedHallucinations(targets, "brand-1");
        Set<String> types = new java.util.HashSet<>();
        for (HallucinationResult h : res) {
            types.add(h.hallucinationType());
            assertEquals("brand-1", h.brandId());
        }
        assertTrue(types.contains("T1"), "T1 beklenir: " + res);
        assertTrue(types.contains("T3"), "T3 beklenir: " + res);
    }

    @Test
    void mergeHallucinationResults() {
        var rules = List.of(
                hall("T2", "AI yanıtı kaynak/citation referansı içeriyor"),
                hall("T4", "AI yanıtı doğrulanmamış olumsuz ifade içeriyor"));
        var mlFindings = List.of(
                hall("T3", "Çelişik sayısal claim: '%30' vs '%60'"),
                hall("T2", "AI yanıtı kaynak/citation referansı içeriyor"));
        var merged = SentimentEngine.mergeHallucinationResults(rules, mlFindings);
        assertEquals(3, merged.size(), merged.toString());
        assertEquals(1, merged.stream().filter(h -> h.hallucinationType().equals("T2")).count());
        assertEquals(1, merged.stream().filter(h -> h.hallucinationType().equals("T3")).count());
        assertEquals(1, merged.stream().filter(h -> h.hallucinationType().equals("T4")).count());
    }

    @Test
    void detectHallucinationsWithMLSuccess() {
        try (FakeServing s = new FakeServing()) {
            s.responder = body -> ok("{\"findings\":[{\"type\":\"T3\",\"severity\":\"high\",\"description\":\"Çelişik sayısal claim\",\"confidence\":0.7,\"engine\":\"chatgpt\"}]}");
            SentimentEngine e = new SentimentEngine(new NoopSentimentDao(), s.client());
            List<CheckTarget> targets = List.of(
                    new CheckTarget("r1", "chatgpt", "MobiTel %30 büyüme bildirdi", "", "", ""),
                    new CheckTarget("r2", "gemini", "MobiTel %60 büyüme iddia ediyor", "", "", ""));
            var res = e.detectHallucinationsWithML(targets, "brand-1");
            assertEquals(1, res.size());
            var h = res.get(0);
            assertEquals("T3", h.hallucinationType());
            assertEquals("chatgpt", h.engineName());
            assertEquals("brand-1", h.brandId());
            assertEquals(0.7, h.confidence());
            assertEquals("high", h.severity());
        }
    }

    @Test
    void detectHallucinationsWithMLErrorThrows() {
        try (FakeServing s = new FakeServing()) {
            s.responder = body -> "500|{}";
            SentimentEngine e = new SentimentEngine(new NoopSentimentDao(), s.client());
            List<CheckTarget> targets = List.of(
                    new CheckTarget("r1", "chatgpt", "a", "", "", ""),
                    new CheckTarget("r2", "gemini", "b", "", "", ""));
            var threw = false;
            try {
                e.detectHallucinationsWithML(targets, "brand-1");
            } catch (RuntimeException ex) {
                threw = true;
            }
            assertTrue(threw, "serving 500 için hata bekleniyor");
        }
    }

    private static HallucinationResult hall(String type, String desc) {
        return new HallucinationResult(null, "brand-1", "chatgpt", type, "medium", desc, 0.5, null, java.time.Instant.now());
    }

    private static List<RawResponse> raw(String... contents) {
        List<RawResponse> out = new ArrayList<>();
        for (int i = 0; i < contents.length; i++) {
            out.add(new RawResponse("r" + (i + 1), "chatgpt", contents[i], java.time.Instant.now()));
        }
        return out;
    }
}