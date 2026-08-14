package dev.geolens.measure;

import java.time.Instant;
import java.util.Map;

/** Tek bir skorun deterministik hesaplama kaydı — Go {@code measure.CalculationRun} portu. */
public record CalculationRun(
        String id,
        String panelId,
        Map<String, Double> scoreComponents,
        String algorithmVersion,
        String inputSnapshot,
        Instant createdAt) {
}