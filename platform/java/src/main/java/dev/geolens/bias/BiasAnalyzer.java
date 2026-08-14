package dev.geolens.bias;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bias/fairness değerlendirme hesaplamaları — Go {@code bias} paketi portu (R5).
 * <p>Üç metrik: {@code demographic_parity} (grup pozitif oranları), {@code equal_opportunity}
 * (True Positive Rate), {@code disparate_impact} (korumalı/korumasız oran, EEOC 4/5 kuralı).
 */
public final class BiasAnalyzer {

    private BiasAnalyzer() {
    }

    /** Go {@code computeBias} portu — metrik tipine göre sonucu hesaplar; bilinmeyen metrikte error döner. */
    public static Map<String, Object> compute(String metricType, Map<String, Object> data) {
        Map<String, Object> results = new LinkedHashMap<>();
        results.put("metric_type", metricType);
        results.put("fairness_score", 0.0);
        results.put("has_bias", false);
        results.put("recommendations", List.of());

        switch (metricType == null ? "" : metricType) {
            case "demographic_parity" -> results.putAll(demographicParity(data));
            case "equal_opportunity" -> results.putAll(equalOpportunity(data));
            case "disparate_impact" -> results.putAll(disparateImpact(data));
            default -> results.put("error", "bilinmeyen metrik: " + metricType);
        }
        return results;
    }

    /** Go {@code demographicParity} portu — grup bazlı pozitif oran farkı (max-min). */
    public static Map<String, Object> demographicParity(Map<String, Object> data) {
        Map<String, Double> groups = numericValues(data);
        if (groups.isEmpty()) {
            return Map.of(
                    "fairness_score", 1.0,
                    "has_bias", false,
                    "max_gap", 0.0,
                    "recommendations", List.of("Değerlendirme için grup bazlı pozitif oran verisi gerekli"));
        }

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double v : groups.values()) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }

        double gap = max - min;
        double score = Math.max(0, 1.0 - gap);

        List<String> recs = new ArrayList<>();
        if (gap > 0.1) {
            recs.add("Gruplar arası pozitif oran farkı %" + formatPct(gap) + " — demografik parite ihlali");
        }
        if (gap > 0.2) {
            recs.add("Kritik eşik aşıldı (%20), model yeniden eğitilmeli veya ağırlıklandırma yapılmalı");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fairness_score", score);
        out.put("has_bias", gap > 0.1);
        out.put("max_gap", gap);
        out.put("groups", groups);
        out.put("recommendations", recs);
        return out;
    }

    /** Go {@code equalOpportunity} portu — True Positive Rate farkı. */
    public static Map<String, Object> equalOpportunity(Map<String, Object> data) {
        Map<String, Double> tpr = numericValues(data);
        if (tpr.isEmpty()) {
            return Map.of(
                    "fairness_score", 1.0,
                    "has_bias", false,
                    "max_gap", 0.0,
                    "recommendations", List.of("True Positive Rate verisi gerekli"));
        }

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double v : tpr.values()) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }

        double gap = max - min;
        double score = Math.max(0, 1.0 - gap);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fairness_score", score);
        out.put("has_bias", gap > 0.1);
        out.put("max_gap", gap);
        out.put("tpr_groups", tpr);
        return out;
    }

    /** Go {@code disparateImpact} portu — korumalı/korumasız oran, EEOC 0.8-1.25 standardı. */
    public static Map<String, Object> disparateImpact(Map<String, Object> data) {
        Double protectedRate = number(data.get("protected_group_rate"));
        Double nonProtectedRate = number(data.get("non_protected_group_rate"));

        if (protectedRate == null || nonProtectedRate == null || nonProtectedRate == 0) {
            return Map.of(
                    "fairness_score", 1.0,
                    "has_bias", false,
                    "max_gap", 0.0,
                    "recommendations", List.of("Korumalı ve korumasız grup oranları gerekli"));
        }

        double ratio = protectedRate / nonProtectedRate;
        double score = ratio > 1 ? 1.0 / ratio : ratio;

        boolean hasBias = ratio < 0.8 || ratio > 1.25;
        List<String> recs = new ArrayList<>();
        if (hasBias) {
            recs.add("Farklı etki oranı: " + formatPct(ratio) + " — 0.8-1.25 aralığı dışında (EEOC standardı)");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fairness_score", score);
        out.put("has_bias", hasBias);
        out.put("max_gap", ratio);
        out.put("disparate_impact", ratio);
        out.put("four_fifths_rule", ratio >= 0.8);
        out.put("recommendations", recs);
        return out;
    }

    /** Go {@code formatPct} portu — {@code %.1f%%} formatı. */
    public static String formatPct(double v) {
        return String.format(Locale.US, "%.1f%%", v * 100);
    }

    private static Map<String, Double> numericValues(Map<String, Object> data) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (data != null) {
            for (Map.Entry<String, Object> e : data.entrySet()) {
                Double v = number(e.getValue());
                if (v != null) {
                    out.put(e.getKey(), v);
                }
            }
        }
        return out;
    }

    private static Double number(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }
}
