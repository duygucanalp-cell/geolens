package dev.geolens.compliance;

/**
 * Uyumluluk kontrolü — Go {@code Control} struct portu.
 */
public record Control(
        String id,
        String category,
        String title,
        String description,
        String status,
        String evidence) {
}
