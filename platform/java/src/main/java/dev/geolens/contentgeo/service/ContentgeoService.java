package dev.geolens.contentgeo.service;

import dev.geolens.contentgeo.ContentGeoEngine;
import dev.geolens.contentgeo.ContentGapResult;
import dev.geolens.contentgeo.ContentHubScore;
import dev.geolens.contentgeo.web.AnalyzeGapRequest;
import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Content GEO iş mantığı — Go {@code contentgeo.handler} portu (FR-E5, FR-E6).
 * <p>Content gap analizi {@link ContentGeoEngine} ile yapılır, kayıtlı gap/topic
 * sorguları bu servistedir; controller yalnızca HTTP katmanıdır
 * (route'lar: POST /v1/workspaces/{ws}/content-geo/gap,
 * GET /content-geo/gap, GET /content-geo/hub-score, GET /content-geo/topics).
 */
@Service
public class ContentgeoService {

    private final ContentGeoEngine engine;
    private final DSLContext dsl;

    public ContentgeoService(ContentGeoEngine engine, DSLContext dsl) {
        this.engine = engine;
        this.dsl = dsl;
    }

    public List<ContentGapResult> analyzeContentGap(String brandId, String workspaceId, String tenantId) {
        try {
            return engine.analyzeContentGap(brandId, workspaceId, tenantId);
        } catch (RuntimeException e) {
            throw new ContentgeoServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "content gap analizi başarısız");
        }
    }

    public List<Map<String, Object>> listContentGaps(String workspaceId, String tenantId, String brandId) {
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
            return List.of();
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
        return results;
    }

    public ContentHubScore getContentHubScore(String brandId, String workspaceId, String tenantId) {
        try {
            return engine.getContentHubScore(brandId, workspaceId, tenantId);
        } catch (RuntimeException e) {
            throw new ContentgeoServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "skor alınamadı");
        }
    }

    public List<Map<String, Object>> listTopicClusters(String workspaceId, String tenantId, String brandId) {
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
            return List.of();
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
        return results;
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
