package dev.geolens.competitive.web;

import dev.geolens.competitive.CompetitiveEngine;
import dev.geolens.competitive.GapDetail;
import dev.geolens.competitive.GapSnapshot;
import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Competitive Gap Analysis REST controller'ı — Go {@code competitive.handler} portu (FR-D11).
 * <p>Route'lar (go cmd/api): POST /v1/workspaces/{ws}/competitive-gap/analyze,
 * GET /competitive-gap/overview, GET /competitive-gap/visibility,
 * GET /competitive-gap/recommendations.
 * <p>Tenant {@code X-Tenant-ID} başlığından, workspace path'ten gelir.
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}/competitive-gap")
public class CompetitiveController {

    private final CompetitiveEngine engine;
    private final DSLContext dsl;

    public CompetitiveController(CompetitiveEngine engine, DSLContext dsl) {
        this.engine = engine;
        this.dsl = dsl;
    }

    // ---------- AnalyzeGap ----------

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeGap(@PathVariable String workspaceId,
                                        @RequestHeader("X-Tenant-ID") String tenantId,
                                        @RequestBody AnalyzeGapRequest req) {
        if (req.brandId() == null || req.brandId().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id zorunludur");
        }

        List<GapSnapshot> result;
        try {
            result = engine.analyzeAllGaps(req.brandId(), workspaceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "gap analizi başarısız");
        }

        return ResponseEntity.ok(result);
    }

    // ---------- GetOverview ----------

    @GetMapping("/overview")
    public ResponseEntity<?> getOverview(@PathVariable String workspaceId,
                                         @RequestHeader("X-Tenant-ID") String tenantId,
                                         @RequestParam(value = "brand_id", required = false) String brandId) {
        if (brandId == null || brandId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id gerekli");
        }

        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch("""
                    SELECT gs.id, gs.competitor_id, b2.name AS competitor_name,
                           gs.visibility_gap, gs.citation_gap, gs.content_gap, gs.topic_gap, gs.prompt_gap,
                           gs.competitive_score, gs.period_start, gs.period_end, gs.created_at
                    FROM competitive.gap_snapshots gs
                    JOIN config.brands b ON b.id = gs.brand_id
                    JOIN config.brands b2 ON b2.id = gs.competitor_id
                    WHERE gs.tenant_id = ? AND b.workspace_id = ? AND gs.brand_id = ?
                    ORDER BY gs.created_at DESC
                    LIMIT 20
                    """, tenantId, workspaceId, brandId).intoMaps();
        } catch (RuntimeException e) {
            return ResponseEntity.ok(List.of());
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", str(r.get("id")));
            item.put("competitor_id", str(r.get("competitor_id")));
            item.put("competitor_name", str(r.get("competitor_name")));
            putGap(item, "visibility_gap", r.get("visibility_gap"));
            putGap(item, "citation_gap", r.get("citation_gap"));
            putGap(item, "content_gap", r.get("content_gap"));
            putGap(item, "topic_gap", r.get("topic_gap"));
            putGap(item, "prompt_gap", r.get("prompt_gap"));
            item.put("competitive_score", num(r.get("competitive_score")));
            item.put("period_start", dateStr(r.get("period_start")));
            item.put("period_end", dateStr(r.get("period_end")));
            item.put("created_at", str(r.get("created_at")));
            results.add(item);
        }
        return ResponseEntity.ok(results);
    }

    // ---------- GetVisibilityGap ----------

    @GetMapping("/visibility")
    public ResponseEntity<?> getVisibilityGap(@PathVariable String workspaceId,
                                              @RequestHeader("X-Tenant-ID") String tenantId,
                                              @RequestParam(value = "brand_id", required = false) String brandId,
                                              @RequestParam(value = "competitor_id", required = false) String competitorId) {
        if (brandId == null || brandId.isBlank() || competitorId == null || competitorId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id ve competitor_id gerekli");
        }

        GapDetail result;
        try {
            result = engine.getGapDetail(brandId, competitorId, "visibility", tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "gap bilgisi alınamadı");
        }

        return ResponseEntity.ok(result);
    }

    // ---------- GetRecommendations ----------

    @GetMapping("/recommendations")
    public ResponseEntity<?> getRecommendations(@PathVariable String workspaceId,
                                                @RequestHeader("X-Tenant-ID") String tenantId,
                                                @RequestParam(value = "brand_id", required = false) String brandId) {
        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch("""
                    SELECT gr.id, gr.gap_type, gr.priority, gr.description, gr.impact, gr.kanit_derecesi
                    FROM competitive.gap_recommendations gr
                    JOIN competitive.gap_snapshots gs ON gs.id = gr.gap_id
                    JOIN config.brands b ON b.id = gs.brand_id
                    WHERE gr.tenant_id = ? AND b.workspace_id = ?
                        AND (? = '' OR gs.brand_id = ?)
                    ORDER BY
                        CASE gr.priority
                            WHEN 'critical' THEN 1
                            WHEN 'high' THEN 2
                            WHEN 'medium' THEN 3
                            WHEN 'low' THEN 4
                        END
                    """, tenantId, workspaceId, nz(brandId), nz(brandId)).intoMaps();
        } catch (RuntimeException e) {
            return ResponseEntity.ok(List.of());
        }

        List<Map<String, Object>> recs = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", str(r.get("id")));
            item.put("gap_type", str(r.get("gap_type")));
            item.put("priority", str(r.get("priority")));
            item.put("description", str(r.get("description")));
            item.put("impact", str(r.get("impact")));
            item.put("kanit_derecesi", str(r.get("kanit_derecesi")));
            recs.add(item);
        }
        return ResponseEntity.ok(recs);
    }

    // ---------- yardımcılar ----------

    /** Go gapRow {@code omitempty} — null gap JSON'da atlanır. */
    private static void putGap(Map<String, Object> item, String key, Object v) {
        if (v != null) {
            item.put(key, num(v));
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static double num(Object o) {
        return o == null ? 0 : ((Number) o).doubleValue();
    }

    private static String dateStr(Object o) {
        if (o == null) {
            return "";
        }
        if (o instanceof LocalDate ld) {
            return ld.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        if (o instanceof Date d) {
            return d.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        return String.valueOf(o);
    }

    private static String str(Object o) {
        if (o == null) {
            return "";
        }
        if (o instanceof java.sql.Timestamp t) {
            return t.toInstant().toString();
        }
        return String.valueOf(o);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
