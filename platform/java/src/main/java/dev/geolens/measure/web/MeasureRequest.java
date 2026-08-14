package dev.geolens.measure.web;

/** Ölçüm tetikleme isteği — Go {@code measure.handler} TriggerMeasurement body'si. */
public record MeasureRequest(String brandId, String panelId) {
}
