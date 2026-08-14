package dev.geolens.measure;

import com.sun.net.httpserver.HttpServer;
import dev.geolens.config.Config;
import dev.geolens.engine.Registry;
import dev.geolens.measure.persistence.NoopScoreDao;
import dev.geolens.ml.HttpMlClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go {@code intent_test.go} portu — 0421 A3-3 intent ölçekleme. */
class IntentTest {

    private static final ComponentWeights DEFAULT = ComponentWeights.V2_DEFAULT;

    private static MeasurementResult result() {
        return new MeasurementResult(List.of(), List.of(), null, "", "", "", "", "",
                "Acme'nin en iyi rakibi kim?");
    }

    private static HttpServer mockServer(String status, String body, AtomicInteger calls) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.equals("200") ? 200 : 500, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();
        return server;
    }

    // ---- applyIntentWeights ----

    @Test
    void applyIntentWeightsRenormalizes() {
        ComponentWeights out = Scoring.applyIntentWeights(DEFAULT, "comparison");
        double sum = out.presenceShare() + out.positionWeight() + out.sourceShare() + out.competitorContext()
                + out.appearanceRate() + out.sentiment() + out.compVisibility();
        assertEquals(1.0, sum, 1e-9);
    }

    @Test
    void applyIntentWeightsInformation() {
        ComponentWeights out = Scoring.applyIntentWeights(DEFAULT, "information");
        double base = 0.30;
        assertTrue(out.presenceShare() >= base - 1e-9, "presence artmalı, got " + out.presenceShare());
    }

    @Test
    void applyIntentWeightsNewIntents() {
        Map<String, Double> expectedNew = Map.of(
                "opinion", DEFAULT.appearanceRate(),
                "complaint", DEFAULT.sentiment(),
                "purchase", DEFAULT.competitorContext(),
                "news", DEFAULT.sourceShare());

        Map<String, String> intent = Map.of(
                "opinion", "appearance",
                "complaint", "sentiment",
                "purchase", "competitorContext",
                "news", "sourceShare");

        for (Map.Entry<String, Double> e : expectedNew.entrySet()) {
            String intentName = e.getKey();
            double baseVal = e.getValue();
            ComponentWeights out = Scoring.applyIntentWeights(DEFAULT, intentName);
            double scaled = switch (intentName) {
                case "opinion" -> out.appearanceRate();
                case "complaint" -> out.sentiment();
                case "purchase" -> out.competitorContext();
                case "news" -> out.sourceShare();
                default -> throw new IllegalStateException();
            };
            assertTrue(scaled > baseVal, intentName + " için " + intent.get(intentName) + " artmalı");
        }
    }

    @Test
    void applyIntentWeightsUnknownIntent() {
        ComponentWeights out = Scoring.applyIntentWeights(DEFAULT, "unknown_intent");
        assertEquals(DEFAULT.presenceShare(), out.presenceShare());
        assertEquals(DEFAULT.positionWeight(), out.positionWeight());
        assertEquals(DEFAULT.sourceShare(), out.sourceShare());
        assertEquals(DEFAULT.competitorContext(), out.competitorContext());
        assertEquals(DEFAULT.appearanceRate(), out.appearanceRate());
        assertEquals(DEFAULT.sentiment(), out.sentiment());
        assertEquals(DEFAULT.compVisibility(), out.compVisibility());
    }

    @Test
    void applyIntentWeightsV1NoChange() {
        ComponentWeights out = Scoring.applyIntentWeights(ComponentWeights.V1_LEGACY, "information");
        assertEquals(ComponentWeights.V1_LEGACY.presenceShare(), out.presenceShare());
        assertEquals(ComponentWeights.V1_LEGACY.competitorContext(), out.competitorContext());
        assertTrue(out.appearanceRate() == 0 && out.sentiment() == 0 && out.compVisibility() == 0);
    }

    // ---- applyIntentWeightsWithScale ----

    @Test
    void applyIntentWeightsWithScaleEnvOverride() {
        Map<String, double[]> scale = Config.parseIntentWeightScaleRaw("information=1.50,1,1,1,1,1,1");
        ComponentWeights out = Scoring.applyIntentWeightsWithScale(DEFAULT, "information", scale);
        assertTrue(out.presenceShare() > 0.33, "information presence override artmalı, got " + out.presenceShare());
        assertFalse(out.presenceShare() == 0.30);
    }

    @Test
    void applyIntentWeightsWithScaleUnknownIntent() {
        Map<String, double[]> scale = Config.parseIntentWeightScaleRaw("information=1.50,1,1,1,1,1,1");
        ComponentWeights out = Scoring.applyIntentWeightsWithScale(DEFAULT, "unknown", scale);
        assertEquals(DEFAULT.presenceShare(), out.presenceShare());
        assertEquals(DEFAULT.competitorContext(), out.competitorContext());
    }

    @Test
    void applyIntentWeightsWithScaleNewIntent() {
        Map<String, double[]> scale = Config.parseIntentWeightScaleRaw("sonuç=0.80,1.10,0.90,1.20,1.10,0.95,1.00");
        ComponentWeights out = Scoring.applyIntentWeightsWithScale(DEFAULT, "sonuç", scale);
        assertTrue(out.competitorContext() > 0.15, "yeni intent competitor'ı artmalı, got " + out.competitorContext());
        assertEquals(1.0, out.presenceShare() + out.positionWeight() + out.sourceShare() + out.competitorContext()
                + out.appearanceRate() + out.sentiment() + out.compVisibility(), 1e-9);
    }

    // ---- intentWeights (MeasureService) ----

    @Test
    void intentWeightsMlFirst() throws Exception {
        String body = """
                {"intent":{"label":"comparison","confidence":0.9},"topic":"competitor","persona":"buyer","funnel":"decision"}
                """;
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = mockServer("200", body, calls);
        try {
            String url = "http://localhost:" + server.getAddress().getPort();
            HttpMlClient ml = new HttpMlClient(url, Duration.ofSeconds(2));
            MeasureService s = new MeasureService(new NoopScoreDao(), new Registry(), new Config(), ml);

            var first = s.intentWeights(List.of(result()), DEFAULT);
            assertTrue(first.ok());
            assertTrue(first.weights().competitorContext() > DEFAULT.competitorContext());

            var second = s.intentWeights(List.of(result()), DEFAULT);
            assertTrue(second.ok());
            assertEquals(1, calls.get(), "aynı prompt ikinci çağrıda önbellekten gelmeli");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void intentWeightsNewIntentScale() throws Exception {
        String body = """
                {"intent":{"label":"karşılaştırma","confidence":0.85},"topic":"tech","persona":"analyst","funnel":"research"}
                """;
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = mockServer("200", body, calls);
        try {
            String url = "http://localhost:" + server.getAddress().getPort();
            HttpMlClient ml = new HttpMlClient(url, Duration.ofSeconds(2));
            Config cfg = new Config();
            cfg.intentWeightScaleRaw = "karşılaştırma=0.90,1.00,0.90,1.40,0.90,0.90,1.30";
            MeasureService s = new MeasureService(new NoopScoreDao(), new Registry(), cfg, ml);

            var out = s.intentWeights(List.of(result()), DEFAULT);
            assertTrue(out.ok());
            assertTrue(out.weights().competitorContext() > DEFAULT.competitorContext());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void intentWeightsEmptyPromptNoCall() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = mockServer("200", "{}", calls);
        try {
            String url = "http://localhost:" + server.getAddress().getPort();
            HttpMlClient ml = new HttpMlClient(url, Duration.ofSeconds(2));
            MeasureService s = new MeasureService(new NoopScoreDao(), new Registry(), new Config(), ml);

            MeasurementResult empty = new MeasurementResult(List.of(), List.of(), null, "", "", "", "", "", "  ");
            var out = s.intentWeights(List.of(empty), DEFAULT);
            assertFalse(out.ok());
            assertEquals(0, calls.get(), "boş prompt servis çağrısı yapmamalı");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void intentWeightsFallbackOnErrorAndCooldown() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = mockServer("500", "error", calls);
        try {
            String url = "http://localhost:" + server.getAddress().getPort();
            HttpMlClient ml = new HttpMlClient(url, Duration.ofSeconds(2));
            MeasureService s = new MeasureService(new NoopScoreDao(), new Registry(), new Config(), ml);

            var first = s.intentWeights(List.of(result()), DEFAULT);
            assertFalse(first.ok(), "serving hatasında fallback varsayılan ağırlıklar");
            assertEquals(1, calls.get());

            var second = s.intentWeights(List.of(result()), DEFAULT);
            assertFalse(second.ok(), "cooldown'da fallback");
            assertEquals(1, calls.get(), "cooldown'da ikinci HTTP isteği yapılmamalı");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void intentWeightsNilClient() {
        MeasureService s = new MeasureService(new NoopScoreDao(), new Registry(), null, null);
        var out = s.intentWeights(List.of(result()), DEFAULT);
        assertFalse(out.ok());
    }

    // ---- effectiveIntentScale ----

    @Test
    void newServiceWithConfigIntentScale() {
        Config cfg = new Config();
        cfg.intentWeightScaleRaw = "information=1.50,1,1,1,1,1,1";
        MeasureService s = new MeasureService(new NoopScoreDao(), new Registry(), cfg, null);
        double[] info = s.effectiveIntentScale().get("information");
        assertEquals(1.5, info[0]);
    }

    @Test
    void newServiceNoConfigScale() {
        MeasureService s = new MeasureService(new NoopScoreDao(), new Registry(), new Config(), null);
        assertTrue(s.effectiveIntentScale() == Scoring.DEFAULT_INTENT_SCALE,
                "override yokken varsayılan ölçek kullanılmalı");
    }
}