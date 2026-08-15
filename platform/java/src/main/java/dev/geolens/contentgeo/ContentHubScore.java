package dev.geolens.contentgeo;

/**
 * Marka için content hub skoru — Go {@code contentgeo.ContentHubScore} struct portu (FR-E6).
 */
public record ContentHubScore(
        String brandId,
        double overall,
        double topicCoverage,
        double sourceDiversity,
        double authorityScore,
        double opportunityGap,
        String grade) {
}
