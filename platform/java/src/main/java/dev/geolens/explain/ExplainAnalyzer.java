package dev.geolens.explain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Explainability analiz motoru — Go {@code explain.handler} {@code computeFeatureImportance} portu (R7).
 * <p>Risk sınıfı ve güven skoruna göre dinamik feature ağırlıkları üretir.
 */
public final class ExplainAnalyzer {

    private ExplainAnalyzer() {
    }

    /**
     * Go {@code computeFeatureImportance} karşılığı: risk_class ve confidence'a göre ağırlıklar.
     */
    public static Map<String, Double> computeFeatureImportance(String riskClass, double confidence) {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("ai_visibility_score", 0.35);
        weights.put("response_quality", 0.25);
        weights.put("citation_accuracy", 0.20);
        weights.put("brand_consistency", 0.12);
        weights.put("sentiment_score", 0.08);

        // Yüksek riskli varlıklarda citation_accuracy daha önemli
        if ("high".equals(riskClass) || "critical".equals(riskClass)) {
            weights.put("citation_accuracy", 0.30);
            weights.put("ai_visibility_score", 0.25);
            weights.put("response_quality", 0.20);
            weights.put("brand_consistency", 0.15);
            weights.put("sentiment_score", 0.10);
        }

        // Düşük confidence → brand_consistency daha az güvenilir
        if (confidence < 0.5 && confidence > 0) {
            double bc = weights.get("brand_consistency") * confidence;
            weights.put("brand_consistency", bc);
            weights.put("ai_visibility_score", weights.get("ai_visibility_score") + (0.12 - bc));
        }

        return weights;
    }
}
