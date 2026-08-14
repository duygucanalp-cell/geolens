package dev.geolens.drift.web;

import dev.geolens.testutil.JooqTestData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go drift/handler_test.go parity testleri — drift tespiti REST. */
@WebMvcTest(DriftController.class)
class DriftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

    private static final String TENANT = "T01";

    // ---------- Record ----------

    @Test
    void recordInvalidJsonReturns400() throws Exception {
        mockMvc.perform(post("/v1/drift/record")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void recordMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/v1/drift/record")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"entity_id\":\"\",\"metric\":\"score\",\"value\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("entity_id ve metric zorunludur"));
    }

    @Test
    void recordSuccess() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(observationRow("obs-1")));

        mockMvc.perform(post("/v1/drift/record")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"entity_id\":\"ent-1\",\"entity_name\":\"Marka A\",\"metric\":\"visibility_score\",\"value\":72.5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("obs-1"))
                .andExpect(jsonPath("$.entity_id").value("ent-1"))
                .andExpect(jsonPath("$.metric").value("visibility_score"))
                .andExpect(jsonPath("$.value").value(72.5));
    }

    // ---------- ListObservations ----------

    @Test
    void listObservationsMissingParamsReturns400() throws Exception {
        mockMvc.perform(get("/v1/drift/observations")
                        .header("X-Tenant-ID", TENANT)
                        .param("entity_id", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("entity_id ve metric parametreleri zorunludur"));
    }

    @Test
    void listObservationsSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(
                        observationRow("obs-1"), observationRow("obs-2"))));

        mockMvc.perform(get("/v1/drift/observations")
                        .header("X-Tenant-ID", TENANT)
                        .param("entity_id", "ent-1")
                        .param("metric", "visibility_score"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.observations.length()").value(2));
    }

    // ---------- ListEntities ----------

    @Test
    void listEntitiesSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(entityRow())));

        mockMvc.perform(get("/v1/drift/entities")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entities.length()").value(1))
                .andExpect(jsonPath("$.entities[0].entity_id").value("ent-1"))
                .andExpect(jsonPath("$.entities[0].observation_count").value(5));
    }

    // ---------- Analyze ----------

    @Test
    void analyzeQueryErrorReturns500() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/drift/analysis")
                        .header("X-Tenant-ID", TENANT)
                        .param("entity_id", "e")
                        .param("metric", "m"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("gözlem sorgu hatası"));
    }

    @Test
    void analyzeInsufficientData() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(
                        valueRow(10), valueRow(11), valueRow(12))));

        mockMvc.perform(get("/v1/drift/analysis")
                        .header("X-Tenant-ID", TENANT)
                        .param("entity_id", "e")
                        .param("metric", "m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severity").value("insufficient_data"))
                .andExpect(jsonPath("$.drift_score").value(0));
    }

    @Test
    void analyzeWithDriftCreatesAlert() throws Exception {
        // 6x10 + 6x50 → referans ortalama 10, güncel ortalama 46 → kritik sapma
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            rows.add(valueRow(i < 6 ? 10 : 50));
        }
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(rows));

        mockMvc.perform(get("/v1/drift/analysis")
                        .header("X-Tenant-ID", TENANT)
                        .param("entity_id", "e")
                        .param("metric", "m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severity").value("critical"))
                .andExpect(jsonPath("$.drift_score").isNotEmpty());
    }

    @Test
    void analyzeMissingParamsReturns400() throws Exception {
        mockMvc.perform(get("/v1/drift/analysis")
                        .header("X-Tenant-ID", TENANT)
                        .param("entity_id", "e"))
                .andExpect(status().isBadRequest());
    }

    // ---------- ListAlerts ----------

    @Test
    void listAlertsSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(alertRow())));

        mockMvc.perform(get("/v1/drift/alerts")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.alerts.length()").value(1))
                .andExpect(jsonPath("$.alerts[0].severity").value("warning"))
                .andExpect(jsonPath("$.alerts[0].drift_score").value(72.0));
    }

    // ---------- yardımcılar ----------

    private static Map<String, Object> valueRow(double v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", v);
        return m;
    }

    private static Map<String, Object> observationRow(String id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("tenant_id", "t-1");
        m.put("entity_id", "ent-1");
        m.put("entity_name", "Marka A");
        m.put("metric", "visibility_score");
        m.put("value", 72.5);
        m.put("window_start", "2026-08-01T00:00:00Z");
        m.put("created_at", "2026-08-01T00:00:00Z");
        return m;
    }

    private static Map<String, Object> entityRow() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entity_id", "ent-1");
        m.put("entity_name", "Marka A");
        m.put("metric", "visibility_score");
        m.put("observation_count", 5);
        m.put("mean_value", 71.4);
        m.put("last_observed", "now");
        return m;
    }

    private static Map<String, Object> alertRow() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "alert-1");
        m.put("tenant_id", "t-1");
        m.put("entity_id", "ent-1");
        m.put("entity_name", "Marka A");
        m.put("metric", "visibility_score");
        m.put("drift_score", 72.0);
        m.put("severity", "warning");
        m.put("reference_mean", 60.0);
        m.put("current_mean", 80.0);
        m.put("delta", 20.0);
        m.put("detail", "sapma");
        m.put("created_at", "now");
        return m;
    }
}
