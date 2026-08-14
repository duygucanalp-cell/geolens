package dev.geolens.incident.web;

/** Incident oluşturma isteği — Go {@code CreateIncident} istek gövdesi. */
public record CreateIncidentRequest(
        String severity,
        String category,
        String title,
        String description,
        String source,
        String entityId,
        String assignedTo,
        double severityScore) {
}
