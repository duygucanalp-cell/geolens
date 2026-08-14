package dev.geolens.alert.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go alert/handler_test.go parity testleri — uyarı kuralları. */
@WebMvcTest(AlertController.class)
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JdbcTemplate jdbc;

    private static final String TENANT = "T01";

    // ---------- List ----------

    @Test
    void listSuccess() throws Exception {
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(java.util.List.of(ruleRow("R01", "B01", "Düşük Skor")));

        mockMvc.perform(get("/v1/workspaces/WS01/alert-rules")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules[0].id").value("R01"))
                .andExpect(jsonPath("$.rules[0].name").value("Düşük Skor"));
    }

    @Test
    void listQueryErrorReturnsEmpty() throws Exception {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/workspaces/WS01/alert-rules")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules").isArray());
    }

    // ---------- Create ----------

    @Test
    void createInvalidJSONReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/WS01/alert-rules")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void createMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/WS01/alert-rules")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\":\"B01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("marka, ad, metrik ve koşul zorunludur"));
    }

    @Test
    void createBrandNotFoundReturns404() throws Exception {
        when(jdbc.queryForObject(contains("SELECT EXISTS"), eq(Boolean.class), any(), any(), any()))
                .thenReturn(false);

        mockMvc.perform(post("/v1/workspaces/WS01/alert-rules")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\":\"none\",\"name\":\"Kural\",\"metric\":\"score\",\"condition\":\"lt\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("marka bulunamadı"));
    }

    @Test
    void createSuccess() throws Exception {
        when(jdbc.queryForObject(contains("SELECT EXISTS"), eq(Boolean.class), any(), any(), any()))
                .thenReturn(true);
        when(jdbc.queryForObject(contains("INSERT INTO governance.alert_rules"), eq(String.class), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("R-new");

        mockMvc.perform(post("/v1/workspaces/WS01/alert-rules")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\":\"B01\",\"name\":\"Düşük Skor\",\"metric\":\"score\",\"condition\":\"lt\",\"threshold\":50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("R-new"))
                .andExpect(jsonPath("$.status").value("created"));
    }

    // ---------- Update ----------

    @Test
    void updateInvalidJSONReturns400() throws Exception {
        mockMvc.perform(put("/v1/workspaces/WS01/alert-rules/R01")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateSuccess() throws Exception {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        mockMvc.perform(put("/v1/workspaces/WS01/alert-rules/R01")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("updated"));
    }

    @Test
    void updateNotFoundReturns404() throws Exception {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);

        mockMvc.perform(put("/v1/workspaces/WS01/alert-rules/none")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"enabled\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("kural bulunamadı"));
    }

    // ---------- Delete ----------

    @Test
    void deleteSuccess() throws Exception {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        mockMvc.perform(delete("/v1/workspaces/WS01/alert-rules/R01")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("deleted"));
    }

    @Test
    void deleteNotFoundReturns404() throws Exception {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);

        mockMvc.perform(delete("/v1/workspaces/WS01/alert-rules/none")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    private static java.util.Map<String, Object> ruleRow(String id, String brandId, String name) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", id);
        m.put("brand_id", brandId);
        m.put("name", name);
        m.put("metric", "score");
        m.put("condition", "lt");
        m.put("threshold", 50.0);
        m.put("channel", "email");
        m.put("channel_config", null);
        m.put("enabled", true);
        m.put("cooldown_min", 60);
        m.put("last_fired_at", null);
        m.put("created_at", "2026-08-14T00:00:00Z");
        m.put("updated_at", "2026-08-14T00:00:00Z");
        return m;
    }
}
