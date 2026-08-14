package dev.geolens.sentiment.web;

import dev.geolens.sentiment.domain.HallucinationResult;
import dev.geolens.sentiment.domain.SentimentResult;
import dev.geolens.sentiment.engine.SentimentEngine;
import dev.geolens.sentiment.persistence.SentimentDao;
import dev.geolens.testutil.JooqTestData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go sentiment handler davranışı ile route/response parity testleri. */
@WebMvcTest(SentimentController.class)
class SentimentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SentimentEngine engine;

    @MockBean
    private SentimentDao dao;

    @MockBean
    private DSLContext dsl;

    @MockBean
    private TransactionTemplate tx;

    private static final String TENANT = "T01";
    private static final String WS = "WS01";

    @Test
    void analyzeReturnsResults() throws Exception {
        when(engine.analyzeSentiment(TENANT, WS, "B01", null))
                .thenReturn(List.of(new SentimentResult(null, "B01", "chatgpt", 1.0, 1.0, 0.0, 0.0,
                        1, null, Instant.parse("2026-08-13T10:00:00Z"))));

        mockMvc.perform(post("/v1/workspaces/{ws}/sentiment/analyze", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\":\"B01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].brand_id").value("B01"))
                .andExpect(jsonPath("$[0].engine_name").value("chatgpt"))
                .andExpect(jsonPath("$[0].overall_sentiment").value(1.0))
                .andExpect(jsonPath("$[0].mention_count").value(1));
    }

    @Test
    void analyzeMissingBrandIdReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/{ws}/sentiment/analyze", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("brand_id zorunludur"));
    }

    @Test
    void analyzeInvalidJsonReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/{ws}/sentiment/analyze", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void analyzeEngineErrorReturns500() throws Exception {
        when(engine.analyzeSentiment(eq(TENANT), eq(WS), eq("B01"), any()))
                .thenThrow(new RuntimeException("db down"));

        mockMvc.perform(post("/v1/workspaces/{ws}/sentiment/analyze", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\":\"B01\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("sentiment analizi başarısız"));
    }

    @Test
    void listReturnsHistory() throws Exception {
        when(tx.execute(any(TransactionCallback.class)))
                .thenReturn(List.of(Map.of(
                        "id", "S1", "brand_id", "B01", "engine_name", "chatgpt",
                        "overall_sentiment", 0.8, "positive_score", 0.7, "neutral_score", 0.2,
                        "negative_score", 0.1, "mention_count", 3, "analyzed_at", "2026-08-13T10:00:00Z")));

        mockMvc.perform(get("/v1/workspaces/{ws}/sentiment", WS)
                        .header("X-Tenant-ID", TENANT)
                        .queryParam("brand_id", "B01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].brand_id").value("B01"))
                .andExpect(jsonPath("$[0].engine_name").value("chatgpt"));
    }

    @Test
    void listReturnsEmptyOnQueryError() throws Exception {
        when(tx.execute(any(TransactionCallback.class)))
                .thenThrow(new RuntimeException("db down"));

        mockMvc.perform(get("/v1/workspaces/{ws}/sentiment", WS).header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void summaryMissingBrandIdReturns400() throws Exception {
        mockMvc.perform(get("/v1/workspaces/{ws}/sentiment/summary", WS).header("X-Tenant-ID", TENANT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("brand_id gerekli"));
    }

    @Test
    void summaryReturnsClassification() throws Exception {
        when(tx.execute(any(TransactionCallback.class)))
                .thenReturn(Map.of(
                        "overall", 0.8, "positive", 0.7, "neutral", 0.2, "negative", 0.1, "mentions", 3));

        mockMvc.perform(get("/v1/workspaces/{ws}/sentiment/summary", WS)
                        .header("X-Tenant-ID", TENANT)
                        .queryParam("brand_id", "B01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand_id").value("B01"))
                .andExpect(jsonPath("$.overall").value(0.8))
                .andExpect(jsonPath("$.mention_count").value(3))
                .andExpect(jsonPath("$.classification").value("olumlu"));
    }

    @Test
    void detectHallucinationsReturnsResults() throws Exception {
        when(engine.detectHallucinations(TENANT, WS, "B01"))
                .thenReturn(List.of(new HallucinationResult(null, "B01", "chatgpt", "T3", "critical",
                        "AI yanıtı kaynaksız istatistik/rakam içeriyor", 0.5, null, Instant.parse("2026-08-13T10:00:00Z"))));

        mockMvc.perform(post("/v1/workspaces/{ws}/sentiment/hallucination/detect", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\":\"B01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].brand_id").value("B01"))
                .andExpect(jsonPath("$[0].hallucination_type").value("T3"))
                .andExpect(jsonPath("$[0].severity").value("critical"));
    }

    @Test
    void detectHallucinationsMissingBrandIdReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/{ws}/sentiment/hallucination/detect", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("brand_id zorunludur"));
    }

    @Test
    void listHallucinationsReturnsFlags() throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        for (String k : new String[]{"id", "brand_id", "engine_name", "hallucination_type", "severity",
                "description", "confidence", "verified", "created_at"}) {
            row.put(k, null);
        }
        row.put("id", "H1");
        row.put("brand_id", "B01");
        row.put("engine_name", "chatgpt");
        row.put("hallucination_type", "T2");
        row.put("severity", "medium");
        row.put("description", "kaynak");
        row.put("confidence", 0.4);
        row.put("created_at", "2026-08-13T10:00:00Z");
        when(tx.execute(any(TransactionCallback.class))).thenReturn(List.of(row));

        mockMvc.perform(get("/v1/workspaces/{ws}/sentiment/hallucination", WS)
                        .header("X-Tenant-ID", TENANT)
                        .queryParam("brand_id", "B01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hallucination_type").value("T2"))
                .andExpect(jsonPath("$[0].severity").value("medium"));
    }

    @Test
    void verifyHallucinationReturnsVerified() throws Exception {
        when(dsl.execute(any(String.class), any(), eq("H1"), eq(TENANT))).thenReturn(1);
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionStatus> cb = inv.getArgument(0);
            cb.accept(null);
            return null;
        }).when(tx).executeWithoutResult(anyConsumer());

        mockMvc.perform(post("/v1/workspaces/{ws}/sentiment/hallucination/H1/verify", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"verified\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("verified"));
    }

    @Test
    void verifyHallucinationNotFoundReturns404() throws Exception {
        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionStatus> cb = inv.getArgument(0);
            cb.accept(null);
            return null;
        }).when(tx).executeWithoutResult(anyConsumer());
        when(dsl.execute(any(String.class), any(), eq("YOK"), eq(TENANT))).thenReturn(0);

        mockMvc.perform(post("/v1/workspaces/{ws}/sentiment/hallucination/YOK/verify", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"verified\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("hallüsinasyon kaydı bulunamadı"));
    }

    @SuppressWarnings("unchecked")
    private static Consumer<TransactionStatus> anyConsumer() {
        return org.mockito.ArgumentMatchers.any(Consumer.class);
    }
}