package dev.geolens.usage.web;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kullanım analitiği REST controller'ı — Go {@code usage.handler} portu.
 * <p>Route'lar (go cmd/api): POST /v1/usage/metrics, GET /v1/usage/metrics,
 * GET /v1/usage/summary (R12).
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir.
 */
@RestController
public class UsageController {

    private final DSLContext dsl;

    public UsageController(DSLContext dsl) {
        this.dsl = dsl;
    }

    @PostMapping("/v1/usage/metrics")
    public ResponseEntity<?> recordUsage(@RequestHeader("X-Tenant-ID") String tenantId,
                                         @RequestBody UsageMetricRequest req) {
        if (req == null || req.endpoint() == null || req.endpoint().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "endpoint gerekli");
        }
        String method = req.method() == null || req.method().isBlank() ? "GET" : req.method();
        int statusCode = req.statusCode() == 0 ? 200 : req.statusCode();
        int latency = req.latencyMs();
        String userId = req.userId() == null ? "" : req.userId();

        String entryId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        try {
            dsl.execute("""
                    INSERT INTO usage.metrics (id, tenant_id, endpoint, method, status_code, latency_ms, user_id, request_size, response_size, recorded_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, entryId, tenantId, req.endpoint(), method, statusCode,
                    latency, userId, req.requestSize(), req.responseSize(), now);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "kullanım kaydedilemedi");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entry_id", entryId);
        body.put("endpoint", req.endpoint());
        body.put("method", method);
        body.put("latency_ms", latency);
        body.put("recorded_at", DateTimeFormatter.ISO_INSTANT.format(now));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/v1/usage/metrics")
    public ResponseEntity<?> listUsage(@RequestHeader("X-Tenant-ID") String tenantId,
                                       @RequestParam(value = "limit", required = false) String limitParam) {
        int limit = 20;
        if (limitParam != null && !limitParam.isBlank()) {
            try {
                int n = Integer.parseInt(limitParam);
                if (n >= 1 && n <= 100) {
                    limit = n;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        List<Map<String, Object>> rows;
        try {
            rows = list("""
                    SELECT id, endpoint, method, status_code, latency_ms, recorded_at
                    FROM usage.metrics WHERE tenant_id = ? ORDER BY recorded_at DESC LIMIT ?
                    """, tenantId, limit + 1);
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("data", List.of(), "has_more", false));
        }

        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, limit));
        }

        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.get("id"));
            item.put("endpoint", r.get("endpoint"));
            item.put("method", r.get("method"));
            item.put("status_code", r.get("status_code"));
            item.put("latency_ms", r.get("latency_ms"));
            item.put("recorded_at", r.get("recorded_at") == null ? null : String.valueOf(r.get("recorded_at")));
            data.add(item);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        body.put("has_more", hasMore);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/v1/usage/summary")
    public ResponseEntity<?> getUsageSummary(@RequestHeader("X-Tenant-ID") String tenantId,
                                             @RequestParam(value = "period", required = false) String periodParam) {
        String period = periodParam == null || periodParam.isBlank() ? "7d" : periodParam;
        String interval;
        switch (period) {
            case "30d":
                interval = "30 days";
                break;
            case "90d":
                interval = "90 days";
                break;
            case "1d":
                interval = "1 day";
                break;
            default:
                interval = "7 days";
        }

        double totalRequests = 0;
        double totalErrors = 0;
        double avgLatency = 0;
        try {
            Map<String, Object> agg = map("""
                    SELECT COUNT(*) AS total, COALESCE(AVG(CASE WHEN status_code >= 400 THEN 1.0 ELSE 0 END), 0) * 100 AS error_rate,
                           COALESCE(AVG(latency_ms), 0) AS avg_latency
                    FROM usage.metrics WHERE tenant_id = ? AND recorded_at > NOW() - ?::INTERVAL
                    """, tenantId, interval);
            totalRequests = ((Number) agg.get("total")).doubleValue();
            totalErrors = ((Number) agg.get("error_rate")).doubleValue();
            avgLatency = ((Number) agg.get("avg_latency")).doubleValue();
        } catch (RuntimeException e) {
            // sorgu hatasında sıfır değerler döner (Go scan err guard)
        }

        List<Map<String, Object>> topEndpoints = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = list("""
                    SELECT endpoint, COUNT(*) AS hits, COALESCE(AVG(latency_ms), 0) AS avg_latency
                    FROM usage.metrics WHERE tenant_id = ? AND recorded_at > NOW() - ?::INTERVAL
                    GROUP BY endpoint ORDER BY hits DESC LIMIT 10
                    """, tenantId, interval);
            for (Map<String, Object> r : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("endpoint", r.get("endpoint"));
                item.put("hits", r.get("hits"));
                item.put("avg_latency_ms", r.get("avg_latency"));
                topEndpoints.add(item);
            }
        } catch (RuntimeException ignored) {
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("period", period);
        body.put("total_requests", totalRequests);
        body.put("error_rate_pct", totalErrors);
        body.put("avg_latency_ms", avgLatency);
        body.put("top_endpoints", topEndpoints);
        return ResponseEntity.ok(body);
    }

    /** ADR-014: plain SQL üzerinden jOOQ — satır erişimi Map ile korunur. */
    private List<Map<String, Object>> list(String sql, Object... args) {
        return dsl.fetch(sql, args).intoMaps();
    }

    private Map<String, Object> map(String sql, Object... args) {
        Record r = dsl.fetchOne(sql, args);
        return r == null ? null : r.intoMap();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
