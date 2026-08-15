package dev.geolens.gate.web;

import dev.geolens.gate.CheckResult;
import dev.geolens.gate.GateText;
import dev.geolens.gate.service.GateCheckResult;
import dev.geolens.gate.service.GateHistoryResult;
import dev.geolens.gate.service.GateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go gate/handler_test.go parity testleri — CI/CD Governance Gate REST. */
@WebMvcTest(GateController.class)
class GateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GateService gateService;

    private static final String TENANT = "T01";

    // ---------- Check ----------

    @Test
    void checkInvalidJsonReturns400() throws Exception {
        mockMvc.perform(post("/v1/gate/check")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void checkNoEntityIdReturns200() throws Exception {
        when(gateService.check(eq(TENANT), any()))
                .thenReturn(new GateCheckResult("ch-1", "", "blocked", 1,
                        List.of(new CheckResult("Bias Test", true, "")), Instant.parse("2026-08-14T10:00:00Z")));

        mockMvc.perform(post("/v1/gate/check")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"entity_id\":\"\",\"entity_type\":\"model\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("blocked"));
    }

    @Test
    void checkAllChecksPassed() throws Exception {
        List<CheckResult> checks = List.of(
                new CheckResult("Registry Entry", true, "AI Registry'de kayıtlı (model, production)"),
                new CheckResult("Risk Assessment", true, "Risk değerlendirmesi mevcut (3 adet)"),
                new CheckResult("Policy Compliance", true, "2 pack aktif, %80 geçti"),
                new CheckResult("Documentation", true, "Teknik dokümantasyon mevcut"),
                new CheckResult("Guardrails", true, "3 guardrail aktif"),
                new CheckResult("Bias Test", true, "Bias testi gerekli değil (varsayılan)"));
        when(gateService.check(eq(TENANT), any()))
                .thenReturn(new GateCheckResult("ch-1", "ent-001", "approved", 6, checks,
                        Instant.parse("2026-08-14T10:00:00Z")));

        mockMvc.perform(post("/v1/gate/check")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"entity_id\":\"ent-001\",\"entity_type\":\"model\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("approved"))
                .andExpect(jsonPath("$.passed").value(6))
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.checks[0].name").value("Registry Entry"))
                .andExpect(jsonPath("$.checks[0].passed").value(true));
    }

    @Test
    void checkAllChecksFailed() throws Exception {
        when(gateService.check(eq(TENANT), any()))
                .thenReturn(new GateCheckResult("ch-1", "nonexistent", "blocked", 1,
                        List.of(new CheckResult("Bias Test", true, "")), Instant.parse("2026-08-14T10:00:00Z")));

        mockMvc.perform(post("/v1/gate/check")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"entity_id\":\"nonexistent\",\"entity_type\":\"agent\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("blocked"));
    }

    @Test
    void checkPartialPass() throws Exception {
        List<CheckResult> checks = List.of(
                new CheckResult("Registry Entry", true, "AI Registry'de kayıtlı (model, staging)"),
                new CheckResult("Risk Assessment", false, "Risk değerlendirmesi yapılmamış"),
                new CheckResult("Policy Compliance", true, "1 pack aktif, %75 geçti"),
                new CheckResult("Documentation", false, "Teknik dokümantasyon kontrol edilmedi"),
                new CheckResult("Guardrails", false, "Guardrail kuralı kontrol edilmedi"),
                new CheckResult("Bias Test", true, "Bias testi gerekli değil (varsayılan)"));
        when(gateService.check(eq(TENANT), any()))
                .thenReturn(new GateCheckResult("ch-1", "ent-001", "flagged", 3, checks,
                        Instant.parse("2026-08-14T10:00:00Z")));

        mockMvc.perform(post("/v1/gate/check")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"entity_id\":\"ent-001\",\"target_environment\":\"staging\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("flagged"))
                .andExpect(jsonPath("$.passed").value(3));
    }

    // ---------- History ----------

    @Test
    void historySuccess() throws Exception {
        when(gateService.history(eq(TENANT), eq("ent-001")))
                .thenReturn(new GateHistoryResult("ent-001", TENANT,
                        List.of(
                                historyRow("ch-001", "approved", 6, 6),
                                historyRow("ch-002", "flagged", 4, 6)),
                        false));

        mockMvc.perform(get("/v1/gate/history/ent-001")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.has_more").value(false))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.history[0].decision").value("approved"));
    }

    @Test
    void historyEmpty() throws Exception {
        when(gateService.history(eq(TENANT), eq("ent-001")))
                .thenReturn(new GateHistoryResult("ent-001", TENANT, List.of(), false));

        mockMvc.perform(get("/v1/gate/history/ent-001")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history.length()").value(0))
                .andExpect(jsonPath("$.has_more").value(false));
    }

    @Test
    void historyQueryErrorReturnsGraceful() throws Exception {
        when(gateService.history(eq(TENANT), eq("ent-001")))
                .thenReturn(GateHistoryResult.empty());

        mockMvc.perform(get("/v1/gate/history/ent-001")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history").isArray())
                .andExpect(jsonPath("$.has_more").value(false));
    }

    // ---------- Helper fonksiyonlar ----------

    @Test
    void helperFunctions() {
        assertEquals("1 pack", GateText.packCount(1));
        assertEquals("3 pack", GateText.packCount(3));
        assertEquals("1 guardrail", GateText.guardrailCount(1));
        assertEquals("5 guardrail", GateText.guardrailCount(5));
        assertEquals("%70 geçti", GateText.controlPct(7, 10));
        assertEquals("%0 geçti", GateText.controlPct(0, 5));
    }

    // ---------- yardımcılar ----------

    private static Map<String, Object> historyRow(String id, String decision, int passed, int total) {
        return Map.of(
                "id", id,
                "entity_id", "ent-001",
                "entity_type", "model",
                "target_env", "production",
                "version", "1.0.0",
                "decision", decision,
                "passed_checks", passed,
                "total_checks", total,
                "checked_at", "2026-08-14T10:00:00Z");
    }
}
