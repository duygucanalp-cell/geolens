package dev.geolens.audit.web;

import dev.geolens.audit.AuditResult;
import dev.geolens.audit.AuditService;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jooq.Field;
import org.jooq.JSON;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import static dev.geolens.jooq.config.tables.Brands.BRANDS;
import static dev.geolens.jooq.governance.tables.AuditLog.AUDIT_LOG;
import static dev.geolens.jooq.governance.tables.AuditResults.AUDIT_RESULTS;

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
    private final DSLContext dsl;

    public AuditController(AuditService service, DSLContext dsl) {
        this.service = service;
        this.dsl = dsl;
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
                Record brandRec = dsl.select(List.of(BRANDS.NAME))
                        .from(BRANDS)
                        .where(BRANDS.ID.eq(req.brandId()).and(BRANDS.WORKSPACE_ID.eq(workspaceId))
                                .and(BRANDS.TENANT_ID.eq(tenantId)).and(BRANDS.IS_ACTIVE.eq(true)))
                        .fetchOne();
                Map<String, Object> row = brandRec == null ? null : brandRec.intoMap();
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
            Record rec = dsl.select(List.of(
                            DSL.coalesce(jsonText(AUDIT_RESULTS.ROBOTS_TXT), DSL.inline("{}")).as("robots_txt"),
                            DSL.coalesce(jsonText(AUDIT_RESULTS.BOT_ACCESS), DSL.inline("{}")).as("bot_access"),
                            DSL.coalesce(jsonText(AUDIT_RESULTS.SSR), DSL.inline("{}")).as("ssr"),
                            DSL.coalesce(jsonText(AUDIT_RESULTS.SSRF), DSL.inline("{}")).as("ssrf"),
                            DSL.coalesce(jsonText(AUDIT_RESULTS.ISSUES), DSL.inline("[]")).as("issues"),
                            AUDIT_RESULTS.OVERALL_SCORE))
                    .from(AUDIT_RESULTS)
                    .where(AUDIT_RESULTS.BRAND_ID.eq(brandId).and(AUDIT_RESULTS.WORKSPACE_ID.eq(workspaceId))
                            .and(AUDIT_RESULTS.TENANT_ID.eq(tenantId)))
                    .orderBy(AUDIT_RESULTS.CREATED_AT.desc())
                    .limit(1)
                    .fetchOne();
            row = rec == null ? null : rec.intoMap();
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
            rows = dsl.select(List.of(
                            AUDIT_LOG.ID,
                            DSL.coalesce(AUDIT_LOG.USER_ID, "").as("user_id"),
                            AUDIT_LOG.EVENT_TYPE,
                            AUDIT_LOG.RESOURCE_TYPE,
                            DSL.coalesce(AUDIT_LOG.RESOURCE_ID, "").as("resource_id"),
                            AUDIT_LOG.ACTION,
                            DSL.coalesce(jsonText(AUDIT_LOG.METADATA), DSL.inline("{}")).as("metadata"),
                            DSL.coalesce(AUDIT_LOG.IP_ADDRESS, "").as("ip_address"),
                            AUDIT_LOG.CREATED_AT))
                    .from(AUDIT_LOG)
                    .where(AUDIT_LOG.TENANT_ID.eq(tenantId)
                            .and(evt.isEmpty() ? DSL.noCondition() : AUDIT_LOG.EVENT_TYPE.eq(evt))
                            .and(res.isEmpty() ? DSL.noCondition() : AUDIT_LOG.RESOURCE_TYPE.eq(res)))
                    .orderBy(AUDIT_LOG.CREATED_AT.desc())
                    .limit(limit + 1)
                    .fetch().intoMaps();
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
            rows = dsl.select(List.of(
                            DSL.coalesce(AUDIT_LOG.USER_ID, "system").as("user_id"),
                            AUDIT_LOG.EVENT_TYPE,
                            AUDIT_LOG.RESOURCE_TYPE,
                            DSL.coalesce(AUDIT_LOG.RESOURCE_ID, "").as("resource_id"),
                            AUDIT_LOG.ACTION,
                            DSL.coalesce(AUDIT_LOG.IP_ADDRESS, "").as("ip_address"),
                            AUDIT_LOG.CREATED_AT))
                    .from(AUDIT_LOG)
                    .where(AUDIT_LOG.TENANT_ID.eq(tenantId))
                    .orderBy(AUDIT_LOG.CREATED_AT.desc())
                    .limit(1000)
                    .fetch().intoMaps();
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

    /** JSONB kolonu {@code ::text} cast'iyle okur (orijinal SQL davranışı). */
    private static Field<String> jsonText(Field<JSON> col) {
        return DSL.field("{0}::text", String.class, col);
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
