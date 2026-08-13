package dev.geolens.recommendation.engine;

import dev.geolens.recommendation.domain.EvaluationContext;
import dev.geolens.recommendation.domain.Recommendation;
import dev.geolens.recommendation.domain.Rule;
import dev.geolens.recommendation.util.Ulid;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Kural motoru — Go {@code evaluateBrand} portu (birebir). */
public final class RecommendationEngine {

    private RecommendationEngine() {
    }

    /** Aktif kuralları context'e karşı çalıştırır ve eşleşen önerileri üretir. */
    public static List<Recommendation> evaluateBrand(EvaluationContext ctx, List<Rule> rules) {
        List<Recommendation> results = new ArrayList<>();
        if (rules == null) {
            return results;
        }
        for (Rule rule : rules) {
            if (!rule.active()) {
                continue;
            }
            if (ConditionEvaluator.evaluateAll(ctx, rule.conditions())) {
                results.add(new Recommendation(
                        Ulid.generate(),
                        ctx.tenantId(),
                        ctx.workspaceId(),
                        ctx.brandId(),
                        rule.category(),
                        rule.severity(),
                        rule.evidence(),
                        rule.title(),
                        rule.detail(),
                        rule.actionUrl(),
                        ConfidenceCalculator.compute(ctx, rule),
                        false,
                        false,
                        Instant.now()));
            }
        }
        return results;
    }
}