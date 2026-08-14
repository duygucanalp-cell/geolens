package dev.geolens.governance;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Go {@code governance/audit_test.go} portu. */
class AuditLoggerTest {

    @Test
    void auditEntryDefaults() {
        AuditEntry e = AuditEntry.empty();
        assertEquals("", e.id());
        assertNull(e.createdAt());
    }

    @Test
    void auditEntryWithValues() {
        Instant now = Instant.now();
        AuditEntry e = new AuditEntry("test-id", "tenant-1", "user-1", "test.event", "brand",
                "brand-1", "create", Map.of(), "127.0.0.1", "test-agent", now);
        assertEquals("tenant-1", e.tenantId());
        assertEquals("test.event", e.eventType());
        assertEquals("create", e.action());
        assertEquals("test-id", e.id());
    }

    @Test
    void newAuditLogger() {
        AuditLogger logger = new AuditLogger(null, null);
        assertThrows(GovernanceException.class,
                () -> logger.recordEvent("tenant-1", "test.event", "brand", "brand-1", "delete"));
    }

    @Test
    void recordNoPool() {
        AuditLogger logger = new AuditLogger(null, null);
        // Go: nil pool hata döner — Java: GovernanceException (panic olmadan).
        assertThrows(GovernanceException.class,
                () -> logger.record(new AuditEntry("", "tenant-1", "", "test.event", "brand",
                        "brand-1", "read", Map.of(), "", "", null)));
    }
}