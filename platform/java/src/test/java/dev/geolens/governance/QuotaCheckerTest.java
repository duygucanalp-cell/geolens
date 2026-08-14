package dev.geolens.governance;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Go {@code governance/quota_test.go} portu. */
class QuotaCheckerTest {

    @Test
    void newQuotaCheckerNotNull() {
        assertNotNull(new QuotaChecker(null, null));
    }

    @Test
    void defaultBuckets() {
        assertEquals(3, QuotaChecker.DEFAULT_BUCKETS.size());

        Map<String, Long> expected = Map.of(
                "engine_calls_per_min", 30L,
                "engine_calls_per_hour", 500L,
                "api_requests_per_hour", 1000L);

        for (QuotaChecker.BucketConfig b : QuotaChecker.DEFAULT_BUCKETS) {
            assertTrue(expected.containsKey(b.bucketName()), "beklenmeyen bucket: " + b.bucketName());
            assertEquals(expected.get(b.bucketName()), b.maxTokens(),
                    "bucket " + b.bucketName() + " max_tokens");
        }
    }

    @Test
    void ensureBucketsNilPool() {
        QuotaChecker qc = new QuotaChecker(null, null);
        // Go: nil pool no-op (sadece uyarı) — hata fırlatmaz.
        qc.ensureBuckets("tenant-1");
    }

    @Test
    void checkAndConsumeNilPool() {
        QuotaChecker qc = new QuotaChecker(null, null);
        assertTrue(qc.checkAndConsume("tenant-1", "engine_calls_per_min"), "nil pool fallback izin verir");
    }
}