package dev.geolens.drift.web;

import dev.geolens.common.ApiError;

import dev.geolens.drift.Observation;
import dev.geolens.drift.service.DriftService;
import dev.geolens.common.ServiceException;
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

import java.util.Map;

/**
 * Drift tespiti REST controller'ı — Go {@code drift.handler} portu (R17).
 * <p>Route'lar (go cmd/api): POST /v1/drift/record, GET /v1/drift/observations,
 * GET /v1/drift/entities, GET /v1/drift/analysis, GET /v1/drift/alerts.
 * <p>İş mantığı {@link DriftService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/drift")
public class DriftController {

    private final DriftService service;

    public DriftController(DriftService service) {
        this.service = service;
    }

    // ---------- Record ----------

    @PostMapping("/record")
    public ResponseEntity<?> record(@RequestHeader("X-Tenant-ID") String tenantId,
                                    @RequestBody RecordObservationRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        if (req.entityId() == null || req.entityId().isBlank()
                || req.metric() == null || req.metric().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "entity_id ve metric zorunludur");
        }

        Observation obs = service.record(tenantId, req.entityId(), req.entityName(),
                req.metric(), req.value(), req.windowStart());
        return ResponseEntity.status(HttpStatus.CREATED).body(obs);
    }

    // ---------- ListObservations ----------

    @GetMapping("/observations")
    public ResponseEntity<?> listObservations(@RequestHeader("X-Tenant-ID") String tenantId,
                                              @RequestParam(value = "entity_id", required = false) String entityId,
                                              @RequestParam(value = "metric", required = false) String metric,
                                              @RequestParam(value = "limit", required = false) String limit) {
        String eid = entityId == null ? "" : entityId;
        String met = metric == null ? "" : metric;
        if (eid.isEmpty() || met.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "entity_id ve metric parametreleri zorunludur");
        }

        return ResponseEntity.ok(service.listObservations(tenantId, eid, met, limit));
    }

    // ---------- ListEntities ----------

    @GetMapping("/entities")
    public ResponseEntity<Map<String, Object>> listEntities(@RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.listEntities(tenantId));
    }

    // ---------- Analyze ----------

    @GetMapping("/analysis")
    public ResponseEntity<?> analyze(@RequestHeader("X-Tenant-ID") String tenantId,
                                     @RequestParam(value = "entity_id", required = false) String entityId,
                                     @RequestParam(value = "metric", required = false) String metric,
                                     @RequestParam(value = "threshold", required = false) String threshold) {
        String eid = entityId == null ? "" : entityId;
        String met = metric == null ? "" : metric;
        if (eid.isEmpty() || met.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "entity_id ve metric parametreleri zorunludur");
        }

        return ResponseEntity.ok(service.analyze(tenantId, eid, met, threshold));
    }

    // ---------- ListAlerts ----------

    @GetMapping("/alerts")
    public ResponseEntity<Map<String, Object>> listAlerts(@RequestHeader("X-Tenant-ID") String tenantId) {
        return ResponseEntity.ok(service.listAlerts(tenantId));
    }

    // ---------- hata yönetimi ----------

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiError> handleService(ServiceException ex) {
        return error(ex.status(), ex.getMessage());
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
