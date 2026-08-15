package dev.geolens.agent.web;

/**
 * POST /traces istek gövdesi — Go {@code agent.handler} {@code StartTrace} input struct portu.
 */
public record StartTraceRequest(String agentName, String workflowName) {
}
