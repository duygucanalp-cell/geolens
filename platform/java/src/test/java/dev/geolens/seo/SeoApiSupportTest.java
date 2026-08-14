package dev.geolens.seo;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SeoApiSupport birim testleri — Go retry/parse yardımcıları parity. */
class SeoApiSupportTest {

    @Test
    void retryableStatusTrueForTransientErrors() {
        assertTrue(SeoApiSupport.retryableStatus(429));
        assertTrue(SeoApiSupport.retryableStatus(500));
        assertTrue(SeoApiSupport.retryableStatus(502));
        assertTrue(SeoApiSupport.retryableStatus(503));
        assertTrue(SeoApiSupport.retryableStatus(504));
    }

    @Test
    void retryableStatusFalseForOthers() {
        assertFalse(SeoApiSupport.retryableStatus(200));
        assertFalse(SeoApiSupport.retryableStatus(400));
        assertFalse(SeoApiSupport.retryableStatus(401));
        assertFalse(SeoApiSupport.retryableStatus(404));
    }

    @Test
    void doWithRetryRetriesUntilSuccess() {
        AtomicInteger calls = new AtomicInteger();
        SeoApiSupport.doWithRetry(4, () -> {
            if (calls.incrementAndGet() < 3) {
                return SeoApiSupport.RetryOutcome.retryAgain();
            }
            return SeoApiSupport.RetryOutcome.done();
        });
        assertEquals(3, calls.get(), "retry başarıyla tamamlanmalı");
    }

    @Test
    void doWithRetryStopsAfterAttempts() {
        AtomicInteger calls = new AtomicInteger();
        SeoApiSupport.doWithRetry(4, () -> {
            calls.incrementAndGet();
            return SeoApiSupport.RetryOutcome.retryAgain();
        });
        assertEquals(4, calls.get(), "4 denemeden sonra durmalı");
    }

    @Test
    void parseInt64AndParseFloat() {
        assertEquals(42, SeoApiSupport.parseInt64("42"));
        assertEquals(0, SeoApiSupport.parseInt64("abc"));
        assertEquals(0, SeoApiSupport.parseInt64(""));
        assertEquals(3.14, SeoApiSupport.parseFloat("3.14"), 1e-9);
        assertEquals(0, SeoApiSupport.parseFloat("xyz"), 1e-9);
    }
}
