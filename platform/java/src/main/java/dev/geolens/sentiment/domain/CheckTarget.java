package dev.geolens.sentiment.domain;

/** Hallüsinasyon kontrol hedefi (raw response + marka profili) — Go {@code checkTarget} portu. */
public record CheckTarget(String id, String engineName, String content, String prompt,
                          String brandName, String websiteUrl) {
}