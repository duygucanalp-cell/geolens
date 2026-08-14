package dev.geolens.config.web;

/** Marka güncelleme isteği — Go {@code config} UpdateBrand gövdesi portu. */
public record UpdateBrandRequest(String name, String websiteUrl) {
}
