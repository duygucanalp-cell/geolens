package dev.geolens.recommendation.web;

/** Hata gövdesi — Go handler'larındaki {@code {"error": ...}} karşılığı. */
public record ApiError(String error) {
}