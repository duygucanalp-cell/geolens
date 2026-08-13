package dev.geolens.recommendation.web;

/** Impact yanıtında skor-at-anı (Go {@code scoreAtTime} ile aynı JSON şekli). */
public record ScoreAtResponse(double value, String fidelity, String measuredAt) {
}