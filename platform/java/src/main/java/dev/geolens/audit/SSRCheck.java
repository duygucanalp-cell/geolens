package dev.geolens.audit;

/** Sunucu tarafı render (SSR) sinyalleri — Go {@code audit.SSRCheck} portu. */
public record SSRCheck(
        boolean hasMetaTags,
        boolean hasOGTags,
        boolean hasStructuredData,
        int contentLength) {
}