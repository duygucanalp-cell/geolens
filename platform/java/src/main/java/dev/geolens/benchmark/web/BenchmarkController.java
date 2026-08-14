package dev.geolens.benchmark.web;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Model benchmark REST controller'ı — Go {@code benchmark.handler} portu (R10).
 * <p>Route'lar (go cmd/api): POST /v1/benchmarks/models, GET /v1/benchmarks/models,
 * GET /v1/benchmarks/compare.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir.
 */
@RestController
@RequestMapping("/v1/benchmarks")
public class BenchmarkController {

    private final DSLContext dsl;
    private final ObjectMapper mapper = new ObjectMapper();

    public BenchmarkController(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ---------- RunBenchmark ----------

    @PostMapping("/models")
    public ResponseEntity<?> runBenchmark(@RequestHeader("X-Tenant-ID") String tenantId,
                                          @RequestBody RunBenchmarkRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        if (req.modelName() == null || req.modelName().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "model_name gerekli");
        }
        if (req.engineName() == null || req.engineName().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "engine_name gerekli");
        }
        String category = req.category() == null || req.category().isBlank() ? "llm" : req.category();

        String benchId = Ulid.generate();
        Instant now = Instant.now();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("accuracy_score", req.accuracyScore());
        details.put("latency_ms", req.latencyMs());
        details.put("cost_per_request", req.costPerRequest());
        details.put("tokens_per_sec", req.tokensPerSecond());
        details.put("response_quality", req.responseQuality());
        details.put("citation_rate", req.citationRate());
        details.put("tested_at", DateTimeFormatter.ISO_INSTANT.format(now));
        String detailsJson;
        try {
            detailsJson = mapper.writeValueAsString(details);
        } catch (Exception e) {
            detailsJson = "{}";
        }

        try {
            dsl.execute("""
                    INSERT INTO benchmark.models (id, tenant_id, model_name, engine_name, category,
                                                  accuracy_score, latency_ms, cost_per_request, tokens_per_second,
                                                  response_quality, citation_rate, details, tested_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                    """, benchId, tenantId, req.modelName(), req.engineName(), category,
                    req.accuracyScore(), req.latencyMs(), req.costPerRequest(), req.tokensPerSecond(),
                    req.responseQuality(), req.citationRate(), detailsJson, java.sql.Timestamp.from(now));
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "benchmark kaydedilemedi");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bench_id", benchId);
        body.put("model_name", req.modelName());
        body.put("engine_name", req.engineName());
        body.put("category", category);
        body.put("accuracy_score", req.accuracyScore());
        body.put("latency_ms", req.latencyMs());
        body.put("cost_per_request", req.costPerRequest());
        body.put("tokens_per_second", req.tokensPerSecond());
        body.put("response_quality", req.responseQuality());
        body.put("citation_rate", req.citationRate());
        body.put("tested_at", DateTimeFormatter.ISO_INSTANT.format(now));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    // ---------- ListBenchmarks ----------

    @GetMapping("/models")
    public ResponseEntity<?> listBenchmarks(@RequestHeader("X-Tenant-ID") String tenantId,
                                            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit,
                                            @RequestParam(value = "offset", required = false, defaultValue = "0") int offset,
                                            @RequestParam(value = "engine", required = false) String engine,
                                            @RequestParam(value = "category", required = false) String category) {
        int lim = limit;
        if (lim < 1 || lim > 100) {
            lim = 20;
        }
        int off = Math.max(offset, 0);
        String engineFilter = engine == null ? "" : engine;
        String categoryFilter = category == null ? "" : category;

        StringBuilder query = new StringBuilder("""
                SELECT id, model_name, engine_name, category, accuracy_score, latency_ms,
                       cost_per_request, tokens_per_second, response_quality, citation_rate, tested_at
                FROM benchmark.models WHERE tenant_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);

        if (!engineFilter.isEmpty()) {
            query.append(" AND engine_name = ?");
            args.add(engineFilter);
        }
        if (!categoryFilter.isEmpty()) {
            query.append(" AND category = ?");
            args.add(categoryFilter);
        }
        query.append(" ORDER BY tested_at DESC LIMIT ? OFFSET ?");
        args.add(lim);
        args.add(off);

        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch(query.toString(), args.toArray()).intoMaps();
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "benchmark geçmişi alınamadı");
        }

        List<Map<String, Object>> benchmarks = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", str(r.get("id")));
            item.put("model_name", str(r.get("model_name")));
            item.put("engine_name", str(r.get("engine_name")));
            item.put("category", str(r.get("category")));
            item.put("accuracy_score", num(r.get("accuracy_score")));
            item.put("latency_ms", r.get("latency_ms") == null ? 0 : ((Number) r.get("latency_ms")).intValue());
            item.put("cost_per_request", num(r.get("cost_per_request")));
            item.put("tokens_per_second", num(r.get("tokens_per_second")));
            item.put("response_quality", num(r.get("response_quality")));
            item.put("citation_rate", num(r.get("citation_rate")));
            item.put("tested_at", ts(r.get("tested_at")));
            benchmarks.add(item);
        }
        return ResponseEntity.ok(benchmarks);
    }

    // ---------- CompareModels ----------

    @GetMapping("/compare")
    public ResponseEntity<?> compareModels(@RequestHeader("X-Tenant-ID") String tenantId,
                                           @RequestParam(value = "engines", required = false) String enginesRaw) {
        if (enginesRaw == null || enginesRaw.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "engines parametresi gerekli (virgülle ayırın)");
        }

        // Engine listesini parse et (boşlukları temizle)
        List<String> engines = new ArrayList<>();
        for (String p : enginesRaw.split(",")) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                engines.add(trimmed);
            }
        }
        if (engines.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "geçerli engine adı gerekli");
        }

        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch("""
                    SELECT DISTINCT ON (engine_name) engine_name, model_name, accuracy_score, latency_ms,
                                     cost_per_request, tokens_per_second, response_quality, citation_rate, tested_at
                    FROM benchmark.models WHERE tenant_id = ? AND engine_name = ANY(?)
                    ORDER BY engine_name, tested_at DESC
                    """, tenantId, engines.toArray(new String[0])).intoMaps();
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "karşılaştırma alınamadı");
        }

        List<Map<String, Object>> models = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("engine_name", str(r.get("engine_name")));
            item.put("model_name", str(r.get("model_name")));
            item.put("accuracy_score", num(r.get("accuracy_score")));
            item.put("latency_ms", r.get("latency_ms") == null ? 0 : ((Number) r.get("latency_ms")).intValue());
            item.put("cost_per_request", num(r.get("cost_per_request")));
            item.put("tokens_per_second", num(r.get("tokens_per_second")));
            item.put("response_quality", num(r.get("response_quality")));
            item.put("citation_rate", num(r.get("citation_rate")));
            item.put("tested_at", ts(r.get("tested_at")));
            models.add(item);
        }

        // En iyi skorlar
        Double bestAccuracy = null, bestTokens = null, bestQuality = null, bestCitation = null;
        Double bestLatency = null, bestCost = null;
        for (Map<String, Object> m : models) {
            double acc = num(m.get("accuracy_score"));
            int lat = m.get("latency_ms") == null ? 0 : ((Number) m.get("latency_ms")).intValue();
            double cost = num(m.get("cost_per_request"));
            double tok = num(m.get("tokens_per_second"));
            double qual = num(m.get("response_quality"));
            double cit = num(m.get("citation_rate"));
            if (bestAccuracy == null || acc > bestAccuracy) {
                bestAccuracy = acc;
            }
            if (bestLatency == null || lat < bestLatency) {
                bestLatency = (double) lat;
            }
            if (bestCost == null || cost < bestCost) {
                bestCost = cost;
            }
            if (bestTokens == null || tok > bestTokens) {
                bestTokens = tok;
            }
            if (bestQuality == null || qual > bestQuality) {
                bestQuality = qual;
            }
            if (bestCitation == null || cit > bestCitation) {
                bestCitation = cit;
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("best_accuracy", String.format(Locale.US, "%.2f", bestAccuracy == null ? 0 : bestAccuracy));
        summary.put("best_latency_ms", String.format(Locale.US, "%.0f", bestLatency == null ? 0 : bestLatency));
        summary.put("best_cost_per_req", String.format(Locale.US, "%.4f", bestCost == null ? 0 : bestCost));
        summary.put("best_tokens_per_sec", String.format(Locale.US, "%.1f", bestTokens == null ? 0 : bestTokens));
        summary.put("best_quality", String.format(Locale.US, "%.2f", bestQuality == null ? 0 : bestQuality));
        summary.put("best_citation_rate", String.format(Locale.US, "%.2f", bestCitation == null ? 0 : bestCitation));

        return ResponseEntity.ok(Map.of(
                "models", models,
                "summary", summary,
                "count", models.size()));
    }

    // ---------- yardımcılar ----------

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static double num(Object o) {
        return o == null ? 0 : ((Number) o).doubleValue();
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
