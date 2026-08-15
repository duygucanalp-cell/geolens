package dev.geolens.usage.service;

import dev.geolens.common.ServiceException;

import dev.geolens.usage.web.UsageMetricRequest;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.geolens.jooq.usage.tables.Metrics.METRICS;

/**
 * Kullanım analitiği iş mantığı — Go {@code usage.handler} portu (R12).
 * <p>Metrik kaydı, liste ve özet sorguları (ADR-014 v4.0 tip güvenli jOOQ DSL)
 * bu servistedir; controller yalnızca HTTP katmanıdır
 * (route'lar: POST /v1/usage/metrics, GET /v1/usage/metrics, GET /v1/usage/summary).
 */
@Service
public class UsageService {

    private final DSLContext dsl;

    public UsageService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Map<String, Object> recordUsage(String tenantId, UsageMetricRequest req) {
        String method = req.method() == null || req.method().isBlank() ? "GET" : req.method();
        int statusCode = req.statusCode() == 0 ? 200 : req.statusCode();
        int latency = req.latencyMs();
        String userId = req.userId() == null ? "" : req.userId();

        String entryId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        try {
            dsl.insertInto(METRICS)
                    .columns(List.of(METRICS.ID, METRICS.TENANT_ID, METRICS.ENDPOINT, METRICS.METHOD,
                            METRICS.STATUS_CODE, METRICS.LATENCY_MS, METRICS.USER_ID,
                            METRICS.REQUEST_SIZE, METRICS.RESPONSE_SIZE, METRICS.RECORDED_AT))
                    .values(entryId, tenantId, req.endpoint(), method, statusCode,
                            latency, userId, req.requestSize(), req.responseSize(),
                            now.atOffset(ZoneOffset.UTC))
                    .execute();
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "kullanım kaydedilemedi");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entry_id", entryId);
        body.put("endpoint", req.endpoint());
        body.put("method", method);
        body.put("latency_ms", latency);
        body.put("recorded_at", DateTimeFormatter.ISO_INSTANT.format(now));
        return body;
    }

    public Map<String, Object> listUsage(String tenantId, String limitParam) {
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
            rows = dsl.select(List.of(METRICS.ID, METRICS.ENDPOINT, METRICS.METHOD, METRICS.STATUS_CODE,
                            METRICS.LATENCY_MS, METRICS.RECORDED_AT))
                    .from(METRICS)
                    .where(METRICS.TENANT_ID.eq(tenantId))
                    .orderBy(METRICS.RECORDED_AT.desc())
                    .limit(limit + 1)
                    .fetch().intoMaps();
        } catch (RuntimeException e) {
            return Map.of("data", List.of(), "has_more", false);
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
        return body;
    }

    public Map<String, Object> getUsageSummary(String tenantId, String periodParam) {
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
            Field<OffsetDateTime> cutoff = intervalCutoff(interval);
            Record aggRec = dsl.select(List.of(
                            DSL.count().as("total"),
                            DSL.avg(DSL.when(METRICS.STATUS_CODE.greaterOrEqual(400), 1.0).otherwise(0.0))
                                    .coalesce(BigDecimal.ZERO).multiply(100).as("error_rate"),
                            DSL.avg(METRICS.LATENCY_MS).coalesce(BigDecimal.ZERO).as("avg_latency")))
                    .from(METRICS)
                    .where(METRICS.TENANT_ID.eq(tenantId).and(METRICS.RECORDED_AT.gt(cutoff)))
                    .fetchOne();
            Map<String, Object> agg = aggRec == null ? null : aggRec.intoMap();
            totalRequests = ((Number) agg.get("total")).doubleValue();
            totalErrors = ((Number) agg.get("error_rate")).doubleValue();
            avgLatency = ((Number) agg.get("avg_latency")).doubleValue();
        } catch (RuntimeException e) {
            // sorgu hatasında sıfır değerler döner (Go scan err guard)
        }

        List<Map<String, Object>> topEndpoints = new ArrayList<>();
        try {
            Field<OffsetDateTime> cutoff = intervalCutoff(interval);
            List<Map<String, Object>> rows = dsl.select(List.of(
                            METRICS.ENDPOINT,
                            DSL.count().as("hits"),
                            DSL.avg(METRICS.LATENCY_MS).coalesce(BigDecimal.ZERO).as("avg_latency")))
                    .from(METRICS)
                    .where(METRICS.TENANT_ID.eq(tenantId).and(METRICS.RECORDED_AT.gt(cutoff)))
                    .groupBy(METRICS.ENDPOINT)
                    .orderBy(DSL.field("hits").desc())
                    .limit(10)
                    .fetch().intoMaps();
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
        return body;
    }

    /** {@code NOW() - ?::INTERVAL} karşılığı — PG'ye özgü cast, şablon field ile. */
    private static Field<OffsetDateTime> intervalCutoff(String interval) {
        return DSL.currentTimestamp()
                .minus(DSL.field("{0}::INTERVAL", org.jooq.types.DayToSecond.class, DSL.inline(interval)))
                .cast(OffsetDateTime.class);
    }
}
