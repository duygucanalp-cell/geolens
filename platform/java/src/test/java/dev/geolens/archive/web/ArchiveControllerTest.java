package dev.geolens.archive.web;

import dev.geolens.archive.ArchiveEngine;
import dev.geolens.archive.Entry;
import dev.geolens.archive.service.ArchiveService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go archive.handler davranış parity testleri — Response Archive REST (FR-D13). */
@WebMvcTest(ArchiveController.class)
@Import(ArchiveService.class)
class ArchiveControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

    @MockBean
    private ArchiveEngine engine;

    private static final String TENANT = "T01";
    private static final String WS = "ws-1";

    // ---------- ListEntries ----------

    @Test
    void listEntriesSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(entryRow("a-1", "b-1"))));

        mockMvc.perform(get("/v1/workspaces/" + WS + "/archive")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("a-1"))
                .andExpect(jsonPath("$[0].brand_id").value("b-1"))
                .andExpect(jsonPath("$[0].version").value(1));
    }

    @Test
    void listEntriesQueryErrorGraceful() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/workspaces/" + WS + "/archive")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void listEntriesFilters() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(entryRow("a-1", "b-1"))));

        mockMvc.perform(get("/v1/workspaces/" + WS + "/archive")
                        .header("X-Tenant-ID", TENANT)
                        .param("brand_id", "b-1")
                        .param("engine", "chatgpt")
                        .param("version", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].brand_id").value("b-1"));
    }

    // ---------- GetEntry ----------

    @Test
    void getEntryNotFound() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class))).thenReturn(null);

        mockMvc.perform(get("/v1/workspaces/" + WS + "/archive/a-1")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("arşiv girişi bulunamadı"));
    }

    @Test
    void getEntrySuccess() throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "a-1");
        row.put("brand_id", "b-1");
        row.put("engine_name", "chatgpt");
        row.put("prompt_text", "selam");
        row.put("response_full", "tam yanıt");
        row.put("version", 2);
        row.put("content_hash", "abc123");
        row.put("created_at", "2026-08-15T10:00:00Z");
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(row));

        mockMvc.perform(get("/v1/workspaces/" + WS + "/archive/a-1")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("a-1"))
                .andExpect(jsonPath("$.response_full").value("tam yanıt"))
                .andExpect(jsonPath("$.version").value(2));
    }

    // ---------- ArchiveResponse ----------

    @Test
    void archiveInvalidJsonReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/" + WS + "/archive")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void archiveMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/" + WS + "/archive")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\": \"b-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("brand_id ve response zorunludur"));
    }

    @Test
    void archiveEngineErrorReturns500() throws Exception {
        when(engine.archive(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(post("/v1/workspaces/" + WS + "/archive")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\": \"b-1\", \"response\": \"yanıt\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("arşivleme başarısız"));
    }

    @Test
    void archiveSuccess() throws Exception {
        when(engine.archive(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new Entry("a-1", "b-1", "chatgpt", "selam", "prev", "full",
                        null, 1, "abc123", TENANT));

        mockMvc.perform(post("/v1/workspaces/" + WS + "/archive")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\": \"b-1\", \"engine_name\": \"chatgpt\", \"prompt_text\": \"selam\", \"response\": \"yanıt içeriği\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("a-1"))
                .andExpect(jsonPath("$.brand_id").value("b-1"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.content_hash").value("abc123"));
    }

    // ---------- GetVersionHistory ----------

    @Test
    void versionHistoryMissingBrandReturns400() throws Exception {
        mockMvc.perform(get("/v1/workspaces/" + WS + "/archive/versions")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("brand_id gerekli"));
    }

    @Test
    void versionHistorySuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(
                        versionRow(2, "a-2"), versionRow(1, "a-1"))));

        mockMvc.perform(get("/v1/workspaces/" + WS + "/archive/versions")
                        .header("X-Tenant-ID", TENANT)
                        .param("brand_id", "b-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[0].entry_id").value("a-2"))
                .andExpect(jsonPath("$[1].version").value(1));
    }

    // ---------- yardımcılar ----------

    private static Map<String, Object> entryRow(String id, String brandId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("brand_id", brandId);
        m.put("engine_name", "chatgpt");
        m.put("prompt_text", "selam");
        m.put("response_preview", "prev");
        m.put("s3_ref", null);
        m.put("version", 1);
        m.put("content_hash", "abc123");
        m.put("created_at", "2026-08-15T10:00:00Z");
        return m;
    }

    private static Map<String, Object> versionRow(int version, String entryId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", version);
        m.put("id", entryId);
        m.put("content_hash", "abc" + version);
        m.put("created_at", "2026-08-15T10:00:00Z");
        return m;
    }
}
