package dev.geolens.redteam;

/** Red team test senaryosu — Go {@code redteam.TestCase} struct portu. */
public record TestCase(
        String id,
        String tenantId,
        String name,
        String category,
        String payload,
        String attackVector,
        String severity,
        boolean enabled,
        String createdAt,
        String updatedAt) {
}
