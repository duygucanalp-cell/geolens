package dev.geolens.drift;

/** Drift uyarısı (alert) — Go {@code drift.Alert} struct portu. */
public record Alert(
        String id,
        String tenantId,
        String entityId,
        String entityName,
        String metric,
        double driftScore,
        String severity,
        double referenceMean,
        double currentMean,
        double delta,
        String detail,
        String createdAt) {
}
