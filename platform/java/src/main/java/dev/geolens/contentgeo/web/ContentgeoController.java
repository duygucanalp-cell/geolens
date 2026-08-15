package dev.geolens.contentgeo.web;

import dev.geolens.contentgeo.ContentGeoEngine;
import dev.geolens.contentgeo.ContentGapResult;
import dev.geolens.contentgeo.ContentHubScore;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Content GEO REST controller'ı — Go {@code contentgeo.handler} portu (FR-E5, FR-E6).
 * <p>Route'lar (go cmd/api): POST /v1/workspaces/{ws}/content-geo/gap,
 * GET /content-geo/gap, GET /content-geo/hub-score, GET /content-geo/topics.
 * <p>Tenant {@code X-Tenant-ID} başlığından, workspace path'ten gelir.
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}/content-geo")
public class ContentgeoController {

    private final ContentGeoEngine engine;
    private final DSLContext dsl;

    public ContentgeoController(ContentGeoEngine engine, DSLContext dsl) {
        this.engine = engine;
        this.dsl = dsl;
    }

    // ---------- AnalyzeContentGap ----------

    @PostMapping("/gap")
    public ResponseEntity<?> analyzeContentGap(@PathVariable String workspaceId,
                                               @RequestHeader("X-Tenant-ID") String tenantId,
                                               @RequestBody AnalyzeGapRequest req) {
        if (req.brandId() == null || req.brandId().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id zorunludur");
        }

        List<ContentGapResult> result;
        try {
            result = engine.analyzeContentGap(req.brandId(), workspaceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "content gap analizi başarısız");
        }

        return ResponseEntity.ok(result);
    }

    // ---------- ListContentGaps ----------

    @GetMapping("/gap")
    public ResponseEntity<?> listContentGaps(@PathVariable String workspaceId,
                                             @RequestHeader("X-Tenant-ID") String tenantId,
                                             @RequestParam(value = "brand_id", required = false) String brandId) {
        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch("""
                    SELECT cg.id, cg.brand_id, cg.gap_type, cg.gap_score, cg.description,
                           cg.recommendation, cg.priority, cg.analyzed_at
                    FROM content.gap_analyses cg
                    JOIN config.brands b ON b.id = cg.brand_id
                    WHERE cg.tenant_id = ? AND b.workspace_id = ?
                        AND (? = '' OR cg.brand_id = ?)
                    ORDER BY cg.analyzed_at DESC
                    LIMIT 50
                    """, tenantId, workspaceId, nz(brandId), nz(brandId)).intoMaps();
        } catch (RuntimeException e) {
            return ResponseEntity.ok(List.of());
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", str(r.get("id")));
            item.put("brand_id", str(r.get("brand_id")));
            item.put("gap_type", str(r.get("gap_type")));
            item.put("gap_score", num(r.get("gap_score")));
            item.put("description", str(r.get("description")));
            item.put("recommendation", str(r.get("recommendation")));
            item.put("priority", str(r.get("priority")));
            item.put("analyzed_at", str(r.get("analyzed_at")));
            results.add(item);
        }
        return ResponseEntity.ok(results);
    }

    // ---------- GetContentHubScore ----------

    @GetMapping("/hub-score")
    public ResponseEntity<?> getContentHubScore(@PathVariable String workspaceId,
                                                @RequestHeader("X-Tenant-ID") String tenantId,
                                                @RequestParam(value = "brand_id", required = false) String brandId) {
        if (brandId == null || brandId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id gerekli");
        }

        ContentHubScore result;
        try {
            result = engine.getContentHubScore(brandId, workspaceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "skor alınamadı");
        }

        return ResponseEntity.ok(result);
    }

    // ---------- ListTopicClusters ----------

    @GetMapping("/topics")
    public ResponseEntity<?> listTopicClusters(@PathVariable String workspaceId,
                                               @RequestHeader("X-Tenant-ID") String tenantId,
                                               @RequestParam(value = "brand_id", required = false) String brandId) {
        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch("""
                    SELECT tc.id, tc.brand_id, tc.topic_name, tc.opportunity_score,
                           tc.relevance, tc.recommendation, tc.created_at
                    FROM content.topic_clusters tc
                    JOIN config.brands b ON b.id = tc.brand_id
                    WHERE tc.tenant_id = ? AND b.workspace_id = ?
                        AND (? = '' OR tc.brand_id = ?)
                    ORDER BY tc.opportunity_score DESC
                    LIMIT 50
                    """, tenantId, workspaceId, nz(brandId), nz(brandId)).intoMaps();
        } catch (RuntimeException e) {
            return ResponseEntity.ok(List.of());
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", str(r.get("id")));
            item.put("brand_id", str(r.get("brand_id")));
            item.put("topic_name", str(r.get("topic_name")));
            item.put("opportunity_score", num(r.get("opportunity_score")));
            item.put("relevance", str(r.get("relevance")));
            item.put("recommendation", str(r.get("recommendation")));
            item.put("created_at", str(r.get("created_at")));
            results.add(item);
        }
        return ResponseEntity.ok(results);
    }

    // ---------- yardımcılar ----------

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static double num(Object o) {
        return o == null ? 0 : ((Number) o).doubleValue();
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
