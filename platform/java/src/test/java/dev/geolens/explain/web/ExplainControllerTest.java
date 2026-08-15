package dev.geolens.explain.web;

import dev.geolens.explain.service.ExplainHistoryResult;
import dev.geolens.explain.service.ExplainResult;
import dev.geolens.explain.service.ExplainService;
import dev.geolens.explain.service.ExplainServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go explain/handler_test.go parity testleri â€” Explainability REST. */
@WebMvcTest(ExplainController.class)
class ExplainControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExplainService explainService;

    private static final String TENANT = "T01";

    // ---------- Explain ----------

    @Test
    void explainNotFound() throws Exception {
        when(explainService.explain(anyString(), anyString()))
                .thenThrow(new ExplainServiceException(HttpStatus.NOT_FOUND, "varlÄ±k bulunamadÄ±"));

        mockMvc.perform(post("/v1/explain/ent-001")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("varlÄ±k bulunamadÄ±"));
    }

    @Test
    void explainNullEntityReturns404() throws Exception {
        when(explainService.explain(anyString(), anyString()))
                .thenThrow(new ExplainServiceException(HttpStatus.NOT_FOUND, "varlÄ±k bulunamadÄ±"));

        mockMvc.perform(post("/v1/explain/ent-001")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("varlÄ±k bulunamadÄ±"));
    }

    @Test
    void explainSuccess() throws Exception {
        when(explainService.explain(anyString(), anyString()))
                .thenReturn(new ExplainResult("a-1", "ent-001", "Test Entity", "model", "high",
                        50.0, 50.0 + 75.0 * 0.15 + 75.0 * 0.85 * 0.10 - 5.8 + 2.1 - 1.5,
                        Map.of("citation_accuracy", 0.30),
                        shapValues(),
                        "Model skoru 57,7, en bÃ¼yÃ¼k katkÄ± citation_accuracy'den (30,0%)"));

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
                .andExpect(jsonPath("$.shap_values[2].shap").value(-5.8));
    }

    @Test
    void explainLowRiskUsesDefaultShap() throws Exception {
        when(explainService.explain(anyString(), anyString()))
                .thenReturn(new ExplainResult("a-2", "ent-002", "Low Entity", "model", "low",
                        50.0, 50.0, Map.of("citation_accuracy", 0.20),
                        shapValues(),
                        ""));

        mockMvc.perform(post("/v1/explain/ent-002")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shap_values[2].shap").value(-3.2))
                .andExpect(jsonPath("$.feature_importance.citation_accuracy").value(0.20));
    }

    @Test
    void explainUsesAvgScoreWhenAvailable() throws Exception {
        when(explainService.explain(anyString(), anyString()))
                .thenReturn(new ExplainResult("a-3", "ent-003", "Scored", "model", "low",
                        50.0, 50.0, Map.of(), shapValues(),
                        ""));

        mockMvc.perform(post("/v1/explain/ent-003")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shap_values[0].value").value(75.0))
                .andExpect(jsonPath("$.shap_values[0].shap").value(75.0 * 0.15));
    }

    // ---------- ListAnalyses ----------

    @Test
    void listAnalysesSuccess() throws Exception {
        when(explainService.listAnalyses(any(), any(), any()))
                .thenReturn(new ExplainHistoryResult(List.of(analysisRow("a-1", "ent-001")), false));

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
        when(explainService.listAnalyses(any(), any(), any()))
                .thenThrow(new ExplainServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "analiz geÃ§miÅŸi alÄ±namadÄ±"));

        mockMvc.perform(get("/v1/explain/results")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("analiz geÃ§miÅŸi alÄ±namadÄ±"));
    }

    @Test
    void listAnalysesHasMoreWhenOverLimit() throws Exception {
        when(explainService.listAnalyses(any(), any(), any()))
                .thenReturn(new ExplainHistoryResult(List.of(analysisRow("a-1", "ent-001")), true));

        mockMvc.perform(get("/v1/explain/results")
                        .header("X-Tenant-ID", TENANT)
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.has_more").value(true));
    }

    @Test
    void listAnalysesInvalidLimitDefaultsTo20() throws Exception {
        when(explainService.listAnalyses(any(), any(), any()))
                .thenReturn(new ExplainHistoryResult(List.of(), false));

        mockMvc.perform(get("/v1/explain/results")
                        .header("X-Tenant-ID", TENANT)
                        .param("limit", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.has_more").value(false));
    }

    // ---------- yardÄ±mcÄ±lar ----------

    private static List<Map<String, Object>> shapValues() {
        return List.of(
                Map.of("feature", "ai_visibility_score", "value", 75.0, "shap", 75.0 * 0.15, "impact", "positive"),
                Map.of("feature", "response_quality", "value", 75.0 * 0.85, "shap", 75.0 * 0.85 * 0.10, "impact", "positive"),
                Map.of("feature", "citation_accuracy", "value", 65.0, "shap", -3.2, "impact", "negative"),
                Map.of("feature", "brand_consistency", "value", 70.0, "shap", 2.1, "impact", "positive"),
                Map.of("feature", "sentiment_score", "value", 55.0, "shap", -1.5, "impact", "negative"));
    }

    private static Map<String, Object> analysisRow(String id, String entityId) {
        return Map.of(
                "id", id,
                "entity_id", entityId,
                "method", "SHAP",
                "base_value", 50.0,
                "prediction", 65.4,
                "feature_importance", Map.of("f1", 0.5),
                "shap_values", List.of(Map.of("feature", "f1", "shap", 15.4)),
                "interpretation", "High score due to visibility",
                "created_at", "2026-08-15T10:00:00Z");
    }
}

