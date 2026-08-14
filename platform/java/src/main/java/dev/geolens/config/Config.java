package dev.geolens.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Çevre değişkenlerinden çözülen uygulama yapılandırması — Go {@code config.Config} portu
 * (skor ile ilgili alanlar). SCORE_WEIGHTS / SCORE_ALGORITHM_VERSION / INTENT_WEIGHT_SCALE /
 * SAMPLE_COUNT parselleme mantığı birebir taşınır.
 */
public class Config {

    /** SCORE_WEIGHTS — "presence,position,source,competitor" (varsayılan 35/25/20/20). */
    public String scoreWeightsRaw;

    /** SCORE_ALGORITHM_VERSION: "1.0.0"=eski 4 bileşenli, "2.0.0" (varsayılan)=7 bileşenli. */
    public String scoreAlgorithmVersion;

    /** INTENT_WEIGHT_SCALE: prompt intent'ine göre VI bileşen çarpanları (0421 A3-3). */
    public String intentWeightScaleRaw;

    /** SAMPLE_COUNT: motor başına örnekleme sayısı (varsayılan 3). */
    public int sampleCount;

    /** STRIPE_PRICE_IDS — "tier=priceId,tier=priceId,..." (varsayılan boş → Stripe default map). */
    public String stripePriceIdsRaw;

    public Config() {
        this.scoreAlgorithmVersion = "2.0.0";
        this.sampleCount = 3;
    }

    /** SCORE_WEIGHTS env'ini (4 bileşen) parseler — geçersiz girdide boş sonuç döner. */
    public ScoreWeightsV4 parseScoreWeights() {
        String raw = scoreWeightsRaw;
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(",");
        if (parts.length != 4) {
            return null;
        }
        double[] vals = new double[4];
        for (int i = 0; i < 4; i++) {
            try {
                vals[i] = Double.parseDouble(parts[i].trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return new ScoreWeightsV4(vals[0], vals[1], vals[2], vals[3]);
    }

    /** 7 bileşenli SCORE_WEIGHTS'ı parseler (A3-5, 0409 v1.3) — geçersiz girdide {@code null}. */
    public ScoreWeightsV2 parseScoreWeightsV2() {
        String raw = scoreWeightsRaw;
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(",");
        if (parts.length != 7) {
            return null;
        }
        double[] vals = new double[7];
        for (int i = 0; i < 7; i++) {
            try {
                vals[i] = Double.parseDouble(parts[i].trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        double sum = 0;
        for (double v : vals) {
            sum += v;
        }
        if (sum <= 0) {
            return null;
        }
        return new ScoreWeightsV2(vals[0], vals[1], vals[2], vals[3], vals[4], vals[5], vals[6]);
    }

    /**
     * STRIPE_PRICE_IDS env'ini ("tier=priceId,tier=priceId,...") Stripe fiyat haritasına
     * çözer — Go {@code config.Config.ParseStripePriceIDs} portu. Boş veya geçersiz girdide
     * {@code null} döner (Stripe default map kullanılır).
     */
    public Map<String, String> parseStripePriceIds() {
        if (stripePriceIdsRaw == null || stripePriceIdsRaw.isBlank()) {
            return null;
        }
        Map<String, String> out = new HashMap<>();
        for (String pair : stripePriceIdsRaw.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim();
            if (!key.isEmpty()) {
                out.put(key, kv[1].trim());
            }
        }
        return out.isEmpty() ? null : out;
    }

    /** INTENT_WEIGHT_SCALE env'ini intent→7 bileşenli çarpan haritasına çözer (0421 A3-3). */
    public Map<String, double[]> parseIntentWeightScale() {
        return parseIntentWeightScaleRaw(intentWeightScaleRaw);
    }

    /**
     * INTENT_WEIGHT_SCALE biçimini parseler: "intent=1.25,1.00,...;intent2=..."
     * (7 değerli çarpanlar). Boş/geçersiz girdide boş döner — varsayılan tablo kullanılır.
     */
    public static Map<String, double[]> parseIntentWeightScaleRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Map<String, double[]> out = new HashMap<>();
        for (String entry : raw.split(";")) {
            entry = entry.trim();
            if (entry.isEmpty()) {
                continue;
            }
            String[] kv = entry.split("=", 2);
            if (kv.length != 2) {
                return null;
            }
            String intent = kv[0].trim();
            if (intent.isEmpty()) {
                return null;
            }
            String[] parts = kv[1].split(",");
            if (parts.length != 7) {
                return null;
            }
            double[] vals = new double[7];
            for (int i = 0; i < 7; i++) {
                double v;
                try {
                    v = Double.parseDouble(parts[i].trim());
                } catch (NumberFormatException e) {
                    return null;
                }
                if (v < 0 || Double.isNaN(v) || Double.isInfinite(v)) {
                    return null;
                }
                vals[i] = v;
            }
            out.put(intent, vals);
        }
        return out.isEmpty() ? null : out;
    }

    /** 4 bileşenli skor ağırlığı kaydı (presence, position, source, competitor). */
    public record ScoreWeightsV4(double presence, double position, double source, double competitor) {
    }
}