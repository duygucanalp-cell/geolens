package dev.geolens.drift;

/** Drift gözlemi (observation) — Go {@code drift.Observation} struct portu. */
public record Observation(
        String id,
        String tenantId,
        String entityId,
        String entityName,
        String metric,
        double value,
        String windowStart,
        String createdAt) {
}
