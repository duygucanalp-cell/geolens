package dev.geolens.recommendation.domain;

import java.time.Instant;
import java.util.Map;

/** Skor kaydı (Go: {@code ScoreSnapshot}). */
public record ScoreSnapshot(
        double value,
        double previousValue,
        Instant freshnessAt,
        Instant previousAt,
        Map<String, Double> engineBreakdown) {

    public static ScoreSnapshot empty() {
        return new ScoreSnapshot(0, 0, null, null, null);
    }

    public boolean hasData() {
        return freshnessAt != null;
    }

    public boolean hasPrevious() {
        return previousValue != 0 && previousAt != null;
    }
}