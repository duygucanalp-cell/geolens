package dev.geolens.prompt.web;

/**
 * Hata yanıtı — Go {@code {"error": msg}} deseni portu.
 */
public record ApiError(String error) {
}
