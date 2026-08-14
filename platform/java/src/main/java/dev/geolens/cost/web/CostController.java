package dev.geolens.cost.web;

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
import java.util.Map;

/**
 * Maliyet analitiği REST controller'ı — Go {@code cost.handler} portu (R11).
 * <p>Route'lar (go cmd/api): POST /v1/costs/entries, GET /v1/costs/entries, GET /v1/costs/summary.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir.
 */
@RestController
@RequestMapping("/v1/costs")
public class CostController {

    private final DSLContext dsl;

    public CostController(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ---------- RecordCost ----------

    @PostMapping("/entries")
    public ResponseEntity<?> recordCost(@RequestHeader("X-Tenant-ID") String tenantId,
                                        @RequestBody RecordCostRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        if (req.engineName() == null || req.engineName().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "engine_name gerekli");
        }
        String operation = req.operation() == null || req.operation().isBlank() ? "other" : req.operation();

        String entryId = Ulid.generate();
        Instant now = Instant.now();

        try {
            dsl.execute("""
                    INSERT INTO cost.entries (id, tenant_id, engine_name, model_name, operation,
                                              token_count, input_tokens, output_tokens, cost_usd, recorded_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, entryId, tenantId, req.engineName(), nz(req.modelName()), operation,
                    req.tokenCount(), req.inputTokens(), req.outputTokens(), req.costUsd(),
                    java.sql.Timestamp.from(now));
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "maliyet kaydedilemedi");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entry_id", entryId);
        body.put("engine_name", req.engineName());
        body.put("model_name", nz(req.modelName()));
        body.put("cost_usd", req.costUsd());
        body.put("token_count", req.tokenCount());
        body.put("recorded_at", DateTimeFormatter.ISO_INSTANT.format(now));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    // ---------- ListCosts ----------

    @GetMapping("/entries")
    public ResponseEntity<?> listCosts(@RequestHeader("X-Tenant-ID") String tenantId,
                                       @RequestParam(value = "limit", required = false, defaultValue = "20") int limit,
                                       @RequestParam(value = "engine", required = false) String engine) {
        int lim = limit;
        if (lim < 1 || lim > 100) {
            lim = 20;
        }
        String engineFilter = engine == null ? "" : engine;

        // LIMIT+1 pattern for has_more
        StringBuilder query = new StringBuilder("""
                SELECT id, engine_name, model_name, operation, token_count, cost_usd, recorded_at
                FROM cost.entries WHERE tenant_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);

        if (!engineFilter.isEmpty()) {
            query.append(" AND engine_name = ?");
            args.add(engineFilter);
        }
        query.append(" ORDER BY recorded_at DESC LIMIT ?");
        args.add(lim + 1);

        List<Map<String, Object>> rows;
        try {
            rows = dsl.fetch(query.toString(), args.toArray()).intoMaps();
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("data", List.of(), "has_more", false));
        }

        boolean hasMore = rows.size() > lim;
        if (hasMore) {
            rows = rows.subList(0, lim);
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", str(r.get("id")));
            item.put("engine_name", str(r.get("engine_name")));
            item.put("model_name", str(r.get("model_name")));
            item.put("operation", str(r.get("operation")));
            item.put("token_count", r.get("token_count") == null ? 0 : ((Number) r.get("token_count")).intValue());
            item.put("cost_usd", num(r.get("cost_usd")));
            item.put("recorded_at", ts(r.get("recorded_at")));
            entries.add(item);
        }

        return ResponseEntity.ok(Map.of(
                "data", entries,
                "has_more", hasMore));
    }

    // ---------- GetCostSummary ----------

    @GetMapping("/summary")
    public ResponseEntity<?> getCostSummary(@RequestHeader("X-Tenant-ID") String tenantId,
                                            @RequestParam(value = "period", required = false, defaultValue = "7d") String period) {
        String interval = switch (period == null ? "" : period) {
            case "30d" -> "30 days";
            case "90d" -> "90 days";
            case "1d" -> "1 day";
            default -> "7 days";
        };

        double totalCost = 0;
        int totalTokens = 0;
        try {
            Record r = dsl.fetchOne("""
                    SELECT COALESCE(SUM(cost_usd), 0), COALESCE(SUM(token_count), 0)
                    FROM cost.entries WHERE tenant_id = ? AND recorded_at > NOW() - ?::INTERVAL
                    """, tenantId, interval);
            if (r != null) {
                totalCost = num(r.get(0));
                totalTokens = r.get(1) == null ? 0 : ((Number) r.get(1)).intValue();
            }
        } catch (RuntimeException e) {
            // uyarı — Go: slog.Warn("cost summary sorgu hatası"); 0 döner
        }

        List<Map<String, Object>> breakdown = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = list("""
                    SELECT engine_name, COALESCE(SUM(cost_usd), 0) AS total, COALESCE(SUM(token_count), 0) AS tokens
                    FROM cost.entries WHERE tenant_id = ? AND recorded_at > NOW() - ?::INTERVAL
                    GROUP BY engine_name ORDER BY total DESC
                    """, tenantId, interval);
            for (Map<String, Object> r : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("engine", str(r.get("engine_name")));
                item.put("cost", num(r.get("total")));
                item.put("tokens", r.get("tokens") == null ? 0 : ((Number) r.get("tokens")).intValue());
                breakdown.add(item);
            }
        } catch (RuntimeException e) {
            // uyarı — Go: slog.Warn("cost breakdown sorgu hatası"); boş breakdown
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("period", period == null ? "7d" : period);
        body.put("total_cost_usd", totalCost);
        body.put("total_tokens", totalTokens);
        body.put("engine_breakdown", breakdown);
        return ResponseEntity.ok(body);
    }

    // ---------- yardımcılar ----------

    private static String nz(String s) {
        return s == null ? "" : s;
    }

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

    private List<Map<String, Object>> list(String sql, Object... args) {
        return dsl.fetch(sql, args).intoMaps();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
