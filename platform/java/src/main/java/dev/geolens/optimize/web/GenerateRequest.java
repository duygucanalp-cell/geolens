package dev.geolens.optimize.web;

/**
 * Öneri üretme isteği — Go {@code GenerateRecommendations} input portu.
 */
public record GenerateRequest(
        String brandId,
        boolean autoSave) {
}
