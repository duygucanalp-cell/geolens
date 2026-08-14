package dev.geolens.cost.web;

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

/** Go cost/handler_test.go parity testleri — maliyet analitiği REST. */
@WebMvcTest(CostController.class)
class CostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

    private static final String TENANT = "T01";

    // ---------- RecordCost ----------

    @Test
    void recordCostInvalidJsonReturns400() throws Exception {
        mockMvc.perform(post("/v1/costs/entries")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void recordCostMissingEngineReturns400() throws Exception {
        mockMvc.perform(post("/v1/costs/entries")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"engine_name\":\"\",\"cost_usd\":0.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("engine_name gerekli"));
    }

    @Test
    void recordCostSuccess() throws Exception {
        mockMvc.perform(post("/v1/costs/entries")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"engine_name\":\"chatgpt\",\"cost_usd\":0.05,\"token_count\":500}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entry_id").isNotEmpty())
                .andExpect(jsonPath("$.engine_name").value("chatgpt"))
                .andExpect(jsonPath("$.cost_usd").value(0.05))
                .andExpect(jsonPath("$.token_count").value(500))
                .andExpect(jsonPath("$.recorded_at").isNotEmpty());
    }

    @Test
    void recordCostDefaultsOperation() throws Exception {
        mockMvc.perform(post("/v1/costs/entries")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"engine_name\":\"gemini\",\"cost_usd\":0.1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entry_id").isNotEmpty());
    }

    // ---------- ListCosts ----------

    @Test
    void listCostsSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(entryRow("c-1", "chatgpt", "gpt-4o"))));

        mockMvc.perform(get("/v1/costs/entries")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.has_more").value(false))
                .andExpect(jsonPath("$.data[0].id").value("c-1"))
                .andExpect(jsonPath("$.data[0].engine_name").value("chatgpt"));
    }

    @Test
    void listCostsQueryErrorReturnsGraceful() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/costs/entries")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.has_more").value(false));
    }

    // ---------- GetCostSummary ----------

    @Test
    void getCostSummarySuccess() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(summaryRow(150.0, 50000)));
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(
                        breakdownRow("chatgpt", 100.0, 30000),
                        breakdownRow("perplexity", 50.0, 20000))));

        mockMvc.perform(get("/v1/costs/summary")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("7d"))
                .andExpect(jsonPath("$.total_cost_usd").value(150.0))
                .andExpect(jsonPath("$.total_tokens").value(50000))
                .andExpect(jsonPath("$.engine_breakdown.length()").value(2))
                .andExpect(jsonPath("$.engine_breakdown[0].engine").value("chatgpt"))
                .andExpect(jsonPath("$.engine_breakdown[1].cost").value(50.0));
    }

    @Test
    void getCostSummaryDefaultPeriod() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(summaryRow(0.0, 0)));
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of()));

        mockMvc.perform(get("/v1/costs/summary")
                        .header("X-Tenant-ID", TENANT)
                        .param("period", "30d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("30d"))
                .andExpect(jsonPath("$.total_cost_usd").value(0.0));
    }

    // ---------- yardımcılar ----------

    private static Map<String, Object> entryRow(String id, String engine, String model) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("engine_name", engine);
        m.put("model_name", model);
        m.put("operation", "inference");
        m.put("token_count", 500);
        m.put("cost_usd", 0.05);
        m.put("recorded_at", "2026-08-14T00:00:00Z");
        return m;
    }

    private static Map<String, Object> summaryRow(double cost, int tokens) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", cost);
        m.put("value2", tokens);
        return m;
    }

    private static Map<String, Object> breakdownRow(String engine, double cost, int tokens) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("engine_name", engine);
        m.put("total", cost);
        m.put("tokens", tokens);
        return m;
    }
}
