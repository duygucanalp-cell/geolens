package dev.geolens.sentiment.web;

import dev.geolens.sentiment.domain.HallucinationResult;
import dev.geolens.sentiment.domain.SentimentResult;
import dev.geolens.sentiment.engine.SentimentEngine;
import dev.geolens.sentiment.persistence.SentimentDao;
import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sentiment REST controller'ı (FR-D7) ve hallüsinasyon (FR-D8) — Go {@code sentiment.handler} portu.
 * <p>Route'lar (go cmd/api): POST /sentiment/analyze, GET /sentiment, GET /sentiment/summary,
 * POST /hallucination/detect, GET /hallucination, POST /hallucination/{flagId}/verify.
 * <p>Tenant, gerçek geçit/middleware tarafından atılan {@code X-Tenant-ID} başlığından gelir
 * (Go {@code httpmw.GetTenantID} karşılığı); workspace URL path'ten.
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}/sentiment")
public class
SentimentController {

    private final SentimentEngine engine;
    private final SentimentDao dao;
    private final DSLContext dsl;
    private final TransactionTemplate tx;

    public SentimentController(SentimentEngine engine, SentimentDao dao, DSLContext dsl, TransactionTemplate tx) {
        this.engine = engine;
        this.dao = dao;
        this.dsl = dsl;
        this.tx = tx;
    }

    private void setTenant(String tenantId) {
        dsl.fetch("SELECT set_config('app.tenant_id', ?, true)", tenantId);
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@PathVariable String workspaceId,
                                     @RequestHeader("X-Tenant-ID") String tenantId,
                                     @RequestBody AnalyzeRequest req) {
        if (req == null || req.brandId() == null || req.brandId().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id zorunludur");
        }
        try {
            List<SentimentResult> results = engine.analyzeSentiment(tenantId, workspaceId, req.brandId(), req.prompt());
            return ResponseEntity.ok(results == null ? List.of() : results);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "sentiment analizi başarısız");
        }
    }

    @GetMapping("/")
    public ResponseEntity<List<Map<String, Object>>> list(@PathVariable String workspaceId,
                                                          @RequestHeader("X-Tenant-ID") String tenantId,
                                                          @RequestParam(value = "brand_id", required = false) String brandId) {
        String brand = brandId == null ? "" : brandId;
        List<Map<String, Object>> rows;
        try {
            rows = tx.execute(status -> {
                setTenant(tenantId);
                return dsl.fetch("""
                        SELECT ss.id, ss.brand_id, ss.engine_name, ss.overall_sentiment,
                               ss.positive_score, ss.neutral_score, ss.negative_score,
                               ss.mention_count, ss.analyzed_at
                        FROM analysis.sentiment_scores ss
                        JOIN config.brands b ON b.id = ss.brand_id
                        WHERE ss.tenant_id = ? AND b.workspace_id = ? AND (? = '' OR ss.brand_id = ?)
                        ORDER BY ss.analyzed_at DESC
                        LIMIT 100
                        """, tenantId, workspaceId, brand, brand).intoMaps();
            });
        } catch (RuntimeException e) {
            return ResponseEntity.ok(List.of());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("brand_id", row.get("brand_id"));
            item.put("engine_name", row.get("engine_name"));
            item.put("overall_sentiment", row.get("overall_sentiment"));
            item.put("positive_score", row.get("positive_score"));
            item.put("neutral_score", row.get("neutral_score"));
            item.put("negative_score", row.get("negative_score"));
            item.put("mention_count", row.get("mention_count"));
            item.put("analyzed_at", row.get("analyzed_at") == null ? null : String.valueOf(row.get("analyzed_at")));
            out.add(item);
        }
        return ResponseEntity.ok(out);
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary(@PathVariable String workspaceId,
                                     @RequestHeader("X-Tenant-ID") String tenantId,
                                     @RequestParam(value = "brand_id", required = false) String brandId) {
        if (brandId == null || brandId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id gerekli");
        }
        Map<String, Object> agg;
        try {
            agg = tx.execute(status -> {
                setTenant(tenantId);
                return dsl.fetchOne("""
                        SELECT COALESCE(AVG(overall_sentiment), 0) AS overall,
                               COALESCE(AVG(positive_score), 0)   AS positive,
                               COALESCE(AVG(neutral_score), 0)    AS neutral,
                               COALESCE(AVG(negative_score), 0)   AS negative,
                               COALESCE(SUM(mention_count), 0)    AS mentions
                        FROM analysis.sentiment_scores ss
                        JOIN config.brands b ON b.id = ss.brand_id
                        WHERE ss.tenant_id = ? AND b.workspace_id = ? AND ss.brand_id = ?
                        """, tenantId, workspaceId, brandId).intoMap();
            });
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "sentiment özeti alınamadı");
        }
        double overall = ((Number) agg.get("overall")).doubleValue();
        double positive = ((Number) agg.get("positive")).doubleValue();
        double neutral = ((Number) agg.get("neutral")).doubleValue();
        double negative = ((Number) agg.get("negative")).doubleValue();
        int mentionCount = ((Number) agg.get("mentions")).intValue();
        return ResponseEntity.ok(Map.of(
                "brand_id", brandId,
                "overall", overall,
                "positive", positive,
                "neutral", neutral,
                "negative", negative,
                "mention_count", mentionCount,
                "classification", classifySentiment(overall)));
    }

    @PostMapping("/hallucination/detect")
    public ResponseEntity<?> detectHallucinations(@PathVariable String workspaceId,
                                                  @RequestHeader("X-Tenant-ID") String tenantId,
                                                  @RequestBody DetectRequest req) {
        if (req == null || req.brandId() == null || req.brandId().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id zorunludur");
        }
        try {
            List<HallucinationResult> results =
                    engine.detectHallucinations(tenantId, workspaceId, req.brandId());
            return ResponseEntity.ok(results == null ? List.of() : results);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "hallüsinasyon tespiti başarısız");
        }
    }

    @GetMapping("/hallucination")
    public ResponseEntity<List<Map<String, Object>>> listHallucinations(@PathVariable String workspaceId,
                                                                        @RequestHeader("X-Tenant-ID") String tenantId,
                                                                        @RequestParam(value = "brand_id", required = false) String brandId) {
        String brand = brandId == null ? "" : brandId;
        List<Map<String, Object>> rows;
        try {
            rows = tx.execute(status -> {
                setTenant(tenantId);
                return dsl.fetch("""
                        SELECT hf.id, hf.brand_id, hf.engine_name, hf.hallucination_type,
                               hf.severity, hf.description, hf.confidence, hf.verified, hf.created_at
                        FROM analysis.hallucination_flags hf
                        JOIN config.brands b ON b.id = hf.brand_id
                        WHERE hf.tenant_id = ? AND b.workspace_id = ? AND (? = '' OR hf.brand_id = ?)
                        ORDER BY hf.created_at DESC
                        LIMIT 100
                        """, tenantId, workspaceId, brand, brand).intoMaps();
            });
        } catch (RuntimeException e) {
            return ResponseEntity.ok(List.of());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("brand_id", row.get("brand_id"));
            item.put("engine_name", row.get("engine_name"));
            item.put("hallucination_type", row.get("hallucination_type"));
            item.put("severity", row.get("severity"));
            item.put("description", row.get("description"));
            item.put("confidence", row.get("confidence"));
            item.put("verified", row.get("verified"));
            item.put("created_at", row.get("created_at") == null ? null : String.valueOf(row.get("created_at")));
            out.add(item);
        }
        return ResponseEntity.ok(out);
    }

    @PostMapping("/hallucination/{flagId}/verify")
    public ResponseEntity<?> verify(@PathVariable String workspaceId,
                                    @RequestHeader("X-Tenant-ID") String tenantId,
                                    @PathVariable String flagId,
                                    @RequestBody VerifyRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        AtomicReference<Integer> updated = new AtomicReference<>();
        try {
            tx.executeWithoutResult(status -> {
                setTenant(tenantId);
                updated.set(dsl.execute("""
                        UPDATE analysis.hallucination_flags SET verified = ? WHERE id = ? AND tenant_id = ?
                        """, req.verified(), flagId, tenantId));
            });
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "doğrulama başarısız");
        }
        if (updated.get() == null || updated.get() == 0) {
            return error(HttpStatus.NOT_FOUND, "hallüsinasyon kaydı bulunamadı");
        }
        return ResponseEntity.ok(Map.of("status", "verified"));
    }

    private static String classifySentiment(double score) {
        if (score >= 0.7) {
            return "olumlu";
        } else if (score >= 0.4) {
            return "nötr";
        }
        return "olumsuz";
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}