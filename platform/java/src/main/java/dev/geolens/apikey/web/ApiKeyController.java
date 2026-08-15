package dev.geolens.apikey.web;

import dev.geolens.apikey.service.ApiKeyService;
import dev.geolens.apikey.service.ApiKeyServiceException;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * API anahtarı yönetimi REST controller'ı — Go {@code apikey.handler} portu (FR-F6).
 * <p>Route'lar (go cmd/api): GET/POST /v1/api-keys, DELETE /v1/api-keys/{keyId} (admin).
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir. Anahtar {@code gls_} ön ekli,
 * bcrypt ile hash'lenir; ham anahtar yalnızca oluşturmada döner.
 * <p>İş mantığı {@link ApiKeyService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/api-keys")
public class ApiKeyController {

    private final ApiKeyService service;

    public ApiKeyController(ApiKeyService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.list(tenantId));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestHeader("X-Tenant-ID") String tenantId,
                                    @RequestBody CreateApiKeyRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "isim zorunludur");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(tenantId, req));
    }

    @DeleteMapping("/{keyId}")
    public ResponseEntity<?> delete(@RequestHeader("X-Tenant-ID") String tenantId,
                                    @PathVariable String keyId) {
        return ResponseEntity.ok(service.delete(tenantId, keyId));
    }

    @ExceptionHandler(ApiKeyServiceException.class)
    public ResponseEntity<ApiError> handleService(ApiKeyServiceException ex) {
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
