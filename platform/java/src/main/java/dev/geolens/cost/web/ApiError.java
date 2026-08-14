package dev.geolens.cost.web;

/** REST hata yanıtı — Go {@code httputil.WriteError} karşılığı. */
public record ApiError(String error) {
}
