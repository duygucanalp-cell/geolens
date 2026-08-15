package dev.geolens.replay.web;

import dev.geolens.replay.DiffResult;
import dev.geolens.replay.ReplayEngine;
import dev.geolens.replay.Snapshot;
import dev.geolens.replay.service.ReplayService;
import dev.geolens.testutil.JooqTestData;
import org.springframework.context.annotation.Import;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go replay.handler davranış parity testleri — Conversation Replay REST (FR-D12). */
@WebMvcTest(ReplayController.class)
@Import(ReplayService.class)
class ReplayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

    @MockBean
    private ReplayEngine engine;

    private static final String TENANT = "T01";
    private static final String WS = "ws-1";

    // ---------- CaptureSnapshot ----------

    @Test
    void captureInvalidJsonReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/" + WS + "/replay/capture")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void captureMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/" + WS + "/replay/capture")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\": \"b-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("brand_id ve prompt zorunludur"));
    }

    @Test
    void captureEngineErrorReturns500() throws Exception {
        when(engine.captureSnapshot(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("hiç yanıt bulunamadı"));

        mockMvc.perform(post("/v1/workspaces/" + WS + "/replay/capture")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\": \"b-1\", \"prompt\": \"selam\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("snapshot alınamadı"));
    }

    @Test
    void captureSuccess() throws Exception {
        when(engine.captureSnapshot(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new Snapshot("snap-1", "b-1", "selam", "chatgpt",
                        "preview", "full", "abc123", null, null, "2026-08-15T10:00:00Z"));

        mockMvc.perform(post("/v1/workspaces/" + WS + "/replay/capture")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\": \"b-1\", \"prompt\": \"selam\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("snap-1"))
                .andExpect(jsonPath("$.brand_id").value("b-1"))
                .andExpect(jsonPath("$.engine_name").value("chatgpt"))
                .andExpect(jsonPath("$.content_hash").value("abc123"));
    }

    // ---------- ListSnapshots ----------

    @Test
    void listSnapshotsSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(snapshotRow("snap-1", "b-1"))));

        mockMvc.perform(get("/v1/workspaces/" + WS + "/replay")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("snap-1"))
                .andExpect(jsonPath("$[0].brand_id").value("b-1"))
                .andExpect(jsonPath("$[0].engine_name").value("chatgpt"));
    }

    @Test
    void listSnapshotsQueryErrorGraceful() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/workspaces/" + WS + "/replay")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void listSnapshotsBrandFilter() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(snapshotRow("snap-1", "b-1"))));

        mockMvc.perform(get("/v1/workspaces/" + WS + "/replay")
                        .header("X-Tenant-ID", TENANT)
                        .param("brand_id", "b-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].brand_id").value("b-1"));
    }

    // ---------- GetSnapshot ----------

    @Test
    void getSnapshotNotFound() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class))).thenReturn(null);

        mockMvc.perform(get("/v1/workspaces/" + WS + "/replay/snap-1")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("snapshot bulunamadı"));
    }

    @Test
    void getSnapshotSuccess() throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "snap-1");
        row.put("brand_id", "b-1");
        row.put("prompt_text", "selam");
        row.put("engine_name", "chatgpt");
        row.put("response_full", "tam yanıt");
        row.put("content_hash", "abc123");
        row.put("s3_ref", null);
        row.put("created_at", "2026-08-15T10:00:00Z");
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(row));

        mockMvc.perform(get("/v1/workspaces/" + WS + "/replay/snap-1")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("snap-1"))
                .andExpect(jsonPath("$.response_full").value("tam yanıt"));
    }

    // ---------- DeleteSnapshot ----------

    @Test
    void deleteSnapshotSuccess() throws Exception {
        when(dsl.execute(anyString(), any(Object[].class))).thenReturn(1);

        mockMvc.perform(delete("/v1/workspaces/" + WS + "/replay/snap-1")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("deleted"));
    }

    @Test
    void deleteSnapshotNotFound() throws Exception {
        when(dsl.execute(anyString(), any(Object[].class))).thenReturn(0);

        mockMvc.perform(delete("/v1/workspaces/" + WS + "/replay/snap-1")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("snapshot bulunamadı"));
    }

    // ---------- CompareSnapshots ----------

    @Test
    void compareMissingParamsReturns400() throws Exception {
        mockMvc.perform(get("/v1/workspaces/" + WS + "/replay/compare")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("snapshot_a ve snapshot_b gerekli"));
    }

    @Test
    void compareSuccess() throws Exception {
        when(engine.compare(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new DiffResult("s-a", "s-b", "b-1", "chatgpt", "selam",
                        true, "Yanıt içeriği değişmiş. Detaylı karşılaştırma için snapshot'ların tam metinlerini inceleyin.",
                        "2026-08-15T10:00:00Z"));

        mockMvc.perform(get("/v1/workspaces/" + WS + "/replay/compare")
                        .header("X-Tenant-ID", TENANT)
                        .param("snapshot_a", "s-a")
                        .param("snapshot_b", "s-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot_a").value("s-a"))
                .andExpect(jsonPath("$.has_changed").value(true))
                .andExpect(jsonPath("$.changes").value(org.hamcrest.Matchers.containsString("değişmiş")));
    }

    // ---------- yardımcılar ----------

    private static Map<String, Object> snapshotRow(String id, String brandId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("brand_id", brandId);
        m.put("prompt_text", "selam");
        m.put("engine_name", "chatgpt");
        m.put("response_preview", "preview");
        m.put("content_hash", "abc123");
        m.put("s3_ref", null);
        m.put("created_at", "2026-08-15T10:00:00Z");
        return m;
    }
}
