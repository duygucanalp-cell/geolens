package dev.geolens.explain.service;

import java.util.List;
import java.util.Map;

/** Explain analiz sonucu — controller'ın HTTP gövdesine dönüştürdüğü iş mantığı çıktısı. */
public record ExplainResult(
        String analysisId,
        String entityId,
        String entityName,
        String entityType,
        String riskClass,
        double baseValue,
        double prediction,
        Map<String, Double> featureImportance,
        List<Map<String, Object>> shapValues,
        String interpretation) {
}
