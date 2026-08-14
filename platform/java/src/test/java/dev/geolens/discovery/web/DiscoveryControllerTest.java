package dev.geolens.discovery.web;

import dev.geolens.discovery.ShadowFinding;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go discovery/handler_test.go parity testleri — Shadow AI Discovery REST. */
@WebMvcTest(DiscoveryController.class)
class DiscoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

    private static final String TENANT = "T01";

    // ---------- StartScan ----------

    @Test
    void startScanInvalidJsonReturns400() throws Exception {
        mockMvc.perform(post("/v1/discovery/scan")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void startScanDbErrorReturns500() throws Exception {
        when(dsl.execute(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("db error"));

        mockMvc.perform(post("/v1/discovery/scan")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"scan_type\":\"api\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("tarama başlatılamadı"));
    }

    @Test
    void startScanSuccess() throws Exception {
        mockMvc.perform(post("/v1/discovery/scan")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"scan_type\":\"full\",\"provider\":\"aws\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("running"))
                .andExpect(jsonPath("$.scan_id").isNotEmpty());
    }

    @Test
    void startScanDefaultsScanType() throws Exception {
        mockMvc.perform(post("/v1/discovery/scan")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("running"));
    }

    // ---------- GetScanResults ----------

    @Test
    void getScanResultsNotFoundReturns404() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class))).thenReturn(null);

        mockMvc.perform(get("/v1/discovery/scans/nonexistent")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("tarama bulunamadı"));
    }

    @Test
    void getScanResultsSuccess() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(scanRow("scan-001", "completed", "aws", 2)));
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of(
                        findingRow("lambda", "ai-fn", "arn:aws:lambda:fn:1", "high"),
                        findingRow("sagemaker", "llm-endpoint", "arn:aws:sagemaker:ep:1", "critical"))));

        mockMvc.perform(get("/v1/discovery/scans/scan-001")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scan.id").value("scan-001"))
                .andExpect(jsonPath("$.scan.status").value("completed"))
                .andExpect(jsonPath("$.scan.total_found").value(2))
                .andExpect(jsonPath("$.findings.length()").value(2))
                .andExpect(jsonPath("$.findings[0].resource_type").value("lambda"))
                .andExpect(jsonPath("$.findings[1].risk_level").value("critical"));
    }

    @Test
    void getScanResultsQueryErrorReturnsScanOnly() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(scanRow("scan-001", "completed", "all", 1)));
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("query error"));

        mockMvc.perform(get("/v1/discovery/scans/scan-001")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scan.id").value("scan-001"))
                .andExpect(jsonPath("$.scan.status").value("completed"));
    }

    @Test
    void getScanResultsEmptyFindings() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(scanRow("scan-002", "completed", "gcp", 0)));
        when(dsl.fetch(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.records(List.of()));

        mockMvc.perform(get("/v1/discovery/scans/scan-002")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scan.status").value("completed"))
                .andExpect(jsonPath("$.findings.length()").value(0));
    }

    // ---------- SimulateScan ----------

    @Test
    void simulateScanReturnsThreeFindings() {
        List<ShadowFinding> findings = DiscoveryController.simulateScan();
        assertEquals(3, findings.size());
        assertEquals("high", findings.get(0).riskLevel());
        assertEquals("critical", findings.get(1).riskLevel());
        assertEquals("medium", findings.get(2).riskLevel());
    }

    // ---------- yardımcılar ----------

    private static Map<String, Object> scanRow(String id, String status, String provider, int totalFound) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("status", status);
        m.put("provider", provider);
        m.put("total_found", totalFound);
        m.put("started_at", "2026-07-25T10:00:00Z");
        m.put("completed_at", "2026-07-25T10:05:00Z");
        m.put("created_at", "2026-07-25T10:00:00Z");
        return m;
    }

    private static Map<String, Object> findingRow(String type, String name, String id, String risk) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("resource_type", type);
        m.put("resource_name", name);
        m.put("resource_id", id);
        m.put("provider", "aws");
        m.put("region", "us-east-1");
        m.put("risk_level", risk);
        m.put("details", "{\"runtime\":\"python3.12\"}");
        m.put("discovered_at", "2026-07-25T10:00:00Z");
        return m;
    }
}
