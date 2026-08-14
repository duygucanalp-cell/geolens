package dev.geolens.gate.web;

/** Gate check isteği — Go {@code Check} istek gövdesi. */
public record CheckRequest(
        String entityId,
        String entityType,
        String targetEnvironment,
        String version) {
}
