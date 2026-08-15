package dev.geolens.contentgeo;

import java.time.OffsetDateTime;

/**
 * Content gap analiz sonucu — Go {@code contentgeo.ContentGapResult} struct portu (FR-E5).
 */
public record ContentGapResult(
        String id,
        String brandId,
        String gapType,
        double gapScore,
        String description,
        String recommendation,
        String priority,
        OffsetDateTime analyzedAt) {
}
