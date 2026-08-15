package dev.geolens.agent.web;

/**
 * POST /traces/{traceId}/steps istek gövdesi — Go {@code agent.handler} {@code RecordStep} input struct portu.
 * <p>status: running / completed / failed (geçersizse running'e düşer — Go birebir).
 */
public record RecordStepRequest(String stepName, String agentName, String input, String output,
                                String status, Integer durationMs, String errorMessage) {
}
