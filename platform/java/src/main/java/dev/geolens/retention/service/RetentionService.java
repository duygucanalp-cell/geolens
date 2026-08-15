package dev.geolens.retention.service;

import dev.geolens.retention.Policy;
import dev.geolens.retention.web.UpsertPolicyRequest;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Veri Saklama iş mantığı — Go {@code retention.handler} portu (K3).
 * <p>Politika CRUD'u, saklama süresi/arşiv stratejisi doğrulaması ve arşiv özeti
 * bu servistedir; controller yalnızca HTTP katmanıdır (route'lar:
 * GET /v1/retention/policies, PUT /v1/retention/policies,
 * DELETE /v1/retention/policies/{policyId}, GET /v1/retention/archive-summary).
 */
@Service
public class RetentionService {

    private static final Set<String> VALID_STRATEGIES = Set.of("delete", "anonymize", "archive_s3");

    private final DSLContext dsl;

    public RetentionService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Map<String, Object> listPolicies(String tenantId) {
        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch("""
                    SELECT id, tenant_id, entity_type, retention_days, archival_strategy, enabled, created_at, updated_at
                    FROM retention.policies WHERE tenant_id = ? ORDER BY entity_type
                    """, tenantId).intoMaps();
        } catch (RuntimeException e) {
            return Map.of("policies", List.of());
        }

        List<Policy> policies = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            policies.add(new Policy(
                    str(r.get("id")), str(r.get("tenant_id")), str(r.get("entity_type")),
                    intNum(r.get("retention_days")), str(r.get("archival_strategy")),
                    r.get("enabled") != null && Boolean.TRUE.equals(r.get("enabled")),
                    str(r.get("created_at")), str(r.get("updated_at"))));
        }
        return Map.of("policies", policies);
    }

    public Policy upsertPolicy(String tenantId, UpsertPolicyRequest req) {
        if (req.retentionDays() < 30) {
            throw new RetentionServiceException(HttpStatus.BAD_REQUEST, "saklama süresi en az 30 gün olmalıdır");
        }
        if (req.archivalStrategy() == null || !VALID_STRATEGIES.contains(req.archivalStrategy())) {
            throw new RetentionServiceException(HttpStatus.BAD_REQUEST, "geçersiz arşiv stratejisi: delete, anonymize, archive_s3");
        }

        Record rec;
        try {
            rec = dsl.fetchOne("""
                    INSERT INTO retention.policies (tenant_id, entity_type, retention_days, archival_strategy, enabled)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (tenant_id, entity_type) DO UPDATE SET
                        retention_days = EXCLUDED.retention_days,
                        archival_strategy = EXCLUDED.archival_strategy,
                        enabled = EXCLUDED.enabled,
                        updated_at = now()
                    RETURNING id, tenant_id, entity_type, retention_days, archival_strategy, enabled, created_at, updated_at
                    """, tenantId, nz(req.entityType()), req.retentionDays(), req.archivalStrategy(), req.enabled());
        } catch (RuntimeException e) {
            throw new RetentionServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "politika kaydedilemedi");
        }
        if (rec == null) {
            throw new RetentionServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "politika kaydedilemedi");
        }
        Map<String, Object> r = rec.intoMap();
        return new Policy(
                str(r.get("id")), str(r.get("tenant_id")), str(r.get("entity_type")),
                intNum(r.get("retention_days")), str(r.get("archival_strategy")),
                r.get("enabled") != null && Boolean.TRUE.equals(r.get("enabled")),
                str(r.get("created_at")), str(r.get("updated_at")));
    }

    public Map<String, Object> deletePolicy(String tenantId, String policyId) {
        int rows;
        try {
            rows = dsl.execute("DELETE FROM retention.policies WHERE id = ? AND tenant_id = ?", policyId, tenantId);
        } catch (RuntimeException e) {
            throw new RetentionServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "politika silinemedi");
        }
        if (rows == 0) {
            throw new RetentionServiceException(HttpStatus.NOT_FOUND, "politika bulunamadı");
        }
        return Map.of("status", "silindi");
    }

    public Map<String, Object> getArchiveSummary(String tenantId) {
        int totalArchived = 0;
        String totalSize = "0";
        try {
            Record countRec = dsl.fetchOne("SELECT COUNT(*) FROM retention.archives WHERE tenant_id = ?", tenantId);
            if (countRec != null) {
                totalArchived = ((Number) countRec.get(0)).intValue();
            }
            Record sizeRec = dsl.fetchOne("SELECT COALESCE(COUNT(*)::TEXT || ' kayıt', '0') FROM retention.archives WHERE tenant_id = ?", tenantId);
            if (sizeRec != null && sizeRec.get(0) != null) {
                totalSize = str(sizeRec.get(0));
            }
        } catch (RuntimeException e) {
            // Go'da hata yok sayılır (0 kalır)
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("total_archived", totalArchived);
        resp.put("total_size", totalSize);
        resp.put("entities", List.of(
                "measurement — ölçüm sonuçları",
                "audit_log — denetim günlükleri",
                "report — PDF raporlar",
                "alert — uyarı kayıtları"));
        return resp;
    }

    private static int intNum(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
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
