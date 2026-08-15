package dev.geolens.gate.service;

import java.util.List;
import java.util.Map;

/** Gate check geçmişi sonucu. */
public record GateHistoryResult(
        String entityId,
        String tenantId,
        List<Map<String, Object>> history,
        boolean hasMore) {

    public static GateHistoryResult empty() {
        return new GateHistoryResult("", "", List.of(), false);
    }
}
