package dev.geolens.pdf.service;

import dev.geolens.common.ServiceException;

import dev.geolens.pdf.ReportRequest;
import dev.geolens.pdf.ReportResult;
import dev.geolens.pdf.ReportType;
import dev.geolens.pdf.web.CreateReportRequest;
import dev.geolens.pdf.web.ScoreCardRequest;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF rapor iş mantığı — Go {@code pdf.handler} portu.
 * <p>Haftalık özet, skor kartı, denetim raporu üretimi (PDF motoru {@code dev.geolens.pdf.PdfService}),
 * async rapor akışı (FR-F5) ve durum/indirme işlemlerini yönetir. Controller yalnızca HTTP katmanıdır;
 * bu sınıf DB ve PDF motoru erişimini içerir.
 */
@Service("pdfReportService")
public class PdfService {

    private final dev.geolens.pdf.PdfService engine;
    private final DSLContext dsl;

    public PdfService(dev.geolens.pdf.PdfService engine, DSLContext dsl) {
        this.engine = engine;
        this.dsl = dsl;
    }

    public ReportResult generateWeeklyDigest(String workspaceId, String tenantId) {
        try {
            return engine.generateWeeklyDigest(workspaceId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "rapor oluşturulamadı");
        }
    }

    public ReportResult generateScoreCard(String workspaceId, String tenantId, ScoreCardRequest req) {
        try {
            return engine.generate(new ReportRequest(ReportType.SCORE_CARD, workspaceId, tenantId,
                    req.brandId(), req.brandName()));
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "skor kartı oluşturulamadı");
        }
    }

    public ReportResult generateAuditReport(String workspaceId, String tenantId, ScoreCardRequest req) {
        try {
            return engine.generate(new ReportRequest(ReportType.AUDIT, workspaceId, tenantId,
                    req.brandId(), req.brandName()));
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "denetim raporu oluşturulamadı");
        }
    }

    public String requestReport(String workspaceId, String tenantId, CreateReportRequest req) {
        String type = req.reportType();
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
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "rapor talebi oluşturulamadı");
        }
        return reportId;
    }

    public Map<String, Object> getReportStatus(String workspaceId, String tenantId, String reportId) {
        Map<String, Object> row;
        try {
            row = map("""
                    SELECT status, report_type, file_name, file_size, error_message, created_at, updated_at
                    FROM measure.reports
                    WHERE id = ? AND workspace_id = ? AND tenant_id = ?
                    """, reportId, workspaceId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "rapor bulunamadı");
        }
        if (row == null) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "rapor bulunamadı");
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
        return body;
    }

    public String prepareDownload(String workspaceId, String tenantId, String reportId) {
        Map<String, Object> row;
        try {
            row = map("""
                    SELECT status, report_type, file_name, params::text AS params
                    FROM measure.reports
                    WHERE id = ? AND workspace_id = ? AND tenant_id = ?
                    """, reportId, workspaceId, tenantId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "rapor bulunamadı");
        }
        if (row == null) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "rapor bulunamadı");
        }

        String status = String.valueOf(row.get("status"));
        if (!"ready".equals(status)) {
            throw new ServiceException(HttpStatus.CONFLICT, "rapor henüz hazır değil");
        }
        return row.get("file_name") == null ? "report.pdf" : String.valueOf(row.get("file_name"));
    }

    public byte[] getReportData(String reportId) {
        try {
            return engine.getReportData(reportId);
        } catch (RuntimeException e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "rapor verisi alınamadı");
        }
    }

    private static String safe(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** ADR-014: plain SQL üzerinden jOOQ — satır erişimi Map ile korunur. */
    private List<Map<String, Object>> list(String sql, Object... args) {
        return dsl.fetch(sql, args).intoMaps();
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
}
