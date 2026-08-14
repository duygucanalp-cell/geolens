package dev.geolens.policy.web;

/**
 * Control durumu güncelleme isteği — Go {@code UpdateControl} input portu.
 */
public record UpdateControlRequest(
        String status,
        String evidence) {
}
