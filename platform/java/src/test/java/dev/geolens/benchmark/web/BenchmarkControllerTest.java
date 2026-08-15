package dev.geolens.benchmark.web;

import dev.geolens.benchmark.service.BenchmarkService;
import dev.geolens.benchmark.service.BenchmarkServiceException;
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

/** Go benchmark/handler_test.go parity testleri — model benchmark REST. */
@WebMvcTest(BenchmarkController.class)
class BenchmarkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BenchmarkService benchmarkService;

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
        when(benchmarkService.runBenchmark(anyString(), any()))
                .thenReturn(runBenchmarkResult());

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
        when(benchmarkService.listBenchmarks(anyString(), anyInt(), anyInt(), any(), any()))
                .thenReturn(List.of(benchRow("b-1", "gpt-4o", "chatgpt")));

        mockMvc.perform(get("/v1/benchmarks/models")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("b-1"))
                .andExpect(jsonPath("$[0].model_name").value("gpt-4o"))
                .andExpect(jsonPath("$[0].accuracy_score").value(0.95));
    }

    @Test
    void listBenchmarksQueryErrorReturns500() throws Exception {
        when(benchmarkService.listBenchmarks(anyString(), anyInt(), anyInt(), any(), any()))
                .thenThrow(new BenchmarkServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "benchmark geçmişi alınamadı"));

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
        when(benchmarkService.compareModels(anyString(), anyString()))
                .thenReturn(compareResult());

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
        when(benchmarkService.compareModels(anyString(), anyString()))
                .thenThrow(new BenchmarkServiceException(HttpStatus.BAD_REQUEST, "geçerli engine adı gerekli"));

        mockMvc.perform(get("/v1/benchmarks/compare")
                        .header("X-Tenant-ID", TENANT)
                        .param("engines", "  , "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçerli engine adı gerekli"));
    }

    // ---------- yardımcılar ----------

    private static Map<String, Object> runBenchmarkResult() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bench_id", "bench-1");
        m.put("model_name", "gpt-4o");
        m.put("engine_name", "chatgpt");
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

    private static Map<String, Object> compareResult() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("models", List.of(
                compareRow("chatgpt", "gpt-4o", 0.95, 200, 0.001, 50.0, 4.5, 0.8),
                compareRow("perplexity", "sonar-pro", 0.88, 350, 0.005, 30.0, 4.2, 0.9)));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("best_accuracy", "0.95");
        summary.put("best_latency_ms", "200");
        summary.put("best_cost_per_req", "0.0010");
        summary.put("best_tokens_per_sec", "50.0");
        summary.put("best_quality", "4.50");
        summary.put("best_citation_rate", "0.90");
        m.put("summary", summary);
        m.put("count", 2);
        return m;
    }

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
