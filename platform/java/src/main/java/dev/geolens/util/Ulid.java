package dev.geolens.util;

import java.security.SecureRandom;

/** ULID üretici — Go {@code internal/id} portu (oklog/ulid uyumlu: 48-bit ms + 80-bit rastgele). */
public final class Ulid {

    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final char[] CROCKFORD = ALPHABET.toCharArray();
    private static final SecureRandom RNG = new SecureRandom();

    private Ulid() {
    }

    public static String generate() {
        long now = System.currentTimeMillis();

        StringBuilder sb = new StringBuilder(26);
        // 48-bit zaman damgası: 10 karakter (her biri 5 bit)
        for (int i = 9; i >= 0; i--) {
            int idx = (int) ((now >>> (i * 5)) & 0x1F);
            sb.append(CROCKFORD[idx]);
        }
        // 80-bit rastgele: 16 karakter
        for (int i = 0; i < 16; i++) {
            sb.append(CROCKFORD[RNG.nextInt(32)]);
        }
        return sb.toString();
    }
}