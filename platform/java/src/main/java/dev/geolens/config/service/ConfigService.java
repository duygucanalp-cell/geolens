package dev.geolens.config.service;

import dev.geolens.common.ServiceException;

import dev.geolens.config.web.BrandRequest;
import dev.geolens.config.web.BrandResponse;
import dev.geolens.config.web.UpdateBrandRequest;
import dev.geolens.config.web.UpdateCompetitorsRequest;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Marka ve rakip yapılandırması iş mantığı — Go {@code config.handler} portu.
 * <p>Marka CRUD, rakip ilişkileri, kurulum durumu ve tenant panoramasını yönetir.
 * Controller yalnızca HTTP katmanıdır; bu sınıf DB ve transaction erişimini içerir.
 */
@Service
public class ConfigService {

    private final DSLContext dsl;
    private final TransactionTemplate tx;

    public ConfigService(DSLContext dsl, TransactionTemplate tx) {
        this.dsl = dsl;
        this.tx = tx;
    }

    public Map<String, Object> searchBrands(String workspaceId, String tenantId, String q,
                                            String exclude, int offset, int limit) {
        int total;
        try {
            Integer t = value("""
                    SELECT count(*)
                    FROM config.brands
                    WHERE workspace_id = ? AND tenant_id = ? AND is_active = true
                        AND (name ILIKE '%' || ? || '%' OR id ILIKE '%' || ? || '%')
                        AND (? = '' OR id != ?)
                    """, Integer.class, workspaceId, tenantId, q, q, exclude, exclude);
            total = t == null ? 0 : t;
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "sorgu hatası");
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
                    """, workspaceId, tenantId, q, q, exclude, exclude, limit, offset);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "sorgu hatası");
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
        return body;
    }

    public List<Map<String, Object>> listBrands(String workspaceId, String tenantId) {
        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT id, name, website_url
                    FROM config.brands
                    WHERE workspace_id = ? AND tenant_id = ? AND is_active = true
                    ORDER BY name
                    """, workspaceId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "sorgu hatası");
        }
        List<Map<String, Object>> brands = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            brands.add(brandMap(r));
        }
        return brands;
    }

    public BrandResponse createBrand(String workspaceId, String tenantId, BrandRequest req) {
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
                        throw new ServiceException(HttpStatus.BAD_REQUEST, "rakip bulunamadı: " + compId);
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

        return new BrandResponse(result[0], req.name(), req.websiteUrl());
    }

    public Map<String, Object> updateBrand(String workspaceId, String tenantId, String brandId,
                                           UpdateBrandRequest req) {
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
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "marka güncellenemedi");
        }
        if (affected == 0) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "marka bulunamadı");
        }

        Map<String, Object> resp;
        try {
            resp = map("""
                    SELECT id, name, website_url FROM config.brands
                    WHERE id = ? AND workspace_id = ? AND tenant_id = ?
                    """, brandId, workspaceId, tenantId);
            if (resp == null) {
                throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "marka bilgisi okunamadı");
            }
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "marka bilgisi okunamadı");
        }
        return brandMap(resp);
    }

    public Map<String, Object> deleteBrand(String workspaceId, String tenantId, String brandId) {
        int affected;
        try {
            affected = dsl.execute("""
                    UPDATE config.brands SET is_active = false
                    WHERE id = ? AND workspace_id = ? AND tenant_id = ? AND is_active = true
                    """, brandId, workspaceId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "marka silinemedi");
        }
        if (affected == 0) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "marka bulunamadı");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "deleted");
        body.put("brand_id", brandId);
        return body;
    }

    public Map<String, Object> deleteBrandCompetitor(String tenantId, String brandId, String competitorId) {
        int affected;
        try {
            affected = dsl.execute("""
                    DELETE FROM config.brand_competitors
                    WHERE brand_id = ? AND competitor_id = ? AND tenant_id = ?
                    """, brandId, competitorId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "rakip silinemedi");
        }
        if (affected == 0) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "rakip ilişkisi bulunamadı");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "deleted");
        body.put("brand_id", brandId);
        body.put("competitor_id", competitorId);
        return body;
    }

    public List<Map<String, Object>> listBrandCompetitors(String workspaceId, String tenantId, String brandId) {
        Boolean brandExists;
        try {
            brandExists = value("""
                    SELECT EXISTS(SELECT 1 FROM config.brands
                        WHERE id = ? AND workspace_id = ? AND tenant_id = ?)
                    """, Boolean.class, brandId, workspaceId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "marka bulunamadı");
        }
        if (Boolean.FALSE.equals(brandExists)) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "marka bulunamadı");
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
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "sorgu hatası");
        }

        List<Map<String, Object>> competitors = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("competitor_id", r.get("competitor_id"));
            item.put("competitor_name", r.get("competitor_name"));
            item.put("created_at", r.get("created_at") == null ? null : String.valueOf(r.get("created_at")));
            competitors.add(item);
        }
        return competitors;
    }

    public Map<String, Object> updateBrandCompetitors(String workspaceId, String tenantId, String brandId,
                                                      List<String> competitors) {
        Boolean brandExists;
        try {
            brandExists = value("""
                    SELECT EXISTS(SELECT 1 FROM config.brands
                        WHERE id = ? AND workspace_id = ? AND tenant_id = ?)
                    """, Boolean.class, brandId, workspaceId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "marka bulunamadı");
        }
        if (Boolean.FALSE.equals(brandExists)) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "marka bulunamadı");
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
                    throw new ServiceException(HttpStatus.BAD_REQUEST, "rakip bulunamadı: " + compId);
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
        return body;
    }

    public Map<String, Object> getSetupStatus(String workspaceId, String tenantId) {
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
        return body;
    }

    public Map<String, Object> listWorkspacePanorama(String tenantId) {
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
            return Map.of("workspaces", List.of());
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
        return Map.of("workspaces", workspaces);
    }

    private int count(String sql, Object... args) {
        try {
            Integer v = value(sql, Integer.class, args);
            return v == null ? 0 : v;
        } catch (RuntimeException e) {
            return 0;
        }
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

    private String[] txExecute(java.util.function.Supplier<String[]> action) {
        try {
            return tx.execute(status -> action.get());
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        }
    }
}
