package dev.geolens.technicalgeo.web;

import dev.geolens.technicalgeo.BotAnalysisResult;
import dev.geolens.technicalgeo.SchemaAnalysisResult;
import dev.geolens.technicalgeo.TechnicalGeoEngine;
import dev.geolens.technicalgeo.TechnicalGeoScore;
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

/** Go technicalgeo.handler davranış parity testleri — Technical GEO REST. */
@WebMvcTest(TechnicalgeoController.class)
class TechnicalgeoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

    @MockBean
    private TechnicalGeoEngine engine;

    private static final String TENANT = "T01";
    private static final String WS = "ws-1";

    // ---------- AnalyzeBots ----------

    @Test
    void analyzeBotsInvalidJsonReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/" + WS + "/technical-geo/bots")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void analyzeBotsMissingBrandReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/" + WS + "/technical-geo/bots")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("brand_id zorunludur"));
    }

    @Test
    void analyzeBotsSuccess() throws Exception {
        when(engine.analyzeBotAccess(anyString(), any(), anyString(), anyString()))
                .thenReturn(new BotAnalysisResult(null, "b-1", null, "https://example.com",
                        false, null, 0, "2026-08-15T10:00:00Z"));

        mockMvc.perform(post("/v1/workspaces/" + WS + "/technical-geo/bots")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\": \"b-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand_id").value("b-1"))
                .andExpect(jsonPath("$.url").value("https://example.com"));
    }

    // ---------- ListBotAnalyses ----------

    @Test
    void listBotAnalysesSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(botRow("ba-1", "GPTBot", false))));

        mockMvc.perform(get("/v1/workspaces/" + WS + "/technical-geo/bots")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("ba-1"))
                .andExpect(jsonPath("$[0].bot_name").value("GPTBot"))
                .andExpect(jsonPath("$[0].is_blocked").value(false))
                .andExpect(jsonPath("$[0].ges_score").value(100.0));
    }

    @Test
    void listBotAnalysesQueryErrorGraceful() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/workspaces/" + WS + "/technical-geo/bots")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ---------- AnalyzeSchema ----------

    @Test
    void analyzeSchemaSuccess() throws Exception {
        when(engine.analyzeSchema(anyString(), anyString(), anyString()))
                .thenReturn(new SchemaAnalysisResult(null, "b-1", null, false, 0, null,
                        "2026-08-15T10:00:00Z"));

        mockMvc.perform(post("/v1/workspaces/" + WS + "/technical-geo/schema")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\": \"b-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand_id").value("b-1"));
    }

    // ---------- ListSchemaAnalyses ----------

    @Test
    void listSchemaAnalysesSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(schemaRow("sa-1", "Product", true))));

        mockMvc.perform(get("/v1/workspaces/" + WS + "/technical-geo/schema")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("sa-1"))
                .andExpect(jsonPath("$[0].schema_type").value("Product"))
                .andExpect(jsonPath("$[0].is_present").value(true))
                .andExpect(jsonPath("$[0].schema_score").value(100.0));
    }

    // ---------- GetTechnicalGEOScore ----------

    @Test
    void getScoreMissingBrandReturns400() throws Exception {
        mockMvc.perform(get("/v1/workspaces/" + WS + "/technical-geo/score")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("brand_id gerekli"));
    }

    @Test
    void getScoreSuccess() throws Exception {
        when(engine.getScore(anyString(), anyString(), anyString()))
                .thenReturn(new TechnicalGeoScore("b-1", 68.0, 80.0, 90.0, 0, "C"));

        mockMvc.perform(get("/v1/workspaces/" + WS + "/technical-geo/score")
                        .header("X-Tenant-ID", TENANT)
                        .param("brand_id", "b-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand_id").value("b-1"))
                .andExpect(jsonPath("$.overall").value(68.0))
                .andExpect(jsonPath("$.bot_score").value(80.0))
                .andExpect(jsonPath("$.schema_score").value(90.0))
                .andExpect(jsonPath("$.grade").value("C"));
    }

    // ---------- yardımcılar ----------

    private static Map<String, Object> botRow(String id, String botName, boolean blocked) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("brand_id", "b-1");
        m.put("bot_name", botName);
        m.put("url", "https://example.com");
        m.put("is_blocked", blocked);
        m.put("robots_txt_rule", "Allow");
        m.put("ges_score", blocked ? 0.0 : 100.0);
        m.put("analyzed_at", "2026-08-15T10:00:00Z");
        return m;
    }

    private static Map<String, Object> schemaRow(String id, String schemaType, boolean present) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("brand_id", "b-1");
        m.put("schema_type", schemaType);
        m.put("is_present", present);
        m.put("schema_score", present ? 100.0 : 0.0);
        m.put("recommendation", present ? "" : "Ürün sayfalarına Product schema eklenmeli");
        m.put("analyzed_at", "2026-08-15T10:00:00Z");
        return m;
    }
}
