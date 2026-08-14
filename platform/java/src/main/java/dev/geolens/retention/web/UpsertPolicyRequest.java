package dev.geolens.retention.web;

/**
 * Saklama politikası upsert isteği — Go {@code UpsertPolicy} input portu.
 */
public record UpsertPolicyRequest(
        String entityType,
        int retentionDays,
        String archivalStrategy,
        boolean enabled) {
}
