package dev.geolens.replay.web;

import dev.geolens.replay.service.ReplayService;
import dev.geolens.replay.service.ReplayServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * Conversation Replay REST controller'ı — Go {@code replay.handler} portu (FR-D12).
 * <p>Route'lar (go cmd/api): GET /v1/workspaces/{ws}/replay, GET /replay/{snapshotId},
 * POST /replay/capture, DELETE /replay/{snapshotId}, GET /replay/compare.
 * <p>Tenant {@code X-Tenant-ID} başlığından, workspace path'ten gelir; snapshot'lar
 * KVKK uyumlu silinir (FR-D12).
 * <p>İş mantığı {@link ReplayService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}/replay")
public class ReplayController {

    private final ReplayService service;

    public ReplayController(ReplayService service) {
        this.service = service;
    }

    // ---------- CaptureSnapshot ----------

    @PostMapping("/capture")
    public ResponseEntity<?> captureSnapshot(@PathVariable String workspaceId,
                                             @RequestHeader("X-Tenant-ID") String tenantId,
                                             @RequestBody CaptureRequest req) {
        if (req == null || req.brandId() == null || req.brandId().isBlank()
                || req.prompt() == null || req.prompt().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "brand_id ve prompt zorunludur");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.captureSnapshot(req.brandId(), req.prompt(), workspaceId, tenantId));
    }

    // ---------- ListSnapshots ----------

    @GetMapping
    public ResponseEntity<?> listSnapshots(@PathVariable String workspaceId,
                                           @RequestHeader("X-Tenant-ID") String tenantId,
                                           @RequestParam(value = "brand_id", required = false) String brandId) {
        return ResponseEntity.ok(service.listSnapshots(workspaceId, tenantId, brandId));
    }

    // ---------- GetSnapshot ----------

    @GetMapping("/{snapshotId}")
    public ResponseEntity<?> getSnapshot(@PathVariable String workspaceId,
                                         @RequestHeader("X-Tenant-ID") String tenantId,
                                         @PathVariable String snapshotId) {
        return ResponseEntity.ok(service.getSnapshot(workspaceId, tenantId, snapshotId));
    }

    // ---------- DeleteSnapshot ----------

    @DeleteMapping("/{snapshotId}")
    public ResponseEntity<?> deleteSnapshot(@RequestHeader("X-Tenant-ID") String tenantId,
                                            @PathVariable String snapshotId) {
        return ResponseEntity.ok(service.deleteSnapshot(tenantId, snapshotId));
    }

    // ---------- CompareSnapshots ----------

    @GetMapping("/compare")
    public ResponseEntity<?> compareSnapshots(@PathVariable String workspaceId,
                                              @RequestHeader("X-Tenant-ID") String tenantId,
                                              @RequestParam(value = "snapshot_a", required = false) String snapshotA,
                                              @RequestParam(value = "snapshot_b", required = false) String snapshotB) {
        if (snapshotA == null || snapshotA.isBlank() || snapshotB == null || snapshotB.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "snapshot_a ve snapshot_b gerekli");
        }
        return ResponseEntity.ok(service.compareSnapshots(snapshotA, snapshotB, workspaceId, tenantId));
    }

    @ExceptionHandler(ReplayServiceException.class)
    public ResponseEntity<ApiError> handleService(ReplayServiceException ex) {
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
