package dev.geolens.usage.web;

/** Kullanım metriği kayıt isteği — Go {@code usage.RecordUsage} input portu. */
public record UsageMetricRequest(String endpoint, String method, int statusCode,
                                 int latencyMs, String userId, int requestSize, int responseSize) {
}
