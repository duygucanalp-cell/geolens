package dev.geolens.version.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go version/handler_test.go parity testleri — versiyon takibi. */
@WebMvcTest(VersionController.class)
class VersionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JdbcTemplate jdbc;

    private static final String TENANT = "T01";

    // ---------- RecordVersion ----------

    @Test
    void recordVersionInvalidJSONReturns400() throws Exception {
        mockMvc.perform(post("/v1/versions/entries")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void recordVersionMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/v1/versions/entries")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"entity_type\":\"engine\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("entity_type ve entity_id gerekli"));
    }

    @Test
    void recordVersionSuccess() throws Exception {
        mockMvc.perform(post("/v1/versions/entries")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"entity_type\":\"engine\",\"entity_id\":\"E01\",\"entity_name\":\"chatgpt\",\"old_version\":\"1.0\",\"new_version\":\"1.1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entity_type").value("engine"))
                .andExpect(jsonPath("$.old_version").value("1.0"))
                .andExpect(jsonPath("$.new_version").value("1.1"))
                .andExpect(jsonPath("$.entry_id").isNotEmpty());
    }

    @Test
    void recordVersionDBErrorReturns500() throws Exception {
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new RuntimeException("db error"));

        mockMvc.perform(post("/v1/versions/entries")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"entity_type\":\"engine\",\"entity_id\":\"E01\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("versiyon kaydedilemedi"));
    }

    // ---------- ListVersions ----------

    @Test
    void listVersionsSuccess() throws Exception {
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(java.util.List.of(versionRow("V01", "engine", "E01", "1.0", "1.1")));

        mockMvc.perform(get("/v1/versions/entries")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("V01"))
                .andExpect(jsonPath("$.data[0].old_version").value("1.0"))
                .andExpect(jsonPath("$.data[0].new_version").value("1.1"))
                .andExpect(jsonPath("$.has_more").value(false));
    }

    @Test
    void listVersionsQueryErrorReturnsEmpty() throws Exception {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/versions/entries")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.has_more").value(false));
    }

    // ---------- GetVersionDiff ----------

    @Test
    void getVersionDiffNotFoundReturns404() throws Exception {
        when(jdbc.queryForMap(contains("FROM version.entries"), any(Object[].class)))
                .thenThrow(new RuntimeException("not found"));

        mockMvc.perform(get("/v1/versions/entries/none")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("versiyon kaydı bulunamadı"));
    }

    @Test
    void getVersionDiffSuccess() throws Exception {
        when(jdbc.queryForMap(contains("FROM version.entries"), any(Object[].class)))
                .thenReturn(versionRow("V01", "engine", "E01", "1.0", "1.1"));

        mockMvc.perform(get("/v1/versions/entries/V01")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry.id").value("V01"))
                .andExpect(jsonPath("$.entry.old_version").value("1.0"))
                .andExpect(jsonPath("$.has_changes").value(true));
    }

    @Test
    void getVersionDiffNoChange() throws Exception {
        when(jdbc.queryForMap(contains("FROM version.entries"), any(Object[].class)))
                .thenReturn(versionRow("V02", "engine", "E01", "1.1", "1.1"));

        mockMvc.perform(get("/v1/versions/entries/V02")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.has_changes").value(false));
    }

    // ---------- helpers ----------

    private static java.util.Map<String, Object> versionRow(String id, String type, String eid, String oldV, String newV) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", id);
        m.put("entity_type", type);
        m.put("entity_id", eid);
        m.put("entity_name", "chatgpt");
        m.put("old_version", oldV);
        m.put("new_version", newV);
        m.put("change_notes", "");
        m.put("changed_by", "");
        m.put("created_at", "2026-08-14T00:00:00Z");
        return m;
    }
}
