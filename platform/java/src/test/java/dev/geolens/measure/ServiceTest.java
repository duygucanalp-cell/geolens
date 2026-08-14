package dev.geolens.measure;

import dev.geolens.config.Config;
import dev.geolens.engine.Citation;
import dev.geolens.engine.RawResponse;
import dev.geolens.engine.Registry;
import dev.geolens.engine.Tier;
import dev.geolens.measure.persistence.NoopScoreDao;
import dev.geolens.util.Ulid;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go {@code service_test.go} portu — saf skor fonksiyonları ve partial yayın testleri. */
class ServiceTest {

    private static RawResponse resp(String engineName, String content) {
        return new RawResponse(engineName, "", content, List.of(), false, Tier.DIRECT, "", "");
    }

    private static RawResponse resp(String engineName, String content, List<Citation> citations) {
        return new RawResponse(engineName, "", content, citations, !citations.isEmpty(), Tier.DIRECT, "", "");
    }

    @Test
    void computePresenceShareBrandMentioned() {
        List<RawResponse> responses = List.of(
                resp("e", "Acme şirketi harika bir ürün sunuyor. Acme pazar lideridir."),
                resp("e", "Rakipler arasında Acme en yenilikçi olanıdır."),
                resp("e", "Sektörde birçok firma var."));
        assertEquals(66.66666666666666, Scoring.computePresenceShare(responses, "Acme"), 1e-9);
    }

    @Test
    void computePresenceShareNoBrand() {
        List<RawResponse> responses = List.of(
                resp("e", "Sektördeki en büyük firma hakkında bilgi."),
                resp("e", "Pazar durumu değerlendirmesi."));
        assertEquals(0, Scoring.computePresenceShare(responses, "Acme"));
    }

    @Test
    void computePresenceShareEmptyResponses() {
        assertEquals(0, Scoring.computePresenceShare(List.of(), "Acme"));
    }

    @Test
    void computePositionWeightEarlyPosition() {
        List<RawResponse> responses = List.of(resp("e", "Acme pazar lideridir."));
        assertEquals(90, Scoring.computePositionWeight(responses));
    }

    @Test
    void computePositionWeightMidPosition() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            sb.append("Bu bir orta konum test cümlesidir. ");
        }
        assertEquals(60, Scoring.computePositionWeight(List.of(resp("e", sb.toString()))));
    }

    @Test
    void computePositionWeightLatePosition() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("Bu bir geç konum test cümlesidir. ");
        }
        assertEquals(30, Scoring.computePositionWeight(List.of(resp("e", sb.toString()))));
    }

    @Test
    void computeSourceShareDiverseSources() {
        List<RawResponse> responses = List.of(resp("e", "x", List.of(
                Citation.direct("https://example.com", "", 1, "e"),
                Citation.direct("https://test.org", "", 2, "e"),
                Citation.direct("https://sample.net", "", 3, "e"),
                Citation.direct("https://demo.com", "", 4, "e"),
                Citation.direct("https://wiki.org", "", 5, "e"))));
        assertEquals(100, Scoring.computeSourceShare(responses));
    }

    @Test
    void computeSourceShareNoCitations() {
        List<RawResponse> responses = List.of(resp("e", "x"));
        assertEquals(20, Scoring.computeSourceShare(responses));
    }

    @Test
    void aggregateFidelityLowestTier() {
        List<RawResponse> responses = List.of(
                new RawResponse("e", "", "x", List.of(), false, Tier.DIRECT, "Kademe 1", ""),
                new RawResponse("e", "", "y", List.of(), false, Tier.DIRECTIONAL, "Kademe 3", ""));
        assertEquals("Kademe 1", Scoring.aggregateFidelity(responses));
    }

    @Test
    void aggregateFidelityEmpty() {
        assertEquals("unknown", Scoring.aggregateFidelity(List.of()));
    }

    @Test
    void extractDomain() {
        assertEquals("example.com", Scoring.extractDomain("https://www.example.com/path"));
        assertEquals("test.org", Scoring.extractDomain("http://test.org"));
        assertEquals("sub.domain.co.uk", Scoring.extractDomain("https://sub.domain.co.uk/page"));
        assertEquals("", Scoring.extractDomain(""));
    }

    // ---- H15: Partial Yayın ----

    @Test
    void partialPublicationSingleEngine() {
        List<RawResponse> partialData = List.of(resp("perplexity",
                "Acme sektör lideridir. Yenilikçi ürünleriyle tanınır.",
                List.of(Citation.direct("https://example.com/acme", "", 1, "perplexity"),
                        Citation.direct("https://test.org/report", "", 2, "perplexity"))));

        assertTrue(Scoring.computePresenceShare(partialData, "Acme") != 0);
        assertTrue(Scoring.computePositionWeight(partialData) != 0);
        assertTrue(Scoring.computeSourceShare(partialData) != 0);
        assertTrue(Scoring.computeCompetitorContext(partialData) != 0);

        Map<String, Double> breakdown = Scoring.computeEngineBreakdown(partialData);
        assertEquals(2, breakdown.size());
        assertTrue(breakdown.containsKey("perplexity"));
        assertTrue(breakdown.containsKey("weighted_average"));
    }

    @Test
    void partialPublicationEmptyData() {
        assertEquals(0, Scoring.computePresenceShare(List.of(), "Acme"));
        assertEquals(0, Scoring.computePositionWeight(List.of()));
        assertEquals(20, Scoring.computeSourceShare(List.of()));
        assertEquals(50, Scoring.computeCompetitorContext(List.of()));

        Map<String, Double> breakdown = Scoring.computeEngineBreakdown(List.of());
        assertTrue(breakdown.isEmpty());
        assertFalse(breakdown.containsKey("weighted_average"));
    }

    @Test
    void partialPublicationMixedEngines() {
        List<RawResponse> data = List.of(
                resp("perplexity", "Acme yenilikçi bir firma.",
                        List.of(Citation.direct("https://example.com", "", 1, "perplexity"))),
                resp("chatgpt", "Acme pazar lideridir ve sektörde öncüdür.",
                        List.of(Citation.direct("https://test.org", "", 1, "chatgpt"))));

        assertTrue(Scoring.computePresenceShare(data, "Acme") != 0);
        assertEquals(3, Scoring.computeEngineBreakdown(data).size());
        assertTrue(Scoring.computeEngineBreakdown(data).containsKey("weighted_average"));

        double total = 0.30 * Scoring.computePresenceShare(data, "Acme")
                + 0.20 * Scoring.computePositionWeight(data)
                + 0.15 * Scoring.computeSourceShare(data)
                + 0.15 * Scoring.computeCompetitorContext(data)
                + 0.10 * Scoring.computeAppearanceRate(data)
                + 0.05 * Scoring.computeSentimentScore(data)
                + 0.05 * Scoring.computeCompVisibility(data, "Acme");
        assertTrue(total > 0);
    }

    // ---- H15: Determinizm (G2 ilkesi) ----

    @Test
    void calculateScoreDeterministic() {
        List<RawResponse> data = List.of(
                resp("perplexity", "Acme sektör lideridir ve yenilikçi ürünleriyle tanınır.",
                        List.of(Citation.direct("https://example.com/acme", "", 1, "perplexity"))),
                resp("chatgpt", "Acme pazar lideridir.",
                        List.of(Citation.direct("https://test.org", "", 1, "chatgpt"))));

        double first = Scoring.computeTotalScore(data, "Acme", ComponentWeights.EMPTY);
        double second = Scoring.computeTotalScore(data, "Acme", ComponentWeights.EMPTY);
        assertEquals(first, second);

        assertEquals(Scoring.computePresenceShare(data, "Acme"), Scoring.computePresenceShare(data, "Acme"));
        assertEquals(Scoring.computePositionWeight(data), Scoring.computePositionWeight(data));
        assertEquals(Scoring.computeSourceShare(data), Scoring.computeSourceShare(data));
        assertEquals(Scoring.computeCompetitorContext(data), Scoring.computeCompetitorContext(data));
    }

    @Test
    void partialPublicationDeterministicRecompute() {
        List<RawResponse> partial = List.of(
                resp("perplexity", "Acme yenilikçi bir firma.",
                        List.of(Citation.direct("https://example.com", "", 1, "perplexity"))));

        double prev = -1;
        for (int i = 0; i < 5; i++) {
            double total = Scoring.computeTotalScore(partial, "Acme", ComponentWeights.EMPTY);
            if (i > 0) {
                assertEquals(prev, total);
            }
            prev = total;
        }

        List<RawResponse> mixed = List.of(
                partial.get(0),
                resp("chatgpt", "Acme pazar lideridir.",
                        List.of(Citation.direct("https://test.org", "", 1, "chatgpt"))));

        double run1 = Scoring.computeTotalScore(mixed, "Acme", ComponentWeights.EMPTY);
        double run2 = Scoring.computeTotalScore(mixed, "Acme", ComponentWeights.EMPTY);
        assertEquals(run1, run2);
    }

    @Test
    void calculateScoreScoreRange() {
        List<RawResponse> empty = List.of(resp("perplexity", "Sektördeki en büyük firma hakkında bilgi."));
        assertTrue(Scoring.computeTotalScore(empty, "Acme", ComponentWeights.EMPTY) >= 0);

        List<RawResponse> high = List.of(resp("perplexity", "Acme Acme Acme Acme Acme Acme Acme Acme Acme Acme Acme Acme",
                List.of(Citation.direct("https://a.com", "", 1, "e"),
                        Citation.direct("https://b.org", "", 2, "e"),
                        Citation.direct("https://c.net", "", 3, "e"),
                        Citation.direct("https://d.io", "", 4, "e"),
                        Citation.direct("https://e.co", "", 5, "e"))));
        assertTrue(Scoring.computeTotalScore(high, "Acme", ComponentWeights.EMPTY) <= 100);
    }

    @Test
    void generateUlidUnique() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String id = Ulid.generate();
            assertTrue(ids.add(id), "yinelenen ULID üretildi: " + id);
        }
        String first = ids.iterator().next();
        assertEquals(26, first.length());
    }

    // ---- A3-5: 7 bileşenli VI ----

    @Test
    void effectiveWeightsV2Default() {
        Config cfg = new Config();
        cfg.scoreAlgorithmVersion = "2.0.0";
        MeasureService s = new MeasureService(new NoopScoreDao(), new Registry(), cfg);
        assertTrue(s.effectiveWeights().isV2());
    }

    @Test
    void effectiveWeightsV1Legacy() {
        Config cfg = new Config();
        cfg.scoreAlgorithmVersion = "1.0.0";
        MeasureService s = new MeasureService(new NoopScoreDao(), new Registry(), cfg);
        assertFalse(s.effectiveWeights().isV2());
        assertEquals(0.35, s.effectiveWeights().presenceShare());
    }

    @Test
    void effectiveWeightsV2EnvOverride() {
        Config cfg = new Config();
        cfg.scoreAlgorithmVersion = "2.0.0";
        cfg.scoreWeightsRaw = "0.25,0.20,0.15,0.15,0.10,0.10,0.05";
        MeasureService s = new MeasureService(new NoopScoreDao(), new Registry(), cfg);
        ComponentWeights w = s.effectiveWeights();
        assertEquals(0.25, w.presenceShare());
        assertEquals(0.10, w.sentiment());
        assertTrue(w.isV2());
    }

    @Test
    void computeComponentScoresV2SevenValues() {
        List<RawResponse> data = List.of(resp("perplexity", "Acme yenilikçi bir firma.",
                List.of(Citation.direct("https://example.com", "", 1, "perplexity"))));
        Scoring.Components c = Scoring.computeComponentScores(data, "Acme");
        assertTrue(c.presence() != 0);
        assertTrue(c.position() != 0);
        assertTrue(c.source() != 0);
        assertTrue(c.competitor() != 0);
        assertTrue(c.appearance() != 0);
        assertEquals(50, c.sentiment());
        assertTrue(c.compvis() != 0);
    }

    @Test
    void computeTotalScoreV2VsV1() {
        List<RawResponse> data = List.of(
                resp("perplexity", "Acme pazar lideri.", List.of(Citation.direct("https://a.com", "", 1, "e"))),
                resp("chatgpt", "Acme yenilikçi."));
        double v2 = Scoring.computeTotalScore(data, "Acme", ComponentWeights.V2_DEFAULT);
        double v1 = Scoring.computeTotalScore(data, "Acme", ComponentWeights.V1_LEGACY);
        assertTrue(v2 > 0);
        assertTrue(v1 > 0);
    }

    @Test
    void computeScoreCIV1FixedV2Dynamic() {
        Scoring.Ci ci1 = Scoring.computeScoreCI(50, ComponentWeights.V1_LEGACY);
        assertEquals(10.0, ci1.high() - ci1.low());

        Scoring.Ci ci2 = Scoring.computeScoreCI(50, ComponentWeights.V2_DEFAULT);
        assertTrue(ci2.high() - ci2.low() > 0);
    }

    // ---- 0309 §6.2: Per-motor ağırlıklı weighted_average ----

    @Test
    void computeEngineBreakdownWeightedAverage() {
        List<RawResponse> data = List.of(
                resp("perplexity", "Acme yenilikçi bir firma."),
                resp("gemini", "Acme pazar lideridir."));
        Map<String, Double> bd = Scoring.computeEngineBreakdown(data);
        assertEquals(75.0, bd.get("weighted_average"), 1e-9);
    }

    @Test
    void computeEngineBreakdownWeightedAverageDifferentScores() {
        List<RawResponse> data = List.of(
                resp("perplexity", "Acme yenilikçi."),
                resp("chatgpt", ""));
        Map<String, Double> bd = Scoring.computeEngineBreakdown(data);
        double want = (75 * 0.30 + 40 * 0.30) / 0.60;
        assertEquals(want, bd.get("weighted_average"), 1e-9);
    }

    @Test
    void computeEngineBreakdownUnknownEngineEqualWeight() {
        List<RawResponse> data = List.of(
                resp("perplexity", "Acme yenilikçi."),
                resp("future_engine", "Acme lider."));
        Map<String, Double> bd = Scoring.computeEngineBreakdown(data);
        assertEquals(75.0, bd.get("weighted_average"), 1e-9);
    }

    @Test
    void parseScoreWeightsV2() {
        Config cfg = new Config();
        cfg.scoreWeightsRaw = "0.25,0.20,0.15,0.15,0.10,0.10,0.05";
        var w = cfg.parseScoreWeightsV2();
        assertNotNull(w);
        assertEquals(0.25, w.presence());
        assertEquals(0.10, w.sentiment());

        Config bad = new Config();
        bad.scoreWeightsRaw = "0.5,0.5";
        assertEquals(null, bad.parseScoreWeightsV2());
    }
}