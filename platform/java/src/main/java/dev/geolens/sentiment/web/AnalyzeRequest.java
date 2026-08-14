package dev.geolens.sentiment.web;

/** POST /sentiment/analyze istek gövdesi — Go handler req (brand_id, prompt?). */
public record AnalyzeRequest(String brandId, String prompt) {
}