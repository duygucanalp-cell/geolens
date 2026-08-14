package dev.geolens.sentiment.web;

/** POST /hallucination/detect istek gövdesi — Go handler req (brand_id). */
public record DetectRequest(String brandId) {
}