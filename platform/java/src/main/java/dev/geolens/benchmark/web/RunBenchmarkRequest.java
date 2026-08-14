package dev.geolens.benchmark.web;

/** Model benchmark çalıştırma isteği — Go {@code RunBenchmark} istek gövdesi. */
public record RunBenchmarkRequest(
        String modelName,
        String engineName,
        String category,
        double accuracyScore,
        int latencyMs,
        double costPerRequest,
        double tokensPerSecond,
        double responseQuality,
        double citationRate) {
}
