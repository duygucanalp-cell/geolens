package dev.geolens.cost.service;

import dev.geolens.cost.web.RecordCostRequest;
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
import java.util.Map;

/**
 * Maliyet analitiği iş mantığı — Go {@code cost.handler} portu (R11).
 * <p>Maliyet kaydı, liste ve özet işlemlerini yapar. Controller yalnızca HTTP katmanıdır;
 * bu sınıf DB erişimini (DSLContext) içerir.
 */
@Service
public class CostService {

    private final DSLContext dsl;

    public CostService(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Go {@code recordCost} karşılığı — maliyet girişi kaydeder, oluşturulan kaydı döner. */
    public Map<String, Object> recordCost(String tenantId, RecordCostRequest req) {
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
            throw new CostServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "maliyet kaydedilemedi");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entry_id", entryId);
        body.put("engine_name", req.engineName());
        body.put("model_name", nz(req.modelName()));
        body.put("cost_usd", req.costUsd());
        body.put("token_count", req.tokenCount());
        body.put("recorded_at", DateTimeFormatter.ISO_INSTANT.format(now));
        return body;
    }

    /** Go {@code listCosts} karşılığı — sorgu hatasında boş liste döner. */
    public Map<String, Object> listCosts(String tenantId, int limit, String engine) {
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
            return Map.of("data", List.of(), "has_more", false);
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

        return Map.of(
                "data", entries,
                "has_more", hasMore);
    }

    /** Go {@code getCostSummary} karşılığı — özet; sorgu hatasında 0/boş döner. */
    public Map<String, Object> getCostSummary(String tenantId, String period) {
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
        return body;
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
