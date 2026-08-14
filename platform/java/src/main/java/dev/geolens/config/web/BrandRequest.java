package dev.geolens.config.web;

import java.util.List;

/** Marka oluşturma isteği — Go {@code config.brandRequest} portu. */
public record BrandRequest(String name, String websiteUrl, List<String> competitors) {
}
