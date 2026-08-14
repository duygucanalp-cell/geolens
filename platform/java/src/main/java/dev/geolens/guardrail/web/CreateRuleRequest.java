package dev.geolens.guardrail.web;

/**
 * Kural oluşturma isteği — Go {@code CreateRule} input portu.
 */
public record CreateRuleRequest(
        String name,
        String category,
        String pattern,
        String action,
        String severity) {
}
