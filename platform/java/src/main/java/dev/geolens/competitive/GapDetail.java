package dev.geolens.competitive;

/**
 * Tek bir gap türü ölçümü — Go {@code competitive.GapDetail} struct portu (FR-D11).
 * <p>direction: brand_ahead / competitor_ahead / equal.
 */
public record GapDetail(
        double gapValue,
        double normalized,
        double brandValue,
        double competitorValue,
        String direction) {
}
