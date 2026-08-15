package dev.geolens.bias.web;

import dev.geolens.bias.service.BiasService;
import dev.geolens.bias.service.BiasServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bias/Fairness REST controller'ı — Go {@code bias.handler} portu (R5).
 * <p>Route'lar (go cmd/api): POST /v1/bias/evaluate, GET /v1/bias/tests.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir.
 * <p>İş mantığı {@link BiasService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/bias")
public class BiasController {

    private final BiasService service;

    public BiasController(BiasService service) {
        this.service = service;
    }

    // ---------- Evaluate ----------

    @PostMapping("/evaluate")
    public ResponseEntity<?> evaluate(@RequestHeader("X-Tenant-ID") String tenantId,
                                      @RequestBody EvaluateRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.evaluate(tenantId, req));
    }

    // ---------- ListTests ----------

    @GetMapping("/tests")
    public ResponseEntity<?> listTests(@RequestHeader("X-Tenant-ID") String tenantId,
                                       @RequestParam(value = "model_id", required = false) String modelId,
                                       @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {
        return ResponseEntity.ok(service.listTests(tenantId, modelId, limit));
    }

    @ExceptionHandler(BiasServiceException.class)
    public ResponseEntity<ApiError> handleService(BiasServiceException ex) {
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
