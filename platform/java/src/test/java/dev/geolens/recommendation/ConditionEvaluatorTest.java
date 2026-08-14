package dev.geolens.recommendation;

import dev.geolens.recommendation.domain.AuditSnapshot;
import dev.geolens.recommendation.domain.Condition;
import dev.geolens.recommendation.domain.EvaluationContext;
import dev.geolens.recommendation.domain.ScoreSnapshot;
import dev.geolens.recommendation.engine.ConditionEvaluator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go {@code service_test.go} koşul testlerinin JUnit portu. */
class ConditionEvaluatorTest {

    private static EvaluationContext ctx(ScoreSnapshot score, AuditSnapshot audit) {
        return new EvaluationContext("B01", "Acme", "WS01", "T01", score, audit);
    }

    private static ScoreSnapshot score(double value, double previous, Map<String, Double> breakdown) {
        Instant now = Instant.now();
        ScoreSnapshot s = new ScoreSnapshot(value, 0, now, null, breakdown);
        if (previous != 0) {
            return new ScoreSnapshot(value, previous, now, now.minus(24, ChronoUnit.HOURS), breakdown);
        }
        return s;
    }

    @Test
    void emptyConditionsEvaluateToTrue() {
        assertTrue(ConditionEvaluator.evaluateAll(ctx(null, null), List.of()));
    }

    @Test
    void nilScoreEvaluatesAllConditionsFalse() {
        assertFalse(ConditionEvaluator.evaluate(ctx(null, null),
                new Condition("score.drop", "gt", 10.0)));
    }

    @Test
    void noPreviousScoreConditionsNotMatch() {
        EvaluationContext c = ctx(new ScoreSnapshot(50, 0, Instant.now(), null, null), null);

        assertFalse(ConditionEvaluator.evaluate(c, new Condition("score.trend", "eq", "declining")));
        assertFalse(ConditionEvaluator.evaluate(c, new Condition("score.engine_gap", "gt", 30.0)));
    }

    @Test
    void scoreDropMatchesOnlyForLargeDrops() {
        EvaluationContext dropped = ctx(score(50, 70, null), null); // 70 → 50 (drop 20)
        assertTrue(ConditionEvaluator.evaluate(dropped, new Condition("score.drop", "gt", 10.0)));

        EvaluationContext small = ctx(score(65, 70, null), null); // drop 5
        assertFalse(ConditionEvaluator.evaluate(small, new Condition("score.drop", "gt", 10.0)));
    }

    @Test
    void trendDeclineMatchesWhenDecliningNotWhenRising() {
        EvaluationContext declining = ctx(score(55, 70, null), null);
        assertTrue(ConditionEvaluator.evaluate(declining, new Condition("score.trend", "eq", "declining")));

        EvaluationContext rising = ctx(score(78, 65, null), null);
        assertFalse(ConditionEvaluator.evaluate(rising, new Condition("score.trend", "eq", "declining")));
    }

    @Test
    void engineGapFiresOverThresholdNotUnder() {
        EvaluationContext bigGap = ctx(score(65, 0, Map.of("perplexity", 85.0, "chatgpt", 45.0, "gemini", 80.0)), null);
        assertTrue(ConditionEvaluator.evaluate(bigGap, new Condition("score.engine_gap", "gt", 30.0)));

        EvaluationContext smallGap = ctx(score(65, 0, Map.of("perplexity", 72.0, "chatgpt", 68.0)), null);
        assertFalse(ConditionEvaluator.evaluate(smallGap, new Condition("score.engine_gap", "gt", 30.0)));
    }

    @Test
    void auditConditionsRequireHasData() {
        EvaluationContext noAudit = ctx(score(50, 0, null), null);
        assertFalse(ConditionEvaluator.evaluate(noAudit,
                new Condition("audit.robots_txt.disallowed_all", "eq", true)));

        EvaluationContext withAudit = ctx(score(50, 0, null),
                new AuditSnapshot(true, 40, true, false, true));
        assertTrue(ConditionEvaluator.evaluate(withAudit,
                new Condition("audit.robots_txt.disallowed_all", "eq", true)));
        assertTrue(ConditionEvaluator.evaluate(withAudit,
                new Condition("audit.ssr.has_structured_data", "eq", false)));
        assertFalse(ConditionEvaluator.evaluate(withAudit,
                new Condition("audit.bot_access.accessible", "eq", false)));
        assertTrue(ConditionEvaluator.evaluate(withAudit,
                new Condition("audit.bot_access.accessible", "eq", true)));
    }

    @Test
    void unknownFieldEvaluatesFalse() {
        EvaluationContext c = ctx(score(50, 70, null), null);
        assertFalse(ConditionEvaluator.evaluate(c, new Condition("bilinmeyen", "gt", 0.0)));
    }

    @Test
    void compareFloatMatchesGoBehavior() {
        assertEquals(true, ConditionEvaluator.compareDouble(10, "gt", 5));
        assertEquals(false, ConditionEvaluator.compareDouble(3, "gt", 5));
        assertEquals(true, ConditionEvaluator.compareDouble(3, "lt", 5));
        assertEquals(false, ConditionEvaluator.compareDouble(10, "lt", 5));
        assertEquals(true, ConditionEvaluator.compareDouble(5, "eq", 5));
        assertEquals(false, ConditionEvaluator.compareDouble(6, "eq", 5));
        assertEquals(true, ConditionEvaluator.compareDouble(5, "gte", 5));
        assertEquals(true, ConditionEvaluator.compareDouble(6, "gte", 5));
        assertEquals(false, ConditionEvaluator.compareDouble(4, "gte", 5));
        assertEquals(true, ConditionEvaluator.compareDouble(5, "lte", 5));
        assertEquals(true, ConditionEvaluator.compareDouble(4, "lte", 5));
        assertEquals(false, ConditionEvaluator.compareDouble(6, "lte", 5));
        assertEquals(false, ConditionEvaluator.compareDouble(10, "unknown", 5));
    }

    @Test
    void toDoubleMatchesGoBehavior() {
        assertEquals(42.5, ConditionEvaluator.toDouble(42.5));
        assertEquals(42.0, ConditionEvaluator.toDouble(42));
        assertEquals(42.0, ConditionEvaluator.toDouble(42L));
        assertEquals(0.0, ConditionEvaluator.toDouble("hello"));
        assertEquals(0.0, ConditionEvaluator.toDouble(null));
    }
}