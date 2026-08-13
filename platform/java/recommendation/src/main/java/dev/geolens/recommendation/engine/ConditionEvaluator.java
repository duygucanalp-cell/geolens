package dev.geolens.recommendation.engine;

import dev.geolens.recommendation.domain.AuditSnapshot;
import dev.geolens.recommendation.domain.Condition;
import dev.geolens.recommendation.domain.EvaluationContext;
import dev.geolens.recommendation.domain.ScoreSnapshot;

import java.util.List;

/**
 * Koşul değerlendirici — Go {@code evaluateCondition} portu (birebir).
 * <p>Go'daki parite noktaları: {@code ctx.Score == nil} ise tüm koşullar false döner
 * (audit koşulları dahil). Bilinmeyen alanlar false döner.
 */
public final class ConditionEvaluator {

    private ConditionEvaluator() {
    }

    /** Tüm koşullar karşılanıyorsa true (boş liste → true). */
    public static boolean evaluateAll(EvaluationContext ctx, List<Condition> conditions) {
        if (conditions.isEmpty()) {
            return true;
        }
        for (Condition c : conditions) {
            if (!evaluate(ctx, c)) {
                return false;
            }
        }
        return true;
    }

    public static boolean evaluate(EvaluationContext ctx, Condition c) {
        if (ctx == null || ctx.score() == null) {
            return false;
        }
        ScoreSnapshot score = ctx.score();

        switch (c.field()) {
            case "score.drop" -> {
                if (score.previousValue() == 0) {
                    return false;
                }
                double drop = score.previousValue() - score.value();
                return compareDouble(drop, c.operator(), toDouble(c.value()));
            }
            case "score.trend" -> {
                if (score.previousValue() == 0) {
                    return false;
                }
                double diff = score.value() - score.previousValue();
                String trend = "stable";
                if (diff < -5) {
                    trend = "declining";
                } else if (diff > 5) {
                    trend = "rising";
                }
                return c.value() instanceof String expected && trend.equals(expected);
            }
            case "score.engine_gap" -> {
                if (score.engineBreakdown() == null || score.engineBreakdown().isEmpty()) {
                    return false;
                }
                double min = Double.MAX_VALUE;
                double max = -Double.MAX_VALUE;
                for (double v : score.engineBreakdown().values()) {
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                }
                return compareDouble(max - min, c.operator(), toDouble(c.value()));
            }
            case "audit.robots_txt.disallowed_all" -> {
                AuditSnapshot a = ctx.audit();
                if (a == null || !a.hasData()) {
                    return false;
                }
                return a.robotsDisallowedAll() == toBool(c.value());
            }
            case "audit.ssr.has_structured_data" -> {
                AuditSnapshot a = ctx.audit();
                if (a == null || !a.hasData()) {
                    return false;
                }
                return a.hasStructuredData() == toBool(c.value());
            }
            case "audit.bot_access.accessible" -> {
                AuditSnapshot a = ctx.audit();
                if (a == null || !a.hasData()) {
                    return false;
                }
                return a.botAccessible() == toBool(c.value());
            }
            default -> {
                // Bilinmeyen alan — Go'da uyarı loglanır ve false döner.
                return false;
            }
        }
    }

    public static boolean compareDouble(double actual, String operator, double expected) {
        switch (operator) {
            case "gt":
                return actual > expected;
            case "lt":
                return actual < expected;
            case "eq":
                return actual == expected;
            case "gte":
                return actual >= expected;
            case "lte":
                return actual <= expected;
            default:
                return false;
        }
    }

    public static double toDouble(Object v) {
        if (v instanceof Double d) {
            return d;
        }
        if (v instanceof Integer i) {
            return i.doubleValue();
        }
        if (v instanceof Long l) {
            return l.doubleValue();
        }
        return 0;
    }

    public static boolean toBool(Object v) {
        return v instanceof Boolean b && b;
    }
}