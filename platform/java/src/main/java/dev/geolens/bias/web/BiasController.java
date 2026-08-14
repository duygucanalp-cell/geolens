package dev.geolens.bias.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.bias.BiasAnalyzer;
import dev.geolens.util.Ulid;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
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
 * Bias/Fairness REST controller'ı — Go {@code bias.handler} portu (R5).
 * <p>Route'lar (go cmd/api): POST /v1/bias/evaluate, GET /v1/bias/tests.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir.
 */
@RestController
@RequestMapping("/v1/bias")
public class BiasController {

    private final DSLContext dsl;
    private final ObjectMapper mapper = new ObjectMapper();

    public BiasController(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ---------- Evaluate ----------

    @PostMapping("/evaluate")
    public ResponseEntity<?> evaluate(@RequestHeader("X-Tenant-ID") String tenantId,
                                      @RequestBody EvaluateRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
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
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    // ---------- ListTests ----------

    @GetMapping("/tests")
    public ResponseEntity<?> listTests(@RequestHeader("X-Tenant-ID") String tenantId,
                                       @RequestParam(value = "model_id", required = false) String modelId,
                                       @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {
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
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "test geçmişi alınamadı");
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

        return ResponseEntity.ok(Map.of(
                "data", tests,
                "has_more", hasMore));
    }

    // ---------- yardımcılar ----------

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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
