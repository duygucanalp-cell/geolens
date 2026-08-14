package dev.geolens.technicalgeo.web;

import dev.geolens.technicalgeo.BotAnalysisResult;
import dev.geolens.technicalgeo.SchemaAnalysisResult;
import dev.geolens.technicalgeo.TechnicalGeoEngine;
import dev.geolens.technicalgeo.TechnicalGeoScore;
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
 * Technical GEO REST controller'ı — Go {@code technicalgeo.handler} portu (FR-B6/B7/E7).
 * <p>Route'lar (go cmd/api): POST /v1/workspaces/{ws}/technical-geo/bots,
 * GET /technical-geo/bots, POST /technical-geo/schema, GET /technical-geo/schema,
 * GET /technical-geo/score.
 * <p>Tenant {@code X-Tenant-ID} başlığından, workspace path'ten gelir; LLM bot
 * erişimi ve Schema.org kullanımı analiz edilir.
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}/technical-geo")
public class TechnicalgeoController {

    private final TechnicalGeoEngine engine;
    private final DSLContext dsl;

    public TechnicalgeoController(TechnicalGeoEngine engine, DSLContext dsl) {
        this.engine = engine;
        this.dsl = dsl;
    }

    // ---------- AnalyzeBots ----------

    @PostMapping("/bots")
    public ResponseEntity<?> analyzeBots(@PathVariable String workspaceId,
                                         @RequestHeader("X-Tenant-ID") String tenantId,
                                         @RequestBody AnalyzeBotsRequest req) {
        if (req.brandId() == null || req.brandId().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id zorunludur");
        }

        BotAnalysisResult result;
        try {
            result = engine.analyzeBotAccess(req.brandId(), req.url(), workspaceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "bot analizi başarısız");
        }

        return ResponseEntity.ok(result);
    }

    // ---------- ListBotAnalyses ----------

    @GetMapping("/bots")
    public ResponseEntity<?> listBotAnalyses(@PathVariable String workspaceId,
                                             @RequestHeader("X-Tenant-ID") String tenantId,
                                             @RequestParam(value = "brand_id", required = false) String brandId) {
        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch("""
                    SELECT ba.id, ba.brand_id, ba.bot_name, ba.url, ba.is_blocked,
                           ba.robots_txt_rule, ba.ges_score, ba.analyzed_at
                    FROM technical.bot_analyses ba
                    JOIN config.brands b ON b.id = ba.brand_id
                    WHERE ba.tenant_id = ? AND b.workspace_id = ?
                        AND (? = '' OR ba.brand_id = ?)
                    ORDER BY ba.analyzed_at DESC
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
            item.put("bot_name", str(r.get("bot_name")));
            item.put("url", str(r.get("url")));
            item.put("is_blocked", r.get("is_blocked") != null && Boolean.TRUE.equals(r.get("is_blocked")));
            item.put("robots_txt_rule", str(r.get("robots_txt_rule")));
            item.put("ges_score", num(r.get("ges_score")));
            item.put("analyzed_at", str(r.get("analyzed_at")));
            results.add(item);
        }
        return ResponseEntity.ok(results);
    }

    // ---------- AnalyzeSchema ----------

    @PostMapping("/schema")
    public ResponseEntity<?> analyzeSchema(@PathVariable String workspaceId,
                                           @RequestHeader("X-Tenant-ID") String tenantId,
                                           @RequestBody AnalyzeSchemaRequest req) {
        if (req.brandId() == null || req.brandId().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id zorunludur");
        }

        SchemaAnalysisResult result;
        try {
            result = engine.analyzeSchema(req.brandId(), workspaceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "schema analizi başarısız");
        }

        return ResponseEntity.ok(result);
    }

    // ---------- ListSchemaAnalyses ----------

    @GetMapping("/schema")
    public ResponseEntity<?> listSchemaAnalyses(@PathVariable String workspaceId,
                                                @RequestHeader("X-Tenant-ID") String tenantId,
                                                @RequestParam(value = "brand_id", required = false) String brandId) {
        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch("""
                    SELECT sa.id, sa.brand_id, sa.schema_type, sa.is_present, sa.schema_score,
                           sa.recommendation, sa.analyzed_at
                    FROM technical.schema_analyses sa
                    JOIN config.brands b ON b.id = sa.brand_id
                    WHERE sa.tenant_id = ? AND b.workspace_id = ?
                        AND (? = '' OR sa.brand_id = ?)
                    ORDER BY sa.analyzed_at DESC
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
            item.put("schema_type", str(r.get("schema_type")));
            item.put("is_present", r.get("is_present") != null && Boolean.TRUE.equals(r.get("is_present")));
            item.put("schema_score", num(r.get("schema_score")));
            item.put("recommendation", str(r.get("recommendation")));
            item.put("analyzed_at", str(r.get("analyzed_at")));
            results.add(item);
        }
        return ResponseEntity.ok(results);
    }

    // ---------- GetTechnicalGEOScore ----------

    @GetMapping("/score")
    public ResponseEntity<?> getTechnicalGeoScore(@PathVariable String workspaceId,
                                                  @RequestHeader("X-Tenant-ID") String tenantId,
                                                  @RequestParam(value = "brand_id", required = false) String brandId) {
        if (brandId == null || brandId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id gerekli");
        }

        TechnicalGeoScore score;
        try {
            score = engine.getScore(brandId, workspaceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "skor alınamadı");
        }

        return ResponseEntity.ok(score);
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
