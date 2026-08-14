package dev.geolens.bias.web;

import java.util.Map;

/** Bias değerlendirme isteği — Go {@code Evaluate} istek gövdesi. */
public record EvaluateRequest(
        String modelId,
        String metricType,
        Map<String, Object> data) {
}
