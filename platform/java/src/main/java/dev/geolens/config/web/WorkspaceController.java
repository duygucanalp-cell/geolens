package dev.geolens.config.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Çalışma alanı yönetimi REST controller'ı — Go {@code config.workspace_handler} portu.
 * <p>Route'lar (go cmd/api): POST /v1/workspaces/{ws}/archive, POST /v1/workspaces/{ws}/unarchive,
 * POST /v1/workspaces/{ws}/transfer (H4).
 * <p>Tenant {@code X-Tenant-ID} başlığından, workspace URL path'ten gelir.
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}")
public class WorkspaceController {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public WorkspaceController(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @PostMapping("/archive")
    public ResponseEntity<?> archiveWorkspace(@PathVariable String workspaceId,
                                              @RequestHeader("X-Tenant-ID") String tenantId) {
        String now = Instant.now().toString();
        try {
            jdbc.update("""
                    UPDATE config.workspaces SET archived_at = ?, updated_at = ?
                    WHERE id = ? AND tenant_id = ?
                    """, now, now, workspaceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "arşivleme başarısız");
        }
        try {
            jdbc.update("""
                    UPDATE config.brands SET archived_at = ?, is_active = false, updated_at = ?
                    WHERE workspace_id = ? AND tenant_id = ?
                    """, now, now, workspaceId, tenantId);
        } catch (RuntimeException ignored) {
            // brand arşivleme hatası non-fatal (Go ile aynı)
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "archived");
        body.put("archived_at", DateTimeFormatter.ISO_INSTANT.format(Instant.parse(now)));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/unarchive")
    public ResponseEntity<?> unarchiveWorkspace(@PathVariable String workspaceId,
                                                @RequestHeader("X-Tenant-ID") String tenantId) {
        try {
            jdbc.update("""
                    UPDATE config.workspaces SET archived_at = NULL, updated_at = now()
                    WHERE id = ? AND tenant_id = ?
                    """, workspaceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "geri alma başarısız");
        }
        return ResponseEntity.ok(Map.of("status", "unarchived"));
    }

    @PostMapping("/transfer")
    public ResponseEntity<?> transferWorkspace(@PathVariable String workspaceId,
                                               @RequestHeader("X-Tenant-ID") String tenantId,
                                               @RequestBody TransferRequest req) {
        if (req == null || req.targetTenantId() == null || req.targetTenantId().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "hedef kiracı ID gerekli");
        }
        Boolean exists;
        try {
            exists = jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM identity.tenants WHERE id = ?)
                    """, Boolean.class, req.targetTenantId());
        } catch (RuntimeException e) {
            return error(HttpStatus.NOT_FOUND, "hedef kiracı bulunamadı");
        }
        if (Boolean.FALSE.equals(exists)) {
            return error(HttpStatus.NOT_FOUND, "hedef kiracı bulunamadı");
        }

        try {
            tx.execute(status -> {
                jdbc.update("""
                        UPDATE config.workspaces SET tenant_id = ?, updated_at = now()
                        WHERE id = ? AND tenant_id = ?
                        """, req.targetTenantId(), workspaceId, tenantId);
                jdbc.update("""
                        UPDATE config.brands SET tenant_id = ?, updated_at = now()
                        WHERE workspace_id = ? AND tenant_id = ?
                        """, req.targetTenantId(), workspaceId, tenantId);
                return null;
            });
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "devir başarısız");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "transferred");
        body.put("target_tenant_id", req.targetTenantId());
        return ResponseEntity.ok(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
