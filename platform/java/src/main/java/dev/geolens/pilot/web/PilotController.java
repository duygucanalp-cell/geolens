package dev.geolens.pilot.web;

import dev.geolens.pilot.service.PilotService;
import dev.geolens.pilot.service.PilotServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kurumsal Pilot REST controller'ı — Go {@code pilot.handler} portu (K4).
 * <p>Route'lar (go cmd/api): GET /v1/pilot/status, POST /v1/pilot/enroll,
 * POST /v1/pilot/extend, POST /v1/pilot/cancel, GET /v1/pilot/tenants.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir; 90 günlük deneme süresi,
 * pilot standartlarının üzerinde limitler (10 workspace / 5 engine) ve
 * otomatik ücretliye geçiş (auto_convert) sunar.
 * <p>İş mantığı {@link PilotService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/pilot")
public class PilotController {

    private final PilotService service;

    public PilotController(PilotService service) {
        this.service = service;
    }

    // ---------- GetStatus ----------

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.getStatus(tenantId));
    }

    // ---------- Enroll ----------

    @PostMapping("/enroll")
    public ResponseEntity<?> enroll(@RequestHeader("X-Tenant-ID") String tenantId,
                                    @RequestBody EnrollRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.enroll(tenantId, req));
    }

    // ---------- ExtendTrial ----------

    @PostMapping("/extend")
    public ResponseEntity<?> extendTrial(@RequestHeader("X-Tenant-ID") String tenantId,
                                         @RequestBody ExtendTrialRequest req) {
        if (req == null || req.extraDays() < 1 || req.extraDays() > 365) {
            return error(HttpStatus.BAD_REQUEST, "ek süre 1-365 gün arasında olmalıdır");
        }
        return ResponseEntity.ok(service.extendTrial(tenantId, req));
    }

    // ---------- Cancel ----------

    @PostMapping("/cancel")
    public ResponseEntity<?> cancel(@RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.cancel(tenantId));
    }

    // ---------- ListAll ----------

    @GetMapping("/tenants")
    public ResponseEntity<?> listAll() {
        return ResponseEntity.ok(service.listAll());
    }

    // ---------- hata yönetimi ----------

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    @ExceptionHandler(PilotServiceException.class)
    public ResponseEntity<ApiError> handleService(PilotServiceException ex) {
        return error(ex.status(), ex.getMessage());
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
