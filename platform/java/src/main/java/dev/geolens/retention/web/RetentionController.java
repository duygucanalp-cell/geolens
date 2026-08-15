package dev.geolens.retention.web;

import dev.geolens.common.ApiError;

import dev.geolens.retention.service.RetentionService;
import dev.geolens.common.ServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Veri Saklama REST controller'ı — Go {@code retention.handler} portu (K3).
 * <p>Route'lar (go cmd/api, /v1/workspaces/{ws} altında): GET /retention/policies,
 * PUT /retention/policies, DELETE /retention/policies/{policyId}, GET /retention/archive-summary.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir; workspace yalnızca URL'de bulunur
 * (Go handler'ı workspace'i sorgulamaz — birebir korundu). Saklama süresi ≥30 gün,
 * arşiv stratejisi delete/anonymize/archive_s3 olmalıdır.
 * <p>İş mantığı {@link RetentionService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}/retention")
public class RetentionController {

    private final RetentionService service;

    public RetentionController(RetentionService service) {
        this.service = service;
    }

    // ---------- ListPolicies ----------

    @GetMapping("/policies")
    public ResponseEntity<?> listPolicies(@PathVariable String workspaceId,
                                          @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.listPolicies(tenantId));
    }

    // ---------- UpsertPolicy ----------

    @PutMapping("/policies")
    public ResponseEntity<?> upsertPolicy(@PathVariable String workspaceId,
                                          @RequestHeader("X-Tenant-ID") String tenantId,
                                          @RequestBody UpsertPolicyRequest req) {
        return ResponseEntity.ok(service.upsertPolicy(tenantId, req));
    }

    // ---------- DeletePolicy ----------

    @DeleteMapping("/policies/{policyId}")
    public ResponseEntity<?> deletePolicy(@PathVariable String workspaceId,
                                          @RequestHeader("X-Tenant-ID") String tenantId,
                                          @PathVariable String policyId) {
        return ResponseEntity.ok(service.deletePolicy(tenantId, policyId));
    }

    // ---------- GetArchiveSummary ----------

    @GetMapping("/archive-summary")
    public ResponseEntity<?> getArchiveSummary(@PathVariable String workspaceId,
                                               @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.getArchiveSummary(tenantId));
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
