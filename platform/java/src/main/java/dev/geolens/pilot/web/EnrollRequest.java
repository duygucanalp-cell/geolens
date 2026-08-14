package dev.geolens.pilot.web;

/**
 * Pilot programına kayıt isteği — Go {@code Enroll} input portu.
 */
public record EnrollRequest(
        String programName,
        String contactEmail,
        String notes,
        String supportLevel) {
}
