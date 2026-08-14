package dev.geolens.pdf.web;

import dev.geolens.pdf.PdfGenerationException;
import dev.geolens.pdf.PdfReportNotFoundException;
import dev.geolens.pdf.PdfService;
import dev.geolens.pdf.ReportRequest;
import dev.geolens.pdf.ReportResult;
import dev.geolens.pdf.ReportType;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PDF rapor REST controller'ı — Go {@code pdf.handler} portu.
 * <p>Route'lar (go cmd/api): POST /v1/workspaces/{ws}/reports/digest,
 * POST /v1/workspaces/{ws}/reports/score-card, POST /v1/workspaces/{ws}/reports/audit (FR),
 * POST /v1/workspaces/{ws}/reports (async FR-F5), GET .../reports/{id}/status,
 * GET .../reports/{id}/download.
 * <p>Tenant {@code X-Tenant-ID} başlığından, workspace URL path'ten gelir.
 */
@RestController
public class PdfController {

    private final PdfService svc;
    private final DSLContext dsl;

    public PdfController(PdfService svc, DSLContext dsl) {
        this.svc = svc;
        this.dsl = dsl;
    }

    @PostMapping("/v1/workspaces/{workspaceId}/reports/digest")
    public ResponseEntity<?> generateWeeklyDigest(@PathVariable String workspaceId,
                                                  @RequestHeader("X-Tenant-ID") String tenantId) {
        ReportResult result;
        try {
            result = svc.generateWeeklyDigest(workspaceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "rapor oluşturulamadı");
        }
        return pdf(result);
    }

    @PostMapping("/v1/workspaces/{workspaceId}/reports/score-card")
    public ResponseEntity<?> generateScoreCard(@PathVariable String workspaceId,
                                               @RequestHeader("X-Tenant-ID") String tenantId,
                                               @RequestBody ScoreCardRequest req) {
        if (req == null || req.brandId() == null || req.brandId().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id zorunludur");
        }
        ReportResult result;
        try {
            result = svc.generate(new ReportRequest(ReportType.SCORE_CARD, workspaceId, tenantId,
                    req.brandId(), req.brandName()));
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "skor kartı oluşturulamadı");
        }
        return pdf(result);
    }

    @PostMapping("/v1/workspaces/{workspaceId}/reports/audit")
    public ResponseEntity<?> generateAuditReport(@PathVariable String workspaceId,
                                                 @RequestHeader("X-Tenant-ID") String tenantId,
                                                 @RequestBody ScoreCardRequest req) {
        if (req == null || req.brandId() == null || req.brandId().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id zorunludur");
        }
        ReportResult result;
        try {
            result = svc.generate(new ReportRequest(ReportType.AUDIT, workspaceId, tenantId,
                    req.brandId(), req.brandName()));
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "denetim raporu oluşturulamadı");
        }
        return pdf(result);
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

        String reportId;
        try {
            reportId = value("""
                    INSERT INTO measure.reports (id, tenant_id, workspace_id, report_type, brand_id, status, params)
                    VALUES (gen_random_uuid()::text, ?, ?, ?, ?, 'pending', ?)
                    RETURNING id
                    """, String.class, tenantId, workspaceId, type,
                    req.brandId() == null || req.brandId().isBlank() ? null : req.brandId(),
                    "{\"brand_name\":\"" + safe(req.brandName()) + "\"}");
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "rapor talebi oluşturulamadı");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("report_id", reportId);
        body.put("status", "pending");
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/v1/workspaces/{workspaceId}/reports/{reportId}/status")
    public ResponseEntity<?> getReportStatus(@PathVariable String workspaceId,
                                             @RequestHeader("X-Tenant-ID") String tenantId,
                                             @PathVariable String reportId) {
        Map<String, Object> row;
        try {
            row = map("""
                    SELECT status, report_type, file_name, file_size, error_message, created_at, updated_at
                    FROM measure.reports
                    WHERE id = ? AND workspace_id = ? AND tenant_id = ?
                    """, reportId, workspaceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.NOT_FOUND, "rapor bulunamadı");
        }
        if (row == null) {
            return error(HttpStatus.NOT_FOUND, "rapor bulunamadı");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("report_id", reportId);
        body.put("report_type", row.get("report_type"));
        body.put("status", row.get("status"));
        body.put("created_at", row.get("created_at") == null ? null : String.valueOf(row.get("created_at")));
        body.put("updated_at", row.get("updated_at") == null ? null : String.valueOf(row.get("updated_at")));
        if (row.get("file_name") != null) {
            body.put("file_name", row.get("file_name"));
        }
        if (row.get("file_size") != null) {
            body.put("file_size", row.get("file_size"));
        }
        if (row.get("error_message") != null) {
            body.put("error", row.get("error_message"));
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping("/v1/workspaces/{workspaceId}/reports/{reportId}/download")
    public ResponseEntity<?> downloadReport(@PathVariable String workspaceId,
                                            @RequestHeader("X-Tenant-ID") String tenantId,
                                            @PathVariable String reportId,
                                            @RequestParam(value = "s3_url", required = false) String s3Url) {
        Map<String, Object> row;
        try {
            row = map("""
                    SELECT status, report_type, file_name, params::text AS params
                    FROM measure.reports
                    WHERE id = ? AND workspace_id = ? AND tenant_id = ?
                    """, reportId, workspaceId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.NOT_FOUND, "rapor bulunamadı");
        }
        if (row == null) {
            return error(HttpStatus.NOT_FOUND, "rapor bulunamadı");
        }

        String status = String.valueOf(row.get("status"));
        if (!"ready".equals(status)) {
            return error(HttpStatus.CONFLICT, "rapor henüz hazır değil");
        }
        String fileName = row.get("file_name") == null ? "report.pdf" : String.valueOf(row.get("file_name"));

        if (s3Url != null && !s3Url.isBlank()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, s3Url)
                    .build();
        }

        byte[] data;
        try {
            data = svc.getReportData(reportId);
        } catch (PdfReportNotFoundException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "rapor verisi alınamadı");
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "rapor verisi alınamadı");
        }

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

    private static String safe(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** ADR-014: plain SQL üzerinden jOOQ — satır erişimi Map ile korunur. */
    private Map<String, Object> map(String sql, Object... args) {
        Record r = dsl.fetchOne(sql, args);
        return r == null ? null : r.intoMap();
    }

    /** ADR-014: plain SQL tek değer — jOOQ dönüşümüyle (fetchValue raw Object döner). */
    private <T> T value(String sql, Class<T> type, Object... args) {
        Record r = dsl.fetchOne(sql, args);
        return r == null ? null : r.get(0, type);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
