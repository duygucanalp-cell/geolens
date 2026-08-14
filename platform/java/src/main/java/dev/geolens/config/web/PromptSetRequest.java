package dev.geolens.config.web;

/** Prompt seti isteği — Go {@code config} CreatePromptSet gövdesi portu. */
public record PromptSetRequest(String name, String description, String promptText) {
}
