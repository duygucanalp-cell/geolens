package dev.geolens.scheduler;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link CronDue} birim testi — Go {@code isDue} davranışı karşılığı. */
class CronDueTest {

    @Test
    void bosCronHerZamanDue() {
        assertTrue(CronDue.isDue("", OffsetDateTime.now().minusDays(1)));
        assertTrue(CronDue.isDue(null, OffsetDateTime.now().minusDays(1)));
    }

    @Test
    void beisAlanliIfadeNormalizeEdilir() {
        assertEquals("0 0 9 * * 1", CronDue.normalize("0 9 * * 1"));
        assertEquals("0 0 9 * * 1", CronDue.normalize(" 0 9 * * 1 "));
        assertEquals("0 30 * * * *", CronDue.normalize("0 30 * * * *"));
    }

    @Test
    void gecmisZamanDueOlur() {
        // Günlük gece yarısı cron'u: son ölçüm 400 gün önce → bir sonraki eşleşme çoktan geçti
        assertTrue(CronDue.isDue("0 0 0 * * *", OffsetDateTime.now(ZoneOffset.UTC).minusDays(400)));
    }

    @Test
    void gelecekZamanDueDegildir() {
        // 29 Şubat cron'u: son ölçüm 400 gün önce olsa bile bir sonraki eşleşme gelecekte
        assertFalse(CronDue.isDue("0 0 0 29 2 *", OffsetDateTime.now(ZoneOffset.UTC).minusDays(400)));
    }

    @Test
    void gecersizIfadeBirSaatEsiginiKullanir() {
        // Geçersiz ifade: son ölçüm 2 saat önce → due (1 saat eşiği aşıldı)
        assertTrue(CronDue.isDue("not-a-cron", OffsetDateTime.now(ZoneOffset.UTC).minusHours(2)));
        // 30 dakika önce → henüz due değil
        assertFalse(CronDue.isDue("not-a-cron", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(30)));
    }
}
