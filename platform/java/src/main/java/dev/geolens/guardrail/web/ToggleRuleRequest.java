package dev.geolens.guardrail.web;

/**
 * Kural aç/kapa isteği — Go {@code ToggleRule} input portu.
 */
public record ToggleRuleRequest(
        boolean enabled) {
}
