package dev.geolens.redteam;

/** Red team test çalıştırması — Go {@code redteam.Run} struct portu. */
public record Run(
        String id,
        String targetName,
        int totalCases,
        int passed,
        int failed,
        double defenseScore,
        String status,
        String createdAt) {
}
