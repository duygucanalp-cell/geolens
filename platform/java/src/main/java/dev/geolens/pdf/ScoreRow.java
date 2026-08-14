package dev.geolens.pdf;

/** PDF tabloları için tek skor satırı — Go {@code pdf.ScoreRow} portu. */
public record ScoreRow(
        String brandName,
        double score,
        double previousScore,
        double change,
        String fidelityLabel) {
}
