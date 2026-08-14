package dev.geolens.redteam.web;

/** REST hata yanıtı — Go {@code httputil.WriteError} karşılığı. */
public record ApiError(String error) {
}
