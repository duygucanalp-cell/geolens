package dev.geolens.incident.web;

import dev.geolens.incident.service.IncidentService;
import dev.geolens.incident.service.IncidentServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Incident yönetimi REST controller'ı — Go {@code incident.handler} portu (R15).
 * <p>Route'lar (go cmd/api): GET /v1/incidents/events, POST /v1/incidents/events,
 * PUT /v1/incidents/events/{incidentId}.
 * <p>İş mantığı {@link IncidentService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/incidents")
public class IncidentController {

    private final IncidentService service;

    public IncidentController(IncidentService service) {
        this.service = service;
    }

    @GetMapping("/events")
    public ResponseEntity<?> listIncidents(@RequestHeader("X-Tenant-ID") String tenantId,
                                           @RequestParam(defaultValue = "20") int limit,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String severity) {
        return ResponseEntity.ok(service.listIncidents(tenantId, limit, status, severity));
    }

    @PostMapping("/events")
    public ResponseEntity<?> createIncident(@RequestHeader("X-Tenant-ID") String tenantId,
                                            @RequestBody CreateIncidentRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createIncident(tenantId, req));
    }

    @PutMapping("/events/{incidentId}")
    public ResponseEntity<?> updateIncident(@RequestHeader("X-Tenant-ID") String tenantId,
                                            @PathVariable String incidentId,
                                            @RequestBody UpdateIncidentRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        return ResponseEntity.ok(service.updateIncident(tenantId, incidentId, req));
    }

    // ---------- hata yönetimi ----------

    @ExceptionHandler(IncidentServiceException.class)
    public ResponseEntity<ApiError> handleService(IncidentServiceException ex) {
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
