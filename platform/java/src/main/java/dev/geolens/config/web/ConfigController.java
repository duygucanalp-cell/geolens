package dev.geolens.config.web;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
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
 * Marka ve rakip yapılandırması REST controller'ı — Go {@code config.handler} portu.
 * <p>Route'lar (go cmd/api): GET/POST /v1/workspaces/{ws}/brands,
 * GET /v1/workspaces/{ws}/brands/search, PUT/DELETE /v1/workspaces/{ws}/brands/{brandId},
 * GET/PUT /v1/workspaces/{ws}/brands/{brandId}/competitors,
 * DELETE /v1/workspaces/{ws}/brands/{brandId}/competitors/{competitorId},
 * GET /v1/workspaces/{ws}/setup-status, GET /v1/tenant/panorama (H5).
 * <p>Tenant {@code X-Tenant-ID} başlığından, workspace URL path'ten gelir (httpmw karşılığı).
 */
@RestController
public class ConfigController {

    private final DSLContext dsl;
    private final TransactionTemplate tx;

    public ConfigController(DSLContext dsl, TransactionTemplate tx) {
        this.dsl = dsl;
        this.tx = tx;
    }

    @GetMapping("/v1/workspaces/{workspaceId}/brands/search")
    public ResponseEntity<?> searchBrands(@PathVariable String workspaceId,
                                          @RequestHeader("X-Tenant-ID") String tenantId,
                                          @RequestParam(value = "q", required = false) String query,
                                          @RequestParam(value = "exclude", required = false) String exclude,
                                          @RequestParam(value = "offset", required = false) String offsetParam,
                                          @RequestParam(value = "limit", required = false) String limitParam) {
        String q = query == null ? "" : query;
        if (q.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "q parametresi gerekli");
        }
        String excl = exclude == null ? "" : exclude;

        int offset = 0;
        if (offsetParam != null && !offsetParam.isBlank()) {
            try {
                int n = Integer.parseInt(offsetParam);
                if (n >= 0) {
                    offset = n;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        int limit = 20;
        if (limitParam != null && !limitParam.isBlank()) {
            try {
                int n = Integer.parseInt(limitParam);
                if (n > 0) {
                    limit = Math.min(n, 100);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        int total;
        try {
            Integer t = value("""
                    SELECT count(*)
                    FROM config.brands
                    WHERE workspace_id = ? AND tenant_id = ? AND is_active = true
                        AND (name ILIKE '%' || ? || '%' OR id ILIKE '%' || ? || '%')
                        AND (? = '' OR id != ?)
                    """, Integer.class, workspaceId, tenantId, q, q, excl, excl);
            total = t == null ? 0 : t;
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "sorgu hatası");
        }

        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT id, name, website_url
                    FROM config.brands
                    WHERE workspace_id = ? AND tenant_id = ? AND is_active = true
                        AND (name ILIKE '%' || ? || '%' OR id ILIKE '%' || ? || '%')
                        AND (? = '' OR id != ?)
                    ORDER BY name
                    LIMIT ? OFFSET ?
                    """, workspaceId, tenantId, q, q, excl, excl, limit, offset);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "sorgu hatası");
        }

        List<Map<String, Object>> brands = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            brands.add(brandMap(r));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", brands);
        body.put("total", total);
        body.put("offset", offset);
        body.put("limit", limit);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/v1/workspaces/{workspaceId}/brands")
    public ResponseEntity<?> listBrands(@PathVariable String workspaceId,
                                        @RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT id, name, website_url
                    FROM config.brands
                    WHERE workspace_id = ? AND tenant_id = ? AND is_active = true
                    ORDER BY name
                    """, workspaceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "sorgu hatası");
        }
        List<Map<String, Object>> brands = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            brands.add(brandMap(r));
        }
        return ResponseEntity.ok(brands);
    }

    @PostMapping("/v1/workspaces/{workspaceId}/brands")
    public ResponseEntity<?> createBrand(@PathVariable String workspaceId,
                                         @RequestHeader("X-Tenant-ID") String tenantId,
                                         @RequestBody BrandRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()
                || req.websiteUrl() == null || req.websiteUrl().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "marka adı ve web sitesi zorunludur");
        }

        String[] result = txExecute(() -> {
            if (req.competitors() != null) {
                for (String compId : req.competitors()) {
                    if (compId == null || compId.isBlank()) {
                        continue;
                    }
                    Boolean exists = value("""
                            SELECT EXISTS(SELECT 1 FROM config.brands
                                WHERE id = ? AND tenant_id = ? AND is_active = true)
                            """, Boolean.class, compId, tenantId);
                    if (Boolean.FALSE.equals(exists)) {
                        throw new ConfigHttpException(HttpStatus.BAD_REQUEST, "rakip bulunamadı: " + compId);
                    }
                }
            }

            String brandId = value("""
                    INSERT INTO config.brands (id, workspace_id, tenant_id, name, website_url)
                    VALUES (gen_random_uuid()::text, ?, ?, ?, ?)
                    RETURNING id
                    """, String.class, workspaceId, tenantId, req.name(), req.websiteUrl());

            if (req.competitors() != null) {
                for (String compId : req.competitors()) {
                    if (compId == null || compId.isBlank() || compId.equals(brandId)) {
                        continue;
                    }
                    dsl.execute("""
                            INSERT INTO config.brand_competitors (id, brand_id, competitor_id, tenant_id)
                            VALUES (gen_random_uuid()::text, ?, ?, ?)
                            ON CONFLICT (brand_id, competitor_id) DO NOTHING
                            """, brandId, compId, tenantId);
                }
            }
            return new String[]{brandId};
        });

        return ResponseEntity.status(HttpStatus.CREATED).body(new BrandResponse(
                result[0], req.name(), req.websiteUrl()));
    }

    @PutMapping("/v1/workspaces/{workspaceId}/brands/{brandId}")
    public ResponseEntity<?> updateBrand(@PathVariable String workspaceId,
                                         @RequestHeader("X-Tenant-ID") String tenantId,
                                         @PathVariable String brandId,
                                         @RequestBody UpdateBrandRequest req) {
        if (brandId == null || brandId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id gerekli");
        }
        if (req == null || (req.name() == null || req.name().isBlank())
                && (req.websiteUrl() == null || req.websiteUrl().isBlank())) {
            return error(HttpStatus.BAD_REQUEST, "en az bir alan gerekli (name veya website_url)");
        }
        String name = req.name() == null ? "" : req.name();
        String websiteUrl = req.websiteUrl() == null ? "" : req.websiteUrl();

        int affected;
        try {
            affected = dsl.execute("""
                    UPDATE config.brands SET
                        name = COALESCE(NULLIF(?, ''), name),
                        website_url = COALESCE(NULLIF(?, ''), website_url)
                    WHERE id = ? AND workspace_id = ? AND tenant_id = ? AND is_active = true
                    """, name, websiteUrl, brandId, workspaceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "marka güncellenemedi");
        }
        if (affected == 0) {
            return error(HttpStatus.NOT_FOUND, "marka bulunamadı");
        }

        Map<String, Object> resp;
        try {
            resp = map("""
                    SELECT id, name, website_url FROM config.brands
                    WHERE id = ? AND workspace_id = ? AND tenant_id = ?
                    """, brandId, workspaceId, tenantId);
            if (resp == null) {
                return error(HttpStatus.INTERNAL_SERVER_ERROR, "marka bilgisi okunamadı");
            }
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "marka bilgisi okunamadı");
        }
        return ResponseEntity.ok(brandMap(resp));
    }

    @DeleteMapping("/v1/workspaces/{workspaceId}/brands/{brandId}")
    public ResponseEntity<?> deleteBrand(@PathVariable String workspaceId,
                                         @RequestHeader("X-Tenant-ID") String tenantId,
                                         @PathVariable String brandId) {
        if (brandId == null || brandId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id gerekli");
        }
        int affected;
        try {
            affected = dsl.execute("""
                    UPDATE config.brands SET is_active = false
                    WHERE id = ? AND workspace_id = ? AND tenant_id = ? AND is_active = true
                    """, brandId, workspaceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "marka silinemedi");
        }
        if (affected == 0) {
            return error(HttpStatus.NOT_FOUND, "marka bulunamadı");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "deleted");
        body.put("brand_id", brandId);
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/v1/workspaces/{workspaceId}/brands/{brandId}/competitors/{competitorId}")
    public ResponseEntity<?> deleteBrandCompetitor(@RequestHeader("X-Tenant-ID") String tenantId,
                                                   @PathVariable String brandId,
                                                   @PathVariable String competitorId) {
        if (brandId == null || brandId.isBlank() || competitorId == null || competitorId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id ve competitor_id gerekli");
        }
        if (brandId.equals(competitorId)) {
            return error(HttpStatus.BAD_REQUEST, "kendi kendine rakip ilişkisi silinemez");
        }
        int affected;
        try {
            affected = dsl.execute("""
                    DELETE FROM config.brand_competitors
                    WHERE brand_id = ? AND competitor_id = ? AND tenant_id = ?
                    """, brandId, competitorId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "rakip silinemedi");
        }
        if (affected == 0) {
            return error(HttpStatus.NOT_FOUND, "rakip ilişkisi bulunamadı");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "deleted");
        body.put("brand_id", brandId);
        body.put("competitor_id", competitorId);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/v1/workspaces/{workspaceId}/brands/{brandId}/competitors")
    public ResponseEntity<?> listBrandCompetitors(@PathVariable String workspaceId,
                                                  @RequestHeader("X-Tenant-ID") String tenantId,
                                                  @PathVariable String brandId) {
        if (brandId == null || brandId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id gerekli");
        }
        Boolean brandExists;
        try {
            brandExists = value("""
                    SELECT EXISTS(SELECT 1 FROM config.brands
                        WHERE id = ? AND workspace_id = ? AND tenant_id = ?)
                    """, Boolean.class, brandId, workspaceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.NOT_FOUND, "marka bulunamadı");
        }
        if (Boolean.FALSE.equals(brandExists)) {
            return error(HttpStatus.NOT_FOUND, "marka bulunamadı");
        }

        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT bc.competitor_id, b.name AS competitor_name, bc.created_at
                    FROM config.brand_competitors bc
                    JOIN config.brands b ON b.id = bc.competitor_id
                    WHERE bc.brand_id = ? AND bc.tenant_id = ?
                    ORDER BY b.name
                    """, brandId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "sorgu hatası");
        }

        List<Map<String, Object>> competitors = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("competitor_id", r.get("competitor_id"));
            item.put("competitor_name", r.get("competitor_name"));
            item.put("created_at", r.get("created_at") == null ? null : String.valueOf(r.get("created_at")));
            competitors.add(item);
        }
        return ResponseEntity.ok(competitors);
    }

    @PutMapping("/v1/workspaces/{workspaceId}/brands/{brandId}/competitors")
    public ResponseEntity<?> updateBrandCompetitors(@PathVariable String workspaceId,
                                                    @RequestHeader("X-Tenant-ID") String tenantId,
                                                    @PathVariable String brandId,
                                                    @RequestBody UpdateCompetitorsRequest req) {
        if (brandId == null || brandId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id gerekli");
        }
        List<String> competitors = req == null || req.competitors() == null
                ? List.of() : req.competitors();

        Boolean brandExists;
        try {
            brandExists = value("""
                    SELECT EXISTS(SELECT 1 FROM config.brands
                        WHERE id = ? AND workspace_id = ? AND tenant_id = ?)
                    """, Boolean.class, brandId, workspaceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.NOT_FOUND, "marka bulunamadı");
        }
        if (Boolean.FALSE.equals(brandExists)) {
            return error(HttpStatus.NOT_FOUND, "marka bulunamadı");
        }

        txExecute(() -> {
            dsl.execute("""
                    DELETE FROM config.brand_competitors
                    WHERE brand_id = ? AND tenant_id = ?
                    """, brandId, tenantId);

            for (String compId : competitors) {
                if (compId == null || compId.isBlank() || compId.equals(brandId)) {
                    continue;
                }
                Boolean exists = value("""
                        SELECT EXISTS(SELECT 1 FROM config.brands
                            WHERE id = ? AND tenant_id = ? AND is_active = true)
                        """, Boolean.class, compId, tenantId);
                if (Boolean.FALSE.equals(exists)) {
                    throw new ConfigHttpException(HttpStatus.BAD_REQUEST, "rakip bulunamadı: " + compId);
                }
                dsl.execute("""
                        INSERT INTO config.brand_competitors (id, brand_id, competitor_id, tenant_id)
                        VALUES (gen_random_uuid()::text, ?, ?, ?)
                        ON CONFLICT (brand_id, competitor_id) DO NOTHING
                        """, brandId, compId, tenantId);
            }
            return new String[]{};
        });

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "updated");
        body.put("brand_id", brandId);
        body.put("competitors", competitors);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/v1/workspaces/{workspaceId}/setup-status")
    public ResponseEntity<?> getSetupStatus(@PathVariable String workspaceId,
                                            @RequestHeader("X-Tenant-ID") String tenantId) {
        int brandCount = count("SELECT COALESCE((SELECT count(*) FROM config.brands WHERE workspace_id = ? AND tenant_id = ?), 0)", workspaceId, tenantId);
        int panelCount = count("SELECT COALESCE((SELECT count(*) FROM config.panels WHERE workspace_id = ? AND tenant_id = ?), 0)", workspaceId, tenantId);
        int promptSetCount = count("SELECT COALESCE((SELECT count(*) FROM config.prompt_sets WHERE workspace_id = ? AND tenant_id = ?), 0)", workspaceId, tenantId);
        int measurementCount = count("SELECT COALESCE((SELECT count(*) FROM measure.scores WHERE workspace_id = ? AND tenant_id = ?), 0)", workspaceId, tenantId);

        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(step("brand", "Marka Ekle", brandCount > 0));
        steps.add(step("panel", "Panel Oluştur", panelCount > 0));
        steps.add(step("prompt_set", "Prompt Seti Oluştur", promptSetCount > 0));
        steps.add(step("measurement", "İlk Ölçümü Çalıştır", measurementCount > 0));

        boolean allDone = brandCount > 0 && panelCount > 0 && promptSetCount > 0 && measurementCount > 0;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("setup_complete", allDone);
        body.put("steps", steps);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/v1/tenant/panorama")
    public ResponseEntity<?> listWorkspacePanorama(@RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT w.id, w.name,
                        COALESCE(s.score_value, 0) AS avg_score,
                        COALESCE(s.brand_count, 0) AS brand_count,
                        COALESCE(s.measurement_count, 0) AS measurement_count,
                        w.archived_at IS NOT NULL AS archived,
                        w.created_at
                    FROM config.workspaces w
                    LEFT JOIN LATERAL (
                        SELECT
                            AVG(s2.value) AS score_value,
                            COUNT(DISTINCT s2.brand_id) AS brand_count,
                            COUNT(*) AS measurement_count
                        FROM measure.scores s2
                        WHERE s2.workspace_id = w.id AND s2.tenant_id = w.tenant_id
                    ) s ON true
                    WHERE w.tenant_id = ?
                    ORDER BY w.created_at DESC
                    """, tenantId);
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("workspaces", List.of()));
        }

        List<Map<String, Object>> workspaces = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> ws = new LinkedHashMap<>();
            ws.put("id", r.get("id"));
            ws.put("name", r.get("name"));
            ws.put("avg_score", r.get("avg_score") == null ? 0 : ((Number) r.get("avg_score")).doubleValue());
            ws.put("brand_count", r.get("brand_count") == null ? 0 : ((Number) r.get("brand_count")).intValue());
            ws.put("measurement_count", r.get("measurement_count") == null ? 0 : ((Number) r.get("measurement_count")).intValue());
            ws.put("archived", r.get("archived") != null && Boolean.parseBoolean(String.valueOf(r.get("archived"))));
            ws.put("created_at", r.get("created_at") == null ? null : String.valueOf(r.get("created_at")));
            workspaces.add(ws);
        }
        return ResponseEntity.ok(Map.of("workspaces", workspaces));
    }

    private int count(String sql, Object... args) {
        try {
            Integer v = value(sql, Integer.class, args);
            return v == null ? 0 : v;
        } catch (RuntimeException e) {
            return 0;
        }
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

    private static Map<String, Object> step(String key, String label, boolean done) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("key", key);
        s.put("label", label);
        s.put("done", done);
        return s;
    }

    private static Map<String, Object> brandMap(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.get("id"));
        item.put("name", row.get("name"));
        item.put("website_url", row.get("website_url"));
        return item;
    }

    private String[] txExecute(java.util.function.Supplier<String[]> action) {
        try {
            return tx.execute(status -> action.get());
        } catch (ConfigHttpException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        }
    }

    @ExceptionHandler(ConfigHttpException.class)
    public ResponseEntity<ApiError> handleConfigError(ConfigHttpException ex) {
        return error(ex.status(), ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
