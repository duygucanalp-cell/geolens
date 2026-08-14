package dev.geolens.retention.web;

import dev.geolens.retention.Policy;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Veri Saklama REST controller'ı — Go {@code retention.handler} portu (K3).
 * <p>Route'lar (go cmd/api): GET /v1/retention/policies, PUT /v1/retention/policies,
 * DELETE /v1/retention/policies/{policyId}, GET /v1/retention/archive-summary.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir; saklama süresi ≥30 gün,
 * arşiv stratejisi delete/anonymize/archive_s3 olmalıdır.
 */
@RestController
@RequestMapping("/v1/retention")
public class RetentionController {

    private static final Set<String> VALID_STRATEGIES = Set.of("delete", "anonymize", "archive_s3");

    private final DSLContext dsl;

    public RetentionController(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ---------- ListPolicies ----------

    @GetMapping("/policies")
    public ResponseEntity<?> listPolicies(@RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch("""
                    SELECT id, tenant_id, entity_type, retention_days, archival_strategy, enabled, created_at, updated_at
                    FROM retention.policies WHERE tenant_id = ? ORDER BY entity_type
                    """, tenantId).intoMaps();
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("policies", List.of()));
        }

        List<Policy> policies = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            policies.add(new Policy(
                    str(r.get("id")), str(r.get("tenant_id")), str(r.get("entity_type")),
                    intNum(r.get("retention_days")), str(r.get("archival_strategy")),
                    r.get("enabled") != null && Boolean.TRUE.equals(r.get("enabled")),
                    str(r.get("created_at")), str(r.get("updated_at"))));
        }
        return ResponseEntity.ok(Map.of("policies", policies));
    }

    // ---------- UpsertPolicy ----------

    @PutMapping("/policies")
    public ResponseEntity<?> upsertPolicy(@RequestHeader("X-Tenant-ID") String tenantId,
                                          @RequestBody UpsertPolicyRequest req) {
        if (req.retentionDays() < 30) {
            return error(HttpStatus.BAD_REQUEST, "saklama süresi en az 30 gün olmalıdır");
        }
        if (req.archivalStrategy() == null || !VALID_STRATEGIES.contains(req.archivalStrategy())) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz arşiv stratejisi: delete, anonymize, archive_s3");
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
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "politika kaydedilemedi");
        }
        if (rec == null) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "politika kaydedilemedi");
        }
        Map<String, Object> r = rec.intoMap();
        Policy p = new Policy(
                str(r.get("id")), str(r.get("tenant_id")), str(r.get("entity_type")),
                intNum(r.get("retention_days")), str(r.get("archival_strategy")),
                r.get("enabled") != null && Boolean.TRUE.equals(r.get("enabled")),
                str(r.get("created_at")), str(r.get("updated_at")));
        return ResponseEntity.ok(p);
    }

    // ---------- DeletePolicy ----------

    @DeleteMapping("/policies/{policyId}")
    public ResponseEntity<?> deletePolicy(@RequestHeader("X-Tenant-ID") String tenantId,
                                          @PathVariable String policyId) {
        int rows;
        try {
            rows = dsl.execute("DELETE FROM retention.policies WHERE id = ? AND tenant_id = ?", policyId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "politika silinemedi");
        }
        if (rows == 0) {
            return error(HttpStatus.NOT_FOUND, "politika bulunamadı");
        }
        return ResponseEntity.ok(Map.of("status", "silindi"));
    }

    // ---------- GetArchiveSummary ----------

    @GetMapping("/archive-summary")
    public ResponseEntity<?> getArchiveSummary(@RequestHeader("X-Tenant-ID") String tenantId) {
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
        return ResponseEntity.ok(resp);
    }

    // ---------- yardımcılar ----------

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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
