package dev.geolens.usage.web;

import dev.geolens.testutil.JooqTestData;
import dev.geolens.usage.service.UsageService;
import org.jooq.Condition;
import org.springframework.context.annotation.Import;
import org.jooq.DSLContext;
import org.jooq.OrderField;
import org.jooq.SelectFieldOrAsterisk;
import org.jooq.Table;
import org.jooq.TableLike;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collection;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go usage/handler_test.go parity testleri — kullanım analitiği (ADR-014 v4.0 typed DSL). */
@WebMvcTest(UsageController.class)
@Import(UsageService.class)
class UsageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

    private static final String TENANT = "T01";

    // ---------- RecordUsage ----------

    @Test
    void recordUsageInvalidJSONReturns400() throws Exception {
        mockMvc.perform(post("/v1/usage/metrics")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void recordUsageMissingEndpointReturns400() throws Exception {
        mockMvc.perform(post("/v1/usage/metrics")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"method\":\"POST\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("endpoint gerekli"));
    }

    @Test
    void recordUsageSuccess() throws Exception {
        when(dsl.insertInto(any(Table.class)).columns(any(Collection.class)).values(any(Object[].class)).execute())
                .thenReturn(1);

        mockMvc.perform(post("/v1/usage/metrics")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"endpoint\":\"/v1/measurements\",\"method\":\"POST\",\"status_code\":201,\"latency_ms\":120}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endpoint").value("/v1/measurements"))
                .andExpect(jsonPath("$.method").value("POST"))
                .andExpect(jsonPath("$.latency_ms").value(120))
                .andExpect(jsonPath("$.entry_id").isNotEmpty());
    }

    @Test
    void recordUsageDBErrorReturns500() throws Exception {
        when(dsl.insertInto(any(Table.class)).columns(any(Collection.class)).values(any(Object[].class)).execute())
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(post("/v1/usage/metrics")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"endpoint\":\"/v1/measurements\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("kullanım kaydedilemedi"));
    }

    // ---------- ListUsage ----------

    @Test
    void listUsageSuccess() throws Exception {
        when(dsl.select(any(Collection.class)).from(any(TableLike.class)).where(any(Condition.class))
                .orderBy(any(OrderField.class)).limit(anyInt()).fetch())
                .thenReturn(JooqTestData.records(usageRow("M01", "/v1/measurements", "POST", 201, 120)));

        mockMvc.perform(get("/v1/usage/metrics")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("M01"))
                .andExpect(jsonPath("$.data[0].endpoint").value("/v1/measurements"))
                .andExpect(jsonPath("$.has_more").value(false));
    }

    @Test
    void listUsageQueryErrorReturnsEmpty() throws Exception {
        when(dsl.select(any(Collection.class)).from(any(TableLike.class)).where(any(Condition.class))
                .orderBy(any(OrderField.class)).limit(anyInt()).fetch())
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/usage/metrics")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.has_more").value(false));
    }

    @Test
    void listUsageWithLimit() throws Exception {
        when(dsl.select(any(Collection.class)).from(any(TableLike.class)).where(any(Condition.class))
                .orderBy(any(OrderField.class)).limit(anyInt()).fetch())
                .thenReturn(JooqTestData.records(usageRow("M01", "/v1/a", "GET", 200, 10)));

        mockMvc.perform(get("/v1/usage/metrics")
                        .header("X-Tenant-ID", TENANT)
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("M01"));
    }

    // ---------- GetUsageSummary ----------

    @Test
    void getUsageSummarySuccess() throws Exception {
        // agg: select list "total" içerir; endpoints: "hits" içerir — derin stub zinciri
        // aynı select/from/where ön ekini paylaştığı için select listesiyle ayırt edilir.
        when(dsl.select(selectContaining("total"))
                .from(any(TableLike.class)).where(any(Condition.class)).fetchOne())
                .thenReturn(JooqTestData.record(Map.of("total", 100L, "error_rate", 5.0, "avg_latency", 42.5)));
        when(dsl.select(selectContaining("hits"))
                .from(any(TableLike.class)).where(any(Condition.class))
                .groupBy(any(org.jooq.GroupField.class)).orderBy(any(OrderField.class)).limit(anyInt()).fetch())
                .thenReturn(JooqTestData.records(Map.of("endpoint", "/v1/a", "hits", 60L, "avg_latency", 30.0)));

        mockMvc.perform(get("/v1/usage/summary")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("7d"))
                .andExpect(jsonPath("$.total_requests").value(100))
                .andExpect(jsonPath("$.error_rate_pct").value(5.0))
                .andExpect(jsonPath("$.avg_latency_ms").value(42.5))
                .andExpect(jsonPath("$.top_endpoints[0].endpoint").value("/v1/a"));
    }

    @Test
    void getUsageSummaryPeriod30d() throws Exception {
        when(dsl.select(selectContaining("total"))
                .from(any(TableLike.class)).where(any(Condition.class)).fetchOne())
                .thenReturn(JooqTestData.record(Map.of("total", 0L, "error_rate", 0.0, "avg_latency", 0.0)));
        when(dsl.select(selectContaining("hits"))
                .from(any(TableLike.class)).where(any(Condition.class))
                .groupBy(any(org.jooq.GroupField.class)).orderBy(any(OrderField.class)).limit(anyInt()).fetch())
                .thenReturn(JooqTestData.records());

        mockMvc.perform(get("/v1/usage/summary")
                        .header("X-Tenant-ID", TENANT)
                        .param("period", "30d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("30d"));
    }

    // ---------- helpers ----------

    /** Select listesini içeriğe göre eşleştirir — iki summary sorgusu aynı ön eki paylaşır. */
    @SuppressWarnings("unchecked")
    private static Collection<? extends SelectFieldOrAsterisk> selectContaining(String fragment) {
        return argThat((Collection<? extends SelectFieldOrAsterisk> c) -> c != null && c.toString().contains(fragment));
    }

    private static Map<String, Object> usageRow(String id, String ep, String method, int code, int latency) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", id);
        m.put("endpoint", ep);
        m.put("method", method);
        m.put("status_code", code);
        m.put("latency_ms", latency);
        m.put("recorded_at", "2026-08-14T00:00:00Z");
        return m;
    }
}
