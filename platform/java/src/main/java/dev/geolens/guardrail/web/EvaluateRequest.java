package dev.geolens.guardrail.web;

/**
 * Değerlendirme isteği — Go {@code Evaluate} input portu.
 */
public record EvaluateRequest(
        String prompt,
        String response) {
}
