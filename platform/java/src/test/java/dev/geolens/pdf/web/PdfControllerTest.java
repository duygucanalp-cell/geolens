package dev.geolens.pdf.web;

import dev.geolens.pdf.PdfService;
import dev.geolens.pdf.ReportResult;
import dev.geolens.pdf.ReportType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go pdf/handler_test.go parity testleri — PDF rapor üretimi ve async rapor akışı. */
@WebMvcTest(PdfController.class)
class PdfControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PdfService svc;

    @MockBean
    private JdbcTemplate jdbc;

    private static final String TENANT = "T01";

    private static ReportResult result() {
        return new ReportResult("R01", ReportType.WEEKLY_DIGEST,
                "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8), "weekly-digest.pdf", 1, Instant.now(), null);
    }

    // ---------- GenerateWeeklyDigest ----------

    @Test
    void weeklyDigestSuccess() throws Exception {
        when(svc.generateWeeklyDigest("WS01", TENANT)).thenReturn(result());

        mockMvc.perform(post("/v1/workspaces/WS01/reports/digest")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"weekly-digest.pdf\""));
    }

    @Test
    void weeklyDigestErrorReturns500() throws Exception {
        when(svc.generateWeeklyDigest("WS01", TENANT)).thenThrow(new RuntimeException("pdf error"));

        mockMvc.perform(post("/v1/workspaces/WS01/reports/digest")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("rapor oluşturulamadı"));
    }

    // ---------- GenerateScoreCard ----------

    @Test
    void scoreCardSuccess() throws Exception {
        when(svc.generate(any(dev.geolens.pdf.ReportRequest.class))).thenReturn(result());

        mockMvc.perform(post("/v1/workspaces/WS01/reports/score-card")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\":\"B01\",\"brand_name\":\"Acme\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    void scoreCardMissingBrandReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/WS01/reports/score-card")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("brand_id zorunludur"));
    }

    @Test
    void scoreCardInvalidJSONReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/WS01/reports/score-card")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest());
    }

    // ---------- GenerateAuditReport ----------

    @Test
    void auditReportSuccess() throws Exception {
        when(svc.generate(any(dev.geolens.pdf.ReportRequest.class))).thenReturn(result());

        mockMvc.perform(post("/v1/workspaces/WS01/reports/audit")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\":\"B01\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    void auditReportMissingBrandReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/WS01/reports/audit")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_name\":\"Acme\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- RequestReport (async FR-F5) ----------

    @Test
    void requestReportInvalidJSONReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/WS01/reports")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz istek"));
    }

    @Test
    void requestReportMissingReportTypeReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/WS01/reports")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"brand_id\":\"B01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("report_type zorunludur (digest, score_card, audit)"));
    }

    @Test
    void requestReportInvalidReportTypeReturns400() throws Exception {
        mockMvc.perform(post("/v1/workspaces/WS01/reports")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"report_type\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("geçersiz rapor tipi (digest, score_card, audit)"));
    }

    @Test
    void requestReportSuccess() throws Exception {
        when(jdbc.queryForObject(contains("INSERT INTO measure.reports"), eq(String.class), any(), any(), any(), any(), any()))
                .thenReturn("R-new");

        mockMvc.perform(post("/v1/workspaces/WS01/reports")
                        .header("X-Tenant-ID", TENANT)
                        .contentType("application/json")
                        .content("{\"report_type\":\"digest\",\"brand_name\":\"Acme\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.report_id").value("R-new"))
                .andExpect(jsonPath("$.status").value("pending"));
    }

    // ---------- GetReportStatus ----------

    @Test
    void getReportStatusSuccess() throws Exception {
        when(jdbc.queryForMap(contains("FROM measure.reports"), any(Object[].class)))
                .thenReturn(statusRow("ready", "digest", "weekly.pdf", 100L, null));

        mockMvc.perform(get("/v1/workspaces/WS01/reports/R01/status")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.report_id").value("R01"))
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.file_name").value("weekly.pdf"))
                .andExpect(jsonPath("$.file_size").value(100));
    }

    @Test
    void getReportStatusNotFoundReturns404() throws Exception {
        when(jdbc.queryForMap(contains("FROM measure.reports"), any(Object[].class)))
                .thenThrow(new RuntimeException("not found"));

        mockMvc.perform(get("/v1/workspaces/WS01/reports/none/status")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("rapor bulunamadı"));
    }

    // ---------- DownloadReport ----------

    @Test
    void downloadReportNotReadyReturns409() throws Exception {
        when(jdbc.queryForMap(contains("FROM measure.reports"), any(Object[].class)))
                .thenReturn(statusRow("pending", "digest", null, null, null));

        mockMvc.perform(get("/v1/workspaces/WS01/reports/R01/download")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("rapor henüz hazır değil"));
    }

    @Test
    void downloadReportNotFoundReturns404() throws Exception {
        when(jdbc.queryForMap(contains("FROM measure.reports"), any(Object[].class)))
                .thenThrow(new RuntimeException("not found"));

        mockMvc.perform(get("/v1/workspaces/WS01/reports/none/download")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadReportS3Redirect() throws Exception {
        when(jdbc.queryForMap(contains("FROM measure.reports"), any(Object[].class)))
                .thenReturn(statusRow("ready", "digest", "weekly.pdf", 100L, null));
        when(svc.getReportData("R01")).thenThrow(new RuntimeException("should not reach"));

        mockMvc.perform(get("/v1/workspaces/WS01/reports/R01/download")
                        .header("X-Tenant-ID", TENANT)
                        .param("s3_url", "https://bucket.s3.amazonaws.com/report.pdf"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://bucket.s3.amazonaws.com/report.pdf"));
    }

    @Test
    void downloadReportReadySuccess() throws Exception {
        when(jdbc.queryForMap(contains("FROM measure.reports"), any(Object[].class)))
                .thenReturn(statusRow("ready", "digest", "weekly.pdf", 100L, null));
        when(svc.getReportData("R01")).thenReturn("%PDF-1.4 ready".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/v1/workspaces/WS01/reports/R01/download")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("X-Report-ID", "R01"));
    }

    // ---------- helpers ----------

    private static Map<String, Object> statusRow(String status, String type, String fileName, Long fileSize, String err) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        m.put("report_type", type);
        m.put("file_name", fileName);
        m.put("file_size", fileSize);
        m.put("error_message", err);
        m.put("created_at", "2026-08-14T00:00:00Z");
        m.put("updated_at", "2026-08-14T00:00:00Z");
        m.put("params", "{}");
        return m;
    }
}
