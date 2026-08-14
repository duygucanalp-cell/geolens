package dev.geolens.retention;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Veri saklama işçisi — Go {@code retention/worker.go} portu (K3).
 * <p>Etkin politikaları tarar; saklama süresi dolan verileri stratejiye göre
 * siler, anonimleştirir veya {@code retention.archives}'a taşır (S3 arşiv kaydı).
 * Varsayılan olarak devre dışıdır (Go'da ayrı goroutine; {@code retention.worker.enabled=false}).
 */
public class RetentionWorker {

    private static final Logger log = LoggerFactory.getLogger(RetentionWorker.class);

    private final DSLContext dsl;

    public RetentionWorker(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Go {@code processExpired} karşılığı — etkin politikaları uygular. */
    public void processExpired() {
        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch("""
                    SELECT p.id, p.tenant_id, p.entity_type, p.retention_days, p.archival_strategy
                    FROM retention.policies p
                    WHERE p.enabled = true
                    """).intoMaps();
        } catch (RuntimeException e) {
            log.error("retention: etkin politika sorgu hatası", e);
            return;
        }

        for (Map<String, Object> r : rows) {
            String entityType = str(r.get("entity_type"));
            String tenantId = str(r.get("tenant_id"));
            int retentionDays = intNum(r.get("retention_days"));
            String strategy = str(r.get("archival_strategy"));
            try {
                applyPolicy(tenantId, entityType, retentionDays, strategy);
            } catch (RuntimeException e) {
                log.error("retention: politika uygulama hatası entity_type={} tenant={}", entityType, tenantId, e);
            }
        }
    }

    /** Go {@code applyPolicy} karşılığı — entity türüne göre arşivleme. */
    private void applyPolicy(String tenantId, String entityType, int retentionDays, String strategy) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(retentionDays);

        switch (entityType) {
            case "measurement" -> archiveMeasurements(tenantId, cutoff, strategy);
            case "audit_log" -> archiveAuditLogs(tenantId, cutoff, strategy);
            case "report" -> archiveReports(tenantId, cutoff, strategy);
            case "alert" -> archiveAlerts(tenantId, cutoff, strategy);
            default -> {
                // bilinmeyen entity türü yok sayılır (Go ile aynı)
            }
        }
    }

    private void archiveMeasurements(String tenantId, OffsetDateTime cutoff, String strategy) {
        switch (strategy) {
            case "delete" -> dsl.execute("""
                    DELETE FROM measure.scores WHERE tenant_id = ? AND created_at < ?
                    """, tenantId, cutoff);
            case "anonymize" -> dsl.execute("""
                    UPDATE measure.scores SET metadata = '{}' WHERE tenant_id = ? AND created_at < ?
                    """, tenantId, cutoff);
            case "archive_s3" -> archiveToS3(tenantId, "measurement", cutoff);
            default -> {
            }
        }
    }

    private void archiveAuditLogs(String tenantId, OffsetDateTime cutoff, String strategy) {
        switch (strategy) {
            case "delete" -> dsl.execute("""
                    DELETE FROM identity.audit_logs WHERE tenant_id = ? AND created_at < ?
                    """, tenantId, cutoff);
            case "archive_s3" -> archiveToS3(tenantId, "audit_log", cutoff);
            default -> {
            }
        }
    }

    private void archiveReports(String tenantId, OffsetDateTime cutoff, String strategy) {
        switch (strategy) {
            case "delete" -> dsl.execute("""
                    DELETE FROM measure.reports WHERE tenant_id = ? AND created_at < ?
                    """, tenantId, cutoff);
            case "archive_s3" -> archiveToS3(tenantId, "report", cutoff);
            default -> {
            }
        }
    }

    private void archiveAlerts(String tenantId, OffsetDateTime cutoff, String strategy) {
        switch (strategy) {
            case "delete" -> dsl.execute("""
                    DELETE FROM governance.alert_rules WHERE tenant_id = ? AND created_at < ?
                    """, tenantId, cutoff);
            case "anonymize" -> dsl.execute("""
                    UPDATE governance.alert_rules SET channel_config = '{}' WHERE tenant_id = ? AND created_at < ?
                    """, tenantId, cutoff);
            case "archive_s3" -> archiveToS3(tenantId, "alert", cutoff);
            default -> {
            }
        }
    }

    /** Go {@code archiveToS3} karşılığı — measure.scores → retention.archives taşıma. */
    private void archiveToS3(String tenantId, String entityType, OffsetDateTime cutoff) {
        int rows = dsl.execute("""
                INSERT INTO retention.archives (tenant_id, entity_type, entity_id, archived_at, expires_at, s3_key, data_hash)
                SELECT ?, ?, m.id, now(), now() + INTERVAL '365 days', '', md5(m.id || ?)
                FROM measure.scores m
                WHERE m.tenant_id = ? AND m.created_at < ?
                ON CONFLICT DO NOTHING
                """, tenantId, entityType, tenantId, tenantId, cutoff);
        log.info("retention: S3 arşivleme tamamlandı tenant={} entity_type={} rows={}", tenantId, entityType, rows);
    }

    /** Zamanlayıcı giriş noktası — {@code retention.worker.enabled=true} ise her 6 saatte bir. */
    @Scheduled(fixedDelayString = "${retention.worker.interval:21600000}")
    public void scheduledProcess() {
        processExpired();
    }

    private static int intNum(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }

    private static String str(Object o) {
        if (o == null) {
            return "";
        }
        if (o instanceof java.sql.Timestamp t) {
            return t.toInstant().toString();
        }
        return String.valueOf(o);
    }
}
