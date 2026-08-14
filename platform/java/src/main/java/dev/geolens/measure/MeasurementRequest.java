package dev.geolens.measure;

/** Tek bir ölçüm operasyonunun girdisi — Go {@code measure.MeasurementRequest} portu. */
public record MeasurementRequest(
        String brandId,
        String brandName,
        String websiteUrl,
        String promptText,
        String engineName,
        String tenantId,
        String workspaceId,
        String panelId,
        String jobId) {
}