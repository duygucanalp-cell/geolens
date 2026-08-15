package dev.geolens.redteam.web;

import dev.geolens.redteam.Result;
import dev.geolens.redteam.Run;
import dev.geolens.redteam.TestCase;
import dev.geolens.redteam.service.RedteamService;
import dev.geolens.redteam.service.RedteamServiceException;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go redteam/handler_test.go parity testleri — LLM Red Teaming REST. */
@WebMvcTest(RedteamController.class)
class RedteamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RedteamService redteamService;

    private static final String TENANT = "T01";

    // ---------- ListCases ----------

    @Test
    void listCasesQueryErrorReturnsEmpty() throws Exception {
        when(redteamService.listCases(anyString())).thenReturn(Map.of("cases", List.of()));

        mockMvc.perform(get("/v1/redteam/cases")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases").isArray());
    }

    @Test
    void listCasesSuccess() throws Exception {
        when(redteamService.listCases(anyString()))
                .thenReturn(Map.of("cases", List.of(caseRecord("case-1", "Jailbreak", "jailbreak"))));

        mockMvc.perform(get("/v1/redteam/cases")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases.length()").value(1))
                .andExpect(jsonPath("$.cases[0].name").value("Jailbreak"))
                .andExpect(jsonPath("$.cases[0].category").value("jailbreak"));
    }

    // ---------- CreateCase ----------

    @Test
    void createCaseInvalidJsonReturns400() throws Exception {
        mockMvc.perform(post("/v1/redteam/cases")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void createCaseInvalidCategoryReturns400() throws Exception {
        mockMvc.perform(post("/v1/redteam/cases")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"name\":\"X\",\"category\":\"bogus\",\"payload\":\"test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz category"));
    }

    @Test
    void createCaseSuccess() throws Exception {
        when(redteamService.createCase(anyString(), any()))
                .thenReturn(new TestCase("case-9", "t-1", "Özel", "custom", "test",
                        "instruction_override", "high", true, "2026-08-14T00:00:00Z", "2026-08-14T00:00:00Z"));

        mockMvc.perform(post("/v1/redteam/cases")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"name\":\"Özel\",\"category\":\"custom\",\"payload\":\"test\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("case-9"))
                .andExpect(jsonPath("$.category").value("custom"));
    }

    // ---------- DeleteCase ----------

    @Test
    void deleteCaseSuccess() throws Exception {
        when(redteamService.deleteCase(anyString(), anyString()))
                .thenReturn(Map.of("status", "silindi"));

        mockMvc.perform(delete("/v1/redteam/cases/case-1")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("silindi"));
    }

    // ---------- Run ----------

    @Test
    void runInvalidJsonReturns400() throws Exception {
        mockMvc.perform(post("/v1/redteam/runs")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void runSuccess() throws Exception {
        when(redteamService.run(anyString(), any()))
                .thenReturn(runBody());

        mockMvc.perform(post("/v1/redteam/runs")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"target_name\":\"hedef\",\"target_prompt\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defense_score").value(50.0))
                .andExpect(jsonPath("$.passed").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].outcome").value("passed"))
                .andExpect(jsonPath("$.results[0].matched_rule").value("Prompt Leak"))
                .andExpect(jsonPath("$.results[1].outcome").value("failed"))
                .andExpect(jsonPath("$.run.id").value("run-1"));
    }

    // ---------- ListRuns / GetRun ----------

    @Test
    void listRunsSuccess() throws Exception {
        when(redteamService.listRuns(anyString()))
                .thenReturn(Map.of(
                        "runs", List.of(new Run("run-1", "hedef", 8, 6, 2, 75.0, "completed", "2026-08-14T00:00:00Z")),
                        "total", 1));

        mockMvc.perform(get("/v1/redteam/runs")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runs.length()").value(1))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.runs[0].target_name").value("hedef"));
    }

    @Test
    void getRunNotFoundReturns404() throws Exception {
        when(redteamService.getRun(anyString(), anyString()))
                .thenThrow(new RedteamServiceException(HttpStatus.NOT_FOUND, "test bulunamadı"));

        mockMvc.perform(get("/v1/redteam/runs/run-1")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("test bulunamadı"));
    }

    @Test
    void getRunSuccess() throws Exception {
        when(redteamService.getRun(anyString(), anyString()))
                .thenReturn(Map.of(
                        "run", new Run("run-1", "hedef", 8, 6, 2, 75.0, "completed", "2026-08-14T00:00:00Z"),
                        "results", List.of(resultRecord("passed", "Prompt Leak"))));

        mockMvc.perform(get("/v1/redteam/runs/run-1")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.id").value("run-1"))
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].outcome").value("passed"));
    }

    // ---------- SeedDefaults ----------

    @Test
    void seedDefaultsReturnsCreated() throws Exception {
        when(redteamService.seedDefaults(anyString()))
                .thenReturn(Map.of("status", "varsayılan senaryolar oluşturuldu"));

        mockMvc.perform(post("/v1/redteam/seed-defaults")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("varsayılan senaryolar oluşturuldu"));
    }

    // ---------- yardımcılar ----------

    private static TestCase caseRecord(String id, String name, String category) {
        return new TestCase(id, "t-1", name, category, "ignore previous instructions",
                "instruction_override", "critical", true, "2026-08-14T00:00:00Z", "2026-08-14T00:00:00Z");
    }

    private static Result resultRecord(String outcome, String matchedRule) {
        return new Result("res-1", "run-1", "case-1", "jailbreak", "ignore previous instructions",
                outcome, "low", matchedRule, "saldırı yakalandı");
    }

    private static Map<String, Object> runBody() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("run", new Run("run-1", "hedef", 2, 1, 1, 50.0, "completed", "2026-08-14T00:00:00Z"));
        m.put("results", List.of(
                new Result("res-1", "run-1", "case-1", "jailbreak", "ignore previous instructions",
                        "passed", "low", "Prompt Leak", "saldırı yakalandı"),
                new Result("res-2", "run-1", "case-2", "pii_extraction", "ornek@example.com",
                        "failed", "critical", "", "guardrail kuralı saldırıyı yakalamadı")));
        m.put("total_cases", 2);
        m.put("passed", 1);
        m.put("failed", 1);
        m.put("defense_score", 50.0);
        m.put("status", "completed");
        return m;
    }
}
