package dev.geolens.policy.web;

import dev.geolens.policy.Pack;
import dev.geolens.policy.service.PolicyService;
import dev.geolens.common.ServiceException;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go policy/handler_test.go parity testleri — Policy Packs REST (R4). */
@WebMvcTest(PolicyController.class)
class PolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PolicyService policyService;

    private static final String TENANT = "T01";

    // ---------- ListPacks ----------

    @Test
    void listPacksQueryErrorGraceful() throws Exception {
        when(policyService.listPacks(any())).thenReturn(Map.of("packs", List.of()));

        mockMvc.perform(get("/v1/policies/packs")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packs").isArray());
    }

    @Test
    void listPacksSuccess() throws Exception {
        when(policyService.listPacks(any())).thenReturn(Map.of("packs", List.of(
                pack("pack-1", "EU AI Act Compliance", "eu_ai_act", "2026-07-25T00:00:00Z"),
                pack("pack-2", "NIST AI RMF", "nist_ai_rmf", null))));

        mockMvc.perform(get("/v1/policies/packs")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packs.length()").value(2))
                .andExpect(jsonPath("$.packs[0].name").value("EU AI Act Compliance"))
                .andExpect(jsonPath("$.packs[0].framework").value("eu_ai_act"))
                .andExpect(jsonPath("$.packs[0].applied_at").value("2026-07-25T00:00:00Z"))
                .andExpect(jsonPath("$.packs[1].framework").value("nist_ai_rmf"));
    }

    // ---------- ListControls ----------

    @Test
    void listControlsQueryErrorGraceful() throws Exception {
        when(policyService.listControls(any(), any())).thenReturn(Map.of("controls", List.of()));

        mockMvc.perform(get("/v1/policies/packs/pack-1/controls")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.controls").isArray());
    }

    @Test
    void listControlsSuccess() throws Exception {
        when(policyService.listControls(any(), any())).thenReturn(Map.of("controls", List.of(
                control("ctrl-1", "Art.9", "passed"),
                control("ctrl-2", "Art.10", "pending"))));

        mockMvc.perform(get("/v1/policies/packs/pack-1/controls")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.controls.length()").value(2))
                .andExpect(jsonPath("$.controls[0].control_id").value("Art.9"))
                .andExpect(jsonPath("$.controls[0].status").value("passed"))
                .andExpect(jsonPath("$.controls[1].control_id").value("Art.10"));
    }

    // ---------- ApplyPack ----------

    @Test
    void applyPackNotFound() throws Exception {
        when(policyService.applyPack(any(), any()))
                .thenThrow(new ServiceException(HttpStatus.NOT_FOUND, "pack bulunamadı"));

        mockMvc.perform(post("/v1/policies/packs/nonexistent/apply")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("pack bulunamadı"));
    }

    @Test
    void applyPackSuccess() throws Exception {
        when(policyService.applyPack(any(), any()))
                .thenReturn(pack("pack-1", "EU AI Act Compliance", "eu_ai_act", "2026-07-25T00:00:00Z"));

        mockMvc.perform(post("/v1/policies/packs/pack-1/apply")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("pack-1"))
                .andExpect(jsonPath("$.name").value("EU AI Act Compliance"))
                .andExpect(jsonPath("$.framework").value("eu_ai_act"));
    }

    // ---------- GetCompliance ----------

    @Test
    void getComplianceSuccess() throws Exception {
        when(policyService.getCompliance(any(), any())).thenReturn(Map.of(
                "entity_id", "ent-001",
                "compliance_pct", 70.0,
                "total_controls", 10,
                "passed", 7,
                "failed", 2,
                "not_applicable", 1,
                "entity_risk_class", "high"));

        mockMvc.perform(get("/v1/policies/compliance/ent-001")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_controls").value(10))
                .andExpect(jsonPath("$.passed").value(7))
                .andExpect(jsonPath("$.failed").value(2))
                .andExpect(jsonPath("$.not_applicable").value(1))
                // 7/10 * 100 = 70
                .andExpect(jsonPath("$.compliance_pct").value(70.0))
                .andExpect(jsonPath("$.entity_risk_class").value("high"))
                .andExpect(jsonPath("$.entity_id").value("ent-001"));
    }

    @Test
    void getComplianceWithoutEntityRisk() throws Exception {
        when(policyService.getCompliance(any(), any())).thenReturn(Map.of(
                "entity_id", "undefined",
                "compliance_pct", 60.0,
                "total_controls", 5,
                "passed", 3,
                "failed", 1,
                "not_applicable", 1));

        mockMvc.perform(get("/v1/policies/compliance/undefined")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_controls").value(5))
                .andExpect(jsonPath("$.compliance_pct").value(60.0))
                .andExpect(jsonPath("$.entity_risk_class").doesNotExist());
    }

    // ---------- UpdateControl ----------

    @Test
    void updateControlInvalidJsonReturns400() throws Exception {
        mockMvc.perform(put("/v1/policies/controls/ctrl-1")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void updateControlSuccess() throws Exception {
        when(policyService.updateControl(any(), any(), any())).thenReturn(Map.of("status", "güncellendi"));

        mockMvc.perform(put("/v1/policies/controls/ctrl-1")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"status\": \"passed\", \"evidence\": \"test evidence\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("güncellendi"));
    }

    @Test
    void updateControlDbErrorReturns500() throws Exception {
        when(policyService.updateControl(any(), any(), any()))
                .thenThrow(new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "control güncellenemedi"));

        mockMvc.perform(put("/v1/policies/controls/ctrl-1")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"status\": \"failed\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("control güncellenemedi"));
    }

    // ---------- SeedPacks ----------

    @Test
    void seedPacksSuccess() throws Exception {
        when(policyService.seedPacks(any())).thenReturn(Map.of("status", "policy packs seeded"));

        mockMvc.perform(post("/v1/policies/packs/seed")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("policy packs seeded"));
    }

    // ---------- yardımcılar ----------

    private static Pack pack(String id, String name, String framework, String appliedAt) {
        return new Pack(id, TENANT, name, framework, framework + " description", "1.0.0", true,
                appliedAt, "2026-07-01T00:00:00Z", "2026-07-25T00:00:00Z");
    }

    private static Map<String, Object> control(String id, String controlId, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("pack_id", "pack-1");
        m.put("tenant_id", TENANT);
        m.put("control_id", controlId);
        m.put("title", "Kontrol " + controlId);
        m.put("description", "Açıklama");
        m.put("category", "Risk Management");
        m.put("status", status);
        m.put("evidence", "");
        m.put("due_date", null);
        m.put("created_at", "2026-07-01T00:00:00Z");
        m.put("updated_at", "2026-07-25T00:00:00Z");
        return m;
    }
}
