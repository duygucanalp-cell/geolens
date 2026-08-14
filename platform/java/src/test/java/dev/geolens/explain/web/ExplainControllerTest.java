package dev.geolens.explain.web;

import dev.geolens.testutil.JooqTestData;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go explain/handler_test.go parity testleri — Explainability REST. */
@WebMvcTest(ExplainController.class)
class ExplainControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

    private static final String TENANT = "T01";

    // ---------- Explain ----------

    @Test
    void explainNotFound() throws Exception {
        // Go MockRow{Err} → QueryRow hatası → 404
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("not found"));

        mockMvc.perform(post("/v1/explain/ent-001")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("varlık bulunamadı"));
    }

    @Test
    void explainNullEntityReturns404() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class))).thenReturn(null);

        mockMvc.perform(post("/v1/explain/ent-001")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("varlık bulunamadı"));
    }

    @Test
    void explainSuccess() throws Exception {
        // Go MockPool: 2 args → entity row, diğer (1 arg) → hata → varsayılan skor 70.0
        when(dsl.fetchOne(anyString(), any(Object[].class))).thenAnswer((Answer<Record>) inv -> {
            // entity sorgusu: sql + entityId + tenantId = 3 arg; shap sorgusu: sql + entityId = 2 arg
            if (inv.getArguments().length == 3) {
                return JooqTestData.record(entityRow("Test Entity", "model", "openai", "high", 0.75));
            }
            throw new RuntimeException("no scores");
        });

        mockMvc.perform(post("/v1/explain/ent-001")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entity_name").value("Test Entity"))
                .andExpect(jsonPath("$.entity_type").value("model"))
                .andExpect(jsonPath("$.risk_class").value("high"))
                .andExpect(jsonPath("$.method").value("SHAP (approximate)"))
                .andExpect(jsonPath("$.base_value").value(50.0))
                .andExpect(jsonPath("$.feature_importance.citation_accuracy").value(0.30))
                .andExpect(jsonPath("$.shap_values.length()").value(5))
                // high risk → citation_accuracy shap -5.8
                .andExpect(jsonPath("$.shap_values[2].shap").value(-5.8));
    }

    @Test
    void explainLowRiskUsesDefaultShap() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class))).thenAnswer((Answer<Record>) inv -> {
            // entity sorgusu: sql + entityId + tenantId = 3 arg; shap sorgusu: sql + entityId = 2 arg
            if (inv.getArguments().length == 3) {
                return JooqTestData.record(entityRow("Low Entity", "model", "azure", "low", 0.5));
            }
            throw new RuntimeException("no scores");
        });

        mockMvc.perform(post("/v1/explain/ent-002")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                // low risk → citation_accuracy shap -3.2
                .andExpect(jsonPath("$.shap_values[2].shap").value(-3.2))
                .andExpect(jsonPath("$.feature_importance.citation_accuracy").value(0.20));
    }

    @Test
    void explainUsesAvgScoreWhenAvailable() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class))).thenAnswer((Answer<Record>) inv -> {
            // entity sorgusu: sql + entityId + tenantId = 3 arg; shap sorgusu: sql + entityId = 2 arg
            if (inv.getArguments().length == 3) {
                return JooqTestData.record(entityRow("Scored", "model", "openai", "low", 0.8));
            }
            return JooqTestData.record(Map.of("value", 75.0));
        });

        mockMvc.perform(post("/v1/explain/ent-003")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                // ai_visibility_score shap = 75.0 * 0.15
                .andExpect(jsonPath("$.shap_values[0].value").value(75.0))
                .andExpect(jsonPath("$.shap_values[0].shap").value(75.0 * 0.15));
    }

    // ---------- ListAnalyses ----------

    @Test
    void listAnalysesSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(analysisRow("a-1", "ent-001"))));

        mockMvc.perform(get("/v1/explain/results")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("a-1"))
                .andExpect(jsonPath("$.data[0].entity_id").value("ent-001"))
                .andExpect(jsonPath("$.data[0].method").value("SHAP"))
                .andExpect(jsonPath("$.has_more").value(false));
    }

    @Test
    void listAnalysesQueryErrorReturns500() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/explain/results")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("analiz geçmişi alınamadı"));
    }

    @Test
    void listAnalysesHasMoreWhenOverLimit() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(
                        analysisRow("a-1", "ent-001"), analysisRow("a-2", "ent-001"))));

        mockMvc.perform(get("/v1/explain/results")
                        .header("X-Tenant-ID", TENANT)
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.has_more").value(true));
    }

    @Test
    void listAnalysesInvalidLimitDefaultsTo20() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of()));

        mockMvc.perform(get("/v1/explain/results")
                        .header("X-Tenant-ID", TENANT)
                        .param("limit", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.has_more").value(false));
    }

    // ---------- yardımcılar ----------

    private static Map<String, Object> entityRow(String name, String entityType, String provider,
                                                 String riskClass, double confidence) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("entity_type", entityType);
        m.put("provider", provider);
        m.put("risk_class", riskClass);
        m.put("confidence", confidence);
        return m;
    }

    private static Map<String, Object> analysisRow(String id, String entityId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("entity_id", entityId);
        m.put("method", "SHAP");
        m.put("base_value", 50.0);
        m.put("prediction", 65.4);
        m.put("feature_importance", "{\"f1\":0.5}");
        m.put("shap_values", "[{\"feature\":\"f1\",\"shap\":15.4}]");
        m.put("interpretation", "High score due to visibility");
        m.put("created_at", "2026-08-15T10:00:00Z");
        return m;
    }
}
