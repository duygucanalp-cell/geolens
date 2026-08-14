package dev.geolens.privacy.web;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * KVKK/GDPR gizlilik REST controller'ı — Go {@code privacy.handler} portu.
 * <p>Route'lar (go cmd/api): GET /v1/account/data (GDPR veri taşınabilirliği),
 * POST /v1/account/deletion + POST /v1/privacy/delete (silme talebi),
 * GET /v1/deletion-requests, POST /v1/deletion-requests/{id}/process (admin).
 * <p>Tenant {@code X-Tenant-ID}, kullanıcı {@code X-User-ID} başlığından gelir
 * (httpmw.GetTenantID/GetUserID karşılığı). Rol DB'den çözülür (userRoleFromDB).
 */
@RestController
public class PrivacyController {

    private final DSLContext dsl;
    private final TransactionTemplate tx;

    public PrivacyController(DSLContext dsl, TransactionTemplate tx) {
        this.dsl = dsl;
        this.tx = tx;
    }

    @GetMapping("/v1/account/data")
    public ResponseEntity<?> exportData(@RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
                                        @RequestHeader(value = "X-User-ID", required = false) String userId) {
        if (tenantId == null || tenantId.isBlank()) {
            return error(HttpStatus.UNAUTHORIZED, "kimlik doğrulama gerekli");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenant_id", tenantId);
        payload.put("exported_at", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        payload.put("format_version", 1);
        payload.put("users", new ArrayList<Object>());
        payload.put("memberships", new ArrayList<Object>());
        payload.put("brands", new ArrayList<Object>());
        payload.put("prompt_sets", new ArrayList<Object>());
        payload.put("measurement_scores", new ArrayList<Object>());

        appendUsers(payload, tenantId);
        appendMemberships(payload, tenantId);
        appendBrands(payload, tenantId);
        appendPromptSets(payload, tenantId);
        appendMeasurementScores(payload, tenantId);

        // GDPR audit kaydı: veri dışa aktarımı loglanır (non-fatal)
        try {
            dsl.execute("""
                    INSERT INTO governance.audit_log (id, tenant_id, user_id, event_type, resource_type, resource_id, action, metadata)
                    VALUES (gen_random_uuid()::text, ?, ?, 'privacy.data_exported', 'tenant', ?, 'export',
                            jsonb_build_object('format', 'json'))
                    """, tenantId, userId == null ? "" : userId, tenantId);
        } catch (RuntimeException ignored) {
            // log kaydı başarısızlığı non-fatal (Go warn + devam)
        }

        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"geolens-data-export.json\"")
                .body(payload);
    }

    @PostMapping({"/v1/account/deletion", "/v1/privacy/delete"})
    public ResponseEntity<?> requestDeletion(@RequestHeader("X-Tenant-ID") String tenantId,
                                             @RequestHeader(value = "X-User-ID", required = false) String userId,
                                             @RequestBody DeletionRequest req) {
        String uid = userId == null ? "" : userId;
        if (uid.isBlank() || tenantId == null || tenantId.isBlank()) {
            return error(HttpStatus.UNAUTHORIZED, "kimlik doğrulama gerekli");
        }
        String reason = req == null || req.reason() == null ? "" : req.reason();

        String role = userRoleFromDB(uid, tenantId);

        if ("admin".equals(role)) {
            // Admin: doğrudan anonimleştir
            String requestId;
            try {
                requestId = txExecute(() -> {
                    String id = value("""
                            INSERT INTO privacy.deletion_requests (id, tenant_id, requested_by, status, reason, processed_at, processed_by)
                            VALUES (gen_random_uuid()::text, ?, ?, 'processing', ?, now(), ?)
                            RETURNING id
                            """, String.class, tenantId, uid, reason, uid);
                    dsl.fetch("SELECT privacy.anonymize_tenant(?)", tenantId);
                    try {
                        dsl.execute("""
                                UPDATE privacy.deletion_requests
                                SET status = 'completed', notes = 'KVKK kapsamında anonimleştirildi'
                                WHERE id = ?
                                """, id);
                    } catch (RuntimeException ignored) {
                        // talep güncelleme hatası non-fatal (Go warn)
                    }
                    return id;
                });
            } catch (RuntimeException e) {
                return error(HttpStatus.INTERNAL_SERVER_ERROR, "silme işlemi başarısız");
            }
            return ResponseEntity.ok(new DeletionResponse(requestId, "completed",
                    "Hesabınız ve tüm kişisel verileriniz başarıyla anonimleştirildi."));
        }

        // Editor/viewer: talep oluştur
        String requestId;
        try {
            requestId = value("""
                    INSERT INTO privacy.deletion_requests (id, tenant_id, requested_by, status, reason)
                    VALUES (gen_random_uuid()::text, ?, ?, 'pending', ?)
                    RETURNING id
                    """, String.class, tenantId, uid, reason);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "talep oluşturulamadı");
        }

        // Audit log (non-fatal)
        try {
            dsl.execute("""
                    INSERT INTO governance.audit_log (id, tenant_id, user_id, event_type, resource_type, resource_id, action, metadata)
                    VALUES (gen_random_uuid()::text, ?, ?, 'privacy.deletion_requested', 'tenant', ?, 'request',
                            jsonb_build_object('reason', ?, 'status', 'pending'))
                    """, tenantId, uid, tenantId, reason);
        } catch (RuntimeException ignored) {
            // audit log kaydı başarısızlığı non-fatal (Go warn)
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new DeletionResponse(requestId, "pending",
                "Veri silme talebiniz alındı. Admin kullanıcı talebinizi değerlendirecektir."));
    }

    @GetMapping("/v1/deletion-requests")
    public ResponseEntity<?> listDeletionRequests(@RequestHeader("X-Tenant-ID") String tenantId,
                                                  @RequestHeader(value = "X-User-ID", required = false) String userId) {
        String role = userRoleFromDB(userId == null ? "" : userId, tenantId);
        if (!"admin".equals(role)) {
            return error(HttpStatus.FORBIDDEN, "bu işlem için admin yetkisi gerekli");
        }

        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT id, requested_by, status, COALESCE(reason, '') AS reason, requested_at,
                           COALESCE(processed_at, '1970-01-01'::timestamptz) AS processed_at, COALESCE(notes, '') AS notes
                    FROM privacy.deletion_requests
                    WHERE tenant_id = ?
                    ORDER BY requested_at DESC
                    LIMIT 50
                    """, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "talepler listelenemedi");
        }

        List<Map<String, Object>> requests = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.get("id"));
            item.put("requested_by", r.get("requested_by"));
            item.put("status", r.get("status"));
            item.put("reason", r.get("reason"));
            item.put("requested_at", r.get("requested_at") == null ? null : String.valueOf(r.get("requested_at")));
            item.put("processed_at", r.get("processed_at") == null ? null : String.valueOf(r.get("processed_at")));
            item.put("notes", r.get("notes"));
            requests.add(item);
        }
        return ResponseEntity.ok(Map.of("requests", requests));
    }

    @PostMapping("/v1/deletion-requests/{id}/process")
    public ResponseEntity<?> processDeletionRequest(@RequestHeader("X-Tenant-ID") String tenantId,
                                                    @RequestHeader(value = "X-User-ID", required = false) String userId,
                                                    @PathVariable String id,
                                                    @RequestBody ProcessRequest req) {
        String role = userRoleFromDB(userId == null ? "" : userId, tenantId);
        if (!"admin".equals(role)) {
            return error(HttpStatus.FORBIDDEN, "bu işlem için admin yetkisi gerekli");
        }
        String action = req == null ? "" : (req.action() == null ? "" : req.action());
        String notes = req == null || req.notes() == null ? "" : req.notes();

        if (!"approve".equals(action) && !"reject".equals(action)) {
            return error(HttpStatus.BAD_REQUEST, "action 'approve' veya 'reject' olmalıdır");
        }

        if ("approve".equals(action)) {
            String requestId;
            try {
                requestId = txExecute(() -> {
                    String rid = value("""
                            UPDATE privacy.deletion_requests
                            SET status = 'processing', processed_at = now(), notes = COALESCE(?, notes)
                            WHERE id = ? AND tenant_id = ? AND status = 'pending'
                            RETURNING id
                            """, String.class, notes, id, tenantId);
                    if (rid == null) {
                        throw new PrivacyHttpException(HttpStatus.NOT_FOUND, "talep bulunamadı veya zaten işlenmiş");
                    }
                    dsl.fetch("SELECT privacy.anonymize_tenant(?)", tenantId);
                    try {
                        dsl.execute("""
                                UPDATE privacy.deletion_requests SET status = 'completed'
                                WHERE id = ?
                                """, rid);
                    } catch (RuntimeException ignored) {
                        // talep güncelleme hatası non-fatal (Go warn)
                    }
                    return rid;
                });
            } catch (PrivacyHttpException e) {
                return error(e.status(), e.getMessage());
            } catch (RuntimeException e) {
                return error(HttpStatus.INTERNAL_SERVER_ERROR, "işlem başarısız");
            }
            return ResponseEntity.ok(new DeletionResponse(requestId, "completed",
                    "Talep onaylandı ve veriler anonimleştirildi."));
        }

        // Reject
        try {
            dsl.execute("""
                    UPDATE privacy.deletion_requests
                    SET status = 'rejected', processed_at = now(), notes = COALESCE(?, notes)
                    WHERE id = ? AND tenant_id = ?
                    """, notes, id, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "işlem başarısız");
        }
        return ResponseEntity.ok(new DeletionResponse(id, "rejected", "Talep reddedildi."));
    }

    private String userRoleFromDB(String userId, String tenantId) {
        if (userId == null || userId.isBlank() || tenantId == null || tenantId.isBlank()) {
            return "";
        }
        try {
            String role = value("""
                    SELECT m.role FROM config.memberships m
                    WHERE m.user_id = ? AND m.tenant_id = ?
                    ORDER BY m.created_at LIMIT 1
                    """, String.class, userId, tenantId);
            return role == null ? "" : role;
        } catch (RuntimeException e) {
            return "";
        }
    }

    private void appendUsers(Map<String, Object> payload, String tenantId) {
        try {
            List<Map<String, Object>> rows = list("""
                    SELECT u.id, u.email, u.full_name, u.created_at
                    FROM identity.users u
                    JOIN identity.user_tenants ut ON ut.user_id = u.id
                    WHERE ut.tenant_id = ?
                    ORDER BY u.created_at
                    """, tenantId);
            for (Map<String, Object> r : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", r.get("id"));
                item.put("email", r.get("email"));
                item.put("full_name", r.get("full_name"));
                item.put("created_at", r.get("created_at") == null ? null : String.valueOf(r.get("created_at")));
                @SuppressWarnings("unchecked")
                List<Object> users = (List<Object>) payload.get("users");
                users.add(item);
            }
        } catch (RuntimeException ignored) {
            // sorgu hatası payload'u bozmayacak şekilde atlanır (Go err == nil guard)
        }
    }

    private void appendMemberships(Map<String, Object> payload, String tenantId) {
        try {
            List<Map<String, Object>> rows = list("""
                    SELECT user_id, role, created_at FROM config.memberships
                    WHERE tenant_id = ? ORDER BY created_at
                    """, tenantId);
            for (Map<String, Object> r : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("user_id", r.get("user_id"));
                item.put("role", r.get("role"));
                item.put("created_at", r.get("created_at") == null ? null : String.valueOf(r.get("created_at")));
                @SuppressWarnings("unchecked")
                List<Object> memberships = (List<Object>) payload.get("memberships");
                memberships.add(item);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void appendBrands(Map<String, Object> payload, String tenantId) {
        try {
            List<Map<String, Object>> rows = list("""
                    SELECT id, workspace_id, name, website_url, created_at
                    FROM config.brands WHERE tenant_id = ? AND is_active = true
                    ORDER BY created_at
                    """, tenantId);
            for (Map<String, Object> r : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", r.get("id"));
                item.put("workspace_id", r.get("workspace_id"));
                item.put("name", r.get("name"));
                item.put("website_url", r.get("website_url"));
                item.put("created_at", r.get("created_at") == null ? null : String.valueOf(r.get("created_at")));
                @SuppressWarnings("unchecked")
                List<Object> brands = (List<Object>) payload.get("brands");
                brands.add(item);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void appendPromptSets(Map<String, Object> payload, String tenantId) {
        try {
            List<Map<String, Object>> rows = list("""
                    SELECT id, name, category, created_at FROM config.prompt_sets
                    WHERE tenant_id = ? ORDER BY created_at
                    """, tenantId);
            for (Map<String, Object> r : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", r.get("id"));
                item.put("name", r.get("name"));
                item.put("category", r.get("category"));
                item.put("created_at", r.get("created_at") == null ? null : String.valueOf(r.get("created_at")));
                @SuppressWarnings("unchecked")
                List<Object> sets = (List<Object>) payload.get("prompt_sets");
                sets.add(item);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private void appendMeasurementScores(Map<String, Object> payload, String tenantId) {
        try {
            List<Map<String, Object>> rows = list("""
                    SELECT brand_id, workspace_id, value, engine_name, freshness_at
                    FROM measure.scores WHERE tenant_id = ?
                    ORDER BY freshness_at DESC LIMIT 1000
                    """, tenantId);
            for (Map<String, Object> r : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("brand_id", r.get("brand_id"));
                item.put("workspace_id", r.get("workspace_id"));
                item.put("value", r.get("value"));
                item.put("engine_name", r.get("engine_name"));
                item.put("freshness_at", r.get("freshness_at") == null ? null : String.valueOf(r.get("freshness_at")));
                @SuppressWarnings("unchecked")
                List<Object> scores = (List<Object>) payload.get("measurement_scores");
                scores.add(item);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private String txExecute(java.util.function.Supplier<String> action) {
        try {
            return tx.execute(new TransactionCallback<>() {
                @Override
                public String doInTransaction(TransactionStatus status) {
                    return action.get();
                }
            });
        } catch (PrivacyHttpException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        }
    }

    /** ADR-014: plain SQL üzerinden jOOQ — satır erişimi Map ile korunur. */
    private List<Map<String, Object>> list(String sql, Object... args) {
        return dsl.fetch(sql, args).intoMaps();
    }

    /** ADR-014: plain SQL tek değer — jOOQ dönüşümüyle (fetchValue raw Object döner). */
    private <T> T value(String sql, Class<T> type, Object... args) {
        Record r = dsl.fetchOne(sql, args);
        return r == null ? null : r.get(0, type);
    }

    @ExceptionHandler(PrivacyHttpException.class)
    public ResponseEntity<ApiError> handlePrivacyError(PrivacyHttpException ex) {
        return error(ex.status(), ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
