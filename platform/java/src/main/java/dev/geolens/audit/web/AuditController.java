package dev.geolens.audit.web;

import dev.geolens.common.ApiError;

import dev.geolens.common.ServiceException;
import dev.geolens.audit.service.AuditWebService;
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

/**
 * Site denetimi REST controller'ı — Go {@code audit.handler} portu.
 * <p>Route'lar (go cmd/api): POST /v1/workspaces/{ws}/audit,
 * GET /v1/workspaces/{ws}/audit/findings, GET /v1/admin/audit-trail,
 * GET /v1/admin/audit-trail/export (T3).
 * <p>İş mantığı {@link AuditWebService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
public class AuditController {

    private final AuditWebService service;

    public AuditController(AuditWebService service) {
        this.service = service;
    }

    @PostMapping("/v1/workspaces/{workspaceId}/audit")
    public ResponseEntity<?> runAudit(@PathVariable String workspaceId,
                                      @RequestHeader("X-Tenant-ID") String tenantId,
                                      @RequestBody AuditRequest req) {
        if (req == null || req.brandId() == null || req.brandId().isBlank()
                || req.websiteUrl() == null || req.websiteUrl().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id ve website_url zorunludur");
        }
        return ResponseEntity.ok(service.runAudit(workspaceId, tenantId, req));
    }

    @GetMapping("/v1/workspaces/{workspaceId}/audit/findings")
    public ResponseEntity<?> getFindingsCatalog(@PathVariable String workspaceId,
                                                @RequestHeader("X-Tenant-ID") String tenantId,
                                                @RequestParam(value = "brand_id", required = false) String brandId) {
        if (brandId == null || brandId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id gerekli");
        }
        return ResponseEntity.ok(service.getFindingsCatalog(workspaceId, tenantId, brandId));
    }

    @GetMapping("/v1/admin/audit-trail")
    public ResponseEntity<?> listAuditTrail(@RequestHeader("X-Tenant-ID") String tenantId,
                                            @RequestParam(value = "event_type", required = false) String eventType,
                                            @RequestParam(value = "resource_type", required = false) String resourceType) {
        return ResponseEntity.ok(service.listAuditTrail(tenantId, eventType, resourceType));
    }

    @GetMapping(value = "/v1/admin/audit-trail/export", produces = "text/csv")
    public ResponseEntity<String> exportAuditTrail(@RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"audit-trail.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(service.exportAuditTrail(tenantId));
    }

    // ---------- hata yönetimi ----------

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiError> handleAuditError(ServiceException ex) {
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
