package dev.geolens.benchmark.service;

import dev.geolens.common.ServiceException;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.benchmark.web.RunBenchmarkRequest;
import dev.geolens.util.Ulid;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Model benchmark iş mantığı — Go {@code benchmark.handler} portu (R10).
 * <p>Benchmark kaydı, geçmiş listeleme ve model karşılaştırmasını yapar.
 * Controller yalnızca HTTP katmanıdır; bu sınıf DB erişimini içerir (ADR-014).
 */
@Service
public class BenchmarkService {

    private final DSLContext dsl;
    private final ObjectMapper mapper = new ObjectMapper();

    public BenchmarkService(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Go {@code runBenchmark} karşılığı — benchmark kaydını oluşturur ve gövdeyi döner. */
    public Map<String, Object> runBenchmark(String tenantId, RunBenchmarkRequest req) {
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
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "benchmark kaydedilemedi");
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
        return body;
    }

    /** Go {@code listBenchmarks} karşılığı — tenant benchmark geçmişini döner. */
    public List<Map<String, Object>> listBenchmarks(String tenantId, int limit, int offset,
                                                     String engine, String category) {
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
            rows = list(query.toString(), args.toArray());
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "benchmark geçmişi alınamadı");
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
        return benchmarks;
    }

    /** Go {@code compareModels} karşılığı — engine başına en iyi model ve özet döner. */
    public Map<String, Object> compareModels(String tenantId, String enginesRaw) {
        // Engine listesini parse et (boşlukları temizle)
        List<String> engines = new ArrayList<>();
        for (String p : enginesRaw.split(",")) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                engines.add(trimmed);
            }
        }
        if (engines.isEmpty()) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "geçerli engine adı gerekli");
        }

        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT DISTINCT ON (engine_name) engine_name, model_name, accuracy_score, latency_ms,
                                     cost_per_request, tokens_per_second, response_quality, citation_rate, tested_at
                    FROM benchmark.models WHERE tenant_id = ? AND engine_name = ANY(?)
                    ORDER BY engine_name, tested_at DESC
                    """, tenantId, engines.toArray(new String[0]));
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "karşılaştırma alınamadı");
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

        return Map.of(
                "models", models,
                "summary", summary,
                "count", models.size());
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
