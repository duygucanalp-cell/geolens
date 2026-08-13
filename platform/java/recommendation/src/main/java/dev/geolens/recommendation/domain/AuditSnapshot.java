package dev.geolens.recommendation.domain;

/** Marka için en güncel audit sonuçları (Go: {@code AuditSnapshot}). */
public record AuditSnapshot(
        boolean hasData,
        double overallScore,
        boolean robotsDisallowedAll,
        boolean hasStructuredData,
        boolean botAccessible) {

    public static AuditSnapshot empty() {
        return new AuditSnapshot(false, 0, false, false, false);
    }
}