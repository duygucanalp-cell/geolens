package dev.geolens.config.service;

import dev.geolens.config.web.TransferRequest;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Çalışma alanı yönetimi iş mantığı — Go {@code config.workspace_handler} portu.
 * <p>Arşivle, geri al ve devret işlemleri (transaction dahil) bu servistedir;
 * controller yalnızca HTTP katmanıdır (route'lar: POST /v1/workspaces/{ws}/archive,
 * POST /v1/workspaces/{ws}/unarchive, POST /v1/workspaces/{ws}/transfer — H4).
 */
@Service
public class WorkspaceService {

    private final DSLContext dsl;
    private final TransactionTemplate tx;

    public WorkspaceService(DSLContext dsl, TransactionTemplate tx) {
        this.dsl = dsl;
        this.tx = tx;
    }

    public Map<String, Object> archiveWorkspace(String workspaceId, String tenantId) {
        String now = Instant.now().toString();
        try {
            dsl.execute("""
                    UPDATE config.workspaces SET archived_at = ?, updated_at = ?
                    WHERE id = ? AND tenant_id = ?
                    """, now, now, workspaceId, tenantId);
        } catch (RuntimeException e) {
            throw new ConfigServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "arşivleme başarısız");
        }
        try {
            dsl.execute("""
                    UPDATE config.brands SET archived_at = ?, is_active = false, updated_at = ?
                    WHERE workspace_id = ? AND tenant_id = ?
                    """, now, now, workspaceId, tenantId);
        } catch (RuntimeException ignored) {
            // brand arşivleme hatası non-fatal (Go ile aynı)
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "archived");
        body.put("archived_at", DateTimeFormatter.ISO_INSTANT.format(Instant.parse(now)));
        return body;
    }

    public Map<String, Object> unarchiveWorkspace(String workspaceId, String tenantId) {
        try {
            dsl.execute("""
                    UPDATE config.workspaces SET archived_at = NULL, updated_at = now()
                    WHERE id = ? AND tenant_id = ?
                    """, workspaceId, tenantId);
        } catch (RuntimeException e) {
            throw new ConfigServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "geri alma başarısız");
        }
        return Map.of("status", "unarchived");
    }

    public Map<String, Object> transferWorkspace(String workspaceId, String tenantId, TransferRequest req) {
        Boolean exists;
        try {
            exists = value("""
                    SELECT EXISTS(SELECT 1 FROM identity.tenants WHERE id = ?)
                    """, Boolean.class, req.targetTenantId());
        } catch (RuntimeException e) {
            throw new ConfigServiceException(HttpStatus.NOT_FOUND, "hedef kiracı bulunamadı");
        }
        if (Boolean.FALSE.equals(exists)) {
            throw new ConfigServiceException(HttpStatus.NOT_FOUND, "hedef kiracı bulunamadı");
        }

        try {
            tx.execute(status -> {
                dsl.execute("""
                        UPDATE config.workspaces SET tenant_id = ?, updated_at = now()
                        WHERE id = ? AND tenant_id = ?
                        """, req.targetTenantId(), workspaceId, tenantId);
                dsl.execute("""
                        UPDATE config.brands SET tenant_id = ?, updated_at = now()
                        WHERE workspace_id = ? AND tenant_id = ?
                        """, req.targetTenantId(), workspaceId, tenantId);
                return null;
            });
        } catch (RuntimeException e) {
            throw new ConfigServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "devir başarısız");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "transferred");
        body.put("target_tenant_id", req.targetTenantId());
        return body;
    }

    /** ADR-014: plain SQL tek değer — jOOQ dönüşümüyle (fetchValue raw Object döner). */
    private <T> T value(String sql, Class<T> type, Object... args) {
        Record r = dsl.fetchOne(sql, args);
        return r == null ? null : r.get(0, type);
    }
}
