package dev.geolens.explain.service;

import dev.geolens.common.ServiceException;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.explain.ExplainAnalyzer;
import dev.geolens.util.Ulid;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Explainability iş mantığı — Go {@code explain.handler} portu (R7).
 * <p>SHAP benzeri katkı değerleriyle model skoru açıklaması üretimi ve sonuç geçmişi burada yaşar;
 * controller yalnızca HTTP/transport'tur.
 */
@Service
public class ExplainService {

    private final DSLContext dsl;
    private final ObjectMapper mapper = new ObjectMapper();

    public ExplainService(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ---------- Explain ----------

    public ExplainResult explain(String tenantId, String entityId) {
        String analysisId = Ulid.generate();

        // Varlık bilgilerini registry'den al
        Record rec;
        try {
            rec = dsl.fetchOne("""
                    SELECT name, entity_type, COALESCE(provider, ''), COALESCE(risk_class, 'unclassified'), COALESCE(confidence, 0.0)
                    FROM registry.entities WHERE id = ? AND tenant_id = ?
                    """, entityId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "varlık bulunamadı");
        }
        if (rec == null) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "varlık bulunamadı");
        }
        Map<String, Object> row = rec.intoMap();
        String name = str(row.get("name"));
        String entityType = str(row.get("entity_type"));
        String riskClass = str(row.get("risk_class"));
        double confidence = num(row.get("confidence"));

        // Feature importance — risk_class ve confidence'a göre dinamik
        double baseValue = 50.0;
        Map<String, Double> featureImportance = ExplainAnalyzer.computeFeatureImportance(riskClass, confidence);

        // SHAP değerleri — son ölçüm varsa ondan faydalan
        List<Map<String, Object>> shapValues = computeShapValues(entityId, riskClass, confidence);

        double prediction = baseValue;
        for (Map<String, Object> f : shapValues) {
            Object shap = f.get("shap");
            if (shap instanceof Number n) {
                prediction += n.doubleValue();
            }
        }

        String topFeature = "";
        double topWeight = 0.0;
        for (Map.Entry<String, Double> e : featureImportance.entrySet()) {
            if (e.getValue() > topWeight) {
                topWeight = e.getValue();
                topFeature = e.getKey();
            }
        }

        String interpretation = String.format("Model skoru %.1f, en büyük katkı %s'den (%.1f%%)",
                prediction, topFeature, topWeight * 100);

        // Persist sonucu DB'ye yaz
        try {
            dsl.execute("""
                    INSERT INTO explain.results (id, tenant_id, entity_id, method, base_value, prediction, feature_importance, shap_values, interpretation)
                    VALUES (?, ?, ?, 'SHAP (approximate)', ?, ?, ?::jsonb, ?::jsonb, ?)
                    """, analysisId, tenantId, entityId, baseValue, prediction,
                    toJson(featureImportance), toJson(shapValues), interpretation);
        } catch (RuntimeException e) {
            // Go'da warn loglanıp geçilir
        }

        return new ExplainResult(analysisId, entityId, name, entityType, riskClass,
                baseValue, prediction, featureImportance, shapValues, interpretation);
    }

    // ---------- ListAnalyses ----------

    public ExplainHistoryResult listAnalyses(String tenantId, String entityId, String limit) {
        int limitInt;
        try {
            limitInt = limit == null || limit.isBlank() ? 20 : Integer.parseInt(limit);
        } catch (NumberFormatException e) {
            limitInt = 20;
        }
        if (limitInt < 1 || limitInt > 100) {
            limitInt = 20;
        }

        // LIMIT+1 pattern for has_more
        String sql = "SELECT id, entity_id, method, base_value, prediction, feature_importance, shap_values, interpretation, created_at"
                + " FROM explain.results WHERE tenant_id = ?";
        List<Map<String, Object>> rows;
        try {
            if (entityId != null && !entityId.isBlank()) {
                rows = dsl.fetch(sql + " AND entity_id = ? ORDER BY created_at DESC LIMIT ?",
                        tenantId, entityId, limitInt + 1).intoMaps();
            } else {
                rows = dsl.fetch(sql + " ORDER BY created_at DESC LIMIT ?", tenantId, limitInt + 1).intoMaps();
            }
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "analiz geçmişi alınamadı");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", str(r.get("id")));
            item.put("entity_id", str(r.get("entity_id")));
            item.put("method", str(r.get("method")));
            item.put("base_value", num(r.get("base_value")));
            item.put("prediction", num(r.get("prediction")));
            item.put("feature_importance", parseJson(r.get("feature_importance"), "{}"));
            item.put("shap_values", parseJson(r.get("shap_values"), "[]"));
            item.put("interpretation", str(r.get("interpretation")));
            item.put("created_at", str(r.get("created_at")));
            results.add(item);
        }

        return ExplainHistoryResult.of(limitInt, results);
    }

    // ---------- yardımcılar ----------

    /**
     * Go {@code computeShapValues} karşılığı: varlık verilerine göre SHAP benzeri katkı değerleri.
     */
    private List<Map<String, Object>> computeShapValues(String entityId, String riskClass, double confidence) {
        // Son ölçüm skorlarını al (varsa)
        double avgScore = 70.0;
        try {
            Record r = dsl.fetchOne("""
                    SELECT COALESCE(AVG(value), 0.0) FROM measure.brand_scores
                    WHERE entity_id = ? AND created_at > NOW() - INTERVAL '30 days'
                    """, entityId);
            if (r != null) {
                avgScore = num(r.get(0));
            }
        } catch (RuntimeException e) {
            // Go'da debug loglanıp varsayılan kullanılır
        }

        List<Map<String, Object>> shap = new ArrayList<>();
        shap.add(shapItem("ai_visibility_score", avgScore, avgScore * 0.15, "positive"));
        shap.add(shapItem("response_quality", avgScore * 0.85, avgScore * 0.10, "positive"));
        shap.add(shapItem("citation_accuracy", 65.0, -3.2, "negative"));
        shap.add(shapItem("brand_consistency", 70.0, 2.1, "positive"));
        shap.add(shapItem("sentiment_score", 55.0, -1.5, "negative"));

        // Yüksek riskli varlıklarda citation_accuracy daha belirleyici
        if ("high".equals(riskClass) || "critical".equals(riskClass)) {
            shap.get(2).put("shap", -5.8);
        }

        return shap;
    }

    private static Map<String, Object> shapItem(String feature, double value, double shap, String impact) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("feature", feature);
        m.put("value", value);
        m.put("shap", shap);
        m.put("impact", impact);
        return m;
    }

    private String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            return o instanceof Map ? "{}" : "[]";
        }
    }

    private Object parseJson(Object o, String fallback) {
        if (o == null) {
            return fallback;
        }
        if (o instanceof String s) {
            try {
                return mapper.readValue(s, Object.class);
            } catch (Exception e) {
                return fallback;
            }
        }
        return o;
    }

    private static double num(Object o) {
        return o == null ? 0 : ((Number) o).doubleValue();
    }

    private static String str(Object o) {
        if (o == null) {
            return "";
        }
        if (o instanceof java.sql.Timestamp t) {
            return t.toInstant().toString();
        }
        return String.valueOf(o);
    }
}