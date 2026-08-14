package dev.geolens.audit;

/** SSRF koruma başlıkları — Go {@code audit.SSRFCheck} portu. */
public record SSRFCheck(
        boolean hasCloudflare,
        boolean hasAWSSecurityHeaders,
        boolean hasRateLimitHeaders,
        boolean cspPresent) {
}