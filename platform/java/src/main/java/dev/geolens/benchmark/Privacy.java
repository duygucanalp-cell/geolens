package dev.geolens.benchmark;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Differansiyel gizlilik yardımcıları — Go {@code benchmark} paketi portu (FR-D5).
 * <p>Laplace mekanizması (inverse CDF): {@code F^(-1)(p) = -b * sign(p-0.5) * ln(1 - 2*|p-0.5|)}.
 */
public final class Privacy {

    private Privacy() {
    }

    /** Laplace gürültüsü ekler ve [ClampMin, ClampMax] aralığına kırpar. Epsilon ≤ 0 ise gürültü yok. */
    public static double addLaplaceNoise(double value, DpConfig config) {
        if (config.epsilon <= 0) {
            return clamp(value, config.clampMin, config.clampMax);
        }
        double scale = config.sensitivity / config.epsilon;
        double noise = laplaceRandom(scale);
        return clamp(value + noise, config.clampMin, config.clampMax);
    }

    /** Laplace(0, scale) dağılımından örnekleme — inverse CDF. */
    public static double laplaceRandom(double scale) {
        // u ∈ (0, 1) — ln(0) sınırından kaçınmak için dışlayıcı
        double u = ThreadLocalRandom.current().nextDouble();
        if (u <= 0) {
            u = 1e-16;
        }
        u -= 0.5;
        return -scale * Math.copySign(Math.log(1 - 2 * Math.abs(u)), u);
    }

    public static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
