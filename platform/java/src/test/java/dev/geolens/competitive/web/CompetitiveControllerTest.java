package dev.geolens.competitive.web;

import dev.geolens.competitive.CompetitiveEngine;
import dev.geolens.competitive.GapDetail;
import dev.geolens.competitive.GapSnapshot;
import dev.geolens.competitive.service.CompetitiveService;
import dev.geolens.testutil.JooqTestData;
import org.springframework.context.annotation.Import;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

/** Go competitive.handler davranış parity testleri — Competitive Gap REST (FR-D11). */
@WebMvcTest(CompetitiveController.class)
@Import(CompetitiveService.class)
class CompetitiveControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

    @MockBean
    private CompetitiveEngine engine;

    private static final String TENANT = "T01";
    private static final String WS = "ws-1";
    private static final String BASE = "/v1/workspaces/" + WS + "/competitive-gap";

    // ---------- AnalyzeGap ----------

    @Test
    void analyzeInvalidJsonReturns400() throws Exception {
        mockMvc.perform(post(BASE + "/analyze")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void analyzeMissingBrandReturns400() throws Exception {
        mockMvc.perform(post(BASE + "/analyze")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("brand_id zorunludur"));
    }

    @Test
    void analyzeEngineErrorReturns500() throws Exception {
        when(engine.analyzeAllGaps(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("db down"));

        mockMvc.perform(post(BASE + "/analyze")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\": \"b-1\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("gap analizi başarısız"));
    }

    @Test
    void analyzeSuccess() throws Exception {
        when(engine.analyzeAllGaps(anyString(), anyString(), anyString()))
                .thenReturn(List.of(snapshot()));

        mockMvc.perform(post(BASE + "/analyze")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\": \"b-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].brand_id").value("b-1"))
                .andExpect(jsonPath("$[0].competitor_name").value("Rakip A"))
                .andExpect(jsonPath("$[0].competitive_score").value(61.5));
    }

    // ---------- GetOverview ----------

    @Test
    void overviewMissingBrandReturns400() throws Exception {
        mockMvc.perform(get(BASE + "/overview")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("brand_id gerekli"));
    }

    @Test
    void overviewSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(overviewRow())));

        mockMvc.perform(get(BASE + "/overview")
                        .header("X-Tenant-ID", TENANT)
                        .param("brand_id", "b-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("gs-1"))
                .andExpect(jsonPath("$[0].competitor_name").value("Rakip A"))
                .andExpect(jsonPath("$[0].visibility_gap").value(20.0))
                .andExpect(jsonPath("$[0].competitive_score").value(61.5))
                .andExpect(jsonPath("$[0].period_start").value("2026-07-16"));
    }

    @Test
    void overviewQueryErrorGraceful() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get(BASE + "/overview")
                        .header("X-Tenant-ID", TENANT)
                        .param("brand_id", "b-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ---------- GetVisibilityGap ----------

    @Test
    void visibilityMissingParamsReturns400() throws Exception {
        mockMvc.perform(get(BASE + "/visibility")
                        .header("X-Tenant-ID", TENANT)
                        .param("brand_id", "b-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("brand_id ve competitor_id gerekli"));
    }

    @Test
    void visibilityEngineErrorReturns500() throws Exception {
        when(engine.getGapDetail(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("db down"));

        mockMvc.perform(get(BASE + "/visibility")
                        .header("X-Tenant-ID", TENANT)
                        .param("brand_id", "b-1")
                        .param("competitor_id", "c-1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("gap bilgisi alınamadı"));
    }

    @Test
    void visibilitySuccess() throws Exception {
        when(engine.getGapDetail(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new GapDetail(20.0, 60.0, 80.0, 60.0, "brand_ahead"));

        mockMvc.perform(get(BASE + "/visibility")
                        .header("X-Tenant-ID", TENANT)
                        .param("brand_id", "b-1")
                        .param("competitor_id", "c-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gap_value").value(20.0))
                .andExpect(jsonPath("$.normalized").value(60.0))
                .andExpect(jsonPath("$.direction").value("brand_ahead"));
    }

    // ---------- GetRecommendations ----------

    @Test
    void recommendationsSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(recRow())));

        mockMvc.perform(get(BASE + "/recommendations")
                        .header("X-Tenant-ID", TENANT)
                        .param("brand_id", "b-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("r-1"))
                .andExpect(jsonPath("$[0].gap_type").value("visibility"))
                .andExpect(jsonPath("$[0].priority").value("medium"))
                .andExpect(jsonPath("$[0].kanit_derecesi").value("korelasyonel"));
    }

    @Test
    void recommendationsQueryErrorGraceful() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get(BASE + "/recommendations")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ---------- yardımcılar ----------

    private static GapSnapshot snapshot() {
        return new GapSnapshot(
                "gs-1", "b-1", "Marka A", "c-1", "Rakip A",
                new GapDetail(20.0, 60.0, 80.0, 60.0, "brand_ahead"),
                new GapDetail(20.0, 60.0, 60.0, 40.0, "brand_ahead"),
                new GapDetail(3.0, 57.5, 5.0, 2.0, "brand_ahead"),
                new GapDetail(20.0, 60.0, 80.0, 60.0, "brand_ahead"),
                new GapDetail(60.0, 80.0, 80.0, 20.0, "brand_ahead"),
                61.5, "2026-07-16", "2026-08-15",
                OffsetDateTime.of(2026, 8, 15, 10, 0, 0, 0, ZoneOffset.UTC));
    }

    private static Map<String, Object> overviewRow() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "gs-1");
        m.put("competitor_id", "c-1");
        m.put("competitor_name", "Rakip A");
        m.put("visibility_gap", 20.0);
        m.put("citation_gap", 20.0);
        m.put("content_gap", 3.0);
        m.put("topic_gap", 20.0);
        m.put("prompt_gap", 60.0);
        m.put("competitive_score", 61.5);
        m.put("period_start", java.sql.Date.valueOf("2026-07-16"));
        m.put("period_end", java.sql.Date.valueOf("2026-08-15"));
        m.put("created_at", java.sql.Timestamp.from(
                OffsetDateTime.of(2026, 8, 15, 10, 0, 0, 0, ZoneOffset.UTC).toInstant()));
        return m;
    }

    private static Map<String, Object> recRow() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "r-1");
        m.put("gap_type", "visibility");
        m.put("priority", "medium");
        m.put("description", "Görünürlük farkı kapatmak için zayıf motorlarda strateji revizyonu yapılmalı");
        m.put("impact", "Visibility gap puanında +5-15 iyileşme");
        m.put("kanit_derecesi", "korelasyonel");
        return m;
    }
}
