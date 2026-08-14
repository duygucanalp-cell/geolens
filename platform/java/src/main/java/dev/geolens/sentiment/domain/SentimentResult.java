package dev.geolens.sentiment.domain;

import java.util.List;

/** Duygu analizi sonucu — Go {@code SentimentResult} portu (birebir JSON). */
public record SentimentResult(
        String id,
        String brandId,
        String engineName,
        double overallSentiment,
        double positiveScore,
        double neutralScore,
        double negativeScore,
        int mentionCount,
        List<MentionResult> mentions,
        java.time.Instant analyzedAt) {

    public static SentimentResult of(String brandId, String engineName, double overall,
                                     double positive, double neutral, double negative,
                                     int mentionCount, java.time.Instant analyzedAt) {
        return new SentimentResult(null, brandId, engineName, overall, positive, neutral, negative,
                mentionCount, null, analyzedAt);
    }
}