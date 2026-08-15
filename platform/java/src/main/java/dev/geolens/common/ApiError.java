package dev.geolens.common;

/**
 * REST hata yanıtı — Go {@code httputil.WriteError} karşılığı ({@code {"error": "..."}}).
 * Tekrarlanan per-paket {@code ApiError} record'ları bu tipte birleştirildi.
 */
public record ApiError(String error) {
}
