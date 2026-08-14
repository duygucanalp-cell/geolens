
package dev.geolens.config;

/** Normalize edilmiş 7 bileşenli Visibility Index v2 ağırlıkları — Go {@code config.ScoreWeightsV2} portu. */
public record ScoreWeightsV2(
        double presence,
        double position,
        double source,
        double competitor,
        double appearance,
        double sentiment,
        double compVis) {
}