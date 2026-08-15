package dev.geolens.guardrail.web;

import dev.geolens.guardrail.Rule;
import dev.geolens.guardrail.service.GuardrailEvaluateResult;
import dev.geolens.guardrail.service.GuardrailService;
import dev.geolens.guardrail.service.GuardrailServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime Guardrails REST controller'ı — Go {@code guardrail.handler} portu (R3).
 * <p>Route'lar (go cmd/api): GET/POST /v1/guardrails/rules, PUT /v1/guardrails/rules/{ruleId}/toggle,
 * DELETE /v1/guardrails/rules/{ruleId}, POST /v1/guardrails/seed-defaults, POST /v1/guardrails/evaluate.
 * <p>Yalnızca HTTP/transport katmanıdır; tüm iş mantığı {@link GuardrailService} içindedir.
 */
@RestController
@RequestMapping("/v1/guardrails")
public class GuardrailController {

    private final GuardrailService guardrailService;

    public GuardrailController(GuardrailService guardrailService) {
        this.guardrailService = guardrailService;
    }

    // ---------- ListRules ----------

    @GetMapping("/rules")
    public ResponseEntity<?> listRules(@RequestHeader("X-Tenant-ID") String tenantId) {
        List<Rule> rules = guardrailService.listRules(tenantId);
        return ResponseEntity.ok(Map.of("rules", rules));
    }

    // ---------- CreateRule ----------

    @PostMapping("/rules")
    public ResponseEntity<?> createRule(@RequestHeader("X-Tenant-ID") String tenantId,
                                        @RequestBody CreateRuleRequest req) {
        Rule rule = guardrailService.createRule(tenantId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(rule);
    }

    // ---------- Evaluate ----------

    @PostMapping("/evaluate")
    public ResponseEntity<?> evaluate(@RequestHeader("X-Tenant-ID") String tenantId,
                                      @RequestBody EvaluateRequest req) {
        GuardrailEvaluateResult r = guardrailService.evaluate(tenantId, req);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("results", r.results());
        resp.put("blocked", r.blocked());
        resp.put("allowed", r.allowed());

        HttpStatus status = r.blocked() ? HttpStatus.FORBIDDEN : HttpStatus.OK;
        return ResponseEntity.status(status).body(resp);
    }

    // ---------- DeleteRule ----------

    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<?> deleteRule(@RequestHeader("X-Tenant-ID") String tenantId,
                                        @PathVariable String ruleId) {
        guardrailService.deleteRule(tenantId, ruleId);
        return ResponseEntity.ok(Map.of("status", "silindi"));
    }

    // ---------- ToggleRule ----------

    @PutMapping("/rules/{ruleId}/toggle")
    public ResponseEntity<?> toggleRule(@RequestHeader("X-Tenant-ID") String tenantId,
                                        @PathVariable String ruleId,
                                        @RequestBody ToggleRuleRequest req) {
        guardrailService.toggleRule(tenantId, ruleId, req.enabled());
        return ResponseEntity.ok(Map.of("status", "güncellendi", "enabled", req.enabled() ? "true" : "false"));
    }

    // ---------- SeedDefaults ----------

    @PostMapping("/seed-defaults")
    public ResponseEntity<?> seedDefaultsHandler(@RequestHeader("X-Tenant-ID") String tenantId) {
        guardrailService.seedDefaults(tenantId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "varsayılan kurallar oluşturuldu"));
    }

    // ---------- hata yönetimi ----------

    @ExceptionHandler(GuardrailServiceException.class)
    public ResponseEntity<ApiError> handleService(GuardrailServiceException ex) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}