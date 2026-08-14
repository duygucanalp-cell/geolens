package dev.geolens.measure;

import java.time.Instant;
import java.util.Map;

/** Bir paneldeki marka için hesaplanmış görünürlük skoru — Go {@code measure.Score} portu. */
public record Score(
        String id,
        String panelId,
        String brandId,
        String workspaceId,
        String tenantId,
        double value,
        double ciLow,
        double ciHigh,
        String fidelityLabel,
        Map<String, Double> engineBreakdown,
        String panelVersion,
        String calculationRunId,
        Instant freshnessAt,
        Instant createdAt) {

    public Score {
        if (engineBreakdown == null) {
            engineBreakdown = Map.of();
        }
    }
}