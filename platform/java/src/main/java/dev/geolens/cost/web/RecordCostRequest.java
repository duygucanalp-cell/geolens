package dev.geolens.cost.web;

/** Maliyet kayıt isteği — Go {@code RecordCost} istek gövdesi. */
public record RecordCostRequest(
        String engineName,
        String modelName,
        String operation,
        int tokenCount,
        int inputTokens,
        int outputTokens,
        double costUsd) {
}
