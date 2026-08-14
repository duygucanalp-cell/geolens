package dev.geolens.sentiment.domain;

/** Hallüsinasyon tespit sonucu — Go {@code HallucinationResult} portu (birebir JSON). */
public record HallucinationResult(
        String id,
        String brandId,
        String engineName,
        String hallucinationType, // T1-T5
        String severity,          // critical / high / medium / low
        String description,
        double confidence,
        String replayId,          // opsiyonel — JSON'da replay_id
        java.time.Instant createdAt) {

    public static HallucinationResult of(String brandId, String engineName, String type, String severity,
                                         String description, double confidence, java.time.Instant createdAt) {
        return new HallucinationResult(null, brandId, engineName, type, severity, description, confidence,
                null, createdAt);
    }
}