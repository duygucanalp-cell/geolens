package dev.geolens.audit.web;

/** Denetim tetikleme isteği — Go {@code audit.AuditRequest} portu. */
public record AuditRequest(String brandId, String brandName, String websiteUrl) {
}
