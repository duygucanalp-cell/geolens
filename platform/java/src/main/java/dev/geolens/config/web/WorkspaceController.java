package dev.geolens.config.web;

import dev.geolens.common.ApiError;

import dev.geolens.common.ServiceException;
import dev.geolens.config.service.WorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * Çalışma alanı yönetimi REST controller'ı — Go {@code config.workspace_handler} portu.
 * <p>Route'lar (go cmd/api): POST /v1/workspaces/{ws}/archive, POST /v1/workspaces/{ws}/unarchive,
 * POST /v1/workspaces/{ws}/transfer (H4).
 * <p>Tenant {@code X-Tenant-ID} başlığından, workspace URL path'ten gelir.
 * <p>İş mantığı {@link WorkspaceService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}")
public class WorkspaceController {

    private final WorkspaceService service;

    public WorkspaceController(WorkspaceService service) {
        this.service = service;
    }

    /** Go'da workspace arşivleme POST /v1/workspaces/{ws}/archive (chi katı trailing slash).
     * Spring'de /archive (no-slash) Response Archive modülüne (ArchiveController) ait olduğundan
     * workspace arşivleme /archive/ olarak eşlenir (WebConfig: opsiyonel trailing slash kapalı). */
    @PostMapping("/archive/")
    public ResponseEntity<?> archiveWorkspace(@PathVariable String workspaceId,
                                              @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.archiveWorkspace(workspaceId, tenantId));
    }

    @PostMapping("/unarchive")
    public ResponseEntity<?> unarchiveWorkspace(@PathVariable String workspaceId,
                                                @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.unarchiveWorkspace(workspaceId, tenantId));
    }

    @PostMapping("/transfer")
    public ResponseEntity<?> transferWorkspace(@PathVariable String workspaceId,
                                               @RequestHeader("X-Tenant-ID") String tenantId,
                                               @RequestBody TransferRequest req) {
        if (req == null || req.targetTenantId() == null || req.targetTenantId().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "hedef kiracı ID gerekli");
        }
        return ResponseEntity.ok(service.transferWorkspace(workspaceId, tenantId, req));
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
