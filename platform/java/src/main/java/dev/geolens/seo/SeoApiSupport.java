package dev.geolens.seo;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * SEO API yardımcıları — Go {@code doWithRetry}/{@code retryableStatus}/{@code parseInt64}/
 * {@code parseFloat} portu. HT2 sertleştirme: API rate limit ve geçici hatalar için
 * exponential backoff'lu smart retry (1s, 2s, 4s, 8s + jitter, en fazla 4 deneme).
 */
public final class SeoApiSupport {

    private SeoApiSupport() {
    }

    /**
     * {@code fn} çalıştırır; retryable hatalarda (status-aware) exponential backoff ile
     * 4 denemeye kadar yeniden dener. Go {@code doWithRetry} portu.
     */
    public static void doWithRetry(int attempts, Retryable fn) {
        RetryOutcome first = fn.run();
        if (!first.retry()) {
            return;
        }
        long backoffMs = 1000;
        for (int i = 2; i <= attempts; i++) {
            sleep(backoffMs + ThreadLocalRandom.current().nextInt(500));
            RetryOutcome out = fn.run();
            if (!out.retry()) {
                return;
            }
            backoffMs = Math.min(backoffMs * 2, 8000);
        }
    }

    /** Retryable HTTP durumları — Go {@code retryableStatus} portu (429, 5xx). */
    public static boolean retryableStatus(int code) {
        return code == 429 || code == 500 || code == 502 || code == 503 || code == 504;
    }

    public static long parseInt64(String s) {
        if (s == null || s.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static double parseFloat(String s) {
        if (s == null || s.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Retry sonucu — {@code retry=true} ise yeniden denenir (Go'daki (bool, error) karşılığı). */
    public record RetryOutcome(boolean retry) {
        public static RetryOutcome done() {
            return new RetryOutcome(false);
        }

        public static RetryOutcome retryAgain() {
            return new RetryOutcome(true);
        }
    }

    @FunctionalInterface
    public interface Retryable {
        RetryOutcome run();
    }
}
