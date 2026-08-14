package dev.geolens.drift;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Drift istatistik hesaplamaları — Go {@code drift} paketi portu (R17).
 * <p>Z-skoru yaklaşımı: sapma referans standart sapmasıyla normalleştirilir ve 0-100
 * aralığına ölçeklenir ({@link #computeDriftScore}).
 */
public final class DriftAnalyzer {

    private DriftAnalyzer() {
    }

    /** Skor 0-100 aralığına ölçeklenir: {@code min(100, |delta|/std * 25)}, 2 ondalığa yuvarlanır. */
    public static DriftResult computeDriftScore(List<Double> refVals, List<Double> curVals) {
        if (refVals.isEmpty() || curVals.isEmpty()) {
            return new DriftResult(0, 0, 0, 0);
        }

        double refMean = mean(refVals);
        double curMean = mean(curVals);
        double delta = curMean - refMean;

        double std = stddev(refVals, refMean);
        if (std < 1e-6) {
            // Sabit referans: göreli sapma baz alınır
            double base = Math.abs(refMean);
            if (base < 1) {
                base = 1;
            }
            std = base * 0.1;
        }

        double z = Math.abs(delta) / std;
        double score = Math.min(100, z * 25);
        score = Math.round(score * 100) / 100.0;
        return new DriftResult(score, delta, refMean, curMean);
    }

    public static String severityFor(double score) {
        if (score >= 50) {
            return "critical";
        }
        if (score >= 20) {
            return "warning";
        }
        return "info";
    }

    public static double mean(List<Double> vals) {
        if (vals.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (double v : vals) {
            sum += v;
        }
        return sum / vals.size();
    }

    /** Örneklem standart sapması (n-1) — Go {@code stddev} portu. */
    public static double stddev(List<Double> vals, double m) {
        if (vals.size() < 2) {
            return 0;
        }
        double sq = 0;
        for (double v : vals) {
            double d = v - m;
            sq += d * d;
        }
        return Math.sqrt(sq / (vals.size() - 1));
    }

    /**
     * Deterministik outbox idempotency anahtarı — Go {@code driftIdempotencyKey} portu.
     * Aynı (entity, metric, skor, delta) kombinasyonu her zaman aynı anahtarı verir;
     * drift_score 2 ondalığa yuvarlandığı için aynı pencere tekrar analiz edilse bile sabittir.
     */
    public static String driftIdempotencyKey(String entityId, String metric, double score, double delta) {
        byte[] input = String.format("%s|%s|%.2f|%.2f", entityId, metric, score, delta)
                .getBytes(StandardCharsets.UTF_8);
        byte[] hash = sha256(input);
        StringBuilder hex = new StringBuilder(24);
        for (int i = 0; i < 12; i++) {
            hex.append(String.format("%02x", hash[i] & 0xFF));
        }
        return "drift:" + entityId + ":" + hex;
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 mevcut değil", e);
        }
    }

    /** Go'daki çoklu dönüş karşılığı: skor + delta + ortalama çifti. */
    public record DriftResult(double score, double delta, double refMean, double curMean) {
    }
}
