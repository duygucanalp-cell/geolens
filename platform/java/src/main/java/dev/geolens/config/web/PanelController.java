package dev.geolens.config.web;

import dev.geolens.config.service.ConfigServiceException;
import dev.geolens.config.service.PanelService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * Panel ve prompt seti yönetimi REST controller'ı — Go {@code config.panel} portu.
 * <p>Route'lar (go cmd/api): GET/POST /v1/workspaces/{ws}/panels,
 * GET /v1/workspaces/{ws}/panels/{panelId}, GET/POST /v1/workspaces/{ws}/prompt-sets.
 * <p>Tenant {@code X-Tenant-ID} başlığından, workspace URL path'ten gelir.
 * <p>İş mantığı {@link PanelService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}")
public class PanelController {

    private final PanelService service;

    public PanelController(PanelService service) {
        this.service = service;
    }

    @GetMapping("/panels")
    public ResponseEntity<?> listPanels(@PathVariable String workspaceId,
                                        @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.listPanels(workspaceId, tenantId));
    }

    @PostMapping("/panels")
    public ResponseEntity<?> createPanel(@PathVariable String workspaceId,
                                         @RequestHeader("X-Tenant-ID") String tenantId,
                                         @RequestBody PanelRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "panel adı zorunludur");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createPanel(workspaceId, tenantId, req));
    }

    @GetMapping("/panels/{panelId}")
    public ResponseEntity<?> getPanel(@PathVariable String workspaceId,
                                      @RequestHeader("X-Tenant-ID") String tenantId,
                                      @PathVariable String panelId) {
        return ResponseEntity.ok(service.getPanel(workspaceId, tenantId, panelId));
    }

    @GetMapping("/prompt-sets")
    public ResponseEntity<?> listPromptSets(@PathVariable String workspaceId,
                                            @RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.listPromptSets(workspaceId, tenantId));
    }

    @PostMapping("/prompt-sets")
    public ResponseEntity<?> createPromptSet(@PathVariable String workspaceId,
                                             @RequestHeader("X-Tenant-ID") String tenantId,
                                             @RequestBody PromptSetRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()
                || req.promptText() == null || req.promptText().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "ad ve prompt metni zorunludur");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createPromptSet(workspaceId, tenantId, req));
    }

    @ExceptionHandler(ConfigServiceException.class)
    public ResponseEntity<ApiError> handleService(ConfigServiceException ex) {
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
