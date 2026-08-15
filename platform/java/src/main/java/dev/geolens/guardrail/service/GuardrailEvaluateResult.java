package dev.geolens.guardrail.service;

import java.util.List;
import java.util.Map;

/** Guardrail değerlendirme sonucu — her kural için eşleşme ve aksiyon. */
public record GuardrailEvaluateResult(
        List<Map<String, Object>> results,
        boolean blocked) {

    public boolean allowed() {
        return !blocked;
    }
}