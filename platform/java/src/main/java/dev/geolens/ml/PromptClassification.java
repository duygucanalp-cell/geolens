package dev.geolens.ml;

/** Serving /v1/prompt/classify çıktısı — Go {@code ml.PromptClassification} portu (0421 A2-3). */
public record PromptClassification(PromptLabel intent, PromptLabel topic, PromptLabel persona, PromptLabel funnel) {
}