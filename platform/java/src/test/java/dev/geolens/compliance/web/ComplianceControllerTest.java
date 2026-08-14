package dev.geolens.compliance.web;

import dev.geolens.testutil.JooqTestData;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go compliance.handler davranış parity testleri — Uyumluluk REST. */
@WebMvcTest(ComplianceController.class)
class ComplianceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;

    private static final String TENANT = "T01";

    // ---------- SOC2Readiness ----------

    @Test
    void soc2ReadinessAllFailed() throws Exception {
        // Tüm COUNT sorguları 0 → CC1-CC5 failed, CC6 passed (sabit)
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(Map.of("count", 0)));

        mockMvc.perform(get("/v1/compliance/soc2")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.framework").value("SOC 2 Tip 1"))
                .andExpect(jsonPath("$.controls.length()").value(6))
                .andExpect(jsonPath("$.summary.total").value(6))
                .andExpect(jsonPath("$.summary.passed").value(1))
                .andExpect(jsonPath("$.summary.failed").value(5))
                // 1/6 * 100 ≈ 16.67
                .andExpect(jsonPath("$.readiness_pct").value(16.666666666666664))
                .andExpect(jsonPath("$.recommendations.length()").value(5));
    }

    @Test
    void soc2ReadinessAllPassed() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(Map.of("count", 3)));

        mockMvc.perform(get("/v1/compliance/soc2")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.passed").value(6))
                .andExpect(jsonPath("$.summary.failed").value(0))
                .andExpect(jsonPath("$.readiness_pct").value(100.0))
                .andExpect(jsonPath("$.recommendations[0]").value("Tüm kontroller başarılı — SOC 2 Tip 1 için hazırsınız"))
                .andExpect(jsonPath("$.controls[0].evidence").value("Bu tenant için kullanıcı-tenant kayıtları mevcut"));
    }

    // ---------- ComplianceReport ----------

    @Test
    void complianceReport() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(Map.of("count", 0)));

        mockMvc.perform(get("/v1/compliance/report")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soc_2_tip_1.length()").value(6))
                .andExpect(jsonPath("$.gdpr_kvkk.framework").value("GDPR / KVKK"))
                .andExpect(jsonPath("$.gdpr_kvkk.controls_checked.length()").value(4))
                .andExpect(jsonPath("$.iso_27001.framework").value("ISO 27001"))
                .andExpect(jsonPath("$.iso_27001.controls_checked.length()").value(5));
    }

    // ---------- ListEvidence ----------

    @Test
    void listEvidence() throws Exception {
        mockMvc.perform(get("/v1/compliance/evidence")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10))
                .andExpect(jsonPath("$.evidence.length()").value(10))
                .andExpect(jsonPath("$.evidence[0].id").value("E001"))
                .andExpect(jsonPath("$.evidence[0].control").value("CC1"));
    }

    // ---------- DownloadEvidence ----------

    @Test
    void downloadEvidence() throws Exception {
        when(dsl.fetchOne(anyString(), any(Object[].class)))
                .thenReturn(JooqTestData.record(Map.of("count", 0)));

        mockMvc.perform(get("/v1/compliance/evidence/download")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.framework").value("SOC 2 Tip 1"))
                .andExpect(jsonPath("$.organization").value("GeoLens Platform"))
                .andExpect(jsonPath("$.tenant_id").value(TENANT))
                .andExpect(jsonPath("$.controls.length()").value(6))
                .andExpect(jsonPath("$.evidence.length()").value(4))
                .andExpect(jsonPath("$.date").isNotEmpty());
    }
}
