package dev.geolens.incident.web;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go incident/handler_test.go parity testleri — incident yönetimi REST. */
@WebMvcTest(IncidentController.class)
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

    private static final String TENANT = "T01";

    // ---------- ListIncidents ----------

    @Test
    void listIncidentsSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(incidentRow("i-1"))));
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(1));

        mockMvc.perform(get("/v1/incidents/events")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.has_more").value(false))
                .andExpect(jsonPath("$.open_count").value(1))
                .andExpect(jsonPath("$.critical_count").value(1))
                .andExpect(jsonPath("$.incidents[0].id").value("i-1"))
                .andExpect(jsonPath("$.incidents[0].severity").value("critical"))
                .andExpect(jsonPath("$.incidents[0].title").value("API Down"))
                .andExpect(jsonPath("$.incidents[0].status").value("open"));
    }

    @Test
    void listIncidentsQueryErrorReturnsGracefulEmpty() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class))).thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/incidents/events")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidents").isArray())
                .andExpect(jsonPath("$.has_more").value(false))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void listIncidentsHasMoreTrue() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(
                        incidentRow("i-1"), incidentRow("i-2"), incidentRow("i-3"))));
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(3));

        mockMvc.perform(get("/v1/incidents/events")
                        .header("X-Tenant-ID", TENANT)
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.has_more").value(true));
    }

    // ---------- CreateIncident ----------

    @Test
    void createIncidentInvalidJsonReturns400() throws Exception {
        mockMvc.perform(post("/v1/incidents/events")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void createIncidentMissingTitleReturns400() throws Exception {
        mockMvc.perform(post("/v1/incidents/events")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"title\":\"\",\"severity\":\"high\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("title gerekli"));
    }

    @Test
    void createIncidentSuccess() throws Exception {
        mockMvc.perform(post("/v1/incidents/events")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"title\":\"API Outage\",\"severity\":\"critical\",\"category\":\"outage\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.incident_id").isNotEmpty())
                .andExpect(jsonPath("$.severity").value("critical"))
                .andExpect(jsonPath("$.title").value("API Outage"))
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.created_at").isNotEmpty());
    }

    @Test
    void createIncidentDefaultsInvalidSeverityAndCategory() throws Exception {
        mockMvc.perform(post("/v1/incidents/events")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"title\":\"T\",\"severity\":\"huge\",\"category\":\"nope\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.severity").value("medium"));
    }

    // ---------- UpdateIncident ----------

    @Test
    void updateIncidentInvalidJsonReturns400() throws Exception {
        mockMvc.perform(put("/v1/incidents/events/i-1")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void updateIncidentInvalidStatusReturns400() throws Exception {
        mockMvc.perform(put("/v1/incidents/events/i-1")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"status\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz durum"));
    }

    @Test
    void updateIncidentNotFoundReturns404() throws Exception {
        when(dsl.execute(anyString(), any(Object[].class))).thenReturn(0);

        mockMvc.perform(put("/v1/incidents/events/i-1")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"status\":\"resolved\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("incident bulunamadı"));
    }

    @Test
    void updateIncidentSuccess() throws Exception {
        when(dsl.execute(anyString(), any(Object[].class))).thenReturn(1);

        mockMvc.perform(put("/v1/incidents/events/i-1")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"status\":\"resolved\",\"resolution\":\"fixed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incident_id").value("i-1"))
                .andExpect(jsonPath("$.status").value("resolved"));
    }

    // ---------- yardımcılar ----------

    private static Map<String, Object> incidentRow(String id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("severity", "critical");
        m.put("category", "outage");
        m.put("title", "API Down");
        m.put("status", "open");
        m.put("source", "monitoring");
        m.put("entity_id", "ent-1");
        m.put("assigned_to", "user-1");
        m.put("severity_score", 9.5);
        m.put("occurred_at", "2026-08-14T00:00:00Z");
        m.put("resolved_at", null);
        m.put("created_at", "2026-08-14T00:00:00Z");
        return m;
    }
}
