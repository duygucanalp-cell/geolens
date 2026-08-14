package dev.geolens.recommendation.domain;

import java.time.Instant;

/**
 * Tek bir aksiyon alınabilir öneri (Go: {@code Recommendation}).
 * <p>Alan sırası Go JSON sırasıyla birebir.
 */
public record Recommendation(
        String id,
        String tenantId,
        String workspaceId,
        String brandId,
        Category category,
        Severity severity,
        EvidenceLabel evidence,
        String title,
        String detail,
        String actionUrl,
        double score,
        boolean applied,
        boolean dismissed,
        Instant createdAt) {
}