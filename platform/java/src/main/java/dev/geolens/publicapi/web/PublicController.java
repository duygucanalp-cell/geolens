package dev.geolens.publicapi.web;

import dev.geolens.common.ApiError;

import dev.geolens.publicapi.service.PublicService;
import dev.geolens.common.ServiceException;
import dev.geolens.publicapi.service.ReportDownload;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Genel (public) REST API controller'ı — Go {@code public.handler} portu (FR-F6).
 * <p>Route'lar (go cmd/api): GET /public/v1/scores/{brandID}, /scores, /trends,
 * /brands, /brands/{brandID}, /citations, /reports, /reports/{reportID}/download.
 * <p>Go'da API anahtarı auth middleware'i tenant'ı bağlama koyar; Java spike'ında
 * {@code X-Tenant-ID} başlığından gelir.
 * <p>İş mantığı {@link PublicService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
public class PublicController {

    private final PublicService service;

    public PublicController(PublicService service) {
        this.service = service;
    }

    @GetMapping("/public/v1/scores/{brandID}")
    public ResponseEntity<?> getScore(@PathVariable String brandID,
                                      @RequestHeader("X-Tenant-ID") String tenantId) {
        if (brandID == null || brandID.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id gerekli");
        }
        return ResponseEntity.ok(service.getScore(brandID, tenantId));
    }

    @GetMapping("/public/v1/scores")
    public ResponseEntity<?> listScores(@RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.listScores(tenantId));
    }

    @GetMapping("/public/v1/brands")
    public ResponseEntity<?> listBrands(@RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.listBrands(tenantId));
    }

    @GetMapping("/public/v1/brands/{brandID}")
    public ResponseEntity<?> getBrand(@PathVariable String brandID,
                                      @RequestHeader("X-Tenant-ID") String tenantId) {
        if (brandID == null || brandID.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id gerekli");
        }
        return ResponseEntity.ok(service.getBrand(brandID, tenantId));
    }

    @GetMapping("/public/v1/citations")
    public ResponseEntity<?> listCitations(@RequestHeader("X-Tenant-ID") String tenantId,
                                           @RequestParam(value = "brand_id", required = false) String brandId) {
        return ResponseEntity.ok(service.listCitations(tenantId, brandId));
    }

    @GetMapping("/public/v1/reports")
    public ResponseEntity<?> listReports(@RequestHeader("X-Tenant-ID") String tenantId,
                                         @RequestParam(value = "brand_id", required = false) String brandId) {
        return ResponseEntity.ok(service.listReports(tenantId, brandId));
    }

    @GetMapping("/public/v1/reports/{reportID}/download")
    public ResponseEntity<?> downloadReport(@PathVariable String reportID,
                                            @RequestHeader("X-Tenant-ID") String tenantId) {
        ReportDownload d = service.downloadReport(reportID, tenantId);
        if (d.isRedirect()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, d.location())
                    .build();
        }

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_PDF);
        if (!d.fileName().isBlank()) {
            builder.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + d.fileName() + "\"");
        }
        return builder.body(d.data());
    }

    @GetMapping("/public/v1/trends")
    public ResponseEntity<?> listTrends(@RequestHeader("X-Tenant-ID") String tenantId,
                                        @RequestParam(value = "brand_id", required = false) String brandId) {
        String b = brandId == null ? "" : brandId;
        if (b.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id parametresi gerekli");
        }
        return ResponseEntity.ok(service.listTrends(tenantId, b));
    }

    // ---------- hata yönetimi ----------

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiError> handleService(ServiceException ex) {
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
