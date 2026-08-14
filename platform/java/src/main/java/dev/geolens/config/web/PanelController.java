package dev.geolens.config.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Panel ve prompt seti yönetimi REST controller'ı — Go {@code config.panel} portu.
 * <p>Route'lar (go cmd/api): GET/POST /v1/workspaces/{ws}/panels,
 * GET /v1/workspaces/{ws}/panels/{panelId}, GET/POST /v1/workspaces/{ws}/prompt-sets.
 * <p>Tenant {@code X-Tenant-ID} başlığından, workspace URL path'ten gelir.
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}")
public class PanelController {

    private final JdbcTemplate jdbc;

    public PanelController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/panels")
    public ResponseEntity<?> listPanels(@PathVariable String workspaceId,
                                        @RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("""
                    SELECT p.id, p.name, COALESCE(p.description, '') AS description,
                           COALESCE(p.prompt_set_id, '') AS prompt_set_id,
                           COALESCE(p.schedule_cron, '') AS schedule_cron,
                           p.is_active, p.created_at
                    FROM config.panels p
                    WHERE p.workspace_id = ? AND p.tenant_id = ?
                    ORDER BY p.name
                    """, workspaceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "sorgu hatası");
        }
        List<Map<String, Object>> panels = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            panels.add(panelMap(r));
        }
        return ResponseEntity.ok(panels);
    }

    @PostMapping("/panels")
    public ResponseEntity<?> createPanel(@PathVariable String workspaceId,
                                         @RequestHeader("X-Tenant-ID") String tenantId,
                                         @RequestBody PanelRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "panel adı zorunludur");
        }
        String desc = req.description() == null ? "" : req.description();
        String promptSetId = req.promptSetId() == null ? "" : req.promptSetId();
        String scheduleCron = req.scheduleCron() == null ? "" : req.scheduleCron();

        String panelId;
        try {
            panelId = jdbc.queryForObject("""
                    INSERT INTO config.panels (id, workspace_id, tenant_id, name, description, prompt_set_id, schedule_cron)
                    VALUES (gen_random_uuid()::text, ?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """, String.class, workspaceId, tenantId, req.name(), desc, promptSetId, scheduleCron);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "panel oluşturulamadı");
        }

        if (req.brandIds() != null) {
            for (String brandId : req.brandIds()) {
                try {
                    jdbc.update("""
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
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/panels/{panelId}")
    public ResponseEntity<?> getPanel(@PathVariable String workspaceId,
                                      @RequestHeader("X-Tenant-ID") String tenantId,
                                      @PathVariable String panelId) {
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("""
                    SELECT id, name, COALESCE(description, '') AS description,
                           COALESCE(prompt_set_id, '') AS prompt_set_id,
                           COALESCE(schedule_cron, '') AS schedule_cron,
                           is_active, created_at
                    FROM config.panels
                    WHERE id = ? AND workspace_id = ? AND tenant_id = ?
                    """, panelId, workspaceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.NOT_FOUND, "panel bulunamadı");
        }
        return ResponseEntity.ok(panelMap(row));
    }

    @GetMapping("/prompt-sets")
    public ResponseEntity<?> listPromptSets(@PathVariable String workspaceId,
                                            @RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("""
                    SELECT id, name, COALESCE(description, '') AS description, prompt_text, is_active
                    FROM config.prompt_sets
                    WHERE workspace_id = ? AND tenant_id = ?
                    ORDER BY name
                    """, workspaceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "sorgu hatası");
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
        return ResponseEntity.ok(sets);
    }

    @PostMapping("/prompt-sets")
    public ResponseEntity<?> createPromptSet(@PathVariable String workspaceId,
                                             @RequestHeader("X-Tenant-ID") String tenantId,
                                             @RequestBody PromptSetRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()
                || req.promptText() == null || req.promptText().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "ad ve prompt metni zorunludur");
        }
        String desc = req.description() == null ? "" : req.description();

        String setId;
        try {
            setId = jdbc.queryForObject("""
                    INSERT INTO config.prompt_sets (id, workspace_id, tenant_id, name, description, prompt_text)
                    VALUES (gen_random_uuid()::text, ?, ?, ?, ?, ?)
                    RETURNING id
                    """, String.class, workspaceId, tenantId, req.name(), desc, req.promptText());
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "prompt set oluşturulamadı");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", setId);
        body.put("name", req.name());
        body.put("description", desc);
        body.put("prompt_text", req.promptText());
        body.put("is_active", true);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
