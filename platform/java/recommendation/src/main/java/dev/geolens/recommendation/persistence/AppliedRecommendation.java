package dev.geolens.recommendation.persistence;

import java.time.Instant;

/** {@code recommendation.results} uygulanmış kaydı — Go {@code GetImpact} sorgusu. */
public record AppliedRecommendation(String brandId, Instant appliedAt) {

    public static AppliedRecommendation of(String brandId, Instant appliedAt) {
        return new AppliedRecommendation(brandId, appliedAt);
    }
}