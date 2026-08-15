package dev.geolens.apikey.service;

import dev.geolens.apikey.web.CreateApiKeyRequest;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API anahtarı yönetimi iş mantığı — Go {@code apikey.handler} portu (FR-F6).
 * <p>Anahtar üretimi ({@code gls_} önekli), bcrypt hash'leme ve CRUD bu serviste
 * yapılır; controller yalnızca HTTP katmanıdır (route'lar: GET/POST /v1/api-keys,
 * DELETE /v1/api-keys/{keyId}).
 */
@Service
public class ApiKeyService {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DSLContext dsl;

    public ApiKeyService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Map<String, Object> list(String tenantId) {
        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT id, name, key_prefix, role, is_active, last_used_at, expires_at, created_at
                    FROM identity.api_keys
                    WHERE tenant_id = ?
                    ORDER BY created_at DESC
                    """, tenantId);
        } catch (RuntimeException e) {
            return Map.of("keys", List.of());
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
        return Map.of("keys", keys);
    }

    public Map<String, Object> create(String tenantId, CreateApiKeyRequest req) {
        String role = req.role() == null || req.role().isBlank() ? "viewer" : req.role();
        if (!"viewer".equals(role)) {
            throw new ApiKeyServiceException(HttpStatus.BAD_REQUEST, "rol yalnızca viewer olabilir");
        }

        byte[] rawBytes = new byte[24];
        RANDOM.nextBytes(rawBytes);
        String rawKey = "gls_" + HexFormat.of().formatHex(rawBytes);
        String keyPrefix = rawKey.substring(0, 12);
        String hash = ENCODER.encode(rawKey);

        String id;
        try {
            id = value("""
                    INSERT INTO identity.api_keys (id, tenant_id, name, key_hash, key_prefix, role, expires_at)
                    VALUES (gen_random_uuid()::text, ?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """, String.class, tenantId, req.name(), hash, keyPrefix, role,
                    req.expiresAt() == null || req.expiresAt().isBlank() ? null : req.expiresAt());
        } catch (RuntimeException e) {
            throw new ApiKeyServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "anahtar oluşturulamadı");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("api_key", rawKey);
        body.put("key_prefix", keyPrefix);
        body.put("warning", "anahtar yalnızca bir kez gösterilir; kopyalayın");
        return body;
    }

    public Map<String, Object> delete(String tenantId, String keyId) {
        int affected;
        try {
            affected = dsl.execute("""
                    DELETE FROM identity.api_keys WHERE id = ? AND tenant_id = ?
                    """, keyId, tenantId);
        } catch (RuntimeException e) {
            throw new ApiKeyServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "anahtar silinemedi");
        }
        if (affected == 0) {
            throw new ApiKeyServiceException(HttpStatus.NOT_FOUND, "anahtar bulunamadı");
        }
        return Map.of("status", "deleted");
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
