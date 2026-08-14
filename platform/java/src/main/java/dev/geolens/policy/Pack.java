package dev.geolens.policy;

/**
 * Policy pack — Go {@code Pack} struct portu (R4).
 */
public record Pack(
        String id,
        String tenantId,
        String name,
        String framework,
        String description,
        String version,
        boolean enabled,
        String appliedAt,
        String createdAt,
        String updatedAt) {
}
