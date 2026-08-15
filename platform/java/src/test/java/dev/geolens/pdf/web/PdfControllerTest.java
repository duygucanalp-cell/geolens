package dev.geolens.pdf.web;

import dev.geolens.pdf.ReportResult;
import dev.geolens.pdf.ReportType;
import dev.geolens.pdf.service.PdfService;
import dev.geolens.common.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
        when(svc.generateWeeklyDigest("WS01", TENANT))
                .thenThrow(new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "rapor oluşturulamadı"));

        mockMvc.perform(post("/v1/workspaces/WS01/reports/digest")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("rapor oluşturulamadı"));
    }

    // ---------- GenerateScoreCard ----------

    @Test
    void scoreCardSuccess() throws Exception {
        when(svc.generateScoreCard(anyString(), anyString(), any())).thenReturn(result());

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
        when(svc.generateAuditReport(anyString(), anyString(), any())).thenReturn(result());

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
        when(svc.requestReport(anyString(), anyString(), any())).thenReturn("R-new");

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
        when(svc.getReportStatus(anyString(), anyString(), anyString()))
                .thenReturn(statusBody("ready", "digest", "weekly.pdf", 100L, null));

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
        when(svc.getReportStatus(anyString(), anyString(), anyString()))
                .thenThrow(new ServiceException(HttpStatus.NOT_FOUND, "rapor bulunamadı"));

        mockMvc.perform(get("/v1/workspaces/WS01/reports/none/status")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("rapor bulunamadı"));
    }

    // ---------- DownloadReport ----------

    @Test
    void downloadReportNotReadyReturns409() throws Exception {
        when(svc.prepareDownload(anyString(), anyString(), anyString()))
                .thenThrow(new ServiceException(HttpStatus.CONFLICT, "rapor henüz hazır değil"));

        mockMvc.perform(get("/v1/workspaces/WS01/reports/R01/download")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("rapor henüz hazır değil"));
    }

    @Test
    void downloadReportNotFoundReturns404() throws Exception {
        when(svc.prepareDownload(anyString(), anyString(), anyString()))
                .thenThrow(new ServiceException(HttpStatus.NOT_FOUND, "rapor bulunamadı"));

        mockMvc.perform(get("/v1/workspaces/WS01/reports/none/download")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadReportS3Redirect() throws Exception {
        when(svc.prepareDownload(anyString(), anyString(), anyString())).thenReturn("weekly.pdf");
        when(svc.getReportData("R01")).thenThrow(new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "rapor verisi alınamadı"));

        mockMvc.perform(get("/v1/workspaces/WS01/reports/R01/download")
                        .header("X-Tenant-ID", TENANT)
                        .param("s3_url", "https://bucket.s3.amazonaws.com/report.pdf"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://bucket.s3.amazonaws.com/report.pdf"));
    }

    @Test
    void downloadReportReadySuccess() throws Exception {
        when(svc.prepareDownload(anyString(), anyString(), anyString())).thenReturn("weekly.pdf");
        when(svc.getReportData("R01")).thenReturn("%PDF-1.4 ready".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/v1/workspaces/WS01/reports/R01/download")
                        .header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("X-Report-ID", "R01"));
    }

    // ---------- helpers ----------

    private static Map<String, Object> statusBody(String status, String type, String fileName, Long fileSize, String err) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("report_id", "R01");
        m.put("report_type", type);
        m.put("status", status);
        m.put("created_at", "2026-08-14T00:00:00Z");
        m.put("updated_at", "2026-08-14T00:00:00Z");
        if (fileName != null) {
            m.put("file_name", fileName);
        }
        if (fileSize != null) {
            m.put("file_size", fileSize);
        }
        if (err != null) {
            m.put("error", err);
        }
        return m;
    }
}
