package dev.geolens.contentgeo.web;

/**
 * Hata yanıtı — Go {@code httputil.WriteError} JSON şekli ({@code {"error": "..."}}).
 */
public record ApiError(String error) {
}
