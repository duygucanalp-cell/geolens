package dev.geolens.recommendation.engine;

import dev.geolens.recommendation.domain.EvaluationContext;
import dev.geolens.recommendation.domain.Rule;
import dev.geolens.recommendation.domain.Severity;

import java.time.Duration;
import java.time.Instant;

/** Güven skoru (0-100) — Go {@code computeConfidence} portu (birebir). */
public final class ConfidenceCalculator {

    private ConfidenceCalculator() {
    }

    public static double compute(EvaluationContext ctx, Rule rule) {
        double score = 75.0;

        if (ctx.score() != null && ctx.score().freshnessAt() != null) {
            long ageMillis = Duration.between(ctx.score().freshnessAt(), Instant.now()).toMillis();
            if (ageMillis > 7L * 24 * 3600 * 1000) {
                score -= 15;
            } else if (ageMillis < 24L * 3600 * 1000) {
                score += 10;
            }
        }

        if (rule.severity() == Severity.CRITICAL) {
            score += 10;
        } else if (rule.severity() == Severity.LOW) {
            score -= 10;
        }

        if (score > 100) {
            score = 100;
        }
        if (score < 0) {
            score = 0;
        }
        return score;
    }
}