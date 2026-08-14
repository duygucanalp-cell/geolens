package dev.geolens.governance;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kiracı (tenant) bazlı kaynak kullanımı kaydedici — Go {@code governance.UsageRecorder} portu.
 * Veritabanı yoksa kayıt {@link GovernanceException} ile başarısız olur
 * (Go {@code "usage: veritabanı bağlantısı yok"} davranışı).
 */
public final class UsageRecorder {

    // Metrik adları (Go sabitleriyle birebir)
    public static final String METRIC_ENGINE_CALLS = "engine_calls";
    public static final String METRIC_API_REQUESTS = "api_requests";
    public static final String METRIC_STORAGE_BYTES = "storage_bytes";
    public static final String METRIC_SCORES_COMPUTED = "scores_computed";

    private final DSLContext dsl;
    private final TransactionTemplate tx;

    public UsageRecorder(DSLContext dsl, TransactionTemplate tx) {
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

    /** Bir kullanım metriğini kaydeder — Go {@code RecordUsage} portu. */
    public void recordUsage(String tenantId, String metricName, long value, String resourceType, String resourceId) {
        if (dsl == null) {
            throw new GovernanceException("usage: veritabanı bağlantısı yok");
        }

        String id = tenantId + "-" + metricName + "-" + System.currentTimeMillis();
        runInTenant(tenantId, () -> dsl.execute("""
                INSERT INTO governance.usage_records (id, tenant_id, metric_name, metric_value, resource_type, resource_id, recorded_at)
                VALUES (?, ?, ?, ?, ?, ?, now())
                """, id, tenantId, metricName, value, resourceType, resourceId));
    }

    /** Metriği 1 arttıran kısayol — Go {@code IncrementUsage} portu. */
    public void incrementUsage(String tenantId, String metricName, String resourceType, String resourceId) {
        recordUsage(tenantId, metricName, 1, resourceType, resourceId);
    }

    /** Belirli bir zaman aralığında kiracı toplam kullanımını döner — Go {@code GetUsageSummary} portu. */
    public Map<String, Long> getUsageSummary(String tenantId, Instant since) {
        if (dsl == null) {
            throw new GovernanceException("usage: veritabanı bağlantısı yok");
        }

        Timestamp sinceTs = Timestamp.from(since);
        if (tx != null) {
            return tx.execute(status -> {
                setTenant(dsl, tenantId);
                return querySummary(tenantId, sinceTs);
            });
        }
        setTenant(dsl, tenantId);
        return querySummary(tenantId, sinceTs);
    }

    private Map<String, Long> querySummary(String tenantId, Timestamp since) {
        Map<String, Long> summary = new LinkedHashMap<>();
        // jOOQ fetch boş sonuçta satır döndürmez; hata fırlatmaz (EmptyResultDataAccessException gerekmez)
        for (Record r : dsl.fetch("""
                SELECT metric_name, SUM(metric_value) AS total
                FROM governance.usage_records
                WHERE tenant_id = ? AND recorded_at >= ?
                GROUP BY metric_name
                """, tenantId, since)) {
            summary.put(r.get("metric_name", String.class), r.get("total", Long.class));
        }
        return summary;
    }
}