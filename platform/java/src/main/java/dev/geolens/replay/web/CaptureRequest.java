package dev.geolens.replay.web;

/**
 * Snapshot alma isteği — Go {@code CaptureSnapshot} input portu.
 */
public record CaptureRequest(
        String brandId,
        String prompt) {
}
