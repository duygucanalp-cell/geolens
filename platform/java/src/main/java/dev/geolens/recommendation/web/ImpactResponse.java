package dev.geolens.recommendation.web;

/** GetImpact yanıtı — Go handler'ındaki map anahtarlarıyla birebir. */
public record ImpactResponse(
        String recommendationId,
        String brandId,
        String appliedAt,
        ScoreAtResponse before,
        ScoreAtResponse after,
        double change) {
}