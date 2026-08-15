package dev.geolens.retention.web;

import dev.geolens.retention.service.RetentionService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go retention.handler davranış parity testleri — Veri Saklama REST (K3). */
@WebMvcTest(RetentionController.class)
@Import(RetentionService.class)
class RetentionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

    private static final String TENANT = "T01";
    private static final String WS = "WS01";

    // ---------- ListPolicies ----------

    @Test
    void listPoliciesSuccess() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(policyRow("p-1", "measurement", 365))));

        mockMvc.perform(get("/v1/workspaces/{ws}/retention/policies", WS)
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policies.length()").value(1))
                .andExpect(jsonPath("$.policies[0].entity_type").value("measurement"))
                .andExpect(jsonPath("$.policies[0].retention_days").value(365))
                .andExpect(jsonPath("$.policies[0].archival_strategy").value("delete"));
    }

    @Test
    void listPoliciesQueryErrorGraceful() throws Exception {
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/workspaces/{ws}/retention/policies", WS)
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policies").isArray());
    }

    // ---------- UpsertPolicy ----------

    @Test
    void upsertInvalidJsonReturns400() throws Exception {
        mockMvc.perform(put("/v1/workspaces/{ws}/retention/policies", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void upsertShortRetentionReturns400() throws Exception {
        mockMvc.perform(put("/v1/workspaces/{ws}/retention/policies", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"entity_type\": \"measurement\", \"retention_days\": 29, \"archival_strategy\": \"delete\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("saklama süresi en az 30 gün olmalıdır"));
    }

    @Test
    void upsertInvalidStrategyReturns400() throws Exception {
        mockMvc.perform(put("/v1/workspaces/{ws}/retention/policies", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"entity_type\": \"measurement\", \"retention_days\": 90, \"archival_strategy\": \"backup\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz arşiv stratejisi: delete, anonymize, archive_s3"));
    }

    @Test
    void upsertSuccess() throws Exception {
        Map<String, Object> row = policyRow("p-1", "measurement", 90);
        row.put("archival_strategy", "anonymize");
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(row));

        mockMvc.perform(put("/v1/workspaces/{ws}/retention/policies", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"entity_type\": \"measurement\", \"retention_days\": 90, \"archival_strategy\": \"anonymize\", \"enabled\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("p-1"))
                .andExpect(jsonPath("$.retention_days").value(90))
                .andExpect(jsonPath("$.archival_strategy").value("anonymize"));
    }

    @Test
    void upsertDbErrorReturns500() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(put("/v1/workspaces/{ws}/retention/policies", WS)
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"entity_type\": \"measurement\", \"retention_days\": 90, \"archival_strategy\": \"delete\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("politika kaydedilemedi"));
    }

    // ---------- DeletePolicy ----------

    @Test
    void deletePolicySuccess() throws Exception {
        when(dsl.execute(anyString(), any(Object[].class))).thenReturn(1);

        mockMvc.perform(delete("/v1/workspaces/{ws}/retention/policies/p-1", WS)
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("silindi"));
    }

    @Test
    void deletePolicyNotFound() throws Exception {
        when(dsl.execute(anyString(), any(Object[].class))).thenReturn(0);

        mockMvc.perform(delete("/v1/workspaces/{ws}/retention/policies/p-1", WS)
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("politika bulunamadı"));
    }

    // ---------- GetArchiveSummary ----------

    @Test
    void archiveSummarySuccess() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(Map.of("count", 42)))
                .thenReturn(JooqTestData.record(Map.of("size", "42 kayıt")));

        mockMvc.perform(get("/v1/workspaces/{ws}/retention/archive-summary", WS)
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_archived").value(42))
                .andExpect(jsonPath("$.total_size").value("42 kayıt"))
                .andExpect(jsonPath("$.entities.length()").value(4))
                .andExpect(jsonPath("$.entities[0]").value("measurement — ölçüm sonuçları"));
    }

    @Test
    void archiveSummaryQueryErrorGraceful() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(get("/v1/workspaces/{ws}/retention/archive-summary", WS)
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_archived").value(0))
                .andExpect(jsonPath("$.entities.length()").value(4));
    }

    // ---------- yardımcılar ----------

    private static Map<String, Object> policyRow(String id, String entityType, int retentionDays) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("tenant_id", TENANT);
        m.put("entity_type", entityType);
        m.put("retention_days", retentionDays);
        m.put("archival_strategy", "delete");
        m.put("enabled", true);
        m.put("created_at", "2026-07-01T00:00:00Z");
        m.put("updated_at", "2026-07-25T00:00:00Z");
        return m;
    }
}
