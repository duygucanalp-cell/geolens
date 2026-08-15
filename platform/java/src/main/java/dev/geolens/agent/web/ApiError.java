package dev.geolens.agent.web;

/**
 * Hata yanıtı — Go {@code httputil.WriteJSON} hata şekli ({@code {"error": "..."}}).
 */
public record ApiError(String error) {
}
