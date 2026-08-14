package dev.geolens.guardrail;

/**
 * API yanıtında dönen kural kaydı — Go {@code Rule} struct portu.
 */
public record Rule(
        String id,
        String tenantId,
        String name,
        String category,
        String pattern,
        String action,
        String severity,
        boolean enabled,
        String createdAt,
        String updatedAt) {
}
