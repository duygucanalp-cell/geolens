package dev.geolens.recommendation;

import dev.geolens.recommendation.domain.EvaluationContext;
import dev.geolens.recommendation.domain.Recommendation;
import dev.geolens.recommendation.domain.Rule;
import dev.geolens.recommendation.domain.ScoreSnapshot;
import dev.geolens.recommendation.engine.RecommendationEngine;
import dev.geolens.recommendation.rules.DefaultRules;
import dev.geolens.recommendation.service.RecommendationService;
import dev.geolens.util.Ulid;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go {@code service_test.go} motor/test portu. */
class RecommendationEngineTest {

    private static EvaluationContext ctx(ScoreSnapshot score) {
        return new EvaluationContext("B01", "Acme", "WS01", "T01", score, null);
    }

    private static ScoreSnapshot score(double value, double previous, Map<String, Double> breakdown) {
        Instant now = Instant.now();
        if (previous != 0) {
            return new ScoreSnapshot(value, previous, now, now.minus(24, ChronoUnit.HOURS), breakdown);
        }
        return new ScoreSnapshot(value, 0, now, null, breakdown);
    }

    @Test
    void noScoreDataFiresNothing() {
        List<Recommendation> results = RecommendationEngine.evaluateBrand(
                ctx(new ScoreSnapshot(0, 0, null, null, null)), DefaultRules.RULES);
        assertEquals(0, results.size());
    }

    @Test
    void scoreDropRuleFires() {
        List<Recommendation> results = RecommendationEngine.evaluateBrand(ctx(score(50, 70, null)), DefaultRules.RULES);
        assertTrue(results.stream().anyMatch(r -> r.title().equals("Görünürlük skorunuz düşüyor")));
    }

    @Test
    void scoreDropRuleDoesNotFireOnSmallDrop() {
        List<Recommendation> results = RecommendationEngine.evaluateBrand(ctx(score(65, 70, null)), DefaultRules.RULES);
        assertTrue(results.stream().noneMatch(r -> r.title().equals("Görünürlük skorunuz düşüyor")));
    }

    @Test
    void trendDeclineRuleFires() {
        List<Recommendation> results = RecommendationEngine.evaluateBrand(ctx(score(55, 70, null)), DefaultRules.RULES);
        assertTrue(results.stream().anyMatch(r -> r.title().equals("Görünürlük trendiniz geriliyor")));
    }

    @Test
    void trendDeclineRuleNotFireOnRising() {
        List<Recommendation> results = RecommendationEngine.evaluateBrand(ctx(score(78, 65, null)), DefaultRules.RULES);
        assertTrue(results.stream().noneMatch(r -> r.title().equals("Görünürlük trendiniz geriliyor")));
    }

    @Test
    void engineGapRuleFires() {
        List<Recommendation> results = RecommendationEngine.evaluateBrand(
                ctx(score(65, 0, Map.of("perplexity", 85.0, "chatgpt", 45.0, "gemini", 80.0))), DefaultRules.RULES);
        assertTrue(results.stream().anyMatch(r -> r.title().equals("Motorlar arasında büyük performans farkı var")));
    }

    @Test
    void engineGapRuleNotFireOnSmallGap() {
        List<Recommendation> results = RecommendationEngine.evaluateBrand(
                ctx(score(65, 0, Map.of("perplexity", 72.0, "chatgpt", 68.0))), DefaultRules.RULES);
        assertTrue(results.stream().noneMatch(r -> r.title().equals("Motorlar arasında büyük performans farkı var")));
    }

    @Test
    void multipleRulesFireTogether() {
        List<Recommendation> results = RecommendationEngine.evaluateBrand(
                ctx(score(30, 50, Map.of("perplexity", 35.0, "chatgpt", 25.0))), DefaultRules.RULES);
        assertTrue(results.size() >= 2);
    }

    @Test
    void confidenceStaysBetweenZeroAndHundred() {
        List<Recommendation> results = RecommendationEngine.evaluateBrand(ctx(score(50, 70, null)), DefaultRules.RULES);
        assertFalse(results.isEmpty());
        for (Recommendation r : results) {
            assertTrue(r.score() >= 0 && r.score() <= 100);
        }
    }

    @Test
    void registerCustomRuleForcesActiveAndGeneratesId() {
        RecommendationService svc = RecommendationService.withoutDatabase();
        svc.registerCustomRule(new Rule("", "Custom Rule", null, null, null, null, List.of(),
                null, null, null, false, null));
        Rule custom = svc.getRules().stream()
                .filter(r -> "Custom Rule".equals(r.name()))
                .findFirst()
                .orElse(null);
        assertNotNull(custom);
        assertTrue(custom.active());
        assertNotNull(custom.id());
        assertTrue(custom.id().startsWith("rule-custom-"));
    }

    @Test
    void registerCustomRulePreservesId() {
        RecommendationService svc = RecommendationService.withoutDatabase();
        svc.registerCustomRule(new Rule("my-custom-rule", "Custom Rule", null, null, null, null, List.of(),
                "title", "detail", null, false, null));
        Rule custom = svc.getRules().stream()
                .filter(r -> "my-custom-rule".equals(r.id()))
                .findFirst()
                .orElse(null);
        assertNotNull(custom);
        assertTrue(custom.active());
    }

    @Test
    void getRulesReturnsBasePlusSectorRules() {
        RecommendationService svc = RecommendationService.withoutDatabase();
        assertEquals(DefaultRules.RULES.size() + 4, svc.getRules().size());
    }

    @Test
    void getRulesBySectorReturnsPackage() {
        RecommendationService svc = RecommendationService.withoutDatabase();
        assertEquals(2, svc.getRulesBySector("e-ticaret").size());
        assertEquals(1, svc.getRulesBySector("saglik").size());
        assertEquals(1, svc.getRulesBySector("finans").size());
        assertEquals(0, svc.getRulesBySector("bilinmeyen").size());
    }

    @Test
    void ulidIsUniqueAndNonEmpty() {
        String id1 = Ulid.generate();
        String id2 = Ulid.generate();
        assertFalse(id1.isEmpty());
        assertEquals(26, id1.length());
        assertFalse(id1.equals(id2));
    }
}