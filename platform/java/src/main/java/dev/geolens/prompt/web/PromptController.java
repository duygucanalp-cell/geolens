package dev.geolens.prompt.web;

import dev.geolens.common.ApiError;

import dev.geolens.prompt.service.PromptService;
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
 * Prompt Audit REST controller'ı — Go {@code prompt.handler} portu (R9).
 * <p>Route'lar (go cmd/api): POST /v1/prompts/audit, GET /v1/prompts/audits,
 * GET /v1/prompts/audits/{auditId}.
 * <p>İş mantığı {@link PromptService} içindedir; bu sınıf yalnızca HTTP katmanıdır.
 */
@RestController
@RequestMapping("/v1/prompts")
public class PromptController {

    private final PromptService service;

    public PromptController(PromptService service) {
        this.service = service;
    }

    // ---------- RunAudit ----------

    @PostMapping("/audit")
    public ResponseEntity<?> runAudit(@RequestHeader("X-Tenant-ID") String tenantId,
                                      @RequestBody RunAuditRequest req) {
        if (req == null || req.promptText() == null || req.promptText().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "prompt_text gerekli");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(service.runAudit(tenantId, req));
    }

    // ---------- ListAudits ----------

    @GetMapping("/audits")
    public ResponseEntity<?> listAudits(@RequestHeader("X-Tenant-ID") String tenantId,
                                        @RequestParam(value = "limit", required = false) String limitStr,
                                        @RequestParam(value = "offset", required = false) String offsetStr,
                                        @RequestParam(value = "status", required = false) String statusFilter,
                                        @RequestParam(value = "engine", required = false) String engineFilter) {
        int limit;
        try {
            limit = limitStr == null || limitStr.isBlank() ? 0 : Integer.parseInt(limitStr);
        } catch (NumberFormatException e) {
            limit = 0;
        }
        if (limit < 1 || limit > 100) {
            limit = 20;
        }

        int offset;
        try {
            offset = offsetStr == null || offsetStr.isBlank() ? 0 : Integer.parseInt(offsetStr);
        } catch (NumberFormatException e) {
            offset = 0;
        }
        if (offset < 0) {
            offset = 0;
        }

        return ResponseEntity.ok(service.listAudits(tenantId, limit, offset, statusFilter, engineFilter));
    }

    // ---------- GetAudit ----------

    @GetMapping("/audits/{auditId}")
    public ResponseEntity<?> getAudit(@RequestHeader("X-Tenant-ID") String tenantId,
                                      @PathVariable String auditId) {
        return ResponseEntity.ok(service.getAudit(tenantId, auditId));
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
