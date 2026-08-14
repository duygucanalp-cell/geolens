package dev.geolens.guardrail.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.geolens.guardrail.GuardRule;
import dev.geolens.guardrail.GuardrailMatcher;
import dev.geolens.guardrail.Rule;
import dev.geolens.util.Ulid;
import org.jooq.DSLContext;
import org.jooq.Record;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime Guardrails REST controller'ı — Go {@code guardrail.handler} portu (R3).
 * <p>Route'lar (go cmd/api): GET/POST /v1/guardrails/rules, PUT /v1/guardrails/rules/{ruleId}/toggle,
 * DELETE /v1/guardrails/rules/{ruleId}, POST /v1/guardrails/seed-defaults, POST /v1/guardrails/evaluate.
 * <p>Tenant {@code X-Tenant-ID} başlığından gelir; eşleşen ihlallerde {@code guardrail.violation}
 * olayı outbox üzerinden {@code q:governance} stream'ine taşınır (O-6, deterministik idempotency anahtarıyla).
 */
@RestController
@RequestMapping("/v1/guardrails")
public class GuardrailController {

    private final DSLContext dsl;
    private final ObjectMapper mapper = new ObjectMapper();

    public GuardrailController(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ---------- ListRules ----------

    @GetMapping("/rules")
    public ResponseEntity<?> listRules(@RequestHeader("X-Tenant-ID") String tenantId) {
        List<Rule> rules;
        try {
            rules = queryRules(tenantId);
        } catch (RuntimeException e) {
            return ResponseEntity.ok(Map.of("rules", List.of()));
        }

        // R3: tenant hiç kural oluşturmamışsa varsayılanları yükle
        if (rules.isEmpty()) {
            seedDefaults(tenantId);
            rules = queryRules(tenantId);
        }

        return ResponseEntity.ok(Map.of("rules", rules));
    }

    // ---------- CreateRule ----------

    @PostMapping("/rules")
    public ResponseEntity<?> createRule(@RequestHeader("X-Tenant-ID") String tenantId,
                                        @RequestBody CreateRuleRequest req) {
        String action = req.action() == null || req.action().isBlank() ? "block" : req.action();
        String severity = req.severity() == null || req.severity().isBlank() ? "high" : req.severity();

        Rule rule;
        try {
            Record r = dsl.fetchOne("""
                    INSERT INTO guardrail.rules (tenant_id, name, category, pattern, action, severity)
                    VALUES (?, ?, ?, ?, ?, ?)
                    RETURNING id, tenant_id, name, category, pattern, action, severity, enabled, created_at, updated_at
                    """, tenantId, nz(req.name()), nz(req.category()), nz(req.pattern()), action, severity);
            rule = toRule(r == null ? Map.of() : r.intoMap());
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "kural oluşturulamadı");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(rule);
    }

    // ---------- Evaluate ----------

    @PostMapping("/evaluate")
    public ResponseEntity<?> evaluate(@RequestHeader("X-Tenant-ID") String tenantId,
                                      @RequestBody EvaluateRequest req) {
        long start = System.currentTimeMillis();

        List<GuardRule> rules;
        try {
            rules = queryGuardRules(tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "kural sorgu hatası");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        boolean blocked = false;

        for (GuardRule rule : rules) {
            boolean matched = GuardrailMatcher.evaluateRule(rule, req.prompt(), req.response());

            String actionTaken = "none";
            if (matched) {
                actionTaken = rule.action();
                if ("block".equals(rule.action())) {
                    blocked = true;
                }
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rule_id", rule.id());
            item.put("rule_name", rule.name());
            item.put("category", rule.category());
            item.put("matched", matched);
            item.put("action_taken", actionTaken);
            results.add(item);

            logEvaluation(tenantId, rule.id(), req.prompt(), req.response(), matched,
                    actionTaken, System.currentTimeMillis() - start);

            // O-6: guardrail ihlali olayını outbox üzerinden taşı — içerik türevli
            // deterministik anahtar tekrarlanan evaluate çağrılarını tek olaya indirger.
            if (matched && !"none".equals(actionTaken)) {
                enqueueViolation(tenantId, rule, actionTaken, req.prompt(), req.response());
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("results", results);
        resp.put("blocked", blocked);
        resp.put("allowed", !blocked);

        HttpStatus status = blocked ? HttpStatus.FORBIDDEN : HttpStatus.OK;
        return ResponseEntity.status(status).body(resp);
    }

    // ---------- DeleteRule ----------

    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<?> deleteRule(@RequestHeader("X-Tenant-ID") String tenantId,
                                        @PathVariable String ruleId) {
        try {
            dsl.execute("DELETE FROM guardrail.rules WHERE id = ? AND tenant_id = ?", ruleId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "silme hatası");
        }
        return ResponseEntity.ok(Map.of("status", "silindi"));
    }

    // ---------- ToggleRule ----------

    @PutMapping("/rules/{ruleId}/toggle")
    public ResponseEntity<?> toggleRule(@RequestHeader("X-Tenant-ID") String tenantId,
                                        @PathVariable String ruleId,
                                        @RequestBody ToggleRuleRequest req) {
        try {
            dsl.execute("UPDATE guardrail.rules SET enabled = ?, updated_at = now() WHERE id = ? AND tenant_id = ?",
                    req.enabled(), ruleId, tenantId);
        } catch (RuntimeException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "kural güncellenemedi");
        }
        return ResponseEntity.ok(Map.of("status", "güncellendi", "enabled", req.enabled() ? "true" : "false"));
    }

    // ---------- SeedDefaults ----------

    @PostMapping("/seed-defaults")
    public ResponseEntity<?> seedDefaultsHandler(@RequestHeader("X-Tenant-ID") String tenantId) {
        seedDefaults(tenantId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "varsayılan kurallar oluşturuldu"));
    }

    // ---------- yardımcılar ----------

    private void seedDefaults(String tenantId) {
        List<Rule> defaults = List.of(
                new Rule(null, null, "SQL Injection", "prompt_injection", "/ignore previous instructions/", "block", "critical", true, null, null),
                new Rule(null, null, "Prompt Leak", "prompt_injection", "/reveal your prompt/", "block", "critical", true, null, null),
                new Rule(null, null, "Email Leak", "pii_leakage", "/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\\\.[a-zA-Z]{2,}/", "block", "high", true, null, null),
                new Rule(null, null, "Phone Leak", "pii_leakage", "/\\\\+?[0-9]{10,15}/", "block", "high", true, null, null),
                new Rule(null, null, "Toxic Content", "toxic_output", "/\\\\b(hate|discriminate|violent|threat)\\\\b/i", "flag", "medium", true, null, null),
                new Rule(null, null, "Credit Card", "pii_leakage", "/\\\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14})\\\\b/", "block", "critical", true, null, null),
                new Rule(null, null, "API Key Leak", "pii_leakage", "/\\\\b(sk-[a-zA-Z0-9]{20,}|ghp_[a-zA-Z0-9]{36,})\\\\b/", "block", "critical", true, null, null),
                new Rule(null, null, "Hallucination Pattern", "hallucination", "/\\\\bI am not aware\\\\b|\\\\bI cannot confirm\\\\b/i", "flag", "medium", true, null, null));

        for (Rule d : defaults) {
            try {
                dsl.execute("""
                        INSERT INTO guardrail.rules (tenant_id, name, category, pattern, action, severity, enabled, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())
                        ON CONFLICT DO NOTHING
                        """, tenantId, d.name(), d.category(), d.pattern(), d.action(), d.severity(), d.enabled());
            } catch (RuntimeException e) {
                // Go'da loglanıp geçilir
            }
        }
    }

    private void logEvaluation(String tenantId, String ruleId, String prompt, String response,
                               boolean matched, String actionTaken, long durationMs) {
        try {
            dsl.execute("""
                    INSERT INTO guardrail.evaluations (tenant_id, rule_id, prompt, response, matched, action_taken, duration_ms)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, tenantId, ruleId, nz(prompt), nz(response), matched, actionTaken, durationMs);
        } catch (RuntimeException e) {
            // Go'da debug loglanıp geçilir
        }
    }

    private void enqueueViolation(String tenantId, GuardRule rule, String actionTaken,
                                  String prompt, String response) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rule_id", rule.id());
        payload.put("rule_name", rule.name());
        payload.put("category", rule.category());
        payload.put("severity", rule.severity());
        payload.put("action_taken", actionTaken);
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            return;
        }
        try {
            dsl.execute("""
                    INSERT INTO public.event_outbox (id, event_type, stream, payload, tenant_id, idempotency_key, created_at)
                    VALUES (?, 'guardrail.violation', 'q:governance', ?::jsonb, ?, ?, now())
                    """, Ulid.generate(), json, tenantId,
                    GuardrailMatcher.idempotencyKey(tenantId, rule.id(), prompt, response));
        } catch (RuntimeException e) {
            // Go'da warn loglanıp geçilir
        }
    }

    private List<Rule> queryRules(String tenantId) {
        List<Map<String, Object>> rows = dsl.fetch("""
                SELECT id, tenant_id, name, category, pattern, action, severity, enabled, created_at, updated_at
                FROM guardrail.rules WHERE tenant_id = ? ORDER BY category, name
                """, tenantId).intoMaps();
        List<Rule> rules = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            rules.add(toRule(r));
        }
        return rules;
    }

    private List<GuardRule> queryGuardRules(String tenantId) {
        List<Map<String, Object>> rows = dsl.fetch("""
                SELECT id, name, category, pattern, action, severity
                FROM guardrail.rules WHERE tenant_id = ? AND enabled = true
                """, tenantId).intoMaps();
        List<GuardRule> rules = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            rules.add(new GuardRule(
                    str(r.get("id")), str(r.get("name")), str(r.get("category")),
                    str(r.get("pattern")), str(r.get("action")), str(r.get("severity"))));
        }
        return rules;
    }

    private static Rule toRule(Map<String, Object> r) {
        return new Rule(
                str(r.get("id")), str(r.get("tenant_id")), str(r.get("name")), str(r.get("category")),
                str(r.get("pattern")), str(r.get("action")), str(r.get("severity")),
                r.get("enabled") != null && Boolean.TRUE.equals(r.get("enabled")),
                str(r.get("created_at")), str(r.get("updated_at")));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String str(Object o) {
        if (o == null) {
            return "";
        }
        if (o instanceof java.sql.Timestamp t) {
            return t.toInstant().toString();
        }
        return String.valueOf(o);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "geçersiz istek");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message));
    }
}
