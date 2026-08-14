package dev.geolens.discovery.web;

/** Shadow AI taraması başlatma isteği — Go {@code StartScan} istek gövdesi. */
public record StartScanRequest(
        String scanType,
        String provider) {
}
