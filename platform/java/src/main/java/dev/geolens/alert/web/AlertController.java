package dev.geolens.alert.web;

import dev.geolens.alert.service.AlertService;
import dev.geolens.alert.service.AlertServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * Uyarı kuralı REST controller'ı — Go {@code alert.handler} portu.
 * <p>Route'lar (go cmd/api): GET/POST /v1/workspaces/{ws}/alert-rules,
 * PUT/DELETE /v1/workspaces/{ws}/alert-rules/{ruleId} (FR-F2).
 * <p>Tenant {@code X-Tenant-ID} başlığından, workspace URL path'ten gelir.
 * <p>İş mantığı {@link AlertService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}/alert-rules")
public class AlertController {

    private final AlertService service;

    public AlertController(AlertService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable String workspaceId,
                                  @RequestHeader("X-Tenant-ID") String tenantId,
                                  @RequestParam(value = "brand_id", required = false) String brandId) {
        return ResponseEntity.ok(service.list(workspaceId, tenantId, brandId));
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable String workspaceId,
                                    @RequestHeader("X-Tenant-ID") String tenantId,
                                    @RequestBody AlertRuleRequest req) {
        if (req == null || req.brandId() == null || req.brandId().isBlank()
                || req.name() == null || req.name().isBlank()
                || req.metric() == null || req.metric().isBlank()
                || req.condition() == null || req.condition().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "marka, ad, metrik ve koşul zorunludur");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(workspaceId, tenantId, req));
    }

    @PutMapping("/{ruleId}")
    public ResponseEntity<?> update(@PathVariable String workspaceId,
                                    @RequestHeader("X-Tenant-ID") String tenantId,
                                    @PathVariable String ruleId,
                                    @RequestBody UpdateAlertRuleRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        return ResponseEntity.ok(service.update(workspaceId, tenantId, ruleId, req));
    }

    @DeleteMapping("/{ruleId}")
    public ResponseEntity<?> delete(@PathVariable String workspaceId,
                                    @RequestHeader("X-Tenant-ID") String tenantId,
                                    @PathVariable String ruleId) {
        return ResponseEntity.ok(service.delete(tenantId, ruleId));
    }

    @ExceptionHandler(AlertServiceException.class)
    public ResponseEntity<ApiError> handleService(AlertServiceException ex) {
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
