package dev.geolens.usage.web;

import dev.geolens.usage.service.UsageService;
import dev.geolens.common.ServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * Kullanım analitiği REST controller'ı — Go {@code usage.handler} portu.
 * <p>Route'lar (go cmd/api): POST /v1/usage/metrics, GET /v1/usage/metrics,
 * GET /v1/usage/summary (R12).
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir.
 * <p>İş mantığı {@link UsageService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
public class UsageController {

    private final UsageService service;

    public UsageController(UsageService service) {
        this.service = service;
    }

    @PostMapping("/v1/usage/metrics")
    public ResponseEntity<?> recordUsage(@RequestHeader("X-Tenant-ID") String tenantId,
                                         @RequestBody UsageMetricRequest req) {
        if (req == null || req.endpoint() == null || req.endpoint().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "endpoint gerekli");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.recordUsage(tenantId, req));
    }

    @GetMapping("/v1/usage/metrics")
    public ResponseEntity<?> listUsage(@RequestHeader("X-Tenant-ID") String tenantId,
                                       @RequestParam(value = "limit", required = false) String limitParam) {
        return ResponseEntity.ok(service.listUsage(tenantId, limitParam));
    }

    @GetMapping("/v1/usage/summary")
    public ResponseEntity<?> getUsageSummary(@RequestHeader("X-Tenant-ID") String tenantId,
                                             @RequestParam(value = "period", required = false) String periodParam) {
        return ResponseEntity.ok(service.getUsageSummary(tenantId, periodParam));
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
