package dev.geolens.governance;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Go {@code governance/usage_test.go} portu. */
class UsageRecorderTest {

    @Test
    void newUsageRecorderNotNull() {
        assertNotNull(new UsageRecorder(null, null));
    }

    @Test
    void metricConstants() {
        assertEquals("engine_calls", UsageRecorder.METRIC_ENGINE_CALLS);
        assertEquals("api_requests", UsageRecorder.METRIC_API_REQUESTS);
        assertEquals("storage_bytes", UsageRecorder.METRIC_STORAGE_BYTES);
        assertEquals("scores_computed", UsageRecorder.METRIC_SCORES_COMPUTED);
    }

    @Test
    void recordUsageNilPool() {
        UsageRecorder ur = new UsageRecorder(null, null);
        // Go: hata beklenir (nil pool — panic olmaz). Java: GovernanceException fırlatır.
        assertThrows(GovernanceException.class,
                () -> ur.recordUsage("tenant-1", UsageRecorder.METRIC_ENGINE_CALLS, 1, "brand", "brand-1"));
    }

    @Test
    void incrementUsageNilPool() {
        UsageRecorder ur = new UsageRecorder(null, null);
        assertThrows(GovernanceException.class,
                () -> ur.incrementUsage("tenant-1", UsageRecorder.METRIC_API_REQUESTS, "api", "req-1"));
    }

    @Test
    void getUsageSummaryNilPool() {
        UsageRecorder ur = new UsageRecorder(null, null);
        assertThrows(GovernanceException.class,
                () -> ur.getUsageSummary("tenant-1", Instant.parse("2024-01-01T00:00:00Z")));
    }
}