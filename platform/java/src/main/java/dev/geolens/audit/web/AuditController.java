package dev.geolens.audit.web;

import dev.geolens.audit.AuditResult;
import dev.geolens.audit.AuditService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Site denetimi REST controller'ı — Go {@code audit.handler} portu.
 * <p>Route'lar (go cmd/api): POST /v1/workspaces/{ws}/audit,
 * GET /v1/workspaces/{ws}/audit/findings, GET /v1/admin/audit-trail,
 * GET /v1/admin/audit-trail/export (T3).
 * <p>Tenant {@code X-Tenant-ID} başlığından, workspace URL path'ten gelir.
 */
@RestController
public class AuditController {

    private final AuditService service;
    private final JdbcTemplate jdbc;

    public AuditController(AuditService service, JdbcTemplate jdbc) {
        this.service = service;
        this.jdbc = jdbc;
    }

    @PostMapping("/v1/workspaces/{workspaceId}/audit")
    public ResponseEntity<?> runAudit(@PathVariable String workspaceId,
                                      @RequestHeader("X-Tenant-ID") String tenantId,
                                      @RequestBody AuditRequest req) {
        if (req == null || req.brandId() == null || req.brandId().isBlank()
                || req.websiteUrl() == null || req.websiteUrl().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id ve website_url zorunludur");
        }

        String brandName = req.brandName();
        if (brandName == null || brandName.isBlank()) {
            try {
                Map<String, Object> row = jdbc.queryForMap("""
                        SELECT name FROM config.brands
                        WHERE id = ? AND workspace_id = ? AND tenant_id = ? AND is_active = true
                        """, req.brandId(), workspaceId, tenantId);
                brandName = String.valueOf(row.get("name"));
            } catch (RuntimeException e) {
                return error(HttpStatus.NOT_FOUND, "marka bulunamadı");
            }
        }

        AuditResult result;
        try {
            result = service.audit(req.brandId(), brandName, req.websiteUrl());
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "denetim başarısız");
        }
        result = result.withContext(workspaceId, tenantId);

        try {
            service.save(result);
        } catch (RuntimeException ignored) {
            // kaydetme başarısız olsa bile sonuç döndürülür (Go ile aynı)
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/v1/workspaces/{workspaceId}/audit/findings")
    public ResponseEntity<?> getFindingsCatalog(@PathVariable String workspaceId,
                                                @RequestHeader("X-Tenant-ID") String tenantId,
                                                @RequestParam(value = "brand_id", required = false) String brandId) {
        if (brandId == null || brandId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id gerekli");
        }
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("""
                    SELECT COALESCE(robots_txt::text, '{}') AS robots_txt,
                           COALESCE(bot_access::text, '{}') AS bot_access,
                           COALESCE(ssr::text, '{}') AS ssr,
                           COALESCE(ssrf::text, '{}') AS ssrf,
                           COALESCE(issues::text, '[]') AS issues,
                           overall_score
                    FROM governance.audit_results
                    WHERE brand_id = ? AND workspace_id = ? AND tenant_id = ?
                    ORDER BY created_at DESC LIMIT 1
                    """, brandId, workspaceId, tenantId);
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of(
                    "brand_id", brandId,
                    "catalog", List.of(),
                    "summary", Map.of("total", 0, "critical", 0, "high", 0, "medium", 0, "low", 0)));
        }

        List<IssueRow> issues = parseIssues(String.valueOf(row.get("issues")));

        Map<String, Object> categorized = new LinkedHashMap<>();
        categorized.put("robots_txt", new ArrayList<Map<String, Object>>());
        categorized.put("bot_access", new ArrayList<Map<String, Object>>());
        categorized.put("ssr", new ArrayList<Map<String, Object>>());
        categorized.put("ssrf", new ArrayList<Map<String, Object>>());

        for (IssueRow iss : issues) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", iss.title());
            item.put("detail", iss.detail());
            item.put("severity", iss.severity());
            item.put("recommendation", iss.recommendation());
            List<Map<String, Object>> target = categorized.containsKey(iss.category())
                    ? (List<Map<String, Object>>) categorized.get(iss.category())
                    : (List<Map<String, Object>>) categorized.get("ssr");
            target.add(item);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", 0);
        summary.put("critical", 0);
        summary.put("high", 0);
        summary.put("medium", 0);
        summary.put("low", 0);
        for (IssueRow iss : issues) {
            summary.put("total", ((Number) summary.get("total")).intValue() + 1);
            if (summary.containsKey(iss.severity())) {
                summary.put(iss.severity(), ((Number) summary.get(iss.severity())).intValue() + 1);
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("brand_id", brandId);
        body.put("overall_score", row.get("overall_score"));
        body.put("summary", summary);
        body.put("catalog", categorized);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/v1/admin/audit-trail")
    public ResponseEntity<?> listAuditTrail(@RequestHeader("X-Tenant-ID") String tenantId,
                                            @RequestParam(value = "event_type", required = false) String eventType,
                                            @RequestParam(value = "resource_type", required = false) String resourceType) {
        String evt = eventType == null ? "" : eventType;
        String res = resourceType == null ? "" : resourceType;
        int limit = 100;
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("""
                    SELECT id, COALESCE(user_id, '') AS user_id, event_type, resource_type,
                           COALESCE(resource_id, '') AS resource_id, action,
                           COALESCE(metadata::text, '{}') AS metadata,
                           COALESCE(ip_address, '') AS ip_address, created_at
                    FROM governance.audit_log
                    WHERE tenant_id = ?
                        AND (? = '' OR event_type = ?)
                        AND (? = '' OR resource_type = ?)
                    ORDER BY created_at DESC
                    LIMIT ?
                    """, tenantId, evt, evt, res, res, limit + 1);
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("entries", List.of(), "has_more", false, "count", 0));
        }

        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, limit));
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", r.get("id"));
            e.put("user_id", r.get("user_id"));
            e.put("event_type", r.get("event_type"));
            e.put("resource_type", r.get("resource_type"));
            e.put("resource_id", r.get("resource_id"));
            e.put("action", r.get("action"));
            e.put("metadata", r.get("metadata"));
            e.put("ip_address", r.get("ip_address"));
            e.put("created_at", r.get("created_at") == null ? null : String.valueOf(r.get("created_at")));
            entries.add(e);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entries", entries);
        body.put("has_more", hasMore);
        body.put("count", entries.size());
        return ResponseEntity.ok(body);
    }

    @GetMapping(value = "/v1/admin/audit-trail/export", produces = "text/csv")
    public ResponseEntity<String> exportAuditTrail(@RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("""
                    SELECT COALESCE(user_id, 'system') AS user_id, event_type, resource_type,
                           COALESCE(resource_id, '') AS resource_id, action,
                           COALESCE(ip_address, '') AS ip_address, created_at
                    FROM governance.audit_log
                    WHERE tenant_id = ?
                    ORDER BY created_at DESC
                    LIMIT 1000
                    """, tenantId);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"dışa aktarılamadı\"}");
        }
        StringBuilder csv = new StringBuilder("user_id,event_type,resource_type,resource_id,action,ip_address,created_at\n");
        for (Map<String, Object> r : rows) {
            csv.append(String.valueOf(r.get("user_id"))).append(',')
                    .append(String.valueOf(r.get("event_type"))).append(',')
                    .append(String.valueOf(r.get("resource_type"))).append(',')
                    .append(String.valueOf(r.get("resource_id"))).append(',')
                    .append(String.valueOf(r.get("action"))).append(',')
                    .append(String.valueOf(r.get("ip_address"))).append(',')
                    .append(String.valueOf(r.get("created_at"))).append('\n');
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"audit-trail.csv\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv"))
                .body(csv.toString());
    }

    private List<IssueRow> parseIssues(String json) {
        List<IssueRow> issues = new ArrayList<>();
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return issues;
        }
        try {
            List<Map<String, Object>> raw = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {
                    });
            for (Map<String, Object> r : raw) {
                issues.add(new IssueRow(
                        str(r.get("severity")),
                        str(r.get("category")),
                        str(r.get("title")),
                        str(r.get("detail")),
                        str(r.get("recommendation"))));
            }
        } catch (Exception ignored) {
            // JSON çözümleme hatasında boş katalog döner (Go warn + devam)
        }
        return issues;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }

    record IssueRow(String severity, String category, String title, String detail, String recommendation) {
    }
}
