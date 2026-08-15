package dev.geolens.cost.web;

import dev.geolens.cost.service.CostService;
import dev.geolens.cost.service.CostServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

    @MockBean
    private CostService costService;

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
        when(costService.recordCost(anyString(), any()))
                .thenReturn(recordRow("e-1", "chatgpt", 0.05, 500));

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
        when(costService.recordCost(anyString(), any()))
                .thenReturn(recordRow("e-2", "gemini", 0.1, 0));

        mockMvc.perform(post("/v1/costs/entries")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"engine_name\":\"gemini\",\"cost_usd\":0.1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entry_id").isNotEmpty());
    }

    @Test
    void recordCostDBErrorReturns500() throws Exception {
        when(costService.recordCost(anyString(), any()))
                .thenThrow(new CostServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "maliyet kaydedilemedi"));

        mockMvc.perform(post("/v1/costs/entries")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"engine_name\":\"chatgpt\",\"cost_usd\":0.05}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("maliyet kaydedilemedi"));
    }

    // ---------- ListCosts ----------

    @Test
    void listCostsSuccess() throws Exception {
        when(costService.listCosts(anyString(), anyInt(), any()))
                .thenReturn(Map.of(
                        "data", List.of(entryRow("c-1", "chatgpt", "gpt-4o")),
                        "has_more", false));

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
        when(costService.listCosts(anyString(), anyInt(), any()))
                .thenReturn(Map.of("data", List.of(), "has_more", false));

        mockMvc.perform(get("/v1/costs/entries")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.has_more").value(false));
    }

    // ---------- GetCostSummary ----------

    @Test
    void getCostSummarySuccess() throws Exception {
        when(costService.getCostSummary(anyString(), any()))
                .thenReturn(Map.of(
                        "period", "7d",
                        "total_cost_usd", 150.0,
                        "total_tokens", 50000,
                        "engine_breakdown", List.of(
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
        when(costService.getCostSummary(anyString(), any()))
                .thenReturn(Map.of(
                        "period", "30d",
                        "total_cost_usd", 0.0,
                        "total_tokens", 0,
                        "engine_breakdown", List.of()));

        mockMvc.perform(get("/v1/costs/summary")
                        .header("X-Tenant-ID", TENANT)
                        .param("period", "30d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("30d"))
                .andExpect(jsonPath("$.total_cost_usd").value(0.0));
    }

    // ---------- yardımcılar ----------

    private static Map<String, Object> recordRow(String id, String engine, double cost, int tokens) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entry_id", id);
        m.put("engine_name", engine);
        m.put("model_name", "");
        m.put("cost_usd", cost);
        m.put("token_count", tokens);
        m.put("recorded_at", "2026-08-14T00:00:00Z");
        return m;
    }

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

    private static Map<String, Object> breakdownRow(String engine, double cost, int tokens) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("engine", engine);
        m.put("cost", cost);
        m.put("tokens", tokens);
        return m;
    }
}
