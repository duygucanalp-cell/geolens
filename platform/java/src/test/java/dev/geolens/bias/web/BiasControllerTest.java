package dev.geolens.bias.web;

import dev.geolens.testutil.JooqTestData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
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

/** Go bias/handler_test.go parity testleri — bias/fairness REST. */
@WebMvcTest(BiasController.class)
class BiasControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

    private static final String TENANT = "T01";

    // ---------- Evaluate ----------

    @Test
    void evaluateInvalidJsonReturns400() throws Exception {
        mockMvc.perform(post("/v1/bias/evaluate")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void evaluateSuccess() throws Exception {
        mockMvc.perform(post("/v1/bias/evaluate")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"model_id\":\"model-1\",\"metric_type\":\"demographic_parity\",\"data\":{\"group_a\":0.8,\"group_b\":0.6}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.test_id").isNotEmpty())
                .andExpect(jsonPath("$.model_id").value("model-1"))
                .andExpect(jsonPath("$.metric_type").value("demographic_parity"))
                .andExpect(jsonPath("$.fairness_score").isNumber());
    }

    @Test
    void evaluateUnknownMetricStillReturns201() throws Exception {
        mockMvc.perform(post("/v1/bias/evaluate")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"model_id\":\"m\",\"metric_type\":\"bogus\",\"data\":{}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.results.error").value("bilinmeyen metrik: bogus"));
    }

    // ---------- ListTests ----------

    @Test
    void listTestsSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(
                        testRow("test-1", "model-1", "demographic_parity", 0.85, false, 0.15),
                        testRow("test-2", "model-2", "equal_opportunity", 0.65, true, 0.35))));

        mockMvc.perform(get("/v1/bias/tests")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.has_more").value(false))
                .andExpect(jsonPath("$.data[0].id").value("test-1"))
                .andExpect(jsonPath("$.data[1].has_bias").value(true));
    }

    @Test
    void listTestsQueryErrorReturns500() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/bias/tests")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("test geçmişi alınamadı"));
    }

    // ---------- yardımcılar ----------

    private static Map<String, Object> testRow(String id, String model, String metric,
                                               double score, boolean hasBias, double gap) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("model_id", model);
        m.put("metric_type", metric);
        m.put("fairness_score", score);
        m.put("has_bias", hasBias);
        m.put("max_gap", gap);
        m.put("details", "{}");
        m.put("recommendations", hasBias ? "[\"retrain\"]" : "[]");
        m.put("created_at", "2026-08-14T00:00:00Z");
        return m;
    }
}
