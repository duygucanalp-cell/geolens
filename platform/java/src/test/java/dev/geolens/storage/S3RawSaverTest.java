package dev.geolens.storage;

import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Go {@code platform/storage} portu — anahtar deseni doğrulaması (minio-java çağrısı
 * gerektirmez; bucket işlemleri entegrasyon kapsamında).
 */
class S3RawSaverTest {

    @Test
    void buildKeyFollowsGoPattern() {
        ZonedDateTime now = ZonedDateTime.of(2026, 8, 15, 10, 30, 45, 123456789, ZoneOffset.UTC);
        String key = S3RawSaver.buildKey("T01", "WS01", "claude", now);

        // raw/{tenant}/{workspace}/{engine}/{yyyy/MM/dd}/{HHmmss}-{hex8}.json
        assertTrue(key.startsWith("raw/T01/WS01/claude/2026/08/15/103045-"), key);
        assertTrue(key.endsWith(".json"), key);
        // Go: now.UnixNano() hex'inin ilk 8 karakteri
        long unixNano = now.toEpochSecond() * 1_000_000_000L + now.getNano();
        String expectedHex = Long.toHexString(unixNano).substring(0, 8);
        assertEquals("raw/T01/WS01/claude/2026/08/15/103045-" + expectedHex + ".json", key);
    }
}
