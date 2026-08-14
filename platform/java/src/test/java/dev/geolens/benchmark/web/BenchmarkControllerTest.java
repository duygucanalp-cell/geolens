package dev.geolens.benchmark.web;

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

/** Go benchmark/handler_test.go parity testleri — model benchmark REST. */
@WebMvcTest(BenchmarkController.class)
class BenchmarkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

    private static final String TENANT = "T01";

    // ---------- RunBenchmark ----------

    @Test
    void runBenchmarkInvalidJsonReturns400() throws Exception {
        mockMvc.perform(post("/v1/benchmarks/models")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void runBenchmarkMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/v1/benchmarks/models")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"model_name\":\"\",\"engine_name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("model_name gerekli"));
    }

    @Test
    void runBenchmarkSuccess() throws Exception {
        mockMvc.perform(post("/v1/benchmarks/models")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"model_name\":\"gpt-4o\",\"engine_name\":\"chatgpt\",\"accuracy_score\":0.95,\"latency_ms\":200}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.model_name").value("gpt-4o"))
                .andExpect(jsonPath("$.engine_name").value("chatgpt"))
                .andExpect(jsonPath("$.category").value("llm"))
                .andExpect(jsonPath("$.bench_id").isNotEmpty())
                .andExpect(jsonPath("$.tested_at").isNotEmpty());
    }

    // ---------- ListBenchmarks ----------

    @Test
    void listBenchmarksSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(benchRow("b-1", "gpt-4o", "chatgpt"))));

        mockMvc.perform(get("/v1/benchmarks/models")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("b-1"))
                .andExpect(jsonPath("$[0].model_name").value("gpt-4o"))
                .andExpect(jsonPath("$[0].accuracy_score").value(0.95));
    }

    @Test
    void listBenchmarksQueryErrorReturns500() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/benchmarks/models")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("benchmark geçmişi alınamadı"));
    }

    // ---------- CompareModels ----------

    @Test
    void compareModelsMissingEnginesReturns400() throws Exception {
        mockMvc.perform(get("/v1/benchmarks/compare")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("engines parametresi gerekli (virgülle ayırın)"));
    }

    @Test
    void compareModelsSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(
                        compareRow("chatgpt", "gpt-4o", 0.95, 200, 0.001, 50.0, 4.5, 0.8),
                        compareRow("perplexity", "sonar-pro", 0.88, 350, 0.005, 30.0, 4.2, 0.9))));

        mockMvc.perform(get("/v1/benchmarks/compare")
                        .header("X-Tenant-ID", TENANT)
                        .param("engines", "chatgpt,perplexity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.models[0].engine_name").value("chatgpt"))
                .andExpect(jsonPath("$.models[1].engine_name").value("perplexity"))
                .andExpect(jsonPath("$.summary.best_accuracy").value("0.95"))
                .andExpect(jsonPath("$.summary.best_latency_ms").value("200"));
    }

    @Test
    void compareModelsBlankEnginesReturns400() throws Exception {
        mockMvc.perform(get("/v1/benchmarks/compare")
                        .header("X-Tenant-ID", TENANT)
                        .param("engines", "  , "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçerli engine adı gerekli"));
    }

    // ---------- yardımcılar ----------

    private static Map<String, Object> benchRow(String id, String model, String engine) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("model_name", model);
        m.put("engine_name", engine);
        m.put("category", "llm");
        m.put("accuracy_score", 0.95);
        m.put("latency_ms", 200);
        m.put("cost_per_request", 0.001);
        m.put("tokens_per_second", 50.0);
        m.put("response_quality", 4.5);
        m.put("citation_rate", 0.8);
        m.put("tested_at", "2026-08-14T00:00:00Z");
        return m;
    }

    private static Map<String, Object> compareRow(String engine, String model, double acc, int lat,
                                                  double cost, double tok, double qual, double cit) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("engine_name", engine);
        m.put("model_name", model);
        m.put("accuracy_score", acc);
        m.put("latency_ms", lat);
        m.put("cost_per_request", cost);
        m.put("tokens_per_second", tok);
        m.put("response_quality", qual);
        m.put("citation_rate", cit);
        m.put("tested_at", "2026-08-14T00:00:00Z");
        return m;
    }
}
