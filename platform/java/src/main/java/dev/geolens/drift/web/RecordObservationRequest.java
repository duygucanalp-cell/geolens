package dev.geolens.drift.web;

/** Drift gözlem kayıt isteği — Go {@code Record} istek gövdesi. */
public record RecordObservationRequest(
        String entityId,
        String entityName,
        String metric,
        double value,
        String windowStart) {
}
