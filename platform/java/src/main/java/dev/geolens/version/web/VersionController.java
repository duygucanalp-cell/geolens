package dev.geolens.version.web;

import dev.geolens.version.service.VersionService;
import dev.geolens.version.service.VersionServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * Versiyon takibi REST controller'ı — Go {@code version.handler} portu.
 * <p>Route'lar (go cmd/api): POST /v1/versions/entries, GET /v1/versions/entries,
 * GET /v1/versions/entries/{entryId} (R14).
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir.
 * <p>İş mantığı {@link VersionService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
public class VersionController {

    private final VersionService service;

    public VersionController(VersionService service) {
        this.service = service;
    }

    @PostMapping("/v1/versions/entries")
    public ResponseEntity<?> recordVersion(@RequestHeader("X-Tenant-ID") String tenantId,
                                           @RequestBody VersionEntryRequest req) {
        if (req == null || req.entityType() == null || req.entityType().isBlank()
                || req.entityId() == null || req.entityId().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "entity_type ve entity_id gerekli");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.recordVersion(tenantId, req));
    }

    @GetMapping("/v1/versions/entries")
    public ResponseEntity<?> listVersions(@RequestHeader("X-Tenant-ID") String tenantId,
                                          @RequestParam(value = "limit", required = false) String limitParam,
                                          @RequestParam(value = "entity_type", required = false) String entityType,
                                          @RequestParam(value = "entity_id", required = false) String entityId) {
        return ResponseEntity.ok(service.listVersions(tenantId, limitParam, entityType, entityId));
    }

    @GetMapping("/v1/versions/entries/{entryId}")
    public ResponseEntity<?> getVersionDiff(@RequestHeader("X-Tenant-ID") String tenantId,
                                            @PathVariable String entryId) {
        return ResponseEntity.ok(service.getVersionDiff(tenantId, entryId));
    }

    @ExceptionHandler(VersionServiceException.class)
    public ResponseEntity<ApiError> handleService(VersionServiceException ex) {
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
