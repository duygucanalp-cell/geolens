package dev.geolens.optimize.web;

import dev.geolens.optimize.OpportunityAnalyzer;
import dev.geolens.util.Ulid;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optimization Recommendations REST controller'ı — Go {@code optimize.handler} portu (R13).
 * <p>Route'lar (go cmd/api): GET /v1/optimizations/recommendations,
 * POST /v1/optimizations/recommendations/generate, PUT /v1/optimizations/recommendations/{recId}/status.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir; öneriler A3-4 (İP-07) Opportunity
 * Scoring formülüyle (Impact × Urgency × Confidence) puanlanır.
 */
@RestController
@RequestMapping("/v1/optimizations")
public class OptimizeController {

    private final DSLContext dsl;

    public OptimizeController(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ---------- ListRecommendations ----------

    @GetMapping("/recommendations")
    public ResponseEntity<?> listRecommendations(@RequestHeader("X-Tenant-ID") String tenantId,
                                                 @RequestParam(value = "limit", required = false) String limit,
                                                 @RequestParam(value = "status", required = false) String statusFilter,
                                                 @RequestParam(value = "category", required = false) String categoryFilter) {
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
            return ResponseEntity.ok(Map.of("data", List.of(), "has_more", false));
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

        return ResponseEntity.ok(Map.of("data", recs, "has_more", hasMore));
    }

    // ---------- GenerateRecommendations ----------

    @PostMapping("/recommendations/generate")
    public ResponseEntity<?> generateRecommendations(@RequestHeader("X-Tenant-ID") String tenantId,
                                                     @RequestBody GenerateRequest req) {
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

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("recommendations", created, "count", created.size()));
    }

    // ---------- UpdateStatus ----------

    @PutMapping("/recommendations/{recId}/status")
    public ResponseEntity<?> updateStatus(@RequestHeader("X-Tenant-ID") String tenantId,
                                          @PathVariable String recId,
                                          @RequestBody UpdateStatusRequest req) {
        if (!"implemented".equals(req.status()) && !"dismissed".equals(req.status())) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz durum: implemented veya dismissed olmalı");
        }

        int rows;
        try {
            rows = dsl.execute("""
                    UPDATE optimize.recommendations SET status = ?, updated_at = NOW()
                    WHERE id = ? AND tenant_id = ?
                    """, req.status(), recId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.NOT_FOUND, "öneri bulunamadı");
        }
        if (rows == 0) {
            return error(HttpStatus.NOT_FOUND, "öneri bulunamadı");
        }

        return ResponseEntity.ok(Map.of("id", recId, "status", req.status()));
    }

    // ---------- yardımcılar ----------

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
