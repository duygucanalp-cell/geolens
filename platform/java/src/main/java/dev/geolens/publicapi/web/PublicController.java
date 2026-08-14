package dev.geolens.publicapi.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Genel (public) REST API controller'ı — Go {@code public.handler} portu (FR-F6).
 * <p>Route'lar (go cmd/api): GET /public/v1/scores/{brandID}, /scores, /trends,
 * /brands, /brands/{brandID}, /citations, /reports, /reports/{reportID}/download.
 * <p>Go'da API anahtarı auth middleware'i tenant'ı bağlama koyar; Java spike'ında
 * {@code X-Tenant-ID} başlığından gelir.
 */
@RestController
public class PublicController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;

    public PublicController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/public/v1/scores/{brandID}")
    public ResponseEntity<?> getScore(@PathVariable String brandID,
                                      @RequestHeader("X-Tenant-ID") String tenantId) {
        if (brandID == null || brandID.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id gerekli");
        }

        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("""
                    SELECT b.name AS brand_name, COALESCE(s.value, 0) AS score,
                           COALESCE(s.fidelity_label, 'yok') AS fidelity, s.freshness_at
                    FROM config.brands b
                    LEFT JOIN measure.scores s ON s.brand_id = b.id AND s.tenant_id = b.tenant_id
                    WHERE b.id = ? AND b.tenant_id = ? AND b.is_active = true
                    ORDER BY s.freshness_at DESC LIMIT 1
                    """, brandID, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.NOT_FOUND, "marka bulunamadı");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("brand_id", brandID);
        body.put("brand_name", row.get("brand_name"));
        body.put("score", row.get("score"));
        body.put("fidelity", row.get("fidelity"));
        body.put("measured_at", row.get("freshness_at") == null ? null : String.valueOf(row.get("freshness_at")));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/public/v1/scores")
    public ResponseEntity<?> listScores(@RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("""
                    SELECT b.id AS brand_id, b.name AS brand_name,
                           COALESCE(s.value, 0) AS score, COALESCE(s.fidelity_label, 'yok') AS fidelity,
                           s.freshness_at
                    FROM config.brands b
                    LEFT JOIN LATERAL (
                        SELECT value, fidelity_label, freshness_at
                        FROM measure.scores
                        WHERE brand_id = b.id AND tenant_id = b.tenant_id
                        ORDER BY freshness_at DESC LIMIT 1
                    ) s ON true
                    WHERE b.tenant_id = ? AND b.is_active = true
                    ORDER BY b.name ASC
                    """, tenantId);
        } catch (RuntimeException e) {
            return ResponseEntity.ok(List.of());
        }

        List<Map<String, Object>> scores = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("brand_id", r.get("brand_id"));
            item.put("brand_name", r.get("brand_name"));
            item.put("score", r.get("score"));
            item.put("fidelity", r.get("fidelity"));
            if (r.get("freshness_at") != null) {
                item.put("measured_at", String.valueOf(r.get("freshness_at")));
            }
            scores.add(item);
        }
        return ResponseEntity.ok(scores);
    }

    @GetMapping("/public/v1/brands")
    public ResponseEntity<?> listBrands(@RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("""
                    SELECT id, name, COALESCE(website_url, '') AS website_url
                    FROM config.brands
                    WHERE tenant_id = ? AND is_active = true
                    ORDER BY name ASC
                    """, tenantId);
        } catch (RuntimeException e) {
            return ResponseEntity.ok(List.of());
        }

        List<Map<String, Object>> brands = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.get("id"));
            item.put("name", r.get("name"));
            item.put("website_url", r.get("website_url"));
            brands.add(item);
        }
        return ResponseEntity.ok(brands);
    }

    @GetMapping("/public/v1/brands/{brandID}")
    public ResponseEntity<?> getBrand(@PathVariable String brandID,
                                      @RequestHeader("X-Tenant-ID") String tenantId) {
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("""
                    SELECT name, COALESCE(website_url, '') AS website_url
                    FROM config.brands
                    WHERE id = ? AND tenant_id = ? AND is_active = true
                    """, brandID, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.NOT_FOUND, "marka bulunamadı");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", brandID);
        body.put("name", row.get("name"));
        body.put("website_url", row.get("website_url"));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/public/v1/citations")
    public ResponseEntity<?> listCitations(@RequestHeader("X-Tenant-ID") String tenantId,
                                           @RequestParam(value = "brand_id", required = false) String brandId) {
        String b = brandId == null ? "" : brandId;
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("""
                    SELECT c.id, c.brand_id, c.url, c.title, c.snippet, c.position, c.engine, c.measured_at
                    FROM measure.citations c
                    JOIN config.brands b ON b.id = c.brand_id
                    WHERE c.tenant_id = ? AND b.tenant_id = ?
                        AND (? = '' OR c.brand_id = ?)
                    ORDER BY c.measured_at DESC
                    LIMIT 200
                    """, tenantId, tenantId, b, b);
        } catch (RuntimeException e) {
            return ResponseEntity.ok(List.of());
        }

        List<Map<String, Object>> citations = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.get("id"));
            item.put("brand_id", r.get("brand_id"));
            item.put("url", r.get("url"));
            item.put("title", r.get("title"));
            if (r.get("snippet") != null) {
                item.put("snippet", r.get("snippet"));
            }
            item.put("position", r.get("position"));
            item.put("engine", r.get("engine"));
            item.put("measured_at", r.get("measured_at") == null ? null : String.valueOf(r.get("measured_at")));
            citations.add(item);
        }
        return ResponseEntity.ok(citations);
    }

    @GetMapping("/public/v1/reports")
    public ResponseEntity<?> listReports(@RequestHeader("X-Tenant-ID") String tenantId,
                                         @RequestParam(value = "brand_id", required = false) String brandId) {
        String b = brandId == null ? "" : brandId;
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("""
                    SELECT r.id, r.report_type, COALESCE(r.file_name, '') AS file_name,
                           COALESCE(CASE WHEN r.params->>'page_count' ~ '^[0-9]+$' THEN (r.params->>'page_count')::int ELSE 0 END, 0) AS page_count,
                           r.created_at
                    FROM measure.reports r
                    WHERE r.tenant_id = ?
                        AND r.status = 'ready'
                        AND (? = '' OR r.brand_id = ?)
                    ORDER BY r.created_at DESC
                    LIMIT 50
                    """, tenantId, b, b);
        } catch (RuntimeException e) {
            return ResponseEntity.ok(List.of());
        }

        List<Map<String, Object>> reports = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.get("id"));
            item.put("type", r.get("report_type"));
            item.put("file_name", r.get("file_name"));
            item.put("page_count", r.get("page_count"));
            item.put("generated_at", r.get("created_at") == null ? null : String.valueOf(r.get("created_at")));
            reports.add(item);
        }
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/public/v1/reports/{reportID}/download")
    public ResponseEntity<?> downloadReport(@PathVariable String reportID,
                                            @RequestHeader("X-Tenant-ID") String tenantId) {
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("""
                    SELECT COALESCE(file_name, '') AS file_name, params::text AS params
                    FROM measure.reports
                    WHERE id = ? AND tenant_id = ? AND status = 'ready'
                    """, reportID, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.NOT_FOUND, "rapor bulunamadı");
        }

        String paramsJson = row.get("params") == null ? "" : String.valueOf(row.get("params"));
        String fileName = row.get("file_name") == null ? "" : String.valueOf(row.get("file_name"));

        Map<String, Object> params;
        try {
            params = MAPPER.readValue(paramsJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "rapor verisi çözümlenemedi");
        }

        String s3Url = params.get("s3_url") == null ? "" : String.valueOf(params.get("s3_url"));
        if (!s3Url.isBlank()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, s3Url)
                    .build();
        }

        byte[] data = null;
        Object fileData = params.get("file_data");
        if (fileData != null) {
            data = String.valueOf(fileData).getBytes(StandardCharsets.UTF_8);
        }
        String pdfB64 = params.get("pdf_b64") == null ? "" : String.valueOf(params.get("pdf_b64"));
        if (!pdfB64.isBlank()) {
            try {
                data = Base64.getDecoder().decode(pdfB64);
            } catch (IllegalArgumentException e) {
                return error(HttpStatus.INTERNAL_SERVER_ERROR, "rapor verisi çözümlenemedi");
            }
        }
        if (data == null || data.length == 0) {
            return error(HttpStatus.NOT_FOUND, "rapor verisi bulunamadı");
        }

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_PDF);
        if (!fileName.isBlank()) {
            builder.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
        }
        return builder.body(data);
    }

    @GetMapping("/public/v1/trends")
    public ResponseEntity<?> listTrends(@RequestHeader("X-Tenant-ID") String tenantId,
                                        @RequestParam(value = "brand_id", required = false) String brandId) {
        String b = brandId == null ? "" : brandId;
        if (b.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id parametresi gerekli");
        }

        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("""
                    SELECT s.value, s.fidelity_label, s.freshness_at
                    FROM measure.scores s
                    WHERE s.brand_id = ? AND s.tenant_id = ?
                    ORDER BY s.freshness_at ASC
                    LIMIT 50
                    """, b, tenantId);
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("trends", List.of()));
        }

        List<Map<String, Object>> trends = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("value", r.get("value"));
            item.put("fidelity_label", r.get("fidelity_label"));
            item.put("measured_at", r.get("freshness_at") == null ? null : String.valueOf(r.get("freshness_at")));
            trends.add(item);
        }
        return ResponseEntity.ok(Map.of("trends", trends));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
