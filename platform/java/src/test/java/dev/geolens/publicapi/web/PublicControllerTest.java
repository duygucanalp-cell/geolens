package dev.geolens.publicapi.web;

import dev.geolens.publicapi.service.PublicService;
import dev.geolens.common.ServiceException;
import dev.geolens.publicapi.service.ReportDownload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Go public/handler_test.go parity testleri — genel API (FR-F6). */
@WebMvcTest(PublicController.class)
class PublicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PublicService publicService;

    private static final String TENANT = "T01";

    // ---------- GetScore ----------

    @Test
    void getScoreNotFoundReturns404() throws Exception {
        when(publicService.getScore(anyString(), anyString()))
                .thenThrow(new ServiceException(HttpStatus.NOT_FOUND, "marka bulunamadı"));

        mockMvc.perform(get("/public/v1/scores/none").header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("marka bulunamadı"));
    }

    @Test
    void getScoreSuccess() throws Exception {
        when(publicService.getScore(anyString(), anyString()))
                .thenReturn(scoreBody("B01", "Acme", 82.5, "yüksek", "2026-08-14T00:00:00Z"));

        mockMvc.perform(get("/public/v1/scores/B01").header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand_id").value("B01"))
                .andExpect(jsonPath("$.brand_name").value("Acme"))
                .andExpect(jsonPath("$.score").value(82.5))
                .andExpect(jsonPath("$.fidelity").value("yüksek"))
                .andExpect(jsonPath("$.measured_at").exists());
    }

    // ---------- ListScores ----------

    @Test
    void listScoresSuccess() throws Exception {
        when(publicService.listScores(anyString()))
                .thenReturn(List.of(
                        scoreRow("B01", "Acme", 82.5, "yüksek", "2026-08-14T00:00:00Z"),
                        scoreRow("B02", "Beta", 55.0, "orta", "2026-08-13T00:00:00Z")));

        mockMvc.perform(get("/public/v1/scores").header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].brand_id").value("B01"))
                .andExpect(jsonPath("$[0].brand_name").value("Acme"))
                .andExpect(jsonPath("$[0].score").value(82.5))
                .andExpect(jsonPath("$[0].fidelity").value("yüksek"))
                .andExpect(jsonPath("$[1].brand_id").value("B02"));
    }

    @Test
    void listScoresQueryErrorReturnsEmpty() throws Exception {
        when(publicService.listScores(anyString())).thenReturn(List.of());

        mockMvc.perform(get("/public/v1/scores").header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ---------- ListBrands / GetBrand ----------

    @Test
    void listBrandsSuccess() throws Exception {
        when(publicService.listBrands(anyString()))
                .thenReturn(List.of(
                        brandRow("B01", "Acme", "https://acme.com"),
                        brandRow("B02", "Beta", "")));

        mockMvc.perform(get("/public/v1/brands").header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("B01"))
                .andExpect(jsonPath("$[0].name").value("Acme"))
                .andExpect(jsonPath("$[0].website_url").value("https://acme.com"))
                .andExpect(jsonPath("$[1].website_url").value(""));
    }

    @Test
    void getBrandNotFoundReturns404() throws Exception {
        when(publicService.getBrand(anyString(), anyString()))
                .thenThrow(new ServiceException(HttpStatus.NOT_FOUND, "marka bulunamadı"));

        mockMvc.perform(get("/public/v1/brands/none").header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("marka bulunamadı"));
    }

    // ---------- ListCitations ----------

    @Test
    void listCitationsSuccess() throws Exception {
        when(publicService.listCitations(anyString(), any()))
                .thenReturn(List.of(
                        citationRow("C1", "B01", "https://x.com/a", "Başlık", "özet", 1, "google", "2026-08-14T00:00:00Z")));

        mockMvc.perform(get("/public/v1/citations").header("X-Tenant-ID", TENANT)
                        .param("brand_id", "B01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("C1"))
                .andExpect(jsonPath("$[0].brand_id").value("B01"))
                .andExpect(jsonPath("$[0].url").value("https://x.com/a"))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].engine").value("google"));
    }

    // ---------- ListReports ----------

    @Test
    void listReportsSuccess() throws Exception {
        when(publicService.listReports(anyString(), any()))
                .thenReturn(List.of(
                        reportRow("R1", "score_card", "score-card-Acme.pdf", 1, "2026-08-14T00:00:00Z"),
                        reportRow("R2", "digest", "weekly-digest.pdf", 2, "2026-08-14T00:00:00Z")));

        mockMvc.perform(get("/public/v1/reports").header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("R1"))
                .andExpect(jsonPath("$[0].type").value("score_card"))
                .andExpect(jsonPath("$[0].file_name").value("score-card-Acme.pdf"))
                .andExpect(jsonPath("$[1].page_count").value(2));
    }

    @Test
    void listReportsQueryErrorReturnsEmpty() throws Exception {
        when(publicService.listReports(anyString(), any())).thenReturn(List.of());

        mockMvc.perform(get("/public/v1/reports").header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ---------- DownloadReport ----------

    @Test
    void downloadReportNotFoundReturns404() throws Exception {
        when(publicService.downloadReport(anyString(), anyString()))
                .thenThrow(new ServiceException(HttpStatus.NOT_FOUND, "rapor bulunamadı"));

        mockMvc.perform(get("/public/v1/reports/NOPE/download").header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("rapor bulunamadı"));
    }

    @Test
    void downloadReportDecodesPdfB64() throws Exception {
        byte[] pdfBytes = "%PDF-1.4 test içerik".getBytes(StandardCharsets.UTF_8);
        when(publicService.downloadReport(anyString(), anyString()))
                .thenReturn(ReportDownload.pdf(pdfBytes, "score-card-Acme.pdf"));

        mockMvc.perform(get("/public/v1/reports/R1/download").header("X-Tenant-ID", TENANT))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"score-card-Acme.pdf\""))
                .andExpect(content().bytes(pdfBytes));
    }

    @Test
    void downloadReportRedirectsToS3() throws Exception {
        when(publicService.downloadReport(anyString(), anyString()))
                .thenReturn(ReportDownload.redirect("https://s3.example.com/report.pdf"));

        mockMvc.perform(get("/public/v1/reports/R1/download").header("X-Tenant-ID", TENANT))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://s3.example.com/report.pdf"));
    }

    @Test
    void downloadReportPdfDataEmptyReturns404() throws Exception {
        when(publicService.downloadReport(anyString(), anyString()))
                .thenThrow(new ServiceException(HttpStatus.NOT_FOUND, "rapor verisi bulunamadı"));

        mockMvc.perform(get("/public/v1/reports/R1/download").header("X-Tenant-ID", TENANT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("rapor verisi bulunamadı"));
    }

    // ---------- ListTrends ----------

    @Test
    void listTrendsNoBrandIdReturns400() throws Exception {
        mockMvc.perform(get("/public/v1/trends").header("X-Tenant-ID", TENANT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("brand_id parametresi gerekli"));
    }

    @Test
    void listTrendsSuccess() throws Exception {
        when(publicService.listTrends(anyString(), anyString()))
                .thenReturn(Map.of("trends", List.of(
                        trendRow(70.0, "orta", "2026-08-10T00:00:00Z"),
                        trendRow(82.5, "yüksek", "2026-08-14T00:00:00Z"))));

        mockMvc.perform(get("/public/v1/trends").header("X-Tenant-ID", TENANT)
                        .param("brand_id", "B01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trends[0].value").value(70.0))
                .andExpect(jsonPath("$.trends[1].fidelity_label").value("yüksek"));
    }

    // ---------- helpers ----------

    private static Map<String, Object> scoreBody(String id, String name, double score, String fidelity, String measuredAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("brand_id", id);
        m.put("brand_name", name);
        m.put("score", score);
        m.put("fidelity", fidelity);
        m.put("measured_at", measuredAt);
        return m;
    }

    private static Map<String, Object> scoreRow(String id, String name, double score, String fidelity, String at) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("brand_id", id);
        m.put("brand_name", name);
        m.put("score", score);
        m.put("fidelity", fidelity);
        m.put("measured_at", at);
        return m;
    }

    private static Map<String, Object> brandRow(String id, String name, String url) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("website_url", url);
        return m;
    }

    private static Map<String, Object> citationRow(String id, String brandId, String url, String title,
                                                   String snippet, int position, String engine, String at) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("brand_id", brandId);
        m.put("url", url);
        m.put("title", title);
        m.put("snippet", snippet);
        m.put("position", position);
        m.put("engine", engine);
        m.put("measured_at", at);
        return m;
    }

    private static Map<String, Object> reportRow(String id, String type, String fileName, int pageCount, String at) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("type", type);
        m.put("file_name", fileName);
        m.put("page_count", pageCount);
        m.put("generated_at", at);
        return m;
    }

    private static Map<String, Object> trendRow(double value, String label, String at) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", value);
        m.put("fidelity_label", label);
        m.put("measured_at", at);
        return m;
    }
}
