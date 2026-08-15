package dev.geolens.competitive.web;

/**
 * Hata yanıtı — Go {@code httputil.WriteError} JSON şekli ({@code {"error": "..."}}).
 */
public record ApiError(String error) {
}
