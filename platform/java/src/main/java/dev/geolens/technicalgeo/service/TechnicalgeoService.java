package dev.geolens.technicalgeo.service;

import dev.geolens.common.ServiceException;

import dev.geolens.technicalgeo.BotAnalysisResult;
import dev.geolens.technicalgeo.SchemaAnalysisResult;
import dev.geolens.technicalgeo.TechnicalGeoEngine;
import dev.geolens.technicalgeo.TechnicalGeoScore;
import dev.geolens.technicalgeo.web.AnalyzeBotsRequest;
import dev.geolens.technicalgeo.web.AnalyzeSchemaRequest;
import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Technical GEO iş mantığı — Go {@code technicalgeo.handler} portu (FR-B6/B7/E7).
 * <p>Bot/schema analizi {@link TechnicalGeoEngine} ile yapılır, kayıtlı analiz ve
 * skor sorguları bu servistedir; controller yalnızca HTTP katmanıdır
 * (route'lar: POST /v1/workspaces/{ws}/technical-geo/bots,
 * GET /technical-geo/bots, POST /technical-geo/schema, GET /technical-geo/schema,
 * GET /technical-geo/score).
 */
@Service
public class TechnicalgeoService {

    private final TechnicalGeoEngine engine;
    private final DSLContext dsl;

    public TechnicalgeoService(TechnicalGeoEngine engine, DSLContext dsl) {
        this.engine = engine;
        this.dsl = dsl;
    }

    public BotAnalysisResult analyzeBots(String brandId, String url, String workspaceId, String tenantId) {
        try {
            return engine.analyzeBotAccess(brandId, url, workspaceId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "bot analizi başarısız");
        }
    }

    public List<Map<String, Object>> listBotAnalyses(String workspaceId, String tenantId, String brandId) {
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
            return List.of();
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
        return results;
    }

    public SchemaAnalysisResult analyzeSchema(String brandId, String workspaceId, String tenantId) {
        try {
            return engine.analyzeSchema(brandId, workspaceId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "schema analizi başarısız");
        }
    }

    public List<Map<String, Object>> listSchemaAnalyses(String workspaceId, String tenantId, String brandId) {
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
            return List.of();
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
        return results;
    }

    public TechnicalGeoScore getTechnicalGeoScore(String brandId, String workspaceId, String tenantId) {
        try {
            return engine.getScore(brandId, workspaceId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "skor alınamadı");
        }
    }

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
}
