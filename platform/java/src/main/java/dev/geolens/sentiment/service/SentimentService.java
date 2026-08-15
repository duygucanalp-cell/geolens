package dev.geolens.sentiment.service;

import dev.geolens.sentiment.domain.HallucinationResult;
import dev.geolens.sentiment.domain.SentimentResult;
import dev.geolens.sentiment.engine.SentimentEngine;
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
 * Sentiment analizi ve hallüsinasyon tespiti iş mantığı — Go {@code sentiment.handler} portu (FR-D7, FR-D8).
 * <p>DB erişimi (DSLContext), engine çağrıları ve doğrulama burada yapılır.
 * Controller yalnızca HTTP katmanıdır.
 */
@Service
public class SentimentService {

    private final SentimentEngine engine;
    private final DSLContext dsl;
    private final TransactionTemplate tx;

    public SentimentService(SentimentEngine engine, DSLContext dsl, TransactionTemplate tx) {
        this.engine = engine;
        this.dsl = dsl;
        this.tx = tx;
    }

    /** Go {@code analyze} karşılığı — motor hatasında 500 döner. */
    public List<SentimentResult> analyze(String tenantId, String workspaceId, String brandId, String prompt) {
        try {
            List<SentimentResult> results = engine.analyzeSentiment(tenantId, workspaceId, brandId, prompt);
            return results == null ? List.of() : results;
        } catch (RuntimeException e) {
            throw new SentimentServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "sentiment analizi başarısız");
        }
    }

    /** Go {@code list} karşılığı — sorgu hatasında boş liste döner. */
    public List<Map<String, Object>> list(String workspaceId, String tenantId, String brandId) {
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
            return List.of();
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
        return out;
    }

    /** Go {@code summary} karşılığı — sorgu hatasında 500 döner. */
    public Map<String, Object> summary(String workspaceId, String tenantId, String brandId) {
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
            throw new SentimentServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "sentiment özeti alınamadı");
        }
        double overall = ((Number) agg.get("overall")).doubleValue();
        double positive = ((Number) agg.get("positive")).doubleValue();
        double neutral = ((Number) agg.get("neutral")).doubleValue();
        double negative = ((Number) agg.get("negative")).doubleValue();
        int mentionCount = ((Number) agg.get("mentions")).intValue();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("brand_id", brandId);
        body.put("overall", overall);
        body.put("positive", positive);
        body.put("neutral", neutral);
        body.put("negative", negative);
        body.put("mention_count", mentionCount);
        body.put("classification", classifySentiment(overall));
        return body;
    }

    /** Go {@code detectHallucinations} karşılığı — motor hatasında 500 döner. */
    public List<HallucinationResult> detectHallucinations(String tenantId, String workspaceId, String brandId) {
        try {
            List<HallucinationResult> results = engine.detectHallucinations(tenantId, workspaceId, brandId);
            return results == null ? List.of() : results;
        } catch (RuntimeException e) {
            throw new SentimentServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "hallüsinasyon tespiti başarısız");
        }
    }

    /** Go {@code listHallucinations} karşılığı — sorgu hatasında boş liste döner. */
    public List<Map<String, Object>> listHallucinations(String workspaceId, String tenantId, String brandId) {
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
            return List.of();
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
        return out;
    }

    /** Go {@code verify} karşılığı — kayıt bulunamazsa 404, sorgu hatasında 500 döner. */
    public Map<String, Object> verify(String tenantId, String flagId, boolean verified) {
        int updated;
        try {
            updated = tx.execute(status -> {
                setTenant(tenantId);
                return dsl.execute("""
                        UPDATE analysis.hallucination_flags SET verified = ? WHERE id = ? AND tenant_id = ?
                        """, verified, flagId, tenantId);
            });
        } catch (RuntimeException e) {
            throw new SentimentServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "doğrulama başarısız");
        }
        if (updated == 0) {
            throw new SentimentServiceException(HttpStatus.NOT_FOUND, "hallüsinasyon kaydı bulunamadı");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "verified");
        return body;
    }

    private static String classifySentiment(double score) {
        if (score >= 0.7) {
            return "olumlu";
        } else if (score >= 0.4) {
            return "nötr";
        }
        return "olumsuz";
    }

    private void setTenant(String tenantId) {
        dsl.fetch("SELECT set_config('app.tenant_id', ?, true)", tenantId);
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
}
