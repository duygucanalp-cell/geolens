package dev.geolens.registry.web;

/**
 * Risk değerlendirme isteği — Go {@code AssessRisk} input portu.
 */
public record AssessRiskRequest(
        String riskClass,
        Double score,
        String summary) {
}
