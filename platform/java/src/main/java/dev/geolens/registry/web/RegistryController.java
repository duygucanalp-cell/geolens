package dev.geolens.registry.web;

import dev.geolens.common.ApiError;

import dev.geolens.registry.service.RegistryService;
import dev.geolens.common.ServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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

import java.util.Map;

/**
 * AI Registry REST controller'ı — Go {@code registry.handler} portu (R1).
 * <p>Route'lar (go cmd/api): GET /v1/registry/entities, GET/PUT/DELETE /entities/{entityId},
 * POST /entities, POST /entities/{entityId}/assess.
 * <p>İş mantığı {@link RegistryService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/registry")
public class RegistryController {

    private final RegistryService service;

    public RegistryController(RegistryService service) {
        this.service = service;
    }

    // ---------- List ----------

    @GetMapping("/entities")
    public ResponseEntity<?> list(@RequestHeader("X-Tenant-ID") String tenantId,
                                  @RequestParam(value = "entity_type", required = false) String entityType,
                                  @RequestParam(value = "lifecycle_state", required = false) String lifecycle,
                                  @RequestParam(value = "risk_class", required = false) String risk) {
        return ResponseEntity.ok(Map.of("entities", service.listEntities(tenantId, entityType, lifecycle, risk)));
    }

    // ---------- Get ----------

    @GetMapping("/entities/{entityId}")
    public ResponseEntity<?> get(@RequestHeader("X-Tenant-ID") String tenantId,
                                 @PathVariable String entityId) {
        return ResponseEntity.ok(service.getEntity(tenantId, entityId));
    }

    // ---------- Create ----------

    @PostMapping("/entities")
    public ResponseEntity<?> create(@RequestHeader("X-Tenant-ID") String tenantId,
                                    @RequestBody CreateEntityRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createEntity(tenantId, req));
    }

    // ---------- Update ----------

    @PutMapping("/entities/{entityId}")
    public ResponseEntity<?> update(@RequestHeader("X-Tenant-ID") String tenantId,
                                    @PathVariable String entityId,
                                    @RequestBody UpdateEntityRequest req) {
        return ResponseEntity.ok(service.updateEntity(tenantId, entityId, req));
    }

    // ---------- Delete ----------

    @DeleteMapping("/entities/{entityId}")
    public ResponseEntity<?> delete(@RequestHeader("X-Tenant-ID") String tenantId,
                                    @PathVariable String entityId) {
        return ResponseEntity.ok(service.deleteEntity(tenantId, entityId));
    }

    // ---------- AssessRisk ----------

    @PostMapping("/entities/{entityId}/assess")
    public ResponseEntity<?> assessRisk(@RequestHeader("X-Tenant-ID") String tenantId,
                                        @PathVariable String entityId,
                                        @RequestBody AssessRiskRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.assessRisk(tenantId, entityId, req));
    }

    // ---------- hata yönetimi ----------

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiError> handleServiceError(ServiceException ex) {
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
