package dev.geolens.governance;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tek bir audit kaydı — Go {@code governance.AuditEntry} portu (json etiketleri birebir).
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
        String userAgent,
        Instant createdAt) {

    public AuditEntry {
        if (metadata == null) {
            metadata = new LinkedHashMap<>();
        }
    }

    /** Go {@code AuditEntry{}} boş değer karşılığı. */
    public static AuditEntry empty() {
        return new AuditEntry("", "", "", "", "", "", "", new LinkedHashMap<>(), "", "", null);
    }
}