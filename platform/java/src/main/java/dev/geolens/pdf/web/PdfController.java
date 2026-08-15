package dev.geolens.pdf.web;

import dev.geolens.pdf.ReportResult;
import dev.geolens.pdf.service.PdfService;
import dev.geolens.pdf.service.PdfServiceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PDF rapor REST controller'ı — Go {@code pdf.handler} portu.
 * <p>Route'lar (go cmd/api): POST /v1/workspaces/{ws}/reports/digest,
 * POST /v1/workspaces/{ws}/reports/score-card, POST /v1/workspaces/{ws}/reports/audit (FR),
 * POST /v1/workspaces/{ws}/reports (async FR-F5), GET .../reports/{id}/status,
 * GET .../reports/{id}/download.
 * <p>İş mantığı {@link PdfService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 * Tenant {@code X-Tenant-ID} başlığından, workspace URL path'ten gelir.
 */
@RestController
public class PdfController {

    private final PdfService svc;

    public PdfController(PdfService svc) {
        this.svc = svc;
    }

    @PostMapping("/v1/workspaces/{workspaceId}/reports/digest")
    public ResponseEntity<?> generateWeeklyDigest(@PathVariable String workspaceId,
                                                  @RequestHeader("X-Tenant-ID") String tenantId) {
        return pdf(svc.generateWeeklyDigest(workspaceId, tenantId));
    }

    @PostMapping("/v1/workspaces/{workspaceId}/reports/score-card")
    public ResponseEntity<?> generateScoreCard(@PathVariable String workspaceId,
                                               @RequestHeader("X-Tenant-ID") String tenantId,
                                               @RequestBody ScoreCardRequest req) {
        if (req == null || req.brandId() == null || req.brandId().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id zorunludur");
        }
        return pdf(svc.generateScoreCard(workspaceId, tenantId, req));
    }

    @PostMapping("/v1/workspaces/{workspaceId}/reports/audit")
    public ResponseEntity<?> generateAuditReport(@PathVariable String workspaceId,
                                                 @RequestHeader("X-Tenant-ID") String tenantId,
                                                 @RequestBody ScoreCardRequest req) {
        if (req == null || req.brandId() == null || req.brandId().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id zorunludur");
        }
        return pdf(svc.generateAuditReport(workspaceId, tenantId, req));
    }

    @PostMapping("/v1/workspaces/{workspaceId}/reports")
    public ResponseEntity<?> requestReport(@PathVariable String workspaceId,
                                           @RequestHeader("X-Tenant-ID") String tenantId,
                                           @RequestBody CreateReportRequest req) {
        if (req == null || req.reportType() == null || req.reportType().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "report_type zorunludur (digest, score_card, audit)");
        }
        String type = req.reportType();
        if (!"digest".equals(type) && !"score_card".equals(type) && !"audit".equals(type)) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz rapor tipi (digest, score_card, audit)");
        }

        String reportId = svc.requestReport(workspaceId, tenantId, req);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("report_id", reportId);
        body.put("status", "pending");
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/v1/workspaces/{workspaceId}/reports/{reportId}/status")
    public ResponseEntity<?> getReportStatus(@PathVariable String workspaceId,
                                             @RequestHeader("X-Tenant-ID") String tenantId,
                                             @PathVariable String reportId) {
        return ResponseEntity.ok(svc.getReportStatus(workspaceId, tenantId, reportId));
    }

    @GetMapping("/v1/workspaces/{workspaceId}/reports/{reportId}/download")
    public ResponseEntity<?> downloadReport(@PathVariable String workspaceId,
                                            @RequestHeader("X-Tenant-ID") String tenantId,
                                            @PathVariable String reportId,
                                            @RequestParam(value = "s3_url", required = false) String s3Url) {
        String fileName = svc.prepareDownload(workspaceId, tenantId, reportId);

        if (s3Url != null && !s3Url.isBlank()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, s3Url)
                    .build();
        }

        byte[] data = svc.getReportData(reportId);

        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header("X-Report-ID", reportId)
                .body(data);
    }

    private static ResponseEntity<byte[]> pdf(ReportResult result) {
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.fileName() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(result.data().length))
                .body(result.data());
    }

    // ---------- hata yönetimi ----------

    @ExceptionHandler(PdfServiceException.class)
    public ResponseEntity<ApiError> handleService(PdfServiceException ex) {
        return error(ex.status(), ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
