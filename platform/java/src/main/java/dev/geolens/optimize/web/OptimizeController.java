package dev.geolens.optimize.web;

import dev.geolens.optimize.service.OptimizeService;
import dev.geolens.optimize.service.OptimizeServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Optimization Recommendations REST controller'ı — Go {@code optimize.handler} portu (R13).
 * <p>Route'lar (go cmd/api): GET /v1/optimizations/recommendations,
 * POST /v1/optimizations/recommendations/generate, PUT /v1/optimizations/recommendations/{recId}/status.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir; öneriler A3-4 (İP-07) Opportunity
 * Scoring formülüyle (Impact × Urgency × Confidence) puanlanır.
 * <p>İş mantığı {@link OptimizeService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/optimizations")
public class OptimizeController {

    private final OptimizeService service;

    public OptimizeController(OptimizeService service) {
        this.service = service;
    }

    // ---------- ListRecommendations ----------

    @GetMapping("/recommendations")
    public ResponseEntity<?> listRecommendations(@RequestHeader("X-Tenant-ID") String tenantId,
                                                 @RequestParam(value = "limit", required = false) String limit,
                                                 @RequestParam(value = "status", required = false) String statusFilter,
                                                 @RequestParam(value = "category", required = false) String categoryFilter) {
        return ResponseEntity.ok(service.listRecommendations(tenantId, limit, statusFilter, categoryFilter));
    }

    // ---------- GenerateRecommendations ----------

    @PostMapping("/recommendations/generate")
    public ResponseEntity<?> generateRecommendations(@RequestHeader("X-Tenant-ID") String tenantId,
                                                     @RequestBody GenerateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.generateRecommendations(tenantId, req));
    }

    // ---------- UpdateStatus ----------

    @PutMapping("/recommendations/{recId}/status")
    public ResponseEntity<?> updateStatus(@RequestHeader("X-Tenant-ID") String tenantId,
                                          @PathVariable String recId,
                                          @RequestBody UpdateStatusRequest req) {
        return ResponseEntity.ok(service.updateStatus(tenantId, recId, req));
    }

    @ExceptionHandler(OptimizeServiceException.class)
    public ResponseEntity<ApiError> handleService(OptimizeServiceException ex) {
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
