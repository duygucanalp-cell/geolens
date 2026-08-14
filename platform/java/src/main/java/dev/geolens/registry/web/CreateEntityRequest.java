package dev.geolens.registry.web;

/**
 * Varlık oluşturma isteği — Go {@code Create} input portu.
 */
public record CreateEntityRequest(
        String entityType,
        String name,
        String description,
        String version,
        String provider,
        String lifecycleState,
        String riskClass,
        String owner,
        String documentationUrl) {
}
