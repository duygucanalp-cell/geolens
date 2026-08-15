package dev.geolens.explain.service;

import java.util.List;
import java.util.Map;

/** Explain analiz geçmişi sonucu. */
public record ExplainHistoryResult(
        List<Map<String, Object>> data,
        boolean hasMore) {

    public static ExplainHistoryResult of(int limit, List<Map<String, Object>> rows) {
        boolean hasMore = rows.size() > limit;
        List<Map<String, Object>> data = hasMore ? new java.util.ArrayList<>(rows.subList(0, limit)) : rows;
        return new ExplainHistoryResult(data, hasMore);
    }
}
