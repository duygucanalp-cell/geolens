package dev.geolens.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.util.Ulid;
import org.jooq.DSLContext;
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

    private final DSLContext dsl;
    private final TransactionTemplate tx;

    public AuditLogger(DSLContext dsl, TransactionTemplate tx) {
        this.dsl = dsl;
        this.tx = tx;
    }

    private static void setTenant(DSLContext dsl, String tenantId) {
        dsl.fetch("SELECT set_config('app.tenant_id', ?, true)", tenantId);
    }

    private void runInTenant(String tenantId, Runnable work) {
        if (tx == null) {
            setTenant(dsl, tenantId);
            work.run();
            return;
        }
        tx.executeWithoutResult(status -> {
            setTenant(dsl, tenantId);
            work.run();
        });
    }

    /** Audit kaydını veritabanına yazar — Go {@code Record} portu. */
    public void record(AuditEntry entry) {
        if (dsl == null) {
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
        runInTenant(entry.tenantId(), () -> dsl.execute("""
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