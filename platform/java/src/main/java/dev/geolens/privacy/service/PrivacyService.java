package dev.geolens.privacy.service;

import dev.geolens.privacy.web.DeletionResponse;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * KVKK/GDPR gizlilik iş mantığı — Go {@code privacy.handler} portu.
 * <p>Veri dışa aktarımı, silme talebi oluşturma/işleme ve talep listeleme DB erişimini
 * ve transaction yönetimini içerir. Controller yalnızca HTTP katmanıdır.
 * <p>Tenant {@code X-Tenant-ID}, kullanıcı {@code X-User-ID} başlığından gelir
 * (httpmw.GetTenantID/GetUserID karşılığı). Rol DB'den çözülür (userRoleFromDB).
 */
@Service
public class PrivacyService {

    private final DSLContext dsl;
    private final TransactionTemplate tx;

    public PrivacyService(DSLContext dsl, TransactionTemplate tx) {
        this.dsl = dsl;
        this.tx = tx;
    }

    /** Go {@code exportData} karşılığı — GDPR veri taşınabilirliği payload'unu üretir. */
    public Map<String, Object> exportData(String tenantId, String userId) {
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

        return payload;
    }

    /** Go {@code requestDeletion} karşılığı — rolü çözer, admin doğrudan anonimleştirir, diğerleri talep oluşturur. */
    public DeletionResponse requestDeletion(String tenantId, String userId, String reason) {
        String uid = userId == null ? "" : userId;
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
                throw new PrivacyServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "silme işlemi başarısız");
            }
            return new DeletionResponse(requestId, "completed",
                    "Hesabınız ve tüm kişisel verileriniz başarıyla anonimleştirildi.");
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
            throw new PrivacyServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "talep oluşturulamadı");
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

        return new DeletionResponse(requestId, "pending",
                "Veri silme talebiniz alındı. Admin kullanıcı talebinizi değerlendirecektir.");
    }

    /** Go {@code listDeletionRequests} karşılığı — admin yetkisi kontrol eder, talepleri listeler. */
    public Map<String, Object> listDeletionRequests(String tenantId, String userId) {
        String role = userRoleFromDB(userId == null ? "" : userId, tenantId);
        if (!"admin".equals(role)) {
            throw new PrivacyServiceException(HttpStatus.FORBIDDEN, "bu işlem için admin yetkisi gerekli");
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
            throw new PrivacyServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "talepler listelenemedi");
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
        return Map.of("requests", requests);
    }

    /** Go {@code processDeletionRequest} karşılığı — admin yetkisi kontrol eder, onay/red işler. */
    public DeletionResponse processDeletionRequest(String tenantId, String userId, String id, String action, String notes) {
        String role = userRoleFromDB(userId == null ? "" : userId, tenantId);
        if (!"admin".equals(role)) {
            throw new PrivacyServiceException(HttpStatus.FORBIDDEN, "bu işlem için admin yetkisi gerekli");
        }

        if (!"approve".equals(action) && !"reject".equals(action)) {
            throw new PrivacyServiceException(HttpStatus.BAD_REQUEST, "action 'approve' veya 'reject' olmalıdır");
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
                        throw new PrivacyServiceException(HttpStatus.NOT_FOUND, "talep bulunamadı veya zaten işlenmiş");
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
            } catch (PrivacyServiceException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new PrivacyServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "işlem başarısız");
            }
            return new DeletionResponse(requestId, "completed",
                    "Talep onaylandı ve veriler anonimleştirildi.");
        }

        // Reject
        try {
            dsl.execute("""
                    UPDATE privacy.deletion_requests
                    SET status = 'rejected', processed_at = now(), notes = COALESCE(?, notes)
                    WHERE id = ? AND tenant_id = ?
                    """, notes, id, tenantId);
        } catch (RuntimeException e) {
            throw new PrivacyServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "işlem başarısız");
        }
        return new DeletionResponse(id, "rejected", "Talep reddedildi.");
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
        return tx.execute(status -> action.get());
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
}
