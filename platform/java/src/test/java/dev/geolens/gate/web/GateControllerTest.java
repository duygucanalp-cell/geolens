package dev.geolens.gate.web;

import dev.geolens.gate.GateText;
import dev.geolens.testutil.JooqTestData;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

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
        // registry sorgusu boş → blocked (Go TestCheck_NoEntityID parity)
        mockMvc.perform(post("/v1/gate/check")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"entity_id\":\"\",\"entity_type\":\"model\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("blocked"));
    }

    @Test
    void checkAllChecksPassed() throws Exception {
        // Sıralı fetchOne: registry, risk count, doc url, pack count, controls, guardrail count
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(
                        JooqTestData.record(registryRow("ent-001", "model", "production")),
                        JooqTestData.record(3),
                        JooqTestData.record("https://docs.example.com"),
                        JooqTestData.record(2),
                        JooqTestData.record(controlsRow(10, 8)),
                        JooqTestData.record(3));

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
        // registry sorgusu boş → tüm check'ler başarısız, bias hariç
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(null, null, null, null, null, null);

        mockMvc.perform(post("/v1/gate/check")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"entity_id\":\"nonexistent\",\"entity_type\":\"agent\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("blocked"));
    }

    @Test
    void checkPartialPass() throws Exception {
        // registry var; risk 0; doc boş; 1 pack + 4 kontrol (3 passed); guardrail 0
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(
                        JooqTestData.record(registryRow("ent-001", "model", "staging")),
                        JooqTestData.record(0),
                        JooqTestData.record(""),
                        JooqTestData.record(1),
                        JooqTestData.record(controlsRow(4, 3)),
                        JooqTestData.record(0));

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
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(
                        historyRow("ch-001", "approved", 6, 6),
                        historyRow("ch-002", "flagged", 4, 6))));

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
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of()));

        mockMvc.perform(get("/v1/gate/history/ent-001")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history.length()").value(0))
                .andExpect(jsonPath("$.has_more").value(false));
    }

    @Test
    void historyQueryErrorReturnsGraceful() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

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

    private static Map<String, Object> registryRow(String id, String type, String lifecycle) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("entity_type", type);
        m.put("lifecycle_state", lifecycle);
        return m;
    }

    private static Map<String, Object> controlsRow(int total, int passed) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("passed", passed);
        return m;
    }

    private static Map<String, Object> historyRow(String id, String decision, int passed, int total) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("entity_id", "ent-001");
        m.put("entity_type", "model");
        m.put("target_env", "production");
        m.put("version", "1.0.0");
        m.put("decision", decision);
        m.put("passed_checks", passed);
        m.put("total_checks", total);
        m.put("created_at", java.sql.Timestamp.from(java.time.Instant.parse("2026-08-14T10:00:00Z")));
        return m;
    }
}
