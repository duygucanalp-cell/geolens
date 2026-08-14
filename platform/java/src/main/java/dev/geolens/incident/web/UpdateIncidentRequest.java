package dev.geolens.incident.web;

/** Incident güncelleme isteği — Go {@code UpdateIncident} istek gövdesi. */
public record UpdateIncidentRequest(
        String status,
        String resolution,
        String assignedTo) {
}
