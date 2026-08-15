package dev.geolens.alert.service;

import dev.geolens.common.ServiceException;

import dev.geolens.alert.web.AlertRuleRequest;
import dev.geolens.alert.web.UpdateAlertRuleRequest;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Uyarı kuralı iş mantığı — Go {@code alert.handler} portu.
 * <p>Kural CRUD ve marka doğrulaması bu serviste yapılır; controller yalnızca
 * HTTP katmanıdır (route'lar: GET/POST /v1/workspaces/{ws}/alert-rules,
 * PUT/DELETE /v1/workspaces/{ws}/alert-rules/{ruleId} — FR-F2).
 */
@Service
public class AlertService {

    private final DSLContext dsl;

    public AlertService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Map<String, Object> list(String workspaceId, String tenantId, String brandId) {
        String b = brandId == null ? "" : brandId;

        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT ar.id, ar.brand_id, ar.name, ar.metric, ar.condition,
                        ar.threshold, ar.channel, ar.channel_config, ar.enabled, ar.cooldown_min, ar.last_fired_at, ar.created_at, ar.updated_at
                    FROM governance.alert_rules ar
                    JOIN config.brands b ON b.id = ar.brand_id
                    WHERE ar.tenant_id = ? AND b.workspace_id = ?
                        AND (? = '' OR ar.brand_id = ?)
                    ORDER BY ar.created_at DESC
                    """, tenantId, workspaceId, b, b);
        } catch (RuntimeException e) {
            return Map.of("rules", List.of());
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
        return Map.of("rules", rules);
    }

    public Map<String, Object> create(String workspaceId, String tenantId, AlertRuleRequest req) {
        String channel = req.channel() == null || req.channel().isBlank() ? "email" : req.channel();
        int cooldownMin = req.cooldownMin() == 0 ? 60 : req.cooldownMin();

        Boolean brandExists;
        try {
            brandExists = value("""
                    SELECT EXISTS(SELECT 1 FROM config.brands WHERE id = ? AND workspace_id = ? AND tenant_id = ?)
                    """, Boolean.class, req.brandId(), workspaceId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "marka bulunamadı");
        }
        if (Boolean.FALSE.equals(brandExists)) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "marka bulunamadı");
        }

        String ruleId;
        try {
            ruleId = value("""
                    INSERT INTO governance.alert_rules
                        (id, tenant_id, brand_id, name, metric, condition, threshold, channel, channel_config, cooldown_min)
                    VALUES (gen_random_uuid()::text, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """, String.class, tenantId, req.brandId(), req.name(), req.metric(), req.condition(),
                    req.threshold(), channel, req.channelConfig(), cooldownMin);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "kural oluşturulamadı");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", ruleId);
        body.put("status", "created");
        return body;
    }

    public Map<String, Object> update(String workspaceId, String tenantId, String ruleId, UpdateAlertRuleRequest req) {
        int affected;
        try {
            affected = dsl.execute("""
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
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "kural güncellenemedi");
        }
        if (affected == 0) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "kural bulunamadı");
        }
        return Map.of("status", "updated");
    }

    public Map<String, Object> delete(String tenantId, String ruleId) {
        int affected;
        try {
            affected = dsl.execute("""
                    DELETE FROM governance.alert_rules WHERE id = ? AND tenant_id = ?
                    """, ruleId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "kural silinemedi");
        }
        if (affected == 0) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "kural bulunamadı");
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
