package dev.geolens.recommendation.ng10;

import dev.geolens.recommendation.domain.ClaimLang;

import java.util.List;

/** Tek bir NG10 sınıflandırma kuralı (Go: {@code NG10Rule}). */
public record Ng10Rule(
        String id,
        ClaimLang category,
        List<String> keywords,
        List<String> patterns,
        String description) {
}