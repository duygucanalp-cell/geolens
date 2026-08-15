package dev.geolens.optimize.service;

import dev.geolens.common.ServiceException;

import dev.geolens.optimize.OpportunityAnalyzer;
import dev.geolens.optimize.web.GenerateRequest;
import dev.geolens.optimize.web.UpdateStatusRequest;
import dev.geolens.util.Ulid;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optimization Recommendations iş mantığı — Go {@code optimize.handler} portu (R13).
 * <p>Öneri listeleme, A3-4 (İP-07) Opportunity Scoring ({@link OpportunityAnalyzer})
 * ile üretim ve durum güncelleme bu servistedir; controller yalnızca HTTP katmanıdır
 * (route'lar: GET /v1/optimizations/recommendations,
 * POST /v1/optimizations/recommendations/generate, PUT /v1/optimizations/recommendations/{recId}/status).
 */
@Service
public class OptimizeService {

    private final DSLContext dsl;

    public OptimizeService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Map<String, Object> listRecommendations(String tenantId, String limit,
                                                   String statusFilter, String categoryFilter) {
        int limitInt;
        try {
            limitInt = limit == null || limit.isBlank() ? 0 : Integer.parseInt(limit);
        } catch (NumberFormatException e) {
            limitInt = 0;
        }
        if (limitInt < 1 || limitInt > 100) {
            limitInt = 20;
        }

        // LIMIT+1 pattern for has_more — dinamik WHERE ile param indexleri Go ile aynı
        StringBuilder query = new StringBuilder(
                "SELECT id, category, title, description, impact, effort, status, score_potential, created_at"
                        + " FROM optimize.recommendations WHERE tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        int paramIdx = 2;

        if ("pending".equals(statusFilter) || "implemented".equals(statusFilter) || "dismissed".equals(statusFilter)) {
            query.append(" AND status = $").append(paramIdx);
            args.add(statusFilter);
            paramIdx++;
        }
        if (categoryFilter != null && !categoryFilter.isBlank()) {
            query.append(" AND category = $").append(paramIdx);
            args.add(categoryFilter);
            paramIdx++;
        }
        query.append(" ORDER BY score_potential DESC LIMIT $").append(paramIdx);
        args.add(limitInt + 1);

        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch(query.toString(), args.toArray()).intoMaps();
        } catch (RuntimeException e) {
            return Map.of("data", List.of(), "has_more", false);
        }

        List<Map<String, Object>> recs = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", str(r.get("id")));
            item.put("category", str(r.get("category")));
            item.put("title", str(r.get("title")));
            item.put("description", str(r.get("description")));
            item.put("impact", str(r.get("impact")));
            item.put("effort", str(r.get("effort")));
            item.put("status", str(r.get("status")));
            item.put("score_potential", num(r.get("score_potential")));
            item.put("created_at", str(r.get("created_at")));
            recs.add(item);
        }

        boolean hasMore = recs.size() > limitInt;
        if (hasMore) {
            recs = new ArrayList<>(recs.subList(0, limitInt));
        }

        return Map.of("data", recs, "has_more", hasMore);
    }

    public Map<String, Object> generateRecommendations(String tenantId, GenerateRequest req) {
        // Analiz için mevcut skorları kontrol et
        int scoreCount = 0;
        try {
            Record r = dsl.fetchOne("SELECT COUNT(*) FROM measure.scores WHERE tenant_id = ?", tenantId);
            if (r != null) {
                scoreCount = ((Number) r.get(0)).intValue();
            }
        } catch (RuntimeException e) {
            // Go'da hata yok sayılır (0 kalır)
        }

        List<Map<String, Object>> recommendations = OpportunityAnalyzer.analyze(scoreCount);

        List<Map<String, Object>> created = new ArrayList<>();
        for (Map<String, Object> rec : recommendations) {
            String recId = Ulid.generate();
            Instant now = Instant.now();

            if (req.autoSave()) {
                try {
                    dsl.execute("""
                            INSERT INTO optimize.recommendations (id, tenant_id, category, title, description, impact, effort, score_potential, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """, recId, tenantId, rec.get("category"), rec.get("title"), rec.get("description"),
                            rec.get("impact"), rec.get("effort"), rec.get("score_potential"), now);
                } catch (RuntimeException e) {
                    continue;
                }
                Map<String, Object> saved = new LinkedHashMap<>(rec);
                saved.put("id", recId);
                saved.put("status", "pending");
                created.add(saved);
            } else {
                created.add(rec);
            }
        }

        return Map.of("recommendations", created, "count", created.size());
    }

    public Map<String, Object> updateStatus(String tenantId, String recId, UpdateStatusRequest req) {
        if (!"implemented".equals(req.status()) && !"dismissed".equals(req.status())) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "geçersiz durum: implemented veya dismissed olmalı");
        }

        int rows;
        try {
            rows = dsl.execute("""
                    UPDATE optimize.recommendations SET status = ?, updated_at = NOW()
                    WHERE id = ? AND tenant_id = ?
                    """, req.status(), recId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "öneri bulunamadı");
        }
        if (rows == 0) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "öneri bulunamadı");
        }

        return Map.of("id", recId, "status", req.status());
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
