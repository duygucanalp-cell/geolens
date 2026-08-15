package dev.geolens.audit.service;

import dev.geolens.common.ServiceException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.audit.AuditResult;
import dev.geolens.audit.AuditService;
import dev.geolens.audit.web.AuditRequest;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Site denetimi web iş mantığı — Go {@code audit.handler} portu.
 * <p>Controller yalnızca HTTP katmanıdır; DB erişimi (DSLContext), denetim motoru
 * ({@link AuditService}) çağrısı ve doğrulama bu sınıfta toplanır.
 * <p>Kurumsal denetim servisinden ayrı olduğu için bean adı {@code auditWebService} —
 * mevcut domain {@code auditService} bean'iyle çakışmaz.
 */
@Service("auditWebService")
public class AuditWebService {

    private final AuditService service;
    private final DSLContext dsl;

    public AuditWebService(AuditService service, DSLContext dsl) {
        this.service = service;
        this.dsl = dsl;
    }

    /** Denetim başlatır: marka adını DB'den çözer, motoru çalıştırır, sonucu kaydeder. */
    public AuditResult runAudit(String workspaceId, String tenantId, AuditRequest req) {
        String brandName = req.brandName();
        if (brandName == null || brandName.isBlank()) {
            Map<String, Object> row;
            try {
                row = map("""
                        SELECT name FROM config.brands
                        WHERE id = ? AND workspace_id = ? AND tenant_id = ? AND is_active = true
                        """, req.brandId(), workspaceId, tenantId);
                if (row == null) {
                    throw new ServiceException(HttpStatus.NOT_FOUND, "marka bulunamadı");
                }
                brandName = String.valueOf(row.get("name"));
            } catch (ServiceException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new ServiceException(HttpStatus.NOT_FOUND, "marka bulunamadı");
            }
        }

        AuditResult result;
        try {
            result = service.audit(req.brandId(), brandName, req.websiteUrl());
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "denetim başarısız");
        }
        result = result.withContext(workspaceId, tenantId);

        try {
            service.save(result);
        } catch (RuntimeException ignored) {
            // kaydetme başarısız olsa bile sonuç döndürülür (Go ile aynı)
        }
        return result;
    }

    /** Bulgu kataloğu — sorgu hatasında boş katalog döner. */
    public Map<String, Object> getFindingsCatalog(String workspaceId, String tenantId, String brandId) {
        Map<String, Object> row;
        try {
            row = map("""
                    SELECT COALESCE(robots_txt::text, '{}') AS robots_txt,
                           COALESCE(bot_access::text, '{}') AS bot_access,
                           COALESCE(ssr::text, '{}') AS ssr,
                           COALESCE(ssrf::text, '{}') AS ssrf,
                           COALESCE(issues::text, '[]') AS issues,
                           overall_score
                    FROM governance.audit_results
                    WHERE brand_id = ? AND workspace_id = ? AND tenant_id = ?
                    ORDER BY created_at DESC
                    LIMIT 1
                    """, brandId, workspaceId, tenantId);
        } catch (RuntimeException e) {
            return Map.of(
                    "brand_id", brandId,
                    "catalog", List.of(),
                    "summary", Map.of("total", 0, "critical", 0, "high", 0, "medium", 0, "low", 0));
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
        return body;
    }

    /** Denetim iz kayıtları — sorgu hatasında boş liste döner. */
    public Map<String, Object> listAuditTrail(String tenantId, String eventType, String resourceType) {
        String evt = eventType == null ? "" : eventType;
        String res = resourceType == null ? "" : resourceType;
        int limit = 100;
        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT id,
                           COALESCE(user_id, '') AS user_id,
                           event_type,
                           resource_type,
                           COALESCE(resource_id, '') AS resource_id,
                           action,
                           COALESCE(metadata::text, '{}') AS metadata,
                           COALESCE(ip_address, '') AS ip_address,
                           created_at
                    FROM governance.audit_log
                    WHERE tenant_id = ?
                        AND (? = '' OR event_type = ?)
                        AND (? = '' OR resource_type = ?)
                    ORDER BY created_at DESC
                    LIMIT ?
                    """, tenantId, evt, evt, res, res, limit + 1);
        } catch (RuntimeException e) {
            return Map.of("entries", List.of(), "has_more", false, "count", 0);
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
        return body;
    }

    /** Denetim iz kayıtlarını CSV olarak dışa aktarır — sorgu hatasında 500 fırlatır. */
    public String exportAuditTrail(String tenantId) {
        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT COALESCE(user_id, 'system') AS user_id,
                           event_type,
                           resource_type,
                           COALESCE(resource_id, '') AS resource_id,
                           action,
                           COALESCE(ip_address, '') AS ip_address,
                           created_at
                    FROM governance.audit_log
                    WHERE tenant_id = ?
                    ORDER BY created_at DESC
                    LIMIT 1000
                    """, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "dışa aktarılamadı");
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
        return csv.toString();
    }

    // ---------- yardımcılar ----------

    private List<IssueRow> parseIssues(String json) {
        List<IssueRow> issues = new ArrayList<>();
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return issues;
        }
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(json, new TypeReference<>() {
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

    private static final ObjectMapper MAPPER = new ObjectMapper();

    record IssueRow(String severity, String category, String title, String detail, String recommendation) {
    }
}
