package dev.geolens.bias.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.bias.BiasAnalyzer;
import dev.geolens.bias.web.EvaluateRequest;
import dev.geolens.util.Ulid;
import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bias/Fairness iş mantığı — Go {@code bias.handler} portu (R5).
 * <p>Değerlendirme hesaplaması ({@link BiasAnalyzer}), sonucun DB'ye yazılması ve
 * test geçmişi sorgusu bu serviste yapılır; controller yalnızca HTTP katmanıdır
 * (route'lar: POST /v1/bias/evaluate, GET /v1/bias/tests).
 */
@Service
public class BiasService {

    private final DSLContext dsl;
    private final ObjectMapper mapper = new ObjectMapper();

    public BiasService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Map<String, Object> evaluate(String tenantId, EvaluateRequest req) {
        String testId = Ulid.generate();

        Map<String, Object> results = BiasAnalyzer.compute(req.metricType(), req.data());

        // Persist sonucu DB'ye yaz
        String detailsJson;
        String recsJson;
        try {
            detailsJson = mapper.writeValueAsString(results);
            recsJson = mapper.writeValueAsString(results.get("recommendations"));
        } catch (Exception e) {
            detailsJson = "{}";
            recsJson = "[]";
        }
        double fairnessScore = number(results.get("fairness_score"));
        boolean hasBias = Boolean.TRUE.equals(results.get("has_bias"));
        double maxGap = number(results.get("max_gap"));

        try {
            dsl.execute("""
                    INSERT INTO bias.tests (id, tenant_id, model_id, metric_type, fairness_score, has_bias,
                                            max_gap, details, recommendations)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
                    """, testId, tenantId, nz(req.modelId()), nz(req.metricType()),
                    fairnessScore, hasBias, maxGap, detailsJson, recsJson);
        } catch (RuntimeException e) {
            // non-fatal: sonucu yine de döndür (Go ile aynı)
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("test_id", testId);
        body.put("model_id", nz(req.modelId()));
        body.put("metric_type", nz(req.metricType()));
        body.put("results", results);
        body.put("fairness_score", fairnessScore);
        body.put("recommendations", results.get("recommendations"));
        return body;
    }

    public Map<String, Object> listTests(String tenantId, String modelId, int limit) {
        int limitInt = limit;
        if (limitInt < 1 || limitInt > 100) {
            limitInt = 20;
        }
        String model = modelId == null ? "" : modelId;

        // LIMIT+1 pattern for has_more
        StringBuilder query = new StringBuilder("""
                SELECT id, model_id, metric_type, fairness_score, has_bias, max_gap, details, recommendations, created_at
                FROM bias.tests WHERE tenant_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);

        if (!model.isEmpty()) {
            query.append(" AND model_id = ?");
            args.add(model);
        }
        query.append(" ORDER BY created_at DESC LIMIT ?");
        args.add(limitInt + 1);

        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch(query.toString(), args.toArray()).intoMaps();
        } catch (RuntimeException e) {
            throw new BiasServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "test geçmişi alınamadı");
        }

        boolean hasMore = rows.size() > limitInt;
        if (hasMore) {
            rows = rows.subList(0, limitInt);
        }

        List<Map<String, Object>> tests = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", str(r.get("id")));
            item.put("model_id", str(r.get("model_id")));
            item.put("metric_type", str(r.get("metric_type")));
            item.put("fairness_score", num(r.get("fairness_score")));
            item.put("has_bias", Boolean.TRUE.equals(r.get("has_bias")));
            item.put("max_gap", num(r.get("max_gap")));
            item.put("details", parseJson(r.get("details")));
            item.put("recommendations", parseJson(r.get("recommendations")));
            item.put("created_at", ts(r.get("created_at")));
            tests.add(item);
        }

        return Map.of("data", tests, "has_more", hasMore);
    }

    @SuppressWarnings("unchecked")
    private Object parseJson(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw);
        if (text.isBlank() || "null".equals(text)) {
            return null;
        }
        try {
            return mapper.readValue(text, Object.class);
        } catch (Exception e) {
            return text;
        }
    }

    private static double num(Object o) {
        return o == null ? 0 : ((Number) o).doubleValue();
    }

    private static double number(Object o) {
        return num(o);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String ts(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof java.sql.Timestamp t) {
            return t.toInstant().toString();
        }
        return String.valueOf(o);
    }
}
