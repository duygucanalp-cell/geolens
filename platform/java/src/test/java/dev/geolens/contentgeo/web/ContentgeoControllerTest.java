package dev.geolens.contentgeo.web;

import dev.geolens.contentgeo.ContentGeoEngine;
import dev.geolens.contentgeo.ContentGapResult;
import dev.geolens.contentgeo.ContentHubScore;
import dev.geolens.contentgeo.service.ContentgeoService;
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

/** Go contentgeo.handler davranış parity testleri — Content GEO REST (FR-E5/E6). */
@WebMvcTest(ContentgeoController.class)
@Import(ContentgeoService.class)
class ContentgeoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

    @MockBean
    private ContentGeoEngine engine;

    private static final String TENANT = "T01";
    private static final String WS = "ws-1";
    private static final String BASE = "/v1/workspaces/" + WS + "/content-geo";

    // ---------- AnalyzeContentGap ----------

    @Test
    void analyzeInvalidJsonReturns400() throws Exception {
        mockMvc.perform(post(BASE + "/gap")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void analyzeMissingBrandReturns400() throws Exception {
        mockMvc.perform(post(BASE + "/gap")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("brand_id zorunludur"));
    }

    @Test
    void analyzeEngineErrorReturns500() throws Exception {
        when(engine.analyzeContentGap(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("db down"));

        mockMvc.perform(post(BASE + "/gap")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\": \"b-1\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("content gap analizi başarısız"));
    }

    @Test
    void analyzeSuccess() throws Exception {
        when(engine.analyzeContentGap(anyString(), anyString(), anyString()))
                .thenReturn(List.of(gapResult()));

        mockMvc.perform(post(BASE + "/gap")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\": \"b-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].gap_type").value("blog"))
                .andExpect(jsonPath("$[0].gap_score").value(0.9))
                .andExpect(jsonPath("$[0].priority").value("high"));
    }

    // ---------- ListContentGaps ----------

    @Test
    void listContentGapsSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(gapRow("g-1", "blog", 0.9))));

        mockMvc.perform(get(BASE + "/gap")
                        .header("X-Tenant-ID", TENANT)
                        .param("brand_id", "b-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("g-1"))
                .andExpect(jsonPath("$[0].brand_id").value("b-1"))
                .andExpect(jsonPath("$[0].gap_type").value("blog"))
                .andExpect(jsonPath("$[0].gap_score").value(0.9))
                .andExpect(jsonPath("$[0].priority").value("high"));
    }

    @Test
    void listContentGapsQueryErrorGraceful() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get(BASE + "/gap")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ---------- GetContentHubScore ----------

    @Test
    void hubScoreMissingBrandReturns400() throws Exception {
        mockMvc.perform(get(BASE + "/hub-score")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("brand_id gerekli"));
    }

    @Test
    void hubScoreEngineErrorReturns500() throws Exception {
        when(engine.getContentHubScore(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("db down"));

        mockMvc.perform(get(BASE + "/hub-score")
                        .header("X-Tenant-ID", TENANT)
                        .param("brand_id", "b-1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("skor alınamadı"));
    }

    @Test
    void hubScoreSuccess() throws Exception {
        when(engine.getContentHubScore(anyString(), anyString(), anyString()))
                .thenReturn(new ContentHubScore("b-1", 49.34, 85.71, 50.0, 20.0, 50.66, "D"));

        mockMvc.perform(get(BASE + "/hub-score")
                        .header("X-Tenant-ID", TENANT)
                        .param("brand_id", "b-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand_id").value("b-1"))
                .andExpect(jsonPath("$.overall").value(49.34))
                .andExpect(jsonPath("$.topic_coverage").value(85.71))
                .andExpect(jsonPath("$.source_diversity").value(50.0))
                .andExpect(jsonPath("$.authority_score").value(20.0))
                .andExpect(jsonPath("$.opportunity_gap").value(50.66))
                .andExpect(jsonPath("$.grade").value("D"));
    }

    // ---------- ListTopicClusters ----------

    @Test
    void listTopicClustersSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(topicRow("t-1", "Yapay Zeka", 85.0))));

        mockMvc.perform(get(BASE + "/topics")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("t-1"))
                .andExpect(jsonPath("$[0].topic_name").value("Yapay Zeka"))
                .andExpect(jsonPath("$[0].opportunity_score").value(85.0))
                .andExpect(jsonPath("$[0].relevance").value("high"));
    }

    @Test
    void listTopicClustersQueryErrorGraceful() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get(BASE + "/topics")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ---------- yardımcılar ----------

    private static ContentGapResult gapResult() {
        return new ContentGapResult(null, "b-1", "blog", 0.9,
                "Blog/Makale türü içerik eksik veya yetersiz alıntılanıyor",
                "Blog/Makale içerik sayısı ve kalitesi artırılmalı", "high",
                OffsetDateTime.of(2026, 8, 15, 10, 0, 0, 0, ZoneOffset.UTC));
    }

    private static Map<String, Object> gapRow(String id, String type, double score) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("brand_id", "b-1");
        m.put("gap_type", type);
        m.put("gap_score", score);
        m.put("description", "Blog/Makale türü içerik eksik veya yetersiz alıntılanıyor");
        m.put("recommendation", "Blog/Makale içerik sayısı ve kalitesi artırılmalı");
        m.put("priority", "high");
        m.put("analyzed_at", "2026-08-15T10:00:00Z");
        return m;
    }

    private static Map<String, Object> topicRow(String id, String topic, double score) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("brand_id", "b-1");
        m.put("topic_name", topic);
        m.put("opportunity_score", score);
        m.put("relevance", "high");
        m.put("recommendation", "Topic cluster içerik planına alınmalı");
        m.put("created_at", "2026-08-15T10:00:00Z");
        return m;
    }
}
