package dev.geolens.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Cron zamanlama yardımcıları — Go {@code isDue} karşılığı (robfig/cron standard 5 alan).
 * <p>Go standard ifadeleri 5 alandır ("0 9 * * 1"); Spring {@link CronExpression} 6 alan
 * beklediğinden 5 alanlı ifadelere öne "0 " (saniye) eklenir. Geçersiz ifadede Go gibi
 * 1 saat varsayılan eşik kullanılır.
 */
public final class CronDue {

    private static final Logger log = LoggerFactory.getLogger(CronDue.class);

    private CronDue() {
    }

    /** Panel ölçüm zamanı gelmiş mi? — Go {@code isDue(cronExpr, lastMeasuredAt)} portu. */
    public static boolean isDue(String cronExpr, OffsetDateTime lastMeasuredAt) {
        if (cronExpr == null || cronExpr.isBlank()) {
            return true;
        }
        CronExpression expr;
        try {
            expr = CronExpression.parse(normalize(cronExpr));
        } catch (IllegalArgumentException e) {
            log.warn("geçersiz cron ifadesi, varsayılan 1 saat kullanılıyor", "cron", cronExpr, "error", e.getMessage());
            return Duration.between(lastMeasuredAt, OffsetDateTime.now()).toSeconds() > 3600;
        }
        OffsetDateTime next = expr.next(lastMeasuredAt);
        if (next == null) {
            return false;
        }
        return OffsetDateTime.now(ZoneOffset.UTC).isAfter(next);
    }

    /** 5 alanlı (Go robfig/cron) ifadeyi 6 alanlı (Spring) ifadeye çevirir. */
    public static String normalize(String cronExpr) {
        String trimmed = cronExpr.trim();
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 5) {
            return "0 " + trimmed;
        }
        return trimmed;
    }
}
