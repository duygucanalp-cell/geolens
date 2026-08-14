package dev.geolens.config.web;

/** Çalışma alanı devir isteği — Go {@code config} TransferWorkspace gövdesi portu. */
public record TransferRequest(String targetTenantId) {
}
