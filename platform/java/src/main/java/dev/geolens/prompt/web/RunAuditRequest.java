package dev.geolens.prompt.web;

/**
 * Prompt denetim isteği — Go {@code RunAudit} input portu.
 */
public record RunAuditRequest(
        String promptId,
        String promptText,
        String engineName) {
}
