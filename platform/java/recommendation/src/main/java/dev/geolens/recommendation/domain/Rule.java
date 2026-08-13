package dev.geolens.recommendation.domain;

import java.util.List;

/**
 * Öneri kuralı (koşul → aksiyon) (Go: {@code Rule}).
 * <p>Alan sırası Go JSON sırasıyla birebir.
 */
public record Rule(
        String id,
        String name,
        String description,
        Category category,
        Severity severity,
        EvidenceLabel evidence,
        List<Condition> conditions,
        String title,
        String detail,
        String actionUrl,
        boolean active,
        ClaimLang claimLang) {
}