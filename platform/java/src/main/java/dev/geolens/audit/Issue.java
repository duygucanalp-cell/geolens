package dev.geolens.audit;

/** Tek bir denetim bulgusu — Go {@code audit.Issue} portu. Severity: critical, high, medium, low, info. */
public record Issue(String severity, String category, String title, String detail, String recommendation) {
}