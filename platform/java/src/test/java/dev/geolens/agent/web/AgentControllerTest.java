package dev.geolens.agent.web;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go agent/handler_test.go davranış parity testleri — Agent Tracing REST (R8). */
@WebMvcTest(AgentController.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

    private static final String TENANT = "T01";
    private static final String BASE = "/v1/workspaces/ws-1/agents";

    // ---------- StartTrace ----------

    @Test
    void startTraceInvalidJsonReturns400() throws Exception {
        mockMvc.perform(post(BASE + "/traces")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void startTraceEmptyAgentNameReturns400() throws Exception {
        mockMvc.perform(post(BASE + "/traces")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"agent_name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("agent_name gerekli"));
    }

    @Test
    void startTraceSuccess() throws Exception {
        mockMvc.perform(post(BASE + "/traces")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"agent_name\": \"test-agent\", \"workflow_name\": \"test-workflow\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agent_name").value("test-agent"))
                .andExpect(jsonPath("$.workflow_name").value("test-workflow"))
                .andExpect(jsonPath("$.status").value("running"))
                .andExpect(jsonPath("$.trace_id").isNotEmpty());
    }

    @Test
    void startTraceDbErrorReturns500() throws Exception {
        when(dsl.execute(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db down"));

        mockMvc.perform(post(BASE + "/traces")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"agent_name\": \"test-agent\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("trace başlatılamadı"));
    }

    // ---------- GetTrace ----------

    @Test
    void getTraceNotFound() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class))).thenReturn(null);

        mockMvc.perform(get(BASE + "/traces/t-1")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("trace bulunamadı"));
    }

    @Test
    void getTraceSuccess() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(traceRow()));
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(stepRow())));

        mockMvc.perform(get(BASE + "/traces/t-1")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trace_id").value("t-1"))
                .andExpect(jsonPath("$.agent_name").value("agent-a"))
                .andExpect(jsonPath("$.workflow_name").value("wf-1"))
                .andExpect(jsonPath("$.status").value("running"))
                .andExpect(jsonPath("$.total_steps").value(2))
                .andExpect(jsonPath("$.completed_steps").value(1))
                .andExpect(jsonPath("$.total_duration_ms").value(500))
                .andExpect(jsonPath("$.steps[0].step_id").value("s-1"))
                .andExpect(jsonPath("$.steps[0].step_name").value("step-1"))
                .andExpect(jsonPath("$.steps[0].agent").value("agent-a"))
                .andExpect(jsonPath("$.steps[0].status").value("completed"));
    }

    @Test
    void getTraceStepsErrorReturns500() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(traceRow()));
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get(BASE + "/traces/t-1")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("adımlar alınamadı"));
    }

    // ---------- RecordStep ----------

    @Test
    void recordStepInvalidJsonReturns400() throws Exception {
        mockMvc.perform(post(BASE + "/traces/t-1/steps")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void recordStepTraceNotFoundReturns404() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class))).thenReturn(null);

        mockMvc.perform(post(BASE + "/traces/t-1/steps")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"step_name\": \"test\", \"agent_name\": \"agent-a\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("trace bulunamadı"));
    }

    @Test
    void recordStepCompletedTraceReturns409() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(Map.of("status", "completed")));

        mockMvc.perform(post(BASE + "/traces/t-1/steps")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"step_name\": \"test\", \"agent_name\": \"agent-a\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("tamamlanmış trace'e adım eklenemez"));
    }

    @Test
    void recordStepSuccess() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(Map.of("status", "running")));

        mockMvc.perform(post(BASE + "/traces/t-1/steps")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"step_name\": \"step-1\", \"agent_name\": \"agent-a\", \"duration_ms\": 200}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trace_id").value("t-1"))
                .andExpect(jsonPath("$.step_name").value("step-1"))
                .andExpect(jsonPath("$.status").value("running"))
                .andExpect(jsonPath("$.duration_ms").value(200));
    }

    @Test
    void recordStepInvalidStatusDefaultsToRunning() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(Map.of("status", "running")));

        mockMvc.perform(post(BASE + "/traces/t-1/steps")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"step_name\": \"step-1\", \"agent_name\": \"agent-a\", \"status\": \"bogus\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("running"));
    }

    // ---------- CompleteTrace ----------

    @Test
    void completeTraceInvalidJsonReturns400() throws Exception {
        mockMvc.perform(post(BASE + "/traces/t-1/complete")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void completeTraceNotFoundWhenZeroRows() throws Exception {
        when(dsl.execute(anyString(), any(Object[].class))).thenReturn(0);

        mockMvc.perform(post(BASE + "/traces/t-1/complete")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"status\": \"completed\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("trace bulunamadı veya zaten tamamlanmış"));
    }

    @Test
    void completeTraceSuccess() throws Exception {
        when(dsl.execute(anyString(), any(Object[].class))).thenReturn(1);

        mockMvc.perform(post(BASE + "/traces/t-1/complete")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"status\": \"completed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trace_id").value("t-1"))
                .andExpect(jsonPath("$.status").value("completed"));
    }

    @Test
    void completeTraceInvalidStatusDefaultsToCompleted() throws Exception {
        when(dsl.execute(anyString(), any(Object[].class))).thenReturn(1);

        mockMvc.perform(post(BASE + "/traces/t-1/complete")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"status\": \"bogus\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"));
    }

    // ---------- ListTraces ----------

    @Test
    void listTracesSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(listRow())));
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(Map.of("count", 1)));

        mockMvc.perform(get(BASE + "/traces")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traces[0].trace_id").value("t-1"))
                .andExpect(jsonPath("$.traces[0].agent_name").value("agent-a"))
                .andExpect(jsonPath("$.traces[0].status").value("running"))
                .andExpect(jsonPath("$.traces[0].total_steps").value(3))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.offset").value(0));
    }

    @Test
    void listTracesInvalidLimitDefaultsTo20() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(listRow())));
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(Map.of("count", 1)));

        mockMvc.perform(get(BASE + "/traces")
                        .header("X-Tenant-ID", TENANT)
                        .param("limit", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(20));
    }

    @Test
    void listTracesQueryErrorReturns500() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get(BASE + "/traces")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("trace listesi alınamadı"));
    }

    // ---------- yardımcılar ----------

    private static Map<String, Object> traceRow() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("agent_name", "agent-a");
        m.put("workflow_name", "wf-1");
        m.put("status", "running");
        m.put("total_steps", 2);
        m.put("completed_steps", 1);
        m.put("total_duration_ms", 500);
        m.put("started_at", "2026-08-15T10:00:00Z");
        m.put("completed_at", null);
        return m;
    }

    private static Map<String, Object> stepRow() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "s-1");
        m.put("step_name", "step-1");
        m.put("agent_name", "agent-a");
        m.put("input", "input1");
        m.put("output", "output1");
        m.put("status", "completed");
        m.put("duration_ms", 200);
        m.put("error_message", "");
        m.put("started_at", "2026-08-15T10:00:00Z");
        m.put("completed_at", "2026-08-15T10:00:01Z");
        return m;
    }

    private static Map<String, Object> listRow() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "t-1");
        m.put("agent_name", "agent-a");
        m.put("workflow_name", "wf-1");
        m.put("status", "running");
        m.put("total_steps", 3);
        m.put("completed_steps", 1);
        m.put("total_duration_ms", 500);
        m.put("started_at", "2026-08-15T10:00:00Z");
        m.put("completed_at", null);
        return m;
    }
}
