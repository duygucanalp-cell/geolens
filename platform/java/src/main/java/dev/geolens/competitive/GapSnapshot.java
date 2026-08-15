package dev.geolens.competitive;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

/**
 * Marka-rakip arası tam gap analizi — Go {@code competitive.GapSnapshot} struct portu (FR-D11).
 * <p>Gap alanları null olabilir (Go'da {@code omitempty} — JSON'da atlanır).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GapSnapshot(
        String id,
        String brandId,
        String brandName,
        String competitorId,
        String competitorName,
        GapDetail visibilityGap,
        GapDetail citationGap,
        GapDetail contentGap,
        GapDetail topicGap,
        GapDetail promptGap,
        double competitiveScore,
        String periodStart,
        String periodEnd,
        OffsetDateTime createdAt) {
}
