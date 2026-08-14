package dev.geolens.technicalgeo;

/**
 * Schema.org kullanım analiz sonucu — Go {@code SchemaAnalysisResult} struct portu (FR-B7).
 */
public record SchemaAnalysisResult(
        String id,
        String brandId,
        String schemaType,
        boolean isPresent,
        double schemaScore,
        String recommendation,
        String analyzedAt) {
}
