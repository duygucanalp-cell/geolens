package dev.geolens.version.web;

import dev.geolens.testutil.JooqTestData;
import dev.geolens.version.service.VersionService;
import org.jooq.Condition;
import org.springframework.context.annotation.Import;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Table;
import org.jooq.TableLike;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go version/handler_test.go parity testleri — versiyon takibi. */
@WebMvcTest(VersionController.class)
@Import(VersionService.class)
class VersionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

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
        when(dsl.insertInto(any(Table.class)).columns(any(Collection.class)).values(any(Object[].class)).execute())
                .thenThrow(new RuntimeException("db error"));

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
        when(dsl.select(any(Collection.class)).from(any(TableLike.class)).where(any(Condition.class))
                .orderBy(any(OrderField.class)).limit(anyInt()).fetch())
                .thenReturn(JooqTestData.records(versionRow("V01", "engine", "E01", "1.0", "1.1")));

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
        when(dsl.select(any(Collection.class)).from(any(TableLike.class)).where(any(Condition.class))
                .orderBy(any(OrderField.class)).limit(anyInt()).fetch())
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/versions/entries")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.has_more").value(false));
    }

    // ---------- GetVersionDiff ----------

    @Test
    void getVersionDiffNotFoundReturns404() throws Exception {
        // jOOQ fetchOne boş sonuçta null döner — deep-stub'ta açıkça null stub'lanmalı
        when(dsl.select(any(Collection.class)).from(any(TableLike.class)).where(any(Condition.class)).fetchOne())
                .thenReturn(null);

        mockMvc.perform(get("/v1/versions/entries/none")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("versiyon kaydı bulunamadı"));
    }

    @Test
    void getVersionDiffSuccess() throws Exception {
        when(dsl.select(any(Collection.class)).from(any(TableLike.class)).where(any(Condition.class)).fetchOne())
                .thenReturn(JooqTestData.record(versionRow("V01", "engine", "E01", "1.0", "1.1")));

        mockMvc.perform(get("/v1/versions/entries/V01")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry.id").value("V01"))
                .andExpect(jsonPath("$.entry.old_version").value("1.0"))
                .andExpect(jsonPath("$.has_changes").value(true));
    }

    @Test
    void getVersionDiffNoChange() throws Exception {
        when(dsl.select(any(Collection.class)).from(any(TableLike.class)).where(any(Condition.class)).fetchOne())
                .thenReturn(JooqTestData.record(versionRow("V02", "engine", "E01", "1.1", "1.1")));

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
