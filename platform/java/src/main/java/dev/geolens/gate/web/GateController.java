package dev.geolens.gate.web;

import dev.geolens.common.ApiError;

import dev.geolens.gate.service.GateCheckResult;
import dev.geolens.gate.service.GateHistoryResult;
import dev.geolens.gate.service.GateService;
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

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CI/CD Governance Gate REST controller'ı — Go {@code gate.handler} portu (R6).
 * <p>Route'lar (go cmd/api): POST /v1/gate/check, GET /v1/gate/history/{entityId}.
 * <p>Yalnızca HTTP/transport katmanıdır; tüm iş mantığı {@link GateService} içindedir.
 */
@RestController
@RequestMapping("/v1/gate")
public class GateController {

    private final GateService gateService;

    public GateController(GateService gateService) {
        this.gateService = gateService;
    }

    // ---------- Check ----------

    @PostMapping("/check")
    public ResponseEntity<?> check(@RequestHeader("X-Tenant-ID") String tenantId,
                                   @RequestBody CheckRequest req) {
        if (req == null) {
            return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
        }
        GateCheckResult r = gateService.check(tenantId, req);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("check_id", r.checkId());
        body.put("entity_id", r.entityId());
        body.put("entity_type", nz(req.entityType()));
        body.put("target_env", nz(req.targetEnvironment()));
        body.put("decision", r.decision());
        body.put("passed", r.passed());
        body.put("total", r.checks().size());
        body.put("checks", r.checks());
        body.put("checked_at", DateTimeFormatter.ISO_INSTANT.format(r.checkedAt()));
        return ResponseEntity.ok(body);
    }

    // ---------- History ----------

    @GetMapping("/history/{entityId}")
    public ResponseEntity<?> history(@RequestHeader("X-Tenant-ID") String tenantId,
                                     @PathVariable String entityId) {
        GateHistoryResult r = gateService.history(tenantId, entityId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entity_id", r.entityId().isEmpty() ? entityId : r.entityId());
        body.put("tenant_id", tenantId);
        body.put("history", r.history());
        body.put("has_more", r.hasMore());
        body.put("total", r.history().size());
        return ResponseEntity.ok(body);
    }

    // ---------- yardımcılar ----------

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
