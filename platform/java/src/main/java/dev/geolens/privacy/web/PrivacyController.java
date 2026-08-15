package dev.geolens.privacy.web;

import dev.geolens.privacy.service.PrivacyService;
import dev.geolens.privacy.service.PrivacyServiceException;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * KVKK/GDPR gizlilik REST controller'ı — Go {@code privacy.handler} portu.
 * <p>Route'lar (go cmd/api): GET /v1/account/data (GDPR veri taşınabilirliği),
 * POST /v1/account/deletion + POST /v1/privacy/delete (silme talebi),
 * GET /v1/deletion-requests, POST /v1/deletion-requests/{id}/process (admin).
 * <p>İş mantığı {@link PrivacyService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 * Tenant {@code X-Tenant-ID}, kullanıcı {@code X-User-ID} başlığından gelir.
 */
@RestController
public class PrivacyController {

    private final PrivacyService service;

    public PrivacyController(PrivacyService service) {
        this.service = service;
    }

    @GetMapping("/v1/account/data")
    public ResponseEntity<?> exportData(@RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
                                        @RequestHeader(value = "X-User-ID", required = false) String userId) {
        if (tenantId == null || tenantId.isBlank()) {
            return error(HttpStatus.UNAUTHORIZED, "kimlik doğrulama gerekli");
        }
        Object payload = service.exportData(tenantId, userId);
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"geolens-data-export.json\"")
                .body(payload);
    }

    @PostMapping({"/v1/account/deletion", "/v1/privacy/delete"})
    public ResponseEntity<?> requestDeletion(@RequestHeader("X-Tenant-ID") String tenantId,
                                             @RequestHeader(value = "X-User-ID", required = false) String userId,
                                             @RequestBody DeletionRequest req) {
        String uid = userId == null ? "" : userId;
        if (uid.isBlank() || tenantId == null || tenantId.isBlank()) {
            return error(HttpStatus.UNAUTHORIZED, "kimlik doğrulama gerekli");
        }
        String reason = req == null || req.reason() == null ? "" : req.reason();

        DeletionResponse resp = service.requestDeletion(tenantId, uid, reason);
        HttpStatus status = "pending".equals(resp.status()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(resp);
    }

    @GetMapping("/v1/deletion-requests")
    public ResponseEntity<?> listDeletionRequests(@RequestHeader("X-Tenant-ID") String tenantId,
                                                  @RequestHeader(value = "X-User-ID", required = false) String userId) {
        return ResponseEntity.ok(service.listDeletionRequests(tenantId, userId));
    }

    @PostMapping("/v1/deletion-requests/{id}/process")
    public ResponseEntity<?> processDeletionRequest(@RequestHeader("X-Tenant-ID") String tenantId,
                                                    @RequestHeader(value = "X-User-ID", required = false) String userId,
                                                    @PathVariable String id,
                                                    @RequestBody ProcessRequest req) {
        String action = req == null ? "" : (req.action() == null ? "" : req.action());
        String notes = req == null || req.notes() == null ? "" : req.notes();
        DeletionResponse resp = service.processDeletionRequest(tenantId, userId, id, action, notes);
        return ResponseEntity.ok(resp);
    }

    // ---------- hata yönetimi ----------

    @ExceptionHandler(PrivacyServiceException.class)
    public ResponseEntity<ApiError> handlePrivacyError(PrivacyServiceException ex) {
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
