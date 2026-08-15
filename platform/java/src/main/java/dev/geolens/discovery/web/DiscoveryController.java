package dev.geolens.discovery.web;

import dev.geolens.common.ApiError;

import dev.geolens.discovery.service.DiscoveryService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Shadow AI Discovery REST controller'ı — Go {@code discovery.handler} portu (R2).
 * <p>Route'lar (go cmd/api): POST /v1/discovery/scan, GET /v1/discovery/scans/{scanId}.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir. Tarama arka planda çalışır (Go'daki
 * goroutine karşılığı, 30sn timeout); bulunan kaynaklar registry.entities'e de yazılır.
 * <p>İş mantığı {@link DiscoveryService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/discovery")
public class DiscoveryController {

    private final DiscoveryService service;

    public DiscoveryController(DiscoveryService service) {
        this.service = service;
    }

    // ---------- StartScan ----------

    @PostMapping("/scan")
    public ResponseEntity<?> startScan(@RequestHeader("X-Tenant-ID") String tenantId,
                                       @RequestBody StartScanRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.startScan(tenantId, req));
    }

    // ---------- GetScanResults ----------

    @GetMapping("/scans/{scanId}")
    public ResponseEntity<?> getScanResults(@RequestHeader("X-Tenant-ID") String tenantId,
                                            @PathVariable String scanId) {
        return ResponseEntity.ok(service.getScanResults(tenantId, scanId));
    }

    // ---------- hata yönetimi ----------

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
