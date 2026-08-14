package dev.geolens.privacy.web;

/** Silme talebi işleme isteği — Go {@code privacy.processRequest} portu. */
public record ProcessRequest(String action, String notes) {
}
