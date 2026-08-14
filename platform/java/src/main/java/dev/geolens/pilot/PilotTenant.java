package dev.geolens.pilot;

/**
 * Pilot programı kiracısı — Go {@code PilotTenant} struct portu (K4).
 */
public record PilotTenant(
        String id,
        String tenantId,
        String programName,
        String trialEndsAt,
        int maxWorkspaces,
        int maxEngines,
        String supportLevel,
        String contactEmail,
        String notes,
        boolean autoConvert,
        String status,
        String createdAt) {
}
