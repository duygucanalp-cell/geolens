package dev.geolens.config.service;

import dev.geolens.common.ServiceException;

import dev.geolens.config.web.PanelRequest;
import dev.geolens.config.web.PromptSetRequest;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Panel ve prompt seti iş mantığı — Go {@code config.panel} portu.
 * <p>Panel/prompt set CRUD'u ve panel-marka ilişkileri bu servistedir; controller
 * yalnızca HTTP katmanıdır (route'lar: GET/POST /v1/workspaces/{ws}/panels,
 * GET /v1/workspaces/{ws}/panels/{panelId}, GET/POST /v1/workspaces/{ws}/prompt-sets).
 */
@Service
public class PanelService {

    private final DSLContext dsl;

    public PanelService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<Map<String, Object>> listPanels(String workspaceId, String tenantId) {
        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT p.id, p.name, COALESCE(p.description, '') AS description,
                           COALESCE(p.prompt_set_id, '') AS prompt_set_id,
                           COALESCE(p.schedule_cron, '') AS schedule_cron,
                           p.is_active, p.created_at
                    FROM config.panels p
                    WHERE p.workspace_id = ? AND p.tenant_id = ?
                    ORDER BY p.name
                    """, workspaceId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "sorgu hatası");
        }
        List<Map<String, Object>> panels = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            panels.add(panelMap(r));
        }
        return panels;
    }

    public Map<String, Object> createPanel(String workspaceId, String tenantId, PanelRequest req) {
        String desc = req.description() == null ? "" : req.description();
        String promptSetId = req.promptSetId() == null ? "" : req.promptSetId();
        String scheduleCron = req.scheduleCron() == null ? "" : req.scheduleCron();

        String panelId;
        try {
            panelId = value("""
                    INSERT INTO config.panels (id, workspace_id, tenant_id, name, description, prompt_set_id, schedule_cron)
                    VALUES (gen_random_uuid()::text, ?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """, String.class, workspaceId, tenantId, req.name(), desc, promptSetId, scheduleCron);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "panel oluşturulamadı");
        }

        if (req.brandIds() != null) {
            for (String brandId : req.brandIds()) {
                try {
                    dsl.execute("""
                            INSERT INTO config.panel_brands (panel_id, brand_id, workspace_id, tenant_id)
                            VALUES (?, ?, ?, ?)
                            ON CONFLICT DO NOTHING
                            """, panelId, brandId, workspaceId, tenantId);
                } catch (RuntimeException ignored) {
                    // ilişki hatası non-fatal (Go warn + devam)
                }
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", panelId);
        body.put("name", req.name());
        body.put("description", desc);
        body.put("prompt_set_id", promptSetId);
        body.put("schedule_cron", scheduleCron);
        body.put("is_active", true);
        body.put("created_at", Instant.now().toString());
        return body;
    }

    public Map<String, Object> getPanel(String workspaceId, String tenantId, String panelId) {
        Map<String, Object> row;
        try {
            row = map("""
                    SELECT id, name, COALESCE(description, '') AS description,
                           COALESCE(prompt_set_id, '') AS prompt_set_id,
                           COALESCE(schedule_cron, '') AS schedule_cron,
                           is_active, created_at
                    FROM config.panels
                    WHERE id = ? AND workspace_id = ? AND tenant_id = ?
                    """, panelId, workspaceId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "panel bulunamadı");
        }
        return panelMap(row);
    }

    public List<Map<String, Object>> listPromptSets(String workspaceId, String tenantId) {
        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT id, name, COALESCE(description, '') AS description, prompt_text, is_active
                    FROM config.prompt_sets
                    WHERE workspace_id = ? AND tenant_id = ?
                    ORDER BY name
                    """, workspaceId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "sorgu hatası");
        }
        List<Map<String, Object>> sets = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> ps = new LinkedHashMap<>();
            ps.put("id", r.get("id"));
            ps.put("name", r.get("name"));
            ps.put("description", r.get("description"));
            ps.put("prompt_text", r.get("prompt_text"));
            ps.put("is_active", r.get("is_active"));
            sets.add(ps);
        }
        return sets;
    }

    public Map<String, Object> createPromptSet(String workspaceId, String tenantId, PromptSetRequest req) {
        String desc = req.description() == null ? "" : req.description();

        String setId;
        try {
            setId = value("""
                    INSERT INTO config.prompt_sets (id, workspace_id, tenant_id, name, description, prompt_text)
                    VALUES (gen_random_uuid()::text, ?, ?, ?, ?, ?)
                    RETURNING id
                    """, String.class, workspaceId, tenantId, req.name(), desc, req.promptText());
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "prompt set oluşturulamadı");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", setId);
        body.put("name", req.name());
        body.put("description", desc);
        body.put("prompt_text", req.promptText());
        body.put("is_active", true);
        return body;
    }

    /** ADR-014: plain SQL üzerinden jOOQ — satır erişimi Map ile korunur. */
    private List<Map<String, Object>> list(String sql, Object... args) {
        return dsl.fetch(sql, args).intoMaps();
    }

    private Map<String, Object> map(String sql, Object... args) {
        Record r = dsl.fetchOne(sql, args);
        return r == null ? null : r.intoMap();
    }

    /** ADR-014: plain SQL tek değer — jOOQ dönüşümüyle (fetchValue raw Object döner). */
    private <T> T value(String sql, Class<T> type, Object... args) {
        Record r = dsl.fetchOne(sql, args);
        return r == null ? null : r.get(0, type);
    }

    private static Map<String, Object> panelMap(Map<String, Object> r) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", r.get("id"));
        p.put("name", r.get("name"));
        p.put("description", r.get("description"));
        p.put("prompt_set_id", r.get("prompt_set_id"));
        p.put("schedule_cron", r.get("schedule_cron"));
        p.put("is_active", r.get("is_active"));
        p.put("created_at", r.get("created_at") == null ? null : String.valueOf(r.get("created_at")));
        return p;
    }
}
