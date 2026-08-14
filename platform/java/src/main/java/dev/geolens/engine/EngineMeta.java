package dev.geolens.engine;

/** Motor çağrısı meta bilgisi — Go {@code engine.EngineMeta} portu. */
public record EngineMeta(String engineName, String modelVersion, Tier tier, long durationMs) {
}