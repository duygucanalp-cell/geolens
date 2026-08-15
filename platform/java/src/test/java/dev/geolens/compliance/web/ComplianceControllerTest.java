package dev.geolens.compliance.web;

import dev.geolens.compliance.Control;
import dev.geolens.compliance.service.ComplianceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go compliance.handler davranış parity testleri — Uyumluluk REST. */
@WebMvcTest(ComplianceController.class)
class ComplianceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ComplianceService complianceService;

    private static final String TENANT = "T01";

    // ---------- SOC2Readiness ----------

    @Test
    void soc2ReadinessAllFailed() throws Exception {
        when(complianceService.soc2Readiness(TENANT)).thenReturn(soc2Result(controls(false, false, false, false, false), 1, 5, 16.666666666666664));

        mockMvc.perform(get("/v1/compliance/soc2")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.framework").value("SOC 2 Tip 1"))
                .andExpect(jsonPath("$.controls.length()").value(6))
                .andExpect(jsonPath("$.summary.total").value(6))
                .andExpect(jsonPath("$.summary.passed").value(1))
                .andExpect(jsonPath("$.summary.failed").value(5))
                .andExpect(jsonPath("$.readiness_pct").value(16.666666666666664))
                .andExpect(jsonPath("$.recommendations.length()").value(5));
    }

    @Test
    void soc2ReadinessAllPassed() throws Exception {
        when(complianceService.soc2Readiness(TENANT)).thenReturn(soc2Result(controls(true, true, true, true, true), 6, 0, 100.0));

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
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("soc_2_tip_1", controls(true, true, true, true, true));
        report.put("gdpr_kvkk", gdpr());
        report.put("iso_27001", iso());
        when(complianceService.complianceReport(TENANT)).thenReturn(report);

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
        when(complianceService.listEvidence()).thenReturn(evidenceResult());

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
        when(complianceService.buildEvidencePack(TENANT)).thenReturn(pack(controls(true, true, true, true, true)));

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

    // ---------- yardımcılar ----------

    private static List<Control> controls(boolean c1, boolean c2, boolean c3, boolean c4, boolean c5) {
        String passedEvidence = "Bu tenant için kullanıcı-tenant kayıtları mevcut";
        return List.of(
                new Control("CC1", "Control Environment", "Kiracı ve kullanıcı yönetimi", "d", ctlStatus(c1), passedEvidence),
                new Control("CC2", "Communication", "Bildirim ve uyarı", "d", ctlStatus(c2), "x"),
                new Control("CC3", "Risk Assessment", "Ölçüm ve risk", "d", ctlStatus(c3), "x"),
                new Control("CC4", "Monitoring", "Denetim günlüğü", "d", ctlStatus(c4), "x"),
                new Control("CC5", "Control Activities", "RBAC", "d", ctlStatus(c5), "x"),
                new Control("CC6", "Logical and Physical Security", "Veri şifreleme", "d", "passed", "x"));
    }

    private static String ctlStatus(boolean b) {
        return b ? "passed" : "failed";
    }

    private static Map<String, Object> soc2Result(List<Control> controls, int passed, int failed, double readiness) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("framework", "SOC 2 Tip 1");
        m.put("readiness_pct", readiness);
        m.put("controls", controls);
        m.put("summary", Map.of("total", 6, "passed", passed, "failed", failed));
        m.put("recommendations", passed == 6
                ? List.of("Tüm kontroller başarılı — SOC 2 Tip 1 için hazırsınız")
                : List.of("r1", "r2", "r3", "r4", "r5"));
        return m;
    }

    private static Map<String, Object> gdpr() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("framework", "GDPR / KVKK");
        m.put("status", "passed");
        m.put("controls_checked", List.of("a", "b", "c", "d"));
        return m;
    }

    private static Map<String, Object> iso() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("framework", "ISO 27001");
        m.put("status", "passed");
        m.put("controls_checked", List.of("a", "b", "c", "d", "e"));
        return m;
    }

    private static Map<String, Object> evidenceResult() {
        List<Map<String, Object>> evidence = new java.util.ArrayList<>();
        String[] controls = {"CC1", "CC2", "CC3", "CC4", "CC5", "CC6", "GDPR", "ISO-A.12.4", "CC4", "CC6"};
        for (int i = 0; i < 10; i++) {
            evidence.add(Map.of("id", "E00" + (i + 1), "control", controls[i], "title", "t", "source", "s"));
        }
        return Map.of("evidence", evidence, "total", 10);
    }

    private static Map<String, Object> pack(List<Control> controls) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("framework", "SOC 2 Tip 1");
        p.put("organization", "GeoLens Platform");
        p.put("date", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        p.put("tenant_id", TENANT);
        p.put("controls", controls);
        p.put("evidence", List.of(
                Map.of("id", "E001", "title", "Tenant kayıtları", "location", "identity.user_tenants"),
                Map.of("id", "E005", "title", "Kullanıcı rolleri", "location", "identity.user_tenants"),
                Map.of("id", "E004", "title", "Audit günlüğü", "location", "identity.audit_logs"),
                Map.of("id", "E006", "title", "Şifreleme", "location", "STORAGE_MASTER_KEY")));
        return p;
    }
}
