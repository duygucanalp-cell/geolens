package dev.geolens.agent.web;

import dev.geolens.common.ApiError;

import dev.geolens.agent.service.AgentService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent tracing REST controller'ı — Go {@code agent.handler} portu (R8).
 * <p>Route'lar (go cmd/api, /v1/workspaces/{ws} altında): POST /agents/traces,
 * GET /agents/traces/{traceId}, GET /agents/traces, POST /agents/traces/{traceId}/steps,
 * POST /agents/traces/{traceId}/complete.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir; workspace yalnızca URL'de bulunur
 * (Go handler'ı workspace'i kullanmaz — birebir korundu).
 * <p>İş mantığı {@link AgentService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/workspaces/{workspaceId}/agents")
public class AgentController {

    private final AgentService service;

    public AgentController(AgentService service) {
        this.service = service;
    }

    // ---------- StartTrace ----------

    @PostMapping("/traces")
    public ResponseEntity<?> startTrace(@RequestHeader("X-Tenant-ID") String tenantId,
                                        @RequestBody StartTraceRequest req) {
        if (req == null || req.agentName() == null || req.agentName().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "agent_name gerekli");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.startTrace(tenantId, req));
    }

    // ---------- GetTrace ----------

    @GetMapping("/traces/{traceId}")
    public ResponseEntity<?> getTrace(@RequestHeader("X-Tenant-ID") String tenantId,
                                      @PathVariable String traceId) {
        return ResponseEntity.ok(service.getTrace(tenantId, traceId));
    }

    // ---------- RecordStep ----------

    @PostMapping("/traces/{traceId}/steps")
    public ResponseEntity<?> recordStep(@RequestHeader("X-Tenant-ID") String tenantId,
                                        @PathVariable String traceId,
                                        @RequestBody RecordStepRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.recordStep(tenantId, traceId, req));
    }

    // ---------- CompleteTrace ----------

    @PostMapping("/traces/{traceId}/complete")
    public ResponseEntity<?> completeTrace(@RequestHeader("X-Tenant-ID") String tenantId,
                                           @PathVariable String traceId,
                                           @RequestBody CompleteTraceRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        return ResponseEntity.ok(service.completeTrace(tenantId, traceId, req));
    }

    // ---------- ListTraces ----------

    @GetMapping("/traces")
    public ResponseEntity<?> listTraces(@RequestHeader("X-Tenant-ID") String tenantId,
                                        @RequestParam(value = "limit", required = false) String limitStr,
                                        @RequestParam(value = "status", required = false) String statusFilter,
                                        @RequestParam(value = "offset", required = false) String offsetStr) {
        int limit;
        try {
            limit = Integer.parseInt(limitStr);
        } catch (RuntimeException e) {
            limit = 20;
        }
        if (limit < 1 || limit > 100) {
            limit = 20;
        }

        int offset;
        try {
            offset = Integer.parseInt(offsetStr);
        } catch (RuntimeException e) {
            offset = 0;
        }
        if (offset < 0) {
            offset = 0;
        }

        return ResponseEntity.ok(service.listTraces(tenantId, limit, statusFilter, offset));
    }

    // ---------- hata yönetimi ----------

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiError> handleServiceError(ServiceException ex) {
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
