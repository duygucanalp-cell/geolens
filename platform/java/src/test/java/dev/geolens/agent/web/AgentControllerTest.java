package dev.geolens.agent.web;

import dev.geolens.agent.service.AgentService;
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
import static org.mockito.ArgumentMatchers.anyInt;
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

    @MockBean
    private AgentService agentService;

    private static final String TENANT = "T01";
    private static final String BASE = "/v1/agents";

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
        when(agentService.startTrace(anyString(), any()))
                .thenReturn(Map.of(
                        "trace_id", "t-new",
                        "agent_name", "test-agent",
                        "workflow_name", "test-workflow",
                        "status", "running",
                        "started_at", "2026-08-15T10:00:00Z"));

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
        when(agentService.startTrace(anyString(), any()))
                .thenThrow(new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "trace başlatılamadı"));

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
        when(agentService.getTrace(anyString(), anyString()))
                .thenThrow(new ServiceException(HttpStatus.NOT_FOUND, "trace bulunamadı"));

        mockMvc.perform(get(BASE + "/traces/t-1")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("trace bulunamadı"));
    }

    @Test
    void getTraceSuccess() throws Exception {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("step_id", "s-1");
        step.put("step_name", "step-1");
        step.put("agent", "agent-a");
        step.put("input", "input1");
        step.put("output", "output1");
        step.put("status", "completed");
        step.put("duration_ms", 200);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("trace_id", "t-1");
        body.put("agent_name", "agent-a");
        body.put("workflow_name", "wf-1");
        body.put("status", "running");
        body.put("total_steps", 2);
        body.put("completed_steps", 1);
        body.put("total_duration_ms", 500);
        body.put("started_at", "2026-08-15T10:00:00Z");
        body.put("steps", List.of(step));

        when(agentService.getTrace(anyString(), anyString())).thenReturn(body);

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
        when(agentService.getTrace(anyString(), anyString()))
                .thenThrow(new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "adımlar alınamadı"));

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
        when(agentService.recordStep(anyString(), anyString(), any()))
                .thenThrow(new ServiceException(HttpStatus.NOT_FOUND, "trace bulunamadı"));

        mockMvc.perform(post(BASE + "/traces/t-1/steps")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"step_name\": \"test\", \"agent_name\": \"agent-a\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("trace bulunamadı"));
    }

    @Test
    void recordStepCompletedTraceReturns409() throws Exception {
        when(agentService.recordStep(anyString(), anyString(), any()))
                .thenThrow(new ServiceException(HttpStatus.CONFLICT, "tamamlanmış trace'e adım eklenemez"));

        mockMvc.perform(post(BASE + "/traces/t-1/steps")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"step_name\": \"test\", \"agent_name\": \"agent-a\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("tamamlanmış trace'e adım eklenemez"));
    }

    @Test
    void recordStepSuccess() throws Exception {
        when(agentService.recordStep(anyString(), anyString(), any()))
                .thenReturn(Map.of(
                        "step_id", "s-new",
                        "trace_id", "t-1",
                        "step_name", "step-1",
                        "status", "running",
                        "duration_ms", 200));

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
        when(agentService.recordStep(anyString(), anyString(), any()))
                .thenReturn(Map.of(
                        "step_id", "s-new",
                        "trace_id", "t-1",
                        "step_name", "step-1",
                        "status", "running",
                        "duration_ms", 0));

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
        when(agentService.completeTrace(anyString(), anyString(), any()))
                .thenThrow(new ServiceException(HttpStatus.NOT_FOUND, "trace bulunamadı veya zaten tamamlanmış"));

        mockMvc.perform(post(BASE + "/traces/t-1/complete")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"status\": \"completed\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("trace bulunamadı veya zaten tamamlanmış"));
    }

    @Test
    void completeTraceSuccess() throws Exception {
        when(agentService.completeTrace(anyString(), anyString(), any()))
                .thenReturn(Map.of("trace_id", "t-1", "status", "completed"));

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
        when(agentService.completeTrace(anyString(), anyString(), any()))
                .thenReturn(Map.of("trace_id", "t-1", "status", "completed"));

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
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("trace_id", "t-1");
        trace.put("agent_name", "agent-a");
        trace.put("workflow_name", "wf-1");
        trace.put("status", "running");
        trace.put("total_steps", 3);
        trace.put("completed_steps", 1);
        trace.put("total_duration_ms", 500);
        trace.put("started_at", "2026-08-15T10:00:00Z");

        when(agentService.listTraces(anyString(), anyInt(), any(), anyInt()))
                .thenReturn(Map.of("traces", List.of(trace), "total", 1, "limit", 20, "offset", 0));

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
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("trace_id", "t-1");

        when(agentService.listTraces(anyString(), anyInt(), any(), anyInt()))
                .thenReturn(Map.of("traces", List.of(trace), "total", 1, "limit", 20, "offset", 0));

        mockMvc.perform(get(BASE + "/traces")
                        .header("X-Tenant-ID", TENANT)
                        .param("limit", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(20));
    }

    @Test
    void listTracesQueryErrorReturns500() throws Exception {
        when(agentService.listTraces(anyString(), anyInt(), any(), anyInt()))
                .thenThrow(new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "trace listesi alınamadı"));

        mockMvc.perform(get(BASE + "/traces")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("trace listesi alınamadı"));
    }
}
