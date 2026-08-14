package dev.geolens.redteam.web;

/** Red team senaryo oluşturma isteği — Go {@code CreateCase} istek gövdesi. */
public record CreateCaseRequest(
        String name,
        String category,
        String payload,
        String attackVector,
        String severity) {
}
