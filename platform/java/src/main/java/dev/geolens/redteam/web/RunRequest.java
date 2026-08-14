package dev.geolens.redteam.web;

/** Red team test çalıştırma isteği — Go {@code Run} istek gövdesi. */
public record RunRequest(
        String targetName,
        String targetPrompt) {
}
