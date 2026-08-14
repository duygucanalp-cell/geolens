package dev.geolens.sentiment.web;

/** Hata yanıtı — Go httputil.WriteJSON hata biçimi ({"error": "..."}). */
public record ApiError(String error) {
}