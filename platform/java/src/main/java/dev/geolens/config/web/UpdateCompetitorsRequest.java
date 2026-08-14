package dev.geolens.config.web;

import java.util.List;

/** Rakip güncelleme isteği — Go {@code config} UpdateBrandCompetitors gövdesi portu. */
public record UpdateCompetitorsRequest(List<String> competitors) {
}
