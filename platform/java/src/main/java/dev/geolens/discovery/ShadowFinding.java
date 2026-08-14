package dev.geolens.discovery;

/** Shadow AI taraması finding kaydı — Go {@code discovery.finding} struct portu. */
public record ShadowFinding(
        String resourceType,
        String resourceName,
        String resourceId,
        String provider,
        String region,
        String riskLevel,
        String details) {
}
