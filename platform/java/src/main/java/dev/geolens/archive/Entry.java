package dev.geolens.archive;

/**
 * Arşivlenmiş yanıt girişi — Go {@code Entry} struct portu (FR-D13).
 */
public record Entry(
        String id,
        String brandId,
        String engineName,
        String promptText,
        String responsePreview,
        String responseFull,
        String s3Ref,
        int version,
        String contentHash,
        String tenantId) {
}
