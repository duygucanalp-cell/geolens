package dev.geolens.incident.web;

import dev.geolens.incident.service.IncidentService;
import dev.geolens.incident.service.IncidentServiceException;
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

    @MockBean
    private IncidentService incidentService;

    private static final String TENANT = "T01";

    // ---------- ListIncidents ----------

    @Test
    void listIncidentsSuccess() throws Exception {
        when(incidentService.listIncidents(any(), anyInt(), any(), any()))
                .thenReturn(Map.of(
                        "incidents", List.of(incidentRow("i-1")),
                        "count", 1, "has_more", false, "open_count", 1, "critical_count", 1));

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
        when(incidentService.listIncidents(any(), anyInt(), any(), any()))
                .thenReturn(Map.of("incidents", List.of(), "has_more", false, "count", 0));

        mockMvc.perform(get("/v1/incidents/events")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidents").isArray())
                .andExpect(jsonPath("$.has_more").value(false))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void listIncidentsHasMoreTrue() throws Exception {
        when(incidentService.listIncidents(any(), anyInt(), any(), any()))
                .thenReturn(Map.of(
                        "incidents", List.of(incidentRow("i-1"), incidentRow("i-2")),
                        "count", 2, "has_more", true, "open_count", 2, "critical_count", 2));

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
        when(incidentService.createIncident(any(), any()))
                .thenThrow(new IncidentServiceException(HttpStatus.BAD_REQUEST, "title gerekli"));

        mockMvc.perform(post("/v1/incidents/events")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"title\":\"\",\"severity\":\"high\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("title gerekli"));
    }

    @Test
    void createIncidentSuccess() throws Exception {
        when(incidentService.createIncident(any(), any()))
                .thenReturn(Map.of(
                        "incident_id", "inc-1",
                        "severity", "critical",
                        "title", "API Outage",
                        "status", "open",
                        "severity_score", 0.0,
                        "created_at", "2026-08-15T00:00:00Z"));

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
        when(incidentService.createIncident(any(), any()))
                .thenReturn(Map.of(
                        "incident_id", "inc-2",
                        "severity", "medium",
                        "title", "T",
                        "status", "open",
                        "severity_score", 0.0,
                        "created_at", "2026-08-15T00:00:00Z"));

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
        when(incidentService.updateIncident(any(), any(), any()))
                .thenThrow(new IncidentServiceException(HttpStatus.BAD_REQUEST, "geçersiz durum"));

        mockMvc.perform(put("/v1/incidents/events/i-1")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"status\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz durum"));
    }

    @Test
    void updateIncidentNotFoundReturns404() throws Exception {
        when(incidentService.updateIncident(any(), any(), any()))
                .thenThrow(new IncidentServiceException(HttpStatus.NOT_FOUND, "incident bulunamadı"));

        mockMvc.perform(put("/v1/incidents/events/i-1")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"status\":\"resolved\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("incident bulunamadı"));
    }

    @Test
    void updateIncidentSuccess() throws Exception {
        when(incidentService.updateIncident(any(), any(), any()))
                .thenReturn(Map.of("incident_id", "i-1", "status", "resolved"));

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
