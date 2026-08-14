package dev.geolens.retention;

/**
 * Saklama politikası — Go {@code Policy} struct portu (K3).
 */
public record Policy(
        String id,
        String tenantId,
        String entityType,
        int retentionDays,
        String archivalStrategy,
        boolean enabled,
        String createdAt,
        String updatedAt) {
}
