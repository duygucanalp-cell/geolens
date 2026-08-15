package dev.geolens.agent.web;

/**
 * POST /traces/{traceId}/complete istek gövdesi — Go {@code agent.handler} {@code CompleteTrace} input struct portu.
 * <p>status: completed / failed / cancelled (geçersizse completed'a düşer — Go birebir).
 */
public record CompleteTraceRequest(String status) {
}
