package dev.geolens.contentgeo.web;

import dev.geolens.common.ApiError;

import dev.geolens.contentgeo.service.ContentgeoService;
import dev.geolens.common.ServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Content GEO REST controller'ı — Go {@code contentgeo.handler} portu (FR-E5, FR-E6).
 * <p>Route'lar (go cmd/api): POST /v1/workspaces/{ws}/content-geo/gap,
 * GET /content-geo/gap, GET /content-geo/hub-score, GET /content-geo/topics.
 * <p>Tenant {@code X-Tenant-ID} başlığından, workspace path'ten gelir.
 * <p>İş mantığı {@link ContentgeoService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}/content-geo")
public class ContentgeoController {

    private final ContentgeoService service;

    public ContentgeoController(ContentgeoService service) {
        this.service = service;
    }

    // ---------- AnalyzeContentGap ----------

    @PostMapping("/gap")
    public ResponseEntity<?> analyzeContentGap(@PathVariable String workspaceId,
                                               @RequestHeader("X-Tenant-ID") String tenantId,
                                               @RequestBody AnalyzeGapRequest req) {
        if (req == null || req.brandId() == null || req.brandId().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id zorunludur");
        }
        return ResponseEntity.ok(service.analyzeContentGap(req.brandId(), workspaceId, tenantId));
    }

    // ---------- ListContentGaps ----------

    @GetMapping("/gap")
    public ResponseEntity<?> listContentGaps(@PathVariable String workspaceId,
                                             @RequestHeader("X-Tenant-ID") String tenantId,
                                             @RequestParam(value = "brand_id", required = false) String brandId) {
        return ResponseEntity.ok(service.listContentGaps(workspaceId, tenantId, brandId));
    }

    // ---------- GetContentHubScore ----------

    @GetMapping("/hub-score")
    public ResponseEntity<?> getContentHubScore(@PathVariable String workspaceId,
                                                @RequestHeader("X-Tenant-ID") String tenantId,
                                                @RequestParam(value = "brand_id", required = false) String brandId) {
        if (brandId == null || brandId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id gerekli");
        }
        return ResponseEntity.ok(service.getContentHubScore(brandId, workspaceId, tenantId));
    }

    // ---------- ListTopicClusters ----------

    @GetMapping("/topics")
    public ResponseEntity<?> listTopicClusters(@PathVariable String workspaceId,
                                               @RequestHeader("X-Tenant-ID") String tenantId,
                                               @RequestParam(value = "brand_id", required = false) String brandId) {
        return ResponseEntity.ok(service.listTopicClusters(workspaceId, tenantId, brandId));
    }

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
