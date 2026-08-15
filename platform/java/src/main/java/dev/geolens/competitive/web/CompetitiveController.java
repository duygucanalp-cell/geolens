package dev.geolens.competitive.web;

import dev.geolens.competitive.service.CompetitiveService;
import dev.geolens.competitive.service.CompetitiveServiceException;
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
 * Competitive Gap Analysis REST controller'ı — Go {@code competitive.handler} portu (FR-D11).
 * <p>Route'lar (go cmd/api): POST /v1/workspaces/{ws}/competitive-gap/analyze,
 * GET /competitive-gap/overview, GET /competitive-gap/visibility,
 * GET /competitive-gap/recommendations.
 * <p>Tenant {@code X-Tenant-ID} başlığından, workspace path'ten gelir.
 * <p>İş mantığı {@link CompetitiveService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}/competitive-gap")
public class CompetitiveController {

    private final CompetitiveService service;

    public CompetitiveController(CompetitiveService service) {
        this.service = service;
    }

    // ---------- AnalyzeGap ----------

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeGap(@PathVariable String workspaceId,
                                        @RequestHeader("X-Tenant-ID") String tenantId,
                                        @RequestBody AnalyzeGapRequest req) {
        if (req == null || req.brandId() == null || req.brandId().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id zorunludur");
        }
        return ResponseEntity.ok(service.analyzeGap(req.brandId(), workspaceId, tenantId));
    }

    // ---------- GetOverview ----------

    @GetMapping("/overview")
    public ResponseEntity<?> getOverview(@PathVariable String workspaceId,
                                         @RequestHeader("X-Tenant-ID") String tenantId,
                                         @RequestParam(value = "brand_id", required = false) String brandId) {
        if (brandId == null || brandId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id gerekli");
        }
        return ResponseEntity.ok(service.getOverview(workspaceId, tenantId, brandId));
    }

    // ---------- GetVisibilityGap ----------

    @GetMapping("/visibility")
    public ResponseEntity<?> getVisibilityGap(@PathVariable String workspaceId,
                                              @RequestHeader("X-Tenant-ID") String tenantId,
                                              @RequestParam(value = "brand_id", required = false) String brandId,
                                              @RequestParam(value = "competitor_id", required = false) String competitorId) {
        if (brandId == null || brandId.isBlank() || competitorId == null || competitorId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id ve competitor_id gerekli");
        }
        return ResponseEntity.ok(service.getVisibilityGap(brandId, competitorId, tenantId));
    }

    // ---------- GetRecommendations ----------

    @GetMapping("/recommendations")
    public ResponseEntity<?> getRecommendations(@PathVariable String workspaceId,
                                                @RequestHeader("X-Tenant-ID") String tenantId,
                                                @RequestParam(value = "brand_id", required = false) String brandId) {
        return ResponseEntity.ok(service.getRecommendations(workspaceId, tenantId, brandId));
    }

    @ExceptionHandler(CompetitiveServiceException.class)
    public ResponseEntity<ApiError> handleService(CompetitiveServiceException ex) {
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
