package dev.geolens.apikey.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API anahtarı yönetimi REST controller'ı — Go {@code apikey.handler} portu (FR-F6).
 * <p>Route'lar (go cmd/api): GET/POST /v1/api-keys, DELETE /v1/api-keys/{keyId} (admin).
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir. Anahtar {@code gls_} ön ekli,
 * bcrypt ile hash'lenir; ham anahtar yalnızca oluşturmada döner.
 */
@RestController
@RequestMapping("/v1/api-keys")
public class ApiKeyController {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;

    public ApiKeyController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("""
                    SELECT id, name, key_prefix, role, is_active, last_used_at, expires_at, created_at
                    FROM identity.api_keys
                    WHERE tenant_id = ?
                    ORDER BY created_at DESC
                    """, tenantId);
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("keys", List.of()));
        }

        List<Map<String, Object>> keys = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.get("id"));
            item.put("name", r.get("name"));
            item.put("key_prefix", r.get("key_prefix"));
            item.put("role", r.get("role"));
            item.put("is_active", r.get("is_active"));
            if (r.get("last_used_at") != null) {
                item.put("last_used_at", String.valueOf(r.get("last_used_at")));
            }
            if (r.get("expires_at") != null) {
                item.put("expires_at", String.valueOf(r.get("expires_at")));
            }
            item.put("created_at", r.get("created_at") == null ? null : String.valueOf(r.get("created_at")));
            keys.add(item);
        }
        return ResponseEntity.ok(Map.of("keys", keys));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestHeader("X-Tenant-ID") String tenantId,
                                    @RequestBody CreateApiKeyRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "isim zorunludur");
        }
        String role = req.role() == null || req.role().isBlank() ? "viewer" : req.role();
        if (!"viewer".equals(role)) {
            return error(HttpStatus.BAD_REQUEST, "rol yalnızca viewer olabilir");
        }

        byte[] rawBytes = new byte[24];
        RANDOM.nextBytes(rawBytes);
        String rawKey = "gls_" + HexFormat.of().formatHex(rawBytes);
        String keyPrefix = rawKey.substring(0, 12);
        String hash = ENCODER.encode(rawKey);

        String id;
        try {
            id = jdbc.queryForObject("""
                    INSERT INTO identity.api_keys (id, tenant_id, name, key_hash, key_prefix, role, expires_at)
                    VALUES (gen_random_uuid()::text, ?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """, String.class, tenantId, req.name(), hash, keyPrefix, role,
                    req.expiresAt() == null || req.expiresAt().isBlank() ? null : req.expiresAt());
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "anahtar oluşturulamadı");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("api_key", rawKey);
        body.put("key_prefix", keyPrefix);
        body.put("warning", "anahtar yalnızca bir kez gösterilir; kopyalayın");
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @DeleteMapping("/{keyId}")
    public ResponseEntity<?> delete(@RequestHeader("X-Tenant-ID") String tenantId,
                                    @PathVariable String keyId) {
        int affected;
        try {
            affected = jdbc.update("""
                    DELETE FROM identity.api_keys WHERE id = ? AND tenant_id = ?
                    """, keyId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "anahtar silinemedi");
        }
        if (affected == 0) {
            return error(HttpStatus.NOT_FOUND, "anahtar bulunamadı");
        }
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
