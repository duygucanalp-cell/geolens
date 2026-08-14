package dev.geolens.registry;

/**
 * Registry varlığı — Go {@code Entity} struct portu (R1).
 */
public record Entity(
        String id,
        String tenantId,
        String entityType,
        String name,
        String description,
        String version,
        String provider,
        String lifecycleState,
        String riskClass,
        String owner,
        String documentationUrl,
        String deployedAt,
        String createdAt,
        String updatedAt) {
}
