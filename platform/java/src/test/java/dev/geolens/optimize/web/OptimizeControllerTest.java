package dev.geolens.optimize.web;

import dev.geolens.optimize.service.OptimizeService;
import dev.geolens.testutil.JooqTestData;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Import;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go optimize/handler_test.go parity testleri — Optimization Recommendations REST. */
@WebMvcTest(OptimizeController.class)
@Import(OptimizeService.class)
class OptimizeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

    private static final String TENANT = "T01";

    // ---------- ListRecommendations ----------

    @Test
    void listRecommendationsSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(recRow("r-1", "measurement", "Test Rec"))));

        mockMvc.perform(get("/v1/optimizations/recommendations")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("r-1"))
                .andExpect(jsonPath("$.data[0].category").value("measurement"))
                .andExpect(jsonPath("$.data[0].score_potential").value(15.0))
                .andExpect(jsonPath("$.has_more").value(false));
    }

    @Test
    void listRecommendationsQueryErrorGraceful() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/optimizations/recommendations")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.has_more").value(false));
    }

    @Test
    void listRecommendationsFilters() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(recRow("r-1", "engine", "Multi"))));

        mockMvc.perform(get("/v1/optimizations/recommendations")
                        .header("X-Tenant-ID", TENANT)
                        .param("status", "pending")
                        .param("category", "engine")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void listRecommendationsHasMore() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(
                        recRow("r-1", "engine", "A"), recRow("r-2", "prompt", "B"))));

        mockMvc.perform(get("/v1/optimizations/recommendations")
                        .header("X-Tenant-ID", TENANT)
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.has_more").value(true));
    }

    @Test
    void listRecommendationsInvalidLimitDefaults() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of()));

        mockMvc.perform(get("/v1/optimizations/recommendations")
                        .header("X-Tenant-ID", TENANT)
                        .param("limit", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.has_more").value(false));
    }

    // ---------- GenerateRecommendations ----------

    @Test
    void generateInvalidJsonReturns400() throws Exception {
        mockMvc.perform(post("/v1/optimizations/recommendations/generate")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void generateSuccessAutoSave() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(Map.of("count", 3)));

        mockMvc.perform(post("/v1/optimizations/recommendations/generate")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"auto_save\": true}"))
                .andExpect(status().isCreated())
                // scoreCount=3 < 5 → 4 öneri
                .andExpect(jsonPath("$.count").value(4))
                .andExpect(jsonPath("$.recommendations.length()").value(4))
                .andExpect(jsonPath("$.recommendations[0].id").isNotEmpty())
                .andExpect(jsonPath("$.recommendations[0].status").value("pending"))
                .andExpect(jsonPath("$.recommendations[0].score_potential").isNumber());

        // auto_save → 4 insert beklenir
        verify(dsl, times(4)).execute(anyString(), any(Object[].class));
    }

    @Test
    void generateWithoutAutoSaveDoesNotPersist() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(Map.of("count", 10)));

        mockMvc.perform(post("/v1/optimizations/recommendations/generate")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isCreated())
                // scoreCount=10 >= 5 → 3 öneri
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.recommendations.length()").value(3));

        verify(dsl, times(0)).execute(anyString(), any(Object[].class));
    }

    // ---------- UpdateStatus ----------

    @Test
    void updateStatusInvalidJsonReturns400() throws Exception {
        mockMvc.perform(put("/v1/optimizations/recommendations/r-1/status")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void updateStatusInvalidStatusReturns400() throws Exception {
        mockMvc.perform(put("/v1/optimizations/recommendations/r-1/status")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"status\": \"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz durum: implemented veya dismissed olmalı"));
    }

    @Test
    void updateStatusNotFound() throws Exception {
        when(dsl.execute(anyString(), any(Object[].class))).thenReturn(0);

        mockMvc.perform(put("/v1/optimizations/recommendations/r-1/status")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"status\": \"implemented\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("öneri bulunamadı"));
    }

    @Test
    void updateStatusSuccess() throws Exception {
        when(dsl.execute(anyString(), any(Object[].class))).thenReturn(1);

        mockMvc.perform(put("/v1/optimizations/recommendations/r-1/status")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"status\": \"implemented\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("r-1"))
                .andExpect(jsonPath("$.status").value("implemented"));
    }

    // ---------- yardımcılar ----------

    private static Map<String, Object> recRow(String id, String category, String title) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("category", category);
        m.put("title", title);
        m.put("description", "description");
        m.put("impact", "high");
        m.put("effort", "low");
        m.put("status", "pending");
        m.put("score_potential", 15.0);
        m.put("created_at", "2026-08-15T10:00:00Z");
        return m;
    }
}
