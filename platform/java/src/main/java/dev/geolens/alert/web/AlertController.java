package dev.geolens.alert.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Uyarı kuralı REST controller'ı — Go {@code alert.handler} portu.
 * <p>Route'lar (go cmd/api): GET/POST /v1/workspaces/{ws}/alert-rules,
 * PUT/DELETE /v1/workspaces/{ws}/alert-rules/{ruleId} (FR-F2).
 * <p>Tenant {@code X-Tenant-ID} başlığından, workspace URL path'ten gelir.
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}/alert-rules")
public class AlertController {

    private final JdbcTemplate jdbc;

    public AlertController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable String workspaceId,
                                  @RequestHeader("X-Tenant-ID") String tenantId,
                                  @RequestParam(value = "brand_id", required = false) String brandId) {
        String b = brandId == null ? "" : brandId;

        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("""
                    SELECT ar.id, ar.brand_id, ar.name, ar.metric, ar.condition,
                        ar.threshold, ar.channel, ar.channel_config, ar.enabled, ar.cooldown_min, ar.last_fired_at, ar.created_at, ar.updated_at
                    FROM governance.alert_rules ar
                    JOIN config.brands b ON b.id = ar.brand_id
                    WHERE ar.tenant_id = ? AND b.workspace_id = ?
                        AND (? = '' OR ar.brand_id = ?)
                    ORDER BY ar.created_at DESC
                    """, tenantId, workspaceId, b, b);
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("rules", List.of()));
        }

        List<Map<String, Object>> rules = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.get("id"));
            item.put("brand_id", r.get("brand_id"));
            item.put("name", r.get("name"));
            item.put("metric", r.get("metric"));
            item.put("condition", r.get("condition"));
            item.put("threshold", r.get("threshold"));
            item.put("channel", r.get("channel"));
            item.put("channel_config", r.get("channel_config"));
            item.put("enabled", r.get("enabled"));
            item.put("cooldown_min", r.get("cooldown_min"));
            item.put("last_fired_at", r.get("last_fired_at") == null ? null : String.valueOf(r.get("last_fired_at")));
            item.put("created_at", r.get("created_at") == null ? null : String.valueOf(r.get("created_at")));
            item.put("updated_at", r.get("updated_at") == null ? null : String.valueOf(r.get("updated_at")));
            rules.add(item);
        }
        return ResponseEntity.ok(Map.of("rules", rules));
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable String workspaceId,
                                    @RequestHeader("X-Tenant-ID") String tenantId,
                                    @RequestBody AlertRuleRequest req) {
        if (req == null || req.brandId() == null || req.brandId().isBlank()
                || req.name() == null || req.name().isBlank()
                || req.metric() == null || req.metric().isBlank()
                || req.condition() == null || req.condition().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "marka, ad, metrik ve koşul zorunludur");
        }
        String channel = req.channel() == null || req.channel().isBlank() ? "email" : req.channel();
        int cooldownMin = req.cooldownMin() == 0 ? 60 : req.cooldownMin();

        Boolean brandExists;
        try {
            brandExists = jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM config.brands WHERE id = ? AND workspace_id = ? AND tenant_id = ?)
                    """, Boolean.class, req.brandId(), workspaceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.NOT_FOUND, "marka bulunamadı");
        }
        if (Boolean.FALSE.equals(brandExists)) {
            return error(HttpStatus.NOT_FOUND, "marka bulunamadı");
        }

        String ruleId;
        try {
            ruleId = jdbc.queryForObject("""
                    INSERT INTO governance.alert_rules
                        (id, tenant_id, brand_id, name, metric, condition, threshold, channel, channel_config, cooldown_min)
                    VALUES (gen_random_uuid()::text, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """, String.class, tenantId, req.brandId(), req.name(), req.metric(), req.condition(),
                    req.threshold(), channel, req.channelConfig(), cooldownMin);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "kural oluşturulamadı");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", ruleId);
        body.put("status", "created");
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PutMapping("/{ruleId}")
    public ResponseEntity<?> update(@PathVariable String workspaceId,
                                    @RequestHeader("X-Tenant-ID") String tenantId,
                                    @PathVariable String ruleId,
                                    @RequestBody UpdateAlertRuleRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        int affected;
        try {
            affected = jdbc.update("""
                    UPDATE governance.alert_rules
                    SET name           = COALESCE(?, name),
                        metric         = COALESCE(?, metric),
                        condition      = COALESCE(?, condition),
                        threshold      = COALESCE(?, threshold),
                        channel        = COALESCE(?, channel),
                        channel_config = COALESCE(?, channel_config),
                        enabled        = COALESCE(?, enabled),
                        cooldown_min   = COALESCE(?, cooldown_min),
                        updated_at     = now()
                    WHERE id = ? AND tenant_id = ?
                    """, req.name(), req.metric(), req.condition(), req.threshold(),
                    req.channel(), req.channelConfig(), req.enabled(), req.cooldownMin(),
                    ruleId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "kural güncellenemedi");
        }
        if (affected == 0) {
            return error(HttpStatus.NOT_FOUND, "kural bulunamadı");
        }
        return ResponseEntity.ok(Map.of("status", "updated"));
    }

    @DeleteMapping("/{ruleId}")
    public ResponseEntity<?> delete(@PathVariable String workspaceId,
                                    @RequestHeader("X-Tenant-ID") String tenantId,
                                    @PathVariable String ruleId) {
        int affected;
        try {
            affected = jdbc.update("""
                    DELETE FROM governance.alert_rules WHERE id = ? AND tenant_id = ?
                    """, ruleId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "kural silinemedi");
        }
        if (affected == 0) {
            return error(HttpStatus.NOT_FOUND, "kural bulunamadı");
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
