package dev.geolens.technicalgeo;

/**
 * Genel Teknik GEO skoru — Go {@code TechnicalGEOScore} struct portu (FR-E7).
 */
public record TechnicalGeoScore(
        String brandId,
        double overall,
        double botScore,
        double schemaScore,
        double sourceShare,
        String grade) {
}
