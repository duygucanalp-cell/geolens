package dev.geolens.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.util.Ulid;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Audit kayıt servisi — Go {@code governance.AuditLogger} portu (0421 G).
 * Veritabanı yoksa (null pool) kayıt {@link GovernanceException} ile başarısız olur
 * — Go {@code errors.Internal("audit: veritabanı bağlantısı yok")} davranışı.
 */
public final class AuditLogger {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public AuditLogger(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    private static void setTenant(JdbcTemplate jdbc, String tenantId) {
        jdbc.execute("SELECT set_config('app.tenant_id', ?, true)",
                (PreparedStatementCallback<Void>) ps -> {
                    ps.setString(1, tenantId);
                    ps.execute();
                    return null;
                });
    }

    private void runInTenant(String tenantId, Runnable work) {
        if (tx == null) {
            setTenant(jdbc, tenantId);
            work.run();
            return;
        }
        tx.executeWithoutResult(status -> {
            setTenant(jdbc, tenantId);
            work.run();
        });
    }

    /** Audit kaydını veritabanına yazar — Go {@code Record} portu. */
    public void record(AuditEntry entry) {
        if (jdbc == null) {
            throw new GovernanceException("audit: veritabanı bağlantısı yok");
        }

        String entryId = Ulid.generate();

        String metaJson;
        try {
            metaJson = MAPPER.writeValueAsString(entry.metadata() != null ? entry.metadata() : Map.of());
        } catch (Exception e) {
            metaJson = "{}";
        }

        String meta = metaJson;
        runInTenant(entry.tenantId(), () -> jdbc.update("""
                INSERT INTO governance.audit_log (id, tenant_id, user_id, event_type, resource_type, resource_id,
                    action, metadata, ip_address, user_agent, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, now())
                """,
                entryId, entry.tenantId(), entry.userId(), entry.eventType(), entry.resourceType(),
                entry.resourceId(), entry.action(), meta,
                entry.ipAddress(), entry.userAgent()));
    }

    /** Yaygın audit olayları için kısayol — Go {@code RecordEvent} portu. */
    public void recordEvent(String tenantId, String eventType, String resourceType, String resourceId, String action) {
        record(new AuditEntry("", tenantId, "", eventType, resourceType, resourceId, action,
                new LinkedHashMap<>(), "", "", null));
    }
}