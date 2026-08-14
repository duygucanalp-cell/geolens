package dev.geolens.policy.web;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

    private static final String TENANT = "T01";

    // ---------- ListPacks ----------

    @Test
    void listPacksQueryErrorGraceful() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/policies/packs")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.packs").isArray());
    }

    @Test
    void listPacksSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(
                        packRow("pack-1", "EU AI Act Compliance", "eu_ai_act", "2026-07-25T00:00:00Z"),
                        packRow("pack-2", "NIST AI RMF", "nist_ai_rmf", null))));

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
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/policies/packs/pack-1/controls")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.controls").isArray());
    }

    @Test
    void listControlsSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(
                        controlRow("ctrl-1", "Art.9", "passed"),
                        controlRow("ctrl-2", "Art.10", "pending"))));

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
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("not found"));

        mockMvc.perform(post("/v1/policies/packs/nonexistent/apply")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("pack bulunamadı"));
    }

    @Test
    void applyPackSuccess() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(packRow("pack-1", "EU AI Act Compliance", "eu_ai_act", "2026-07-25T00:00:00Z")));

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
        // Go MockPool: 2 args → risk_class, diğer (1 arg) → aggregation
        when(dsl.fetchOne(anyString(), any(Object[].class))).thenAnswer((Answer<Record>) inv -> {
            if (inv.getArguments().length == 3) {
                return JooqTestData.record(Map.of("risk_class", "high"));
            }
            return JooqTestData.record(aggRow(10, 7, 2, 1));
        });

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
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(aggRow(5, 3, 1, 1)));

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
        mockMvc.perform(put("/v1/policies/controls/ctrl-1")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"status\": \"passed\", \"evidence\": \"test evidence\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("güncellendi"));
    }

    @Test
    void updateControlDbErrorReturns500() throws Exception {
        when(dsl.execute(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

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
        // 4 framework pack insert (fetchOne RETURNING) + control insert'leri
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(Map.of("id", "pack-seeded")));

        mockMvc.perform(post("/v1/policies/packs/seed")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("policy packs seeded"));

        // Go TestSeedDefaultPacks_AllFrameworks: 4 pack seed çağrısı
        verify(dsl, times(4)).fetchOne(anyString(), any(Object[].class));
    }

    // ---------- yardımcılar ----------

    private static Map<String, Object> packRow(String id, String name, String framework, String appliedAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("tenant_id", TENANT);
        m.put("name", name);
        m.put("framework", framework);
        m.put("description", framework + " description");
        m.put("version", "1.0.0");
        m.put("enabled", true);
        m.put("applied_at", appliedAt);
        m.put("created_at", "2026-07-01T00:00:00Z");
        m.put("updated_at", "2026-07-25T00:00:00Z");
        return m;
    }

    private static Map<String, Object> controlRow(String id, String controlId, String status) {
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

    private static Map<String, Object> aggRow(int total, int passed, int failed, int na) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("0", total);
        m.put("1", passed);
        m.put("2", failed);
        m.put("3", na);
        return m;
    }
}
