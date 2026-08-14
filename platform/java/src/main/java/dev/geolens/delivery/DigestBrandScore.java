package dev.geolens.delivery;

/** Haftalık özette tek bir marka için skor verisi — Go {@code digestBrandScore} portu. */
record DigestBrandScore(String brandId, String brandName, double score, double previousScore, double change) {
}