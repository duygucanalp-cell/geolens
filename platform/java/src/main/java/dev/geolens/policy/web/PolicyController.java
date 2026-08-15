package dev.geolens.policy.web;

import dev.geolens.common.ApiError;

import dev.geolens.policy.service.PolicyService;
import dev.geolens.common.ServiceException;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Policy Packs REST controller'ı — Go {@code policy.handler} portu (R4).
 * <p>Route'lar (go cmd/api): GET /v1/policies/packs, GET /packs/{packId}/controls,
 * POST /packs/seed, POST /packs/{packId}/apply, PUT /controls/{controlId},
 * GET /compliance/{entityId}.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir; EU AI Act, NIST AI RMF, KVKK,
 * ISO 42001 varsayılan pack'leri otomatik seed'lenir.
 * <p>İş mantığı {@link PolicyService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/policies")
public class PolicyController {

    private final PolicyService service;

    public PolicyController(PolicyService service) {
        this.service = service;
    }

    // ---------- ListPacks ----------

    @GetMapping("/packs")
    public ResponseEntity<?> listPacks(@RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.listPacks(tenantId));
    }

    // ---------- ListControls ----------

    @GetMapping("/packs/{packId}/controls")
    public ResponseEntity<?> listControls(@RequestHeader("X-Tenant-ID") String tenantId,
                                          @PathVariable String packId) {
        return ResponseEntity.ok(service.listControls(tenantId, packId));
    }

    // ---------- ApplyPack ----------

    @PostMapping("/packs/{packId}/apply")
    public ResponseEntity<?> applyPack(@RequestHeader("X-Tenant-ID") String tenantId,
                                       @PathVariable String packId) {
        return ResponseEntity.ok(service.applyPack(tenantId, packId));
    }

    // ---------- GetCompliance ----------

    @GetMapping("/compliance/{entityId}")
    public ResponseEntity<?> getCompliance(@RequestHeader("X-Tenant-ID") String tenantId,
                                           @PathVariable String entityId) {
        return ResponseEntity.ok(service.getCompliance(tenantId, entityId));
    }

    // ---------- SeedPacks ----------

    @PostMapping("/packs/seed")
    public ResponseEntity<?> seedPacks(@RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.seedPacks(tenantId));
    }

    // ---------- UpdateControl ----------

    @PutMapping("/controls/{controlId}")
    public ResponseEntity<?> updateControl(@RequestHeader("X-Tenant-ID") String tenantId,
                                           @PathVariable String controlId,
                                           @RequestBody UpdateControlRequest req) {
        return ResponseEntity.ok(service.updateControl(tenantId, controlId, req));
    }

    // ---------- hata yönetimi ----------

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiError> handlePolicyError(ServiceException ex) {
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
