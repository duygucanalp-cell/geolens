package dev.geolens.cost.web;

import dev.geolens.cost.service.CostService;
import dev.geolens.cost.service.CostServiceException;
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
 * Maliyet analitiği REST controller'ı — Go {@code cost.handler} portu (R11).
 * <p>Route'lar (go cmd/api): POST /v1/costs/entries, GET /v1/costs/entries, GET /v1/costs/summary.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir.
 * <p>İş mantığı {@link CostService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/costs")
public class CostController {

    private final CostService service;

    public CostController(CostService service) {
        this.service = service;
    }

    // ---------- RecordCost ----------

    @PostMapping("/entries")
    public ResponseEntity<?> recordCost(@RequestHeader("X-Tenant-ID") String tenantId,
                                        @RequestBody RecordCostRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        if (req.engineName() == null || req.engineName().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "engine_name gerekli");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.recordCost(tenantId, req));
    }

    // ---------- ListCosts ----------

    @GetMapping("/entries")
    public ResponseEntity<?> listCosts(@RequestHeader("X-Tenant-ID") String tenantId,
                                       @RequestParam(value = "limit", required = false, defaultValue = "20") int limit,
                                       @RequestParam(value = "engine", required = false) String engine) {
        return ResponseEntity.ok(service.listCosts(tenantId, limit, engine));
    }

    // ---------- GetCostSummary ----------

    @GetMapping("/summary")
    public ResponseEntity<?> getCostSummary(@RequestHeader("X-Tenant-ID") String tenantId,
                                            @RequestParam(value = "period", required = false, defaultValue = "7d") String period) {
        return ResponseEntity.ok(service.getCostSummary(tenantId, period));
    }

    // ---------- hata yönetimi ----------

    @ExceptionHandler(CostServiceException.class)
    public ResponseEntity<ApiError> handleCostError(CostServiceException ex) {
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
