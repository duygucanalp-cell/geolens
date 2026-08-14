package dev.geolens.optimize.web;

/**
 * Öneri durumu güncelleme isteği — Go {@code UpdateStatus} input portu.
 */
public record UpdateStatusRequest(
        String status) {
}
