package dev.geolens.archive.web;

/**
 * Yanıt arşivleme isteği — Go {@code ArchiveResponse} input portu.
 */
public record ArchiveRequest(
        String brandId,
        String engineName,
        String promptText,
        String response) {
}
