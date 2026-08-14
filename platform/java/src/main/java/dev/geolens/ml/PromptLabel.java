package dev.geolens.ml;

/** Prompt sınıflandırıcıda tek hedefin (intent/topic/persona/funnel) tahmini — Go {@code ml.PromptLabel} portu. */
public record PromptLabel(String label, double confidence) {
}