package dev.geolens.registry.web;

/**
 * Varlık güncelleme isteği — Go {@code Update} input portu.
 */
public record UpdateEntityRequest(
        String name,
        String description,
        String version,
        String provider,
        String lifecycleState,
        String riskClass,
        String owner,
        String documentationUrl) {
}
