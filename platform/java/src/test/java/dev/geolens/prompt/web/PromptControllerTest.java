package dev.geolens.prompt.web;

import dev.geolens.prompt.service.PromptService;
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

/** Go prompt/handler_test.go parity testleri — Prompt Audit REST (R9). */
@WebMvcTest(PromptController.class)
class PromptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PromptService promptService;

    private static final String TENANT = "T01";

    // ---------- RunAudit ----------

    @Test
    void runAuditInvalidJsonReturns400() throws Exception {
        mockMvc.perform(post("/v1/prompts/audit")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void runAuditEmptyPromptReturns400() throws Exception {
        mockMvc.perform(post("/v1/prompts/audit")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"prompt_text\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("prompt_text gerekli"));
    }

    @Test
    void runAuditSuccess() throws Exception {
        when(promptService.runAudit(any(), any()))
                .thenReturn(auditResponse("aud-1", "p-1", "perplexity", "flagged", 0.6));

        mockMvc.perform(post("/v1/prompts/audit")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"prompt_text\": \"Bu bir test promptudur\", \"engine_name\": \"perplexity\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.audit_id").isNotEmpty())
                .andExpect(jsonPath("$.engine_name").value("perplexity"))
                .andExpect(jsonPath("$.status").value("flagged"))
                .andExpect(jsonPath("$.score").isNumber())
                .andExpect(jsonPath("$.issues").isArray())
                .andExpect(jsonPath("$.token_count").isNumber())
                .andExpect(jsonPath("$.latency_ms").isNumber());
    }

    @Test
    void runAuditDefaultEngineGeneric() throws Exception {
        when(promptService.runAudit(any(), any()))
                .thenReturn(auditResponse("aud-2", "p-2", "generic", "flagged", 0.6));

        mockMvc.perform(post("/v1/prompts/audit")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"prompt_text\": \"Marka analizi kaynakla\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.engine_name").value("generic"));
    }

    @Test
    void runAuditDbErrorReturns500() throws Exception {
        when(promptService.runAudit(any(), any()))
                .thenThrow(new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "denetim kaydedilemedi"));

        mockMvc.perform(post("/v1/prompts/audit")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"prompt_text\": \"deneme promptu\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("denetim kaydedilemedi"));
    }

    // ---------- ListAudits ----------

    @Test
    void listAuditsSuccess() throws Exception {
        when(promptService.listAudits(anyString(), anyInt(), anyInt(), any(), any()))
                .thenReturn(List.of(auditRow("a-1", "p-1", "passed")));

        mockMvc.perform(get("/v1/prompts/audits")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("a-1"))
                .andExpect(jsonPath("$[0].status").value("passed"))
                .andExpect(jsonPath("$[0].score").value(0.95))
                .andExpect(jsonPath("$[0].issues").isArray());
    }

    @Test
    void listAuditsQueryErrorReturns500() throws Exception {
        when(promptService.listAudits(anyString(), anyInt(), anyInt(), any(), any()))
                .thenThrow(new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "denetim geçmişi alınamadı"));

        mockMvc.perform(get("/v1/prompts/audits")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("denetim geçmişi alınamadı"));
    }

    @Test
    void listAuditsFilters() throws Exception {
        when(promptService.listAudits(anyString(), anyInt(), anyInt(), any(), any()))
                .thenReturn(List.of(auditRow("a-1", "p-1", "flagged")));

        mockMvc.perform(get("/v1/prompts/audits")
                        .header("X-Tenant-ID", TENANT)
                        .param("status", "flagged")
                        .param("engine", "perplexity")
                        .param("limit", "5")
                        .param("offset", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("flagged"));
    }

    @Test
    void listAuditsInvalidLimitDefaults() throws Exception {
        when(promptService.listAudits(anyString(), anyInt(), anyInt(), any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/v1/prompts/audits")
                        .header("X-Tenant-ID", TENANT)
                        .param("limit", "500")
                        .param("offset", "-5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ---------- GetAudit ----------

    @Test
    void getAuditNotFound() throws Exception {
        when(promptService.getAudit(any(), any()))
                .thenThrow(new ServiceException(HttpStatus.NOT_FOUND, "denetim bulunamadı"));

        mockMvc.perform(get("/v1/prompts/audits/a-1")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("denetim bulunamadı"));
    }

    @Test
    void getAuditSuccess() throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("audit_id", "a-1");
        row.put("prompt_id", "p-1");
        row.put("prompt_text", "test prompt");
        row.put("engine_name", "perplexity");
        row.put("status", "passed");
        row.put("score", 0.9);
        row.put("token_count", 50);
        row.put("latency_ms", 200);
        row.put("issues", List.of());
        row.put("metadata", Map.of());
        row.put("created_at", "2026-08-15T10:00:00Z");
        when(promptService.getAudit(any(), any()))
                .thenReturn(row);

        mockMvc.perform(get("/v1/prompts/audits/a-1")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audit_id").value("a-1"))
                .andExpect(jsonPath("$.prompt_id").value("p-1"))
                .andExpect(jsonPath("$.status").value("passed"))
                .andExpect(jsonPath("$.score").value(0.9))
                .andExpect(jsonPath("$.metadata").isMap());
    }

    // ---------- yardımcılar ----------

    private static Map<String, Object> auditResponse(String id, String promptId, String engine, String status, double score) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("audit_id", id);
        m.put("prompt_id", promptId);
        m.put("prompt_text", "test prompt");
        m.put("engine_name", engine);
        m.put("status", status);
        m.put("score", score);
        m.put("token_count", 50);
        m.put("latency_ms", 200);
        m.put("issues", List.of());
        return m;
    }

    private static Map<String, Object> auditRow(String id, String promptId, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("prompt_id", promptId);
        m.put("prompt_text", "test prompt");
        m.put("engine_name", "perplexity");
        m.put("status", status);
        m.put("score", 0.95);
        m.put("token_count", 50);
        m.put("latency_ms", 200);
        m.put("issues", List.of());
        m.put("created_at", "2026-08-15T10:00:00Z");
        return m;
    }
}
