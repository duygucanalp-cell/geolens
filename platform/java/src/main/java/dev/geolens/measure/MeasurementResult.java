package dev.geolens.measure;

import dev.geolens.engine.Citation;
import dev.geolens.engine.EngineMeta;
import dev.geolens.engine.RawResponse;

import java.util.List;

/** Tek bir ölçüm operasyonunun çıktısı — Go {@code measure.MeasurementResult} portu. */
public record MeasurementResult(
        List<RawResponse> rawResponses,
        List<Citation> citations,
        EngineMeta engineMeta,
        String brandId,
        String brandName,
        String panelId,
        String workspaceId,
        String tenantId,
        String promptText) {

    public MeasurementResult {
        if (rawResponses == null) {
            rawResponses = List.of();
        }
        if (citations == null) {
            citations = List.of();
        }
    }
}