package dev.geolens.search;

import java.time.Instant;
import java.util.Map;

/**
 * Audit kaydı indeksleme girişi — Go {@code search.AuditEntry} struct portu.
 */
public record AuditEntry(
        String id,
        String tenantId,
        String userId,
        String eventType,
        String resourceType,
        String resourceId,
        String action,
        Map<String, Object> metadata,
        String ipAddress,
        Instant createdAt) {
}
