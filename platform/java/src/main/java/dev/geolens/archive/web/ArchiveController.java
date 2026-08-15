package dev.geolens.archive.web;

import dev.geolens.common.ApiError;

import dev.geolens.archive.service.ArchiveService;
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
 * Response Archive REST controller'ı — Go {@code archive.handler} portu (FR-D13).
 * <p>Route'lar (go cmd/api): GET /v1/workspaces/{ws}/archive, GET /archive/{entryId},
 * POST /archive, GET /archive/versions.
 * <p>Tenant {@code X-Tenant-ID} başlığından, workspace path'ten gelir; yanıtlar
 * SHA-256 hash + versiyonlama ile arşivlenir.
 * <p>İş mantığı {@link ArchiveService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}/archive")
public class ArchiveController {

    private final ArchiveService service;

    public ArchiveController(ArchiveService service) {
        this.service = service;
    }

    // ---------- ListEntries ----------

    @GetMapping
    public ResponseEntity<?> listEntries(@PathVariable String workspaceId,
                                         @RequestHeader("X-Tenant-ID") String tenantId,
                                         @RequestParam(value = "brand_id", required = false) String brandId,
                                         @RequestParam(value = "engine", required = false) String engineName,
                                         @RequestParam(value = "version", required = false) String versionStr) {
        return ResponseEntity.ok(service.listEntries(workspaceId, tenantId, brandId, engineName, versionStr));
    }

    // ---------- GetEntry ----------

    @GetMapping("/{entryId}")
    public ResponseEntity<?> getEntry(@PathVariable String workspaceId,
                                      @RequestHeader("X-Tenant-ID") String tenantId,
                                      @PathVariable String entryId) {
        return ResponseEntity.ok(service.getEntry(workspaceId, tenantId, entryId));
    }

    // ---------- ArchiveResponse ----------

    @PostMapping
    public ResponseEntity<?> archiveResponse(@PathVariable String workspaceId,
                                             @RequestHeader("X-Tenant-ID") String tenantId,
                                             @RequestBody ArchiveRequest req) {
        if (req == null || req.brandId() == null || req.brandId().isBlank()
                || req.response() == null || req.response().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id ve response zorunludur");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.archiveResponse(workspaceId, tenantId, req));
    }

    // ---------- GetVersionHistory ----------

    @GetMapping("/versions")
    public ResponseEntity<?> getVersionHistory(@PathVariable String workspaceId,
                                               @RequestHeader("X-Tenant-ID") String tenantId,
                                               @RequestParam(value = "brand_id", required = false) String brandId,
                                               @RequestParam(value = "engine", required = false) String engineName) {
        if (brandId == null || brandId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id gerekli");
        }
        return ResponseEntity.ok(service.getVersionHistory(workspaceId, tenantId, brandId, engineName));
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
